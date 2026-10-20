package com.driving.exam.server.integration;

import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.pojo.dto.ExamAnswerDTO;
import com.driving.exam.pojo.dto.ExamSubmitDTO;
import com.driving.exam.pojo.vo.ExamReportVO;
import com.driving.exam.server.exam.ExamService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模拟考试「并发交卷」集成测试（**真实 MySQL 测试库** `driving_exam_test`，非 Mock）。
 *
 * <h3>验证目标</h3>
 * 同一学员、同一场 {@code ONGOING} 考试，两个线程**并发**调用真实的
 * {@link ExamService#submit}，断言最终数据库状态满足「**只结算一次**」的关键不变量：
 * 结算由 {@code MockExamMapper.claimForSettle} 的条件更新
 * （{@code UPDATE mock_exam SET status=? WHERE id=? AND status='ONGOING'}）抢先决定，
 * 只有抢到的请求写库；另一个走 {@code submit} 的“未抢到 → 重读既有报告”分支。
 *
 * <h3>安全边界</h3>
 * <ul>
 *   <li><b>只连测试库</b>：依赖 {@code @ActiveProfiles("test")} + {@code application-test.yml}
 *       的 {@code ${TEST_DB_*}}（无默认值）。<b>不</b>在类/方法上加 {@code @Transactional}
 *       （子线程各自提交，测试事务回滚无法清理它们）。</li>
 *   <li><b>只动专用数据</b>：先插入带唯一前缀的专用学员/试卷，记录其自增 id；清理**只按这些 id**，
 *       不用 {@code TRUNCATE}、不用无 {@code WHERE} 的 {@code DELETE}、不按名称模糊删。</li>
 *   <li><b>不修改生产代码/DDL</b>：测试自带建数据与清理；数据访问用 {@link DataSource} + JDBC。</li>
 * </ul>
 *
 * <h3>诚实声明（限制）</h3>
 * <p>起跑同步（{@link CyclicBarrier}）只能对齐两线程**调用入口**的时刻，<b>无法保证</b>两者都恰好
 * 通过 {@code submit} 前置检查后再竞争 {@code claimForSettle}。并发测试是<b>概率性</b>的：本用例断言的是
 * “**本次运行下**最终库状态满足不变量”，**不等于**证明“任意调度下都不会双重结算”。</p>
 *
 * <p>本测试**不覆盖**：{@code start} 的重复进行中考试竞态、{@code applySubjectStatus} 的跨考试并发丢失更新。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("模拟考试并发交卷 · 只结算一次（真实 MySQL）")
class ExamSettlementConcurrencyTest {

    /** 专用标记前缀，用于生成可识别的测试用户名/试卷名。 */
    private static final String TAG = "zz_it_settle_" + System.nanoTime();
    private static final String SUBJECT = "SUBJECT_1";
    private static final int QUESTION_SCORE = 1;
    private static final int EXPECTED_SCORE = 2; // 2 题 × 1 分，全部答对

    @Autowired
    private DataSource dataSource;
    @Autowired
    private ExamService examService;

    /* 本次测试创建的专用主键（供清理；未创建则保持 null） */
    private Long userId;
    private Long kpId;
    private Long paperId;
    private Long examId;
    private final List<Long> questionIds = new ArrayList<>();

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    @DisplayName("两个并发 submit：仅结算一次，不重复写 answer_record，草稿被清理")
    void concurrentSubmit_settlesExactlyOnce() throws Exception {
        Throwable primary = null;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // ---------- 1. 准备最小专用数据 ----------
            prepareData();

            // ---------- 2. 并发调用真实 ExamService.submit ----------
            CyclicBarrier gate = new CyclicBarrier(2);
            Callable<Outcome> task = () -> {
                gate.await(20, TimeUnit.SECONDS);       // 对齐两线程入口
                try {
                    ExamReportVO report = examService.submit(userId, examId, submitDto());
                    return new Outcome(report, null);
                } catch (Throwable t) {
                    return new Outcome(null, t);
                }
            };
            Future<Outcome> f1 = pool.submit(task);
            Future<Outcome> f2 = pool.submit(task);
            Outcome o1 = f1.get(60, TimeUnit.SECONDS);
            Outcome o2 = f2.get(60, TimeUnit.SECONDS);

            // ---------- 3. 断言：线程结果须落在 submit 的合法返回路径上 ----------
            assertOutcomeLegal(o1, "T1");
            assertOutcomeLegal(o2, "T2");
            assertThat(o1.report() != null || o2.report() != null)
                    .as("至少一个线程应返回成绩报告").isTrue();

            // ---------- 4. 断言：数据库「只结算一次」不变量 ----------
            // 4.1 mock_exam：唯一一行、进入终态、交卷时间非空、分数正确
            Object[] exam = queryRow(
                    "SELECT status, score, submit_time FROM mock_exam WHERE id = ?", examId);
            assertThat(exam).as("考试记录应存在").isNotNull();
            assertThat(exam[0]).as("状态应为 SUBMITTED").isEqualTo("SUBMITTED");
            assertThat((Integer) exam[1]).as("分数应等于 2").isEqualTo(EXPECTED_SCORE);
            assertThat(exam[2]).as("交卷时间应非空").isNotNull();

            // 4.2 answer_record：仅 2 条（已作答题数），无重复插入
            int recordCount = count(
                    "SELECT COUNT(*) FROM answer_record WHERE exam_id = ? AND source = 'EXAM'", examId);
            assertThat(recordCount).as("answer_record 应恰好 2 条（无重复结算）").isEqualTo(2);
            int distinctCount = count(
                    "SELECT COUNT(DISTINCT question_id) FROM answer_record WHERE exam_id = ? AND source = 'EXAM'", examId);
            assertThat(distinctCount).as("不存在同一题的重复作答记录").isEqualTo(2);

            // 4.3 草稿已清理
            int draftCount = count("SELECT COUNT(*) FROM mock_exam_answer WHERE exam_id = ?", examId);
            assertThat(draftCount).as("考试草稿应被清理为 0").isZero();

            // 4.4 全答对 → 本次考试不产生错题
            int wrongCount = count("SELECT COUNT(*) FROM wrong_book WHERE user_id = ?", userId);
            assertThat(wrongCount).as("全部答对，不应产生错题记录").isZero();

            // 4.5 student_subject_status：专用新学员，期望“新建一行、及格 +1”
            assertThat(count("SELECT COUNT(*) FROM student_subject_status WHERE student_id = ? AND subject_code = ?",
                    userId, SUBJECT)).as("科目进度应恰好一行").isEqualTo(1);
            Object[] status = queryRow(
                    "SELECT mock_pass_count, last_mock_score FROM student_subject_status WHERE student_id = ? AND subject_code = ?",
                    userId, SUBJECT);
            assertThat((Integer) status[0]).as("及格次数应为 1").isEqualTo(1);
            assertThat((Integer) status[1]).as("最近分数应为 2").isEqualTo(EXPECTED_SCORE);

            // 4.6 该 (user, paper) 只应有一场考试（无重复创建）
            assertThat(count("SELECT COUNT(*) FROM mock_exam WHERE user_id = ? AND paper_id = ?", userId, paperId))
                    .as("同一学员同一试卷只应有一场考试").isEqualTo(1);
        } catch (Throwable t) {
            primary = t;
        } finally {
            pool.shutdownNow();
            try {
                pool.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            try {
                cleanup();
            } catch (Throwable ce) {
                if (primary != null) {
                    primary.addSuppressed(ce); // 保留原始异常，附带清理错误
                } else {
                    primary = ce;              // 无原始异常时，清理失败本身即失败
                }
            }
        }
        if (primary != null) {
            if (primary instanceof Exception) throw (Exception) primary;
            if (primary instanceof Error) throw (Error) primary;
            throw new RuntimeException(primary);
        }
    }

    /* ============================ 断言辅助 ============================ */

    /** 线程结果必须落在 submit 的合法路径：返回报告（分数正确）或抛 2105（防御性分支）。 */
    private void assertOutcomeLegal(Outcome o, String who) {
        if (o.error() == null) {
            assertThat(o.report()).as(who + " 返回的报告不应为 null").isNotNull();
            assertThat(o.report().getScore()).as(who + " 报告分数应为 2").isEqualTo(EXPECTED_SCORE);
            return;
        }
        assertThat(o.error())
                .as(who + " 若失败，只能是业务异常（submit 的合法分支）")
                .isInstanceOf(BusinessException.class);
        BusinessException be = (BusinessException) o.error();
        assertThat(be.getCode())
                .as(who + " 业务异常码只能是 2105（未抢到且重读仍 ONGOING 的防御分支）")
                .isEqualTo(2105);
    }

    /* ============================ 数据准备 ============================ */

    private void prepareData() throws SQLException {
        // 专用学员（password NOT NULL，填占位串；登录不参与本测试）
        userId = insert("INSERT INTO sys_user (username, password, real_name, role, status, deleted) "
                        + "VALUES (?, ?, ?, 'STUDENT', 1, 0)",
                TAG + "_stu", "{noop}BCryptPlaceholder", "并发测试学员");

        // 专用知识点（question.knowledge_point_id NOT NULL）
        kpId = insert("INSERT INTO knowledge_point (subject_code, parent_id, name, level, sort_order, deleted) "
                        + "VALUES (?, 0, ?, 1, 0, 0)",
                SUBJECT, TAG + "_kp");

        // 专用试卷：2 题 × 1 分，及格分 2（全对即及格）
        paperId = insert("INSERT INTO exam_paper (subject_code, title, paper_type, total_score, pass_score, "
                        + "duration, question_count, status, deleted) VALUES (?, ?, 'MOCK', ?, ?, 30, ?, 1, 0)",
                SUBJECT, TAG + "_paper", EXPECTED_SCORE, EXPECTED_SCORE, 2);

        // 2 道单选，答案均为 A
        long q1 = insert("INSERT INTO question (subject_code, knowledge_point_id, question_type, content, answer, "
                        + "difficulty, status, deleted) VALUES (?, ?, 'SINGLE', ?, 'A', 1, 1, 0)",
                SUBJECT, kpId, TAG + "_q1");
        long q2 = insert("INSERT INTO question (subject_code, knowledge_point_id, question_type, content, answer, "
                        + "difficulty, status, deleted) VALUES (?, ?, 'SINGLE', ?, 'A', 1, 1, 0)",
                SUBJECT, kpId, TAG + "_q2");
        questionIds.add(q1);
        questionIds.add(q2);

        insert("INSERT INTO exam_paper_question (paper_id, question_id, score, sort_order) VALUES (?, ?, ?, 1)",
                paperId, q1, QUESTION_SCORE);
        insert("INSERT INTO exam_paper_question (paper_id, question_id, score, sort_order) VALUES (?, ?, ?, 2)",
                paperId, q2, QUESTION_SCORE);

        // 一场 ONGOING 考试（start_time=now → 非超时；total/pass 从试卷复制）
        examId = insert("INSERT INTO mock_exam (user_id, subject_code, paper_id, start_time, total_score, "
                        + "pass_score, status) VALUES (?, ?, ?, ?, ?, ?, 'ONGOING')",
                userId, SUBJECT, paperId, Timestamp.valueOf(LocalDateTime.now()),
                EXPECTED_SCORE, EXPECTED_SCORE);

        // 预置草稿（用于验证“结算后草稿被清理”确实发生）
        insert("INSERT INTO mock_exam_answer (exam_id, question_id, user_answer) VALUES (?, ?, 'A')", examId, q1);
        insert("INSERT INTO mock_exam_answer (exam_id, question_id, user_answer) VALUES (?, ?, 'A')", examId, q2);
    }

    /** 两个线程各自一份**等价**的提交 DTO（相同答案 → 胜者不同不影响预期分）。 */
    private ExamSubmitDTO submitDto() {
        List<ExamAnswerDTO> answers = new ArrayList<>();
        for (Long qid : questionIds) {
            ExamAnswerDTO a = new ExamAnswerDTO();
            a.setQuestionId(qid);
            a.setUserAnswer("A");
            answers.add(a);
        }
        ExamSubmitDTO dto = new ExamSubmitDTO();
        dto.setAnswers(answers);
        return dto;
    }

    /* ============================ 清理（仅按专用主键，依赖反向） ============================ */

    private void cleanup() throws SQLException {
        // 反向依赖顺序；每步仅在对应 id 已捕获时执行
        if (examId != null) {
            update("DELETE FROM answer_record WHERE exam_id = ?", examId);
            update("DELETE FROM mock_exam_answer WHERE exam_id = ?", examId);
        }
        if (userId != null) {
            update("DELETE FROM wrong_book WHERE user_id = ?", userId);
            update("DELETE FROM student_subject_status WHERE student_id = ?", userId);
        }
        if (examId != null) {
            update("DELETE FROM mock_exam WHERE id = ?", examId);
        }
        if (paperId != null) {
            update("DELETE FROM exam_paper_question WHERE paper_id = ?", paperId);
        }
        for (Long qid : questionIds) {
            update("DELETE FROM question_option WHERE question_id = ?", qid);
        }
        if (!questionIds.isEmpty()) {
            update("DELETE FROM question WHERE id IN (" + placeholders(questionIds.size()) + ")",
                    questionIds.toArray());
        }
        if (paperId != null) {
            update("DELETE FROM exam_paper WHERE id = ?", paperId);
        }
        if (kpId != null) {
            update("DELETE FROM knowledge_point WHERE id = ?", kpId);
        }
        if (userId != null) {
            update("DELETE FROM sys_user WHERE id = ?", userId);
        }
    }

    private static String placeholders(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(',');
            sb.append('?');
        }
        return sb.toString();
    }

    /* ============================ JDBC 工具（仅用 DataSource，规避 @TableLogic 逻辑删） ============================ */

    private long insert(String sql, Object... args) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, args);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new SQLException("未取得自增主键：" + sql);
                }
                return rs.getLong(1);
            }
        }
    }

    private void update(String sql, Object... args) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            ps.executeUpdate();
        }
    }

    private int count(String sql, Object... args) throws SQLException {
        Object v = scalar(sql, args);
        return v == null ? 0 : ((Number) v).intValue();
    }

    private Object scalar(String sql, Object... args) throws SQLException {
        Object[] row = queryRow(sql, args);
        return row == null ? null : row[0];
    }

    private Object[] queryRow(String sql, Object... args) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                int n = rs.getMetaData().getColumnCount();
                Object[] row = new Object[n];
                for (int i = 0; i < n; i++) {
                    row[i] = rs.getObject(i + 1);
                }
                return row;
            }
        }
    }

    private static void bind(PreparedStatement ps, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            ps.setObject(i + 1, args[i]);
        }
    }

    /** 线程结果载体：要么返回报告，要么抛异常。 */
    private record Outcome(ExamReportVO report, Throwable error) {
    }
}
