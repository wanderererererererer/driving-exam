package com.driving.exam.server.coach.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.entity.CoachStudent;
import com.driving.exam.pojo.enums.WrongBookCategory;
import com.driving.exam.pojo.vo.WrongBookItemVO;
import com.driving.exam.server.analysis.AnalysisService;
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
 * 教练端学员错题接口的离线单元测试。
 *
 * <p>验证 {@code CoachStudentServiceImpl.listStudentWrongBook} 的编排契约：先按
 * {@code coach_student.id + coach_id + status=1} 做归属校验，再把反查出的 studentId
 * 交给既有 {@code WrongBookService.list}；不启动 Spring，不连接数据库。</p>
 */
@DisplayName("教练端学员错题（单元，Mockito）")
class CoachStudentServiceImplWrongBookTest {

    private static final Long COACH_A = 101L;
    private static final Long COACH_B = 202L;
    private static final Long STUDENT_A = 301L;
    private static final Long STUDENT_B = 302L;
    private static final Long BIND_A = 401L;

    private CoachStudentMapper coachStudentMapper;
    private UserMapper userMapper;
    private StudentSubjectStatusMapper studentSubjectStatusMapper;
    private AnalysisService analysisService;
    private WrongBookService wrongBookService;
    private ExamService examService;
    private CoachStudentServiceImpl service;

    /** 纯 Mockito 测试没有 MyBatis-Plus 的 Spring 启动流程，需手工初始化实体元数据。 */
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
    @DisplayName("有效绑定：按反查 studentId 调用既有错题列表，分类/科目/分页原样传递")
    void validBinding_delegatesToWrongBookService() {
        when(coachStudentMapper.selectOne(any())).thenReturn(bind(BIND_A, COACH_A, STUDENT_A));
        WrongBookItemVO item = new WrongBookItemVO();
        item.setId(8L);
        item.setQuestionId(88L);
        PageResult<WrongBookItemVO> page =
                new PageResult<>(1L, 1L, 1L, 10L, List.of(item));
        when(wrongBookService.list(STUDENT_A, "SUBJECT_1", WrongBookCategory.MASTERED, 1, 10))
                .thenReturn(page);

        PageResult<WrongBookItemVO> result = service.listStudentWrongBook(
                COACH_A, BIND_A, "SUBJECT_1", WrongBookCategory.MASTERED, 1, 10);

        assertThat(result).isSameAs(page);
        verify(wrongBookService).list(STUDENT_A, "SUBJECT_1", WrongBookCategory.MASTERED, 1, 10);

        // 只读：不触碰绑定、用户、科目状态表
        verify(coachStudentMapper, never()).insert(any(CoachStudent.class));
        verify(coachStudentMapper, never()).updateById(any(CoachStudent.class));
        verifyNoInteractions(userMapper, studentSubjectStatusMapper, analysisService, examService);
    }

    @Test
    @DisplayName("绑定不存在：返回 3102，且不调用错题服务")
    void missingBinding_throws3102_withoutWrongBookQuery() {
        when(coachStudentMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = catchThrowableOfType(
                () -> service.listStudentWrongBook(COACH_A, 999L, null, null, 1, 10),
                BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(3102);
        verifyNoInteractions(wrongBookService);
    }

    @Test
    @DisplayName("绑定属于其他教练：返回 3102，归属查询带当前教练 ID")
    void otherCoachBinding_throws3102_andChecksCurrentCoach() {
        when(coachStudentMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = catchThrowableOfType(
                () -> service.listStudentWrongBook(COACH_B, BIND_A, null, null, 1, 10),
                BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(3102);
        assertOwnershipQueryContains(COACH_B, BIND_A);
        verifyNoInteractions(wrongBookService);
    }

    @Test
    @DisplayName("绑定已解绑：返回 3102，且不调用错题服务")
    void releasedBinding_throws3102_withoutWrongBookQuery() {
        when(coachStudentMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = catchThrowableOfType(
                () -> service.listStudentWrongBook(COACH_A, BIND_A, null, null, 1, 10),
                BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(3102);
        assertOwnershipQueryContains(COACH_A, BIND_A);
        verifyNoInteractions(wrongBookService);
    }

    @Test
    @DisplayName("空错题数据：返回 total=0 的空分页，不出现异常")
    void emptyWrongBook_returnsEmptyPage() {
        when(coachStudentMapper.selectOne(any())).thenReturn(bind(402L, COACH_B, STUDENT_B));
        PageResult<WrongBookItemVO> empty =
                new PageResult<>(0L, 0L, 1L, 10L, List.of());
        when(wrongBookService.list(STUDENT_B, null, null, 1, 10)).thenReturn(empty);

        PageResult<WrongBookItemVO> result = service.listStudentWrongBook(
                COACH_B, 402L, null, null, 1, 10);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecords()).isEmpty();
    }

    @Test
    @DisplayName("分页参数归一化：页码<1 按 1，每页<1 按 10，每页>20 按 20")
    void pagination_isNormalizedBeforeDelegation() {
        when(coachStudentMapper.selectOne(any())).thenReturn(bind(BIND_A, COACH_A, STUDENT_A));
        PageResult<WrongBookItemVO> empty =
                new PageResult<>(0L, 0L, 1L, 10L, List.of());
        when(wrongBookService.list(STUDENT_A, null, null, 1, 10)).thenReturn(empty);
        when(wrongBookService.list(STUDENT_A, null, null, 2, 20)).thenReturn(empty);

        service.listStudentWrongBook(COACH_A, BIND_A, null, null, 0, 0);
        service.listStudentWrongBook(COACH_A, BIND_A, null, null, 2, 100);

        verify(wrongBookService).list(STUDENT_A, null, null, 1, 10);
        verify(wrongBookService).list(STUDENT_A, null, null, 2, 20);
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

    /**
     * 校验归属查询条件：先渲染 SQL（参数填充是延迟的），再按列名映射到实际参数值。
     */
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
