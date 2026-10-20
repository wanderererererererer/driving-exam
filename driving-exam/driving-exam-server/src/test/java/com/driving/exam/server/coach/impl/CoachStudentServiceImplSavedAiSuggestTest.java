package com.driving.exam.server.coach.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.pojo.entity.CoachStudent;
import com.driving.exam.pojo.entity.StudentSubjectStatus;
import com.driving.exam.server.analysis.AnalysisService;
import com.driving.exam.server.coach.AiSuggestVO;
import com.driving.exam.server.exam.ExamService;
import com.driving.exam.server.integration.deepseek.DeepSeekClient;
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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 教练端读取已保存 AI/规则建议的离线单元测试。
 *
 * <p>只读读取，不启动 Spring、不连接数据库、不调用真实 AI。</p>
 */
@DisplayName("教练端读取已保存建议（单元，Mockito）")
class CoachStudentServiceImplSavedAiSuggestTest {

    private static final Long COACH_A = 101L;
    private static final Long COACH_B = 202L;
    private static final Long STUDENT_A = 301L;
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
    @DisplayName("已保存 AI 建议：恢复文本、时间、来源和确认状态，不调用 AI")
    void savedAiSuggestion_isRestored() {
        when(coachStudentMapper.selectOne(any())).thenReturn(bind());
        LocalDateTime aiTime = LocalDateTime.of(2026, 10, 19, 15, 5, 25);
        when(studentSubjectStatusMapper.selectOne(any()))
                .thenReturn(row("READY", "历史 AI 建议", aiTime, "CONFIRMED_READY"));

        AiSuggestVO result = service.getSavedAiSuggest(COACH_A, BIND_A, SUBJECT);

        assertThat(result).isNotNull();
        assertThat(result.getStudentId()).isEqualTo(STUDENT_A);
        assertThat(result.getSubjectCode()).isEqualTo(SUBJECT);
        assertThat(result.getExamReadySuggest()).isEqualTo("READY");
        assertThat(result.getSuggestion()).isEqualTo("历史 AI 建议");
        assertThat(result.getSource()).isEqualTo(AiSuggestVO.SOURCE_AI);
        assertThat(result.getAiSuccess()).isTrue();
        assertThat(result.getDegraded()).isFalse();
        assertThat(result.getAiSuggestTime()).isEqualTo(aiTime);
        assertThat(result.getCoachConfirmStatus()).isEqualTo("CONFIRMED_READY");

        verifyNoInteractions(deepSeekClient, analysisService, wrongBookService, examService);
        verifyNoWriteCalls();
    }

    @Test
    @DisplayName("已保存规则建议：只恢复等级和确认状态，不伪造历史规则文本")
    void savedRuleSuggestion_isRestoredWithoutInventedText() {
        when(coachStudentMapper.selectOne(any())).thenReturn(bind());
        when(studentSubjectStatusMapper.selectOne(any()))
                .thenReturn(row("NEED_HELP", null, null, "NOT_READY"));

        AiSuggestVO result = service.getSavedAiSuggest(COACH_A, BIND_A, SUBJECT);

        assertThat(result).isNotNull();
        assertThat(result.getExamReadySuggest()).isEqualTo("NEED_HELP");
        assertThat(result.getSuggestion()).isNull();
        assertThat(result.getSource()).isEqualTo(AiSuggestVO.SOURCE_RULE);
        assertThat(result.getAiSuccess()).isFalse();
        assertThat(result.getDegraded()).isTrue();
        assertThat(result.getDegradedReason()).isNull();
        assertThat(result.getAiSuggestTime()).isNull();
        assertThat(result.getCoachConfirmStatus()).isEqualTo("NOT_READY");

        verifyNoInteractions(deepSeekClient, analysisService, wrongBookService, examService);
        verifyNoWriteCalls();
    }

    @Test
    @DisplayName("无建议行：返回 null，不调用 AI、不写库")
    void noSuggestion_returnsNull() {
        when(coachStudentMapper.selectOne(any())).thenReturn(bind());
        when(studentSubjectStatusMapper.selectOne(any())).thenReturn(null);

        AiSuggestVO result = service.getSavedAiSuggest(COACH_A, BIND_A, SUBJECT);

        assertThat(result).isNull();
        verifyNoInteractions(deepSeekClient, analysisService, wrongBookService, examService);
        verifyNoWriteCalls();
    }

    @Test
    @DisplayName("有行但两个建议字段都为空：返回 null，不伪造建议")
    void emptySuggestionFields_returnsNull() {
        when(coachStudentMapper.selectOne(any())).thenReturn(bind());
        when(studentSubjectStatusMapper.selectOne(any()))
                .thenReturn(row(null, null, null, null));

        AiSuggestVO result = service.getSavedAiSuggest(COACH_A, BIND_A, SUBJECT);

        assertThat(result).isNull();
        verifyNoInteractions(deepSeekClient, analysisService, wrongBookService, examService);
        verifyNoWriteCalls();
    }

    @Test
    @DisplayName("其他教练绑定：返回 3102，不读取建议行")
    void otherCoachBinding_throws3102_withoutStatusRead() {
        when(coachStudentMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = catchThrowableOfType(
                () -> service.getSavedAiSuggest(COACH_B, BIND_A, SUBJECT),
                BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(3102);
        assertOwnershipQueryContains(COACH_B, BIND_A);
        verifyNoInteractions(studentSubjectStatusMapper, deepSeekClient,
                analysisService, wrongBookService, examService);
    }

    @Test
    @DisplayName("绑定不存在 / 已解绑：返回 3102，不读取建议行")
    void missingOrReleasedBinding_throws3102() {
        when(coachStudentMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = catchThrowableOfType(
                () -> service.getSavedAiSuggest(COACH_A, BIND_A, SUBJECT),
                BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(3102);
        verifyNoInteractions(studentSubjectStatusMapper, deepSeekClient,
                analysisService, wrongBookService, examService);
    }

    private CoachStudent bind() {
        CoachStudent bind = new CoachStudent();
        bind.setId(BIND_A);
        bind.setCoachId(COACH_A);
        bind.setStudentId(STUDENT_A);
        bind.setStatus(1);
        return bind;
    }

    private StudentSubjectStatus row(String examReady, String aiSuggest,
                                     LocalDateTime aiTime, String confirmStatus) {
        StudentSubjectStatus row = new StudentSubjectStatus();
        row.setStudentId(STUDENT_A);
        row.setSubjectCode(SUBJECT);
        row.setExamReadySuggest(examReady);
        row.setAiSuggest(aiSuggest);
        row.setAiSuggestTime(aiTime);
        row.setCoachConfirmStatus(confirmStatus);
        return row;
    }

    private void verifyNoWriteCalls() {
        verify(studentSubjectStatusMapper, never()).upsertAiSuggestion(any(), any(), any(), any(), any());
        verify(studentSubjectStatusMapper, never()).upsertCoachConfirm(any(), any(), any());
        verifyNoInteractions(userMapper);
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
