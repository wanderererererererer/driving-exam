package com.driving.exam.server.coach.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.entity.CoachStudent;
import com.driving.exam.pojo.entity.StudentSubjectStatus;
import com.driving.exam.pojo.vo.AnalysisOverviewVO;
import com.driving.exam.pojo.vo.ExamRecordVO;
import com.driving.exam.pojo.vo.KPStatVO;
import com.driving.exam.pojo.vo.WrongBookSummaryVO;
import com.driving.exam.server.analysis.AnalysisService;
import com.driving.exam.server.coach.AiSuggestVO;
import com.driving.exam.server.exam.ExamService;
import com.driving.exam.server.integration.deepseek.DeepSeekClient;
import com.driving.exam.server.integration.deepseek.dto.ChatResult;
import com.driving.exam.server.mapper.CoachStudentMapper;
import com.driving.exam.server.mapper.StudentSubjectStatusMapper;
import com.driving.exam.server.mapper.UserMapper;
import com.driving.exam.server.service.WrongBookService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 教练端 AI 学习建议与确认的离线单元测试。
 *
 * <p>不启动 Spring、不连接数据库、不调用真实 DeepSeek。AI 客户端被 Mockito 桩住，
 * 用于验证「AI 成功」「AI 失败降级」「归属失败不触发 AI」「确认状态流转」四类契约。</p>
 */
@DisplayName("教练端 AI 建议与确认（单元，Mockito）")
class CoachStudentServiceImplAiSuggestTest {

    private static final Long COACH_A = 101L;
    private static final Long COACH_B = 202L;
    private static final Long STUDENT_A = 301L;
    private static final Long STUDENT_B = 302L;
    private static final Long BIND_A = 401L;
    private static final String SUBJECT = "SUBJECT_1";

    private CoachStudentMapper coachStudentMapper;
    private UserMapper userMapper;
    private StudentSubjectStatusMapper studentSubjectStatusMapper;
    private AnalysisService analysisService;
    private WrongBookService wrongBookService;
    private ExamService examService;
    private DeepSeekClient deepSeekClient;
    private CoachStudentServiceImpl service;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                CoachStudent.class);
    }

    @BeforeEach
    void setUp() {
        coachStudentMapper = mock(CoachStudentMapper.class);
        userMapper = mock(UserMapper.class);
        studentSubjectStatusMapper = mock(StudentSubjectStatusMapper.class);
        analysisService = mock(AnalysisService.class);
        wrongBookService = mock(WrongBookService.class);
        examService = mock(ExamService.class);
        deepSeekClient = mock(DeepSeekClient.class);

        service = new CoachStudentServiceImpl(
                coachStudentMapper,
                userMapper,
                studentSubjectStatusMapper,
                mock(PasswordEncoder.class),
                analysisService,
                wrongBookService,
                examService,
                deepSeekClient);
    }

    @Test
    @DisplayName("AI 成功：返回 AI 文本并持久化 ai_suggest；规则建议同时落库；重置确认状态")
    void aiSuccess_persistsAiSuggestion() {
        stubValidLearningData();
        when(deepSeekClient.chat(anyList()))
                .thenReturn(new ChatResult("建议先复习信号灯并完成章节练习。", "deepseek-flash", 10, 20));
        when(studentSubjectStatusMapper.upsertAiSuggestion(
                eq(STUDENT_A), eq(SUBJECT), eq("READY"), eq("建议先复习信号灯并完成章节练习。"),
                any(LocalDateTime.class))).thenReturn(1);
        when(studentSubjectStatusMapper.selectOne(any()))
                .thenReturn(statusRow("READY", "建议先复习信号灯并完成章节练习。", "CONFIRMED_READY"));

        AiSuggestVO result = service.generateAiSuggest(COACH_A, BIND_A, "subject_1");

        assertThat(result.getStudentId()).isEqualTo(STUDENT_A);
        assertThat(result.getSubjectCode()).isEqualTo(SUBJECT);
        assertThat(result.getExamReadySuggest()).isEqualTo("READY");
        assertThat(result.getSuggestion()).isEqualTo("建议先复习信号灯并完成章节练习。");
        assertThat(result.getSource()).isEqualTo(AiSuggestVO.SOURCE_AI);
        assertThat(result.getAiSuccess()).isTrue();
        assertThat(result.getDegraded()).isFalse();
        assertThat(result.getDegradedReason()).isNull();
        assertThat(result.getAiSuggestTime()).isNotNull();
        assertThat(result.getCoachConfirmStatus()).isEqualTo("CONFIRMED_READY");

        verify(deepSeekClient).chat(anyList());
        verify(studentSubjectStatusMapper).upsertAiSuggestion(
                eq(STUDENT_A), eq(SUBJECT), eq("READY"), eq("建议先复习信号灯并完成章节练习。"),
                any(LocalDateTime.class));
        verifyNoInteractions(userMapper);
    }

    @Test
    @DisplayName("AI 超时：降级为规则建议，ai_suggest 与 ai_suggest_time 不写成功值")
    void aiTimeout_degradesToRuleWithoutFakeSuccess() {
        stubValidLearningData();
        when(analysisService.overview(STUDENT_A, SUBJECT))
                .thenReturn(overview(10, 2, 0.5, 0.2, 1));
        when(analysisService.knowledge(STUDENT_A, SUBJECT))
                .thenReturn(List.of(kp("信号灯", true, 0.3)));
        when(deepSeekClient.chat(anyList()))
                .thenThrow(new BusinessException(ErrorCode.AI_TIMEOUT));

        AiSuggestVO result = service.generateAiSuggest(COACH_A, BIND_A, SUBJECT);

        assertThat(result.getSource()).isEqualTo(AiSuggestVO.SOURCE_RULE);
        assertThat(result.getAiSuccess()).isFalse();
        assertThat(result.getDegraded()).isTrue();
        assertThat(result.getDegradedReason()).isEqualTo("AI_TIMEOUT");
        assertThat(result.getSuggestion()).isNotBlank();
        assertThat(result.getAiSuggestTime()).isNull();
        assertThat(result.getCoachConfirmStatus()).isNull();

        verify(studentSubjectStatusMapper).upsertAiSuggestion(
                eq(STUDENT_A), eq(SUBJECT), eq("NEED_HELP"), isNull(), isNull());
        verify(studentSubjectStatusMapper, never()).upsertCoachConfirm(any(), any(), any());
    }

    @Test
    @DisplayName("AI 调用失败：降级标识为 AI_CALL_FAILED")
    void aiCallFailed_degrades() {
        stubValidLearningData();
        when(deepSeekClient.chat(anyList()))
                .thenThrow(new BusinessException(ErrorCode.AI_CALL_FAILED));

        AiSuggestVO result = service.generateAiSuggest(COACH_A, BIND_A, SUBJECT);

        assertThat(result.getAiSuccess()).isFalse();
        assertThat(result.getSource()).isEqualTo(AiSuggestVO.SOURCE_RULE);
        assertThat(result.getDegradedReason()).isEqualTo("AI_CALL_FAILED");
        assertThat(result.getSuggestion()).isNotBlank();
    }

    @Test
    @DisplayName("绑定不存在：返回 3102，且不调用 AI/学情/错题服务")
    void missingBinding_throws3102_withoutAiCall() {
        when(coachStudentMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = catchThrowableOfType(
                () -> service.generateAiSuggest(COACH_A, 999L, SUBJECT),
                BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(3102);
        verifyNoInteractions(deepSeekClient, analysisService, wrongBookService, examService,
                studentSubjectStatusMapper);
    }

    @Test
    @DisplayName("其他教练绑定：返回 3102，且不调用 AI")
    void otherCoachBinding_throws3102_withoutAiCall() {
        when(coachStudentMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = catchThrowableOfType(
                () -> service.generateAiSuggest(COACH_B, BIND_A, SUBJECT),
                BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(3102);
        assertOwnershipQueryContains(COACH_B, BIND_A);
        verifyNoInteractions(deepSeekClient, analysisService, wrongBookService, examService,
                studentSubjectStatusMapper);
    }

    @Test
    @DisplayName("已解绑：返回 3102，且不调用 AI")
    void releasedBinding_throws3102_withoutAiCall() {
        when(coachStudentMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = catchThrowableOfType(
                () -> service.generateAiSuggest(COACH_A, BIND_A, SUBJECT),
                BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(3102);
        assertOwnershipQueryContains(COACH_A, BIND_A);
        verifyNoInteractions(deepSeekClient, analysisService, wrongBookService, examService,
                studentSubjectStatusMapper);
    }

    @Test
    @DisplayName("确认成功：独立确认不读取建议行，直接 upsert 确认状态")
    void confirmSuccess_updatesStatus() {
        when(coachStudentMapper.selectOne(any())).thenReturn(bind(BIND_A, COACH_A, STUDENT_A));
        when(studentSubjectStatusMapper.upsertCoachConfirm(STUDENT_A, SUBJECT, "CONFIRMED_READY"))
                .thenReturn(1);

        service.confirmSuggestion(COACH_A, BIND_A, SUBJECT, "CONFIRMED_READY");

        verify(studentSubjectStatusMapper).upsertCoachConfirm(
                STUDENT_A, SUBJECT, "CONFIRMED_READY");
        verifyNoInteractions(deepSeekClient);
    }

    @Test
    @DisplayName("已确认后修改：允许改为 NOT_READY，不要求建议存在")
    void existingConfirm_canBeChanged() {
        when(coachStudentMapper.selectOne(any())).thenReturn(bind(BIND_A, COACH_A, STUDENT_A));
        when(studentSubjectStatusMapper.upsertCoachConfirm(STUDENT_A, SUBJECT, "NOT_READY"))
                .thenReturn(1);

        service.confirmSuggestion(COACH_A, BIND_A, SUBJECT, "NOT_READY");

        verify(studentSubjectStatusMapper).upsertCoachConfirm(STUDENT_A, SUBJECT, "NOT_READY");
    }

    @Test
    @DisplayName("无建议：确认仍可创建确认状态，不依赖 AI 建议")
    void confirmWithoutSuggestion_isAllowed() {
        when(coachStudentMapper.selectOne(any())).thenReturn(bind(BIND_A, COACH_A, STUDENT_A));
        when(studentSubjectStatusMapper.upsertCoachConfirm(STUDENT_A, SUBJECT, "NEED_HELP"))
                .thenReturn(1);

        service.confirmSuggestion(COACH_A, BIND_A, SUBJECT, "NEED_HELP");

        verify(studentSubjectStatusMapper).upsertCoachConfirm(STUDENT_A, SUBJECT, "NEED_HELP");
        verifyNoInteractions(deepSeekClient);
    }

    @Test
    @DisplayName("其他教练确认：返回 3102，且不读取建议状态")
    void otherCoachConfirm_throws3102_withoutStatusRead() {
        when(coachStudentMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = catchThrowableOfType(
                () -> service.confirmSuggestion(COACH_B, BIND_A, SUBJECT, "CONFIRMED_READY"),
                BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(3102);
        assertOwnershipQueryContains(COACH_B, BIND_A);
        verifyNoInteractions(studentSubjectStatusMapper, deepSeekClient);
    }

    @Test
    @DisplayName("非法确认值：返回 1001，且不读取/更新建议状态")
    void invalidConfirmStatus_rejected() {
        when(coachStudentMapper.selectOne(any())).thenReturn(bind(BIND_A, COACH_A, STUDENT_A));

        BusinessException ex = catchThrowableOfType(
                () -> service.confirmSuggestion(COACH_A, BIND_A, SUBJECT, "INVALID"),
                BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(1001);
        verifyNoInteractions(studentSubjectStatusMapper, deepSeekClient);
    }

    /* ============================== 测试数据 ============================== */

    private void stubValidLearningData() {
        when(coachStudentMapper.selectOne(any())).thenReturn(bind(BIND_A, COACH_A, STUDENT_A));
        when(analysisService.overview(STUDENT_A, SUBJECT))
                .thenReturn(overview(10, 10, 0.9, 1.0, 0));
        when(analysisService.knowledge(STUDENT_A, SUBJECT)).thenReturn(List.of());
        when(wrongBookService.summary(STUDENT_A, SUBJECT)).thenReturn(wrongSummary(0, 0, 0, 0));
        when(examService.history(STUDENT_A, SUBJECT, 1, 5))
                .thenReturn(new PageResult<>(0L, 0L, 1L, 5L, List.of()));
    }

    private CoachStudent bind(Long bindId, Long coachId, Long studentId) {
        CoachStudent bind = new CoachStudent();
        bind.setId(bindId);
        bind.setCoachId(coachId);
        bind.setStudentId(studentId);
        bind.setStatus(1);
        return bind;
    }

    private AnalysisOverviewVO overview(int total, int answered, double rate,
                                        double progress, int weakCount) {
        AnalysisOverviewVO vo = new AnalysisOverviewVO();
        vo.setTotalQuestions(total);
        vo.setAnsweredQuestions(answered);
        vo.setCorrectRate(rate);
        vo.setProgress(progress);
        vo.setWeakCount(weakCount);
        return vo;
    }

    private WrongBookSummaryVO wrongSummary(int pending, int highFrequency, int mastered, int total) {
        WrongBookSummaryVO vo = new WrongBookSummaryVO();
        vo.setPending(pending);
        vo.setHighFrequency(highFrequency);
        vo.setMastered(mastered);
        vo.setTotal(total);
        return vo;
    }

    private KPStatVO kp(String name, boolean weak, double mastery) {
        KPStatVO vo = new KPStatVO();
        vo.setKnowledgePointName(name);
        vo.setIsWeak(weak);
        vo.setMastery(mastery);
        return vo;
    }

    private StudentSubjectStatus statusRow(String examReady, String aiSuggest, String confirmStatus) {
        StudentSubjectStatus row = new StudentSubjectStatus();
        row.setStudentId(STUDENT_A);
        row.setSubjectCode(SUBJECT);
        row.setExamReadySuggest(examReady);
        row.setAiSuggest(aiSuggest);
        row.setCoachConfirmStatus(confirmStatus);
        return row;
    }

    @SuppressWarnings("unchecked")
    private void assertOwnershipQueryContains(Long coachId, Long bindId) {
        ArgumentCaptor<LambdaQueryWrapper<CoachStudent>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(coachStudentMapper).selectOne(captor.capture());
        LambdaQueryWrapper<CoachStudent> wrapper = captor.getValue();
        String sqlSegment = wrapper.getSqlSegment();
        Map<String, Object> values = wrapper.getParamNameValuePairs();
        Map<String, Object> byColumn = new java.util.HashMap<>();
        Matcher matcher = Pattern.compile(
                        "([A-Za-z0-9_]+)\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)}")
                .matcher(sqlSegment);
        while (matcher.find()) {
            byColumn.put(matcher.group(1), values.get(matcher.group(2)));
        }
        assertThat(byColumn.get("id")).isEqualTo(bindId);
        assertThat(byColumn.get("coach_id")).isEqualTo(coachId);
        assertThat(byColumn.get("status")).isEqualTo(1);
    }
}
