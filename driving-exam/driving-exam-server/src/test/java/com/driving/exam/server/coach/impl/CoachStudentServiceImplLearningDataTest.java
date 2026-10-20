package com.driving.exam.server.coach.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.entity.CoachStudent;
import com.driving.exam.pojo.vo.AnalysisOverviewVO;
import com.driving.exam.pojo.vo.ExamRecordVO;
import com.driving.exam.pojo.vo.KPStatVO;
import com.driving.exam.pojo.vo.WrongBookSummaryVO;
import com.driving.exam.server.analysis.AnalysisService;
import com.driving.exam.server.coach.LearningDataVO;
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

import java.util.HashMap;
import java.util.List;
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
 * 教练端学习数据接口的离线单元测试。
 *
 * <p>只验证 {@code CoachStudentServiceImpl.getLearningData} 的编排契约：先归属校验、
 * 再把已反查出的 studentId 交给既有分析/错题/考试服务；不启动 Spring，不连接数据库。</p>
 *
 * <p>统计公式本身不在本测试中重算——正确率、错题分类、薄弱知识点、考试历史的统计口径
 * 分别由既有 {@code AnalysisService}、{@code WrongBookService}、{@code ExamService} 负责。</p>
 */
@DisplayName("教练端学习数据（单元，Mockito）")
class CoachStudentServiceImplLearningDataTest {

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
    private CoachStudentServiceImpl service;

    /**
     * 纯 Mockito 单元测试不经过 MyBatis-Plus 的 Spring 启动流程，TableInfo 缓存为空；
     * 而 {@code LambdaQueryWrapper.getSqlSegment()} 需要该缓存把 Lambda 列名解析成列名。
     * 这里只初始化 {@code CoachStudent} 的元数据，不连接数据库。
     */
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

        service = new CoachStudentServiceImpl(
                coachStudentMapper,
                userMapper,
                studentSubjectStatusMapper,
                mock(PasswordEncoder.class),
                analysisService,
                wrongBookService,
                examService,
                mock(DeepSeekClient.class));
    }

    @Test
    @DisplayName("有效绑定：按反查 studentId 复用既有统计，只返回薄弱知识点，且不写业务表")
    void validBinding_delegatesWithVerifiedStudentId_andReadsOnly() {
        CoachStudent bind = bind(BIND_A, COACH_A, STUDENT_A);
        when(coachStudentMapper.selectOne(any())).thenReturn(bind);

        AnalysisOverviewVO overview = overview(0.75, 15, 20);
        WrongBookSummaryVO wrongSummary = wrongSummary(2, 3, 1, 6);
        KPStatVO weak = kp(11L, true);
        KPStatVO strong = kp(12L, false);
        ExamRecordVO exam = new ExamRecordVO();
        exam.setExamId(900L);
        PageResult<ExamRecordVO> history = new PageResult<>(1L, 1L, 1L, 10L, List.of(exam));

        when(analysisService.overview(STUDENT_A, SUBJECT)).thenReturn(overview);
        when(analysisService.knowledge(STUDENT_A, SUBJECT)).thenReturn(List.of(weak, strong));
        when(wrongBookService.summary(STUDENT_A, SUBJECT)).thenReturn(wrongSummary);
        when(examService.history(STUDENT_A, SUBJECT, 1, 10)).thenReturn(history);

        LearningDataVO result = service.getLearningData(COACH_A, BIND_A, "subject_1", 1, 10);

        assertThat(result.getStudentId()).isEqualTo(STUDENT_A);
        assertThat(result.getSubjectCode()).isEqualTo(SUBJECT);
        assertThat(result.getOverview()).isSameAs(overview);
        assertThat(result.getWrongBookSummary()).isSameAs(wrongSummary);
        assertThat(result.getWeakKnowledgePoints()).containsExactly(weak);
        assertThat(result.getMockExamHistory()).isSameAs(history);

        // 统计范围必须使用归属校验后反查出的 studentId，而不是任何前端入参
        verify(analysisService).overview(STUDENT_A, SUBJECT);
        verify(analysisService).knowledge(STUDENT_A, SUBJECT);
        verify(wrongBookService).summary(STUDENT_A, SUBJECT);
        verify(examService).history(STUDENT_A, SUBJECT, 1, 10);

        // 本接口只读：不写绑定、用户、科目状态
        verify(coachStudentMapper, never()).insert(any(CoachStudent.class));
        verify(coachStudentMapper, never()).updateById(any(CoachStudent.class));
        verifyNoInteractions(userMapper, studentSubjectStatusMapper);
    }

    @Test
    @DisplayName("绑定不存在：返回 3102，且不查询学习数据")
    void missingBinding_throws3102_withoutLearningQueries() {
        when(coachStudentMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = catchThrowableOfType(
                () -> service.getLearningData(COACH_A, 999L, SUBJECT, 1, 10),
                BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(3102);
        verifyNoInteractions(analysisService, wrongBookService, examService);
    }

    @Test
    @DisplayName("绑定属于其他教练：返回 3102，归属查询带当前教练 ID")
    void otherCoachBinding_throws3102_andChecksCurrentCoach() {
        when(coachStudentMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = catchThrowableOfType(
                () -> service.getLearningData(COACH_B, BIND_A, SUBJECT, 1, 10),
                BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(3102);
        assertOwnershipQueryContains(COACH_B, BIND_A);
        verifyNoInteractions(analysisService, wrongBookService, examService);
    }

    @Test
    @DisplayName("绑定已解绑：返回 3102，归属查询要求 status=1")
    void releasedBinding_throws3102_andRequiresActiveStatus() {
        when(coachStudentMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = catchThrowableOfType(
                () -> service.getLearningData(COACH_A, BIND_A, SUBJECT, 1, 10),
                BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(3102);
        assertOwnershipQueryContains(COACH_A, BIND_A);
        verifyNoInteractions(analysisService, wrongBookService, examService);
    }

    @Test
    @DisplayName("空数据：无考试、无错题、无薄弱知识点时返回空值，不抛异常")
    void emptyData_returnsEmptyValues() {
        CoachStudent bind = bind(402L, COACH_B, STUDENT_B);
        when(coachStudentMapper.selectOne(any())).thenReturn(bind);

        when(analysisService.overview(STUDENT_B, SUBJECT)).thenReturn(overview(null, 0, 0));
        when(analysisService.knowledge(STUDENT_B, SUBJECT)).thenReturn(List.<KPStatVO>of());
        when(wrongBookService.summary(STUDENT_B, SUBJECT)).thenReturn(wrongSummary(0, 0, 0, 0));
        PageResult<ExamRecordVO> emptyHistory = new PageResult<>(0L, 0L, 1L, 10L, List.<ExamRecordVO>of());
        when(examService.history(STUDENT_B, SUBJECT, 1, 10)).thenReturn(emptyHistory);

        LearningDataVO result = service.getLearningData(COACH_B, 402L, SUBJECT, 1, 10);

        assertThat(result.getStudentId()).isEqualTo(STUDENT_B);
        assertThat(result.getOverview().getCorrectRate()).isNull();
        assertThat(result.getOverview().getAnsweredQuestions()).isZero();
        assertThat(result.getWeakKnowledgePoints()).isEmpty();
        assertThat(result.getWrongBookSummary().getTotal()).isZero();
        assertThat(result.getMockExamHistory().getTotal()).isZero();
        assertThat(result.getMockExamHistory().getRecords()).isEmpty();
    }

    @Test
    @DisplayName("非法科目：归属通过后返回 1001，且不查询学习数据")
    void invalidSubject_throws1001_withoutLearningQueries() {
        CoachStudent bind = bind(BIND_A, COACH_A, STUDENT_A);
        when(coachStudentMapper.selectOne(any())).thenReturn(bind);

        BusinessException ex = catchThrowableOfType(
                () -> service.getLearningData(COACH_A, BIND_A, "SUBJECT_9", 1, 10),
                BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(1001);
        verifyNoInteractions(analysisService, wrongBookService, examService);
    }

    /* ============================== 测试数据 ============================== */

    private CoachStudent bind(Long bindId, Long coachId, Long studentId) {
        CoachStudent bind = new CoachStudent();
        bind.setId(bindId);
        bind.setCoachId(coachId);
        bind.setStudentId(studentId);
        bind.setStatus(1);
        return bind;
    }

    private AnalysisOverviewVO overview(Double correctRate, int answered, int total) {
        AnalysisOverviewVO vo = new AnalysisOverviewVO();
        vo.setCorrectRate(correctRate);
        vo.setAnsweredQuestions(answered);
        vo.setTotalQuestions(total);
        vo.setProgress(total == 0 ? 0.0 : (double) answered / total);
        vo.setWeakCount(0);
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

    private KPStatVO kp(Long id, boolean weak) {
        KPStatVO vo = new KPStatVO();
        vo.setKnowledgePointId(id);
        vo.setKnowledgePointName("KP-" + id);
        vo.setIsWeak(weak);
        return vo;
    }

    /**
     * 校验归属查询的 SQL 条件与绑定值。
     *
     * <p><b>为什么先调用 {@code getSqlSegment()}：</b>MyBatis-Plus 的
     * {@code LambdaQueryWrapper} 在 {@code eq(...)} 时只把参数表达式作为延迟片段挂到
     * SQL 树上；{@code paramNameValuePairs} 要等到 SQL 片段真正渲染时才写入。
     * 若直接读 {@code getParamNameValuePairs()}，得到的是空 Map（这就是本测试此前
     * 捕获到 {@code []} 的原因），并非业务没有执行归属查询。</p>
     */
    @SuppressWarnings("unchecked")
    private void assertOwnershipQueryContains(Long coachId, Long bindId) {
        ArgumentCaptor<LambdaQueryWrapper<CoachStudent>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(coachStudentMapper).selectOne(captor.capture());

        LambdaQueryWrapper<CoachStudent> wrapper = captor.getValue();
        // 先渲染 SQL，触发延迟参数写入 paramNameValuePairs
        String sqlSegment = wrapper.getSqlSegment();

        // 从 SQL 片段中按列名取回对应的参数名，再映射到实际参数值
        Map<String, Object> byColumn = new HashMap<>();
        Matcher matcher = Pattern.compile(
                        "([A-Za-z0-9_]+)\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)}")
                .matcher(sqlSegment);
        while (matcher.find()) {
            byColumn.put(matcher.group(1), wrapper.getParamNameValuePairs().get(matcher.group(2)));
        }

        assertThat(byColumn.get("id")).isEqualTo(bindId);
        assertThat(byColumn.get("coach_id")).isEqualTo(coachId);
        assertThat(byColumn.get("status")).isEqualTo(1);
    }
}
