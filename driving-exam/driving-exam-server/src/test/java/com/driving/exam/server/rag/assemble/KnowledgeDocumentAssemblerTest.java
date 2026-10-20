package com.driving.exam.server.rag.assemble;

import com.driving.exam.pojo.entity.KnowledgePoint;
import com.driving.exam.pojo.entity.Question;
import com.driving.exam.server.rag.KnowledgeDocType;
import com.driving.exam.server.rag.KnowledgeDocument;
import com.driving.exam.server.rag.KnowledgeDocumentConverter;
import com.driving.exam.server.service.KnowledgePointService;
import com.driving.exam.server.service.QuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link KnowledgeDocumentAssembler} 离线单元测试。
 *
 * <p>Mockito 模拟 {@link QuestionService}/{@link KnowledgePointService}/{@link KnowledgeDocumentConverter}：
 * <b>不启动 Spring、不连数据库、不调用 Embedding API、不访问 Qdrant、不访问网络</b>。</p>
 *
 * <p>转换器按真实 docId 规则（{@code 类型:主键}）生成替身文档，用于验证：装配器只把通过过滤的记录
 * 交给转换器、调用次数与参数正确、统计互斥且可解释。</p>
 */
@DisplayName("R2 知识文档装配（离线）")
class KnowledgeDocumentAssemblerTest {

    private QuestionService questionService;
    private KnowledgePointService knowledgePointService;
    private KnowledgeDocumentConverter converter;
    private KnowledgeDocumentAssembler assembler;

    @BeforeEach
    void setUp() {
        questionService = mock(QuestionService.class);
        knowledgePointService = mock(KnowledgePointService.class);
        converter = mock(KnowledgeDocumentConverter.class);

        // 默认：两个服务均返回空列表；转换器按真实 docId 规则生成替身文档
        when(questionService.list()).thenReturn(List.of());
        when(knowledgePointService.list()).thenReturn(List.of());
        when(converter.fromQuestion(any(Question.class))).thenAnswer(inv -> {
            Question q = inv.getArgument(0);
            return stubDoc(KnowledgeDocType.QUESTION_ANALYSIS, q.getId());
        });
        when(converter.fromKnowledgePoint(any(KnowledgePoint.class))).thenAnswer(inv -> {
            KnowledgePoint kp = inv.getArgument(0);
            return stubDoc(KnowledgeDocType.KNOWLEDGE_POINT, kp.getId());
        });

        assembler = new KnowledgeDocumentAssembler(questionService, knowledgePointService, converter);
    }

    /* ==================== 1. 正常装配 ==================== */

    @Test
    @DisplayName("正常装配：SUBJECT_1/SUBJECT_4 启用题目 + 非空描述知识点 → 文档与统计正确")
    void normalAssembly() {
        Question q1 = question(1L, "SUBJECT_1", 1, "红灯停，绿灯行。");
        Question q4 = question(4L, "SUBJECT_4", 1, "雨天路滑应减速慢行。");
        KnowledgePoint kp1 = knowledgePoint(1L, "交通信号灯含义");
        KnowledgePoint kp2 = knowledgePoint(2L, "限速规定");
        when(questionService.list()).thenReturn(List.of(q1, q4));
        when(knowledgePointService.list()).thenReturn(List.of(kp1, kp2));

        KnowledgeDocumentAssembler.AssemblyResult r = assembler.assemble();

        // 文档：先题目、后知识点，顺序稳定
        assertThat(r.documents()).extracting(KnowledgeDocument::docId)
                .containsExactly("QUESTION_ANALYSIS:1", "QUESTION_ANALYSIS:4",
                        "KNOWLEDGE_POINT:1", "KNOWLEDGE_POINT:2");
        // 题目统计
        assertThat(r.questionReadCount()).isEqualTo(2);
        assertThat(r.questionSkippedByStatusCount()).isZero();
        assertThat(r.questionSkippedBySubjectCount()).isZero();
        assertThat(r.questionSkippedByBlankAnalysisCount()).isZero();
        assertThat(r.questionDocumentCount()).isEqualTo(2);
        // 知识点统计
        assertThat(r.knowledgePointReadCount()).isEqualTo(2);
        assertThat(r.knowledgePointSkippedByBlankDescriptionCount()).isZero();
        assertThat(r.knowledgePointDocumentCount()).isEqualTo(2);
        // 合计
        assertThat(r.totalDocumentCount()).isEqualTo(4);
        assertThat(r.documents()).hasSize(r.totalDocumentCount());

        verify(converter, times(1)).fromQuestion(q1);
        verify(converter, times(1)).fromQuestion(q4);
        verify(converter, times(1)).fromKnowledgePoint(kp1);
        verify(converter, times(1)).fromKnowledgePoint(kp2);
        // 只使用服务接口 list()，再无其他服务交互
        verify(questionService, times(1)).list();
        verifyNoMoreInteractions(questionService);
        verify(knowledgePointService, times(1)).list();
        verifyNoMoreInteractions(knowledgePointService);
    }

    /* ==================== 2. 停用题目过滤 ==================== */

    @Test
    @DisplayName("停用题目过滤：status=0/2/null 均不进入转换器，计数正确")
    void disabledQuestionsFiltered() {
        Question disabled = question(1L, "SUBJECT_1", 0, "解析");
        Question abnormal = question(2L, "SUBJECT_1", 2, "解析");
        Question nullStatus = question(3L, "SUBJECT_1", null, "解析");
        Question enabled = question(4L, "SUBJECT_1", 1, "解析");
        when(questionService.list()).thenReturn(List.of(disabled, abnormal, nullStatus, enabled));

        KnowledgeDocumentAssembler.AssemblyResult r = assembler.assemble();

        assertThat(r.questionReadCount()).isEqualTo(4);
        assertThat(r.questionSkippedByStatusCount()).isEqualTo(3);
        assertThat(r.questionDocumentCount()).isEqualTo(1);
        assertThat(r.documents()).extracting(KnowledgeDocument::docId)
                .containsExactly("QUESTION_ANALYSIS:4");

        verify(converter, never()).fromQuestion(disabled);
        verify(converter, never()).fromQuestion(abnormal);
        verify(converter, never()).fromQuestion(nullStatus);
        verify(converter, times(1)).fromQuestion(enabled);
    }

    /* ==================== 3. 科目过滤 ==================== */

    @Test
    @DisplayName("科目过滤：仅 SUBJECT_1/SUBJECT_4 进入转换器，其他科目与 null 被排除")
    void subjectScopeFiltered() {
        Question s1 = question(1L, "SUBJECT_1", 1, "解析");
        Question s4 = question(2L, "SUBJECT_4", 1, "解析");
        Question s2 = question(3L, "SUBJECT_2", 1, "解析");
        Question s3 = question(4L, "SUBJECT_3", 1, "解析");
        Question nullSubject = question(5L, null, 1, "解析");
        when(questionService.list()).thenReturn(List.of(s1, s4, s2, s3, nullSubject));

        KnowledgeDocumentAssembler.AssemblyResult r = assembler.assemble();

        assertThat(r.questionSkippedBySubjectCount()).isEqualTo(3);
        assertThat(r.questionDocumentCount()).isEqualTo(2);
        assertThat(r.documents()).extracting(KnowledgeDocument::docId)
                .containsExactly("QUESTION_ANALYSIS:1", "QUESTION_ANALYSIS:2");

        verify(converter, never()).fromQuestion(s2);
        verify(converter, never()).fromQuestion(s3);
        verify(converter, never()).fromQuestion(nullSubject);
    }

    /* ==================== 4. 过滤统计互斥 ==================== */

    @Test
    @DisplayName("互斥统计：同时停用且科目超范围 → 只按状态归类一次，不重复计数")
    void filterCountsAreMutuallyExclusive() {
        Question bothBad = question(1L, "SUBJECT_2", 0, "解析");      // 状态 + 科目均不符
        Question subjectOnly = question(2L, "SUBJECT_3", 1, "解析"); // 仅科目不符
        Question statusOnly = question(3L, "SUBJECT_1", 0, "解析");  // 仅状态不符
        Question valid = question(4L, "SUBJECT_1", 1, "解析");
        when(questionService.list()).thenReturn(List.of(bothBad, subjectOnly, statusOnly, valid));

        KnowledgeDocumentAssembler.AssemblyResult r = assembler.assemble();

        // bothBad 只计入状态桶（优先级：状态 → 科目）
        assertThat(r.questionSkippedByStatusCount()).isEqualTo(2);
        assertThat(r.questionSkippedBySubjectCount()).isEqualTo(1);
        assertThat(r.questionSkippedByBlankAnalysisCount()).isZero();
        assertThat(r.questionDocumentCount()).isEqualTo(1);
        // 统计可解释：读取数 = 各过滤桶之和 + 文档数，且无重复计数
        assertThat(r.questionReadCount()).isEqualTo(4)
                .isEqualTo(r.questionSkippedByStatusCount() + r.questionSkippedBySubjectCount()
                        + r.questionSkippedByBlankAnalysisCount() + r.questionDocumentCount());
    }

    /* ==================== 5. 空解析过滤 ==================== */

    @Test
    @DisplayName("空解析：null/空串/纯空白 → 跳过且单独统计，转换器完全不接收（无题干/答案兜底）")
    void blankAnalysisSkipped() {
        Question nullAnalysis = question(1L, "SUBJECT_1", 1, null);
        Question emptyAnalysis = question(2L, "SUBJECT_1", 1, "");
        Question whitespaceAnalysis = question(3L, "SUBJECT_1", 1, " \t\n ");
        when(questionService.list()).thenReturn(List.of(nullAnalysis, emptyAnalysis, whitespaceAnalysis));

        KnowledgeDocumentAssembler.AssemblyResult r = assembler.assemble();

        assertThat(r.questionSkippedByBlankAnalysisCount()).isEqualTo(3);
        assertThat(r.questionDocumentCount()).isZero();
        assertThat(r.totalDocumentCount()).isZero();
        // 无兜底：转换器从未被调用（题干/答案不可能被当作正文传输）
        verify(converter, never()).fromQuestion(any(Question.class));
    }

    /* ==================== 6. 空知识点描述过滤 ==================== */

    @Test
    @DisplayName("空描述：null/空串/纯空白 → 跳过且单独统计，转换器完全不接收（无名称兜底）")
    void blankDescriptionSkipped() {
        KnowledgePoint nullDesc = knowledgePoint(1L, null);
        KnowledgePoint emptyDesc = knowledgePoint(2L, "");
        KnowledgePoint whitespaceDesc = knowledgePoint(3L, "   ");
        when(knowledgePointService.list()).thenReturn(List.of(nullDesc, emptyDesc, whitespaceDesc));

        KnowledgeDocumentAssembler.AssemblyResult r = assembler.assemble();

        assertThat(r.knowledgePointSkippedByBlankDescriptionCount()).isEqualTo(3);
        assertThat(r.knowledgePointDocumentCount()).isZero();
        assertThat(r.totalDocumentCount()).isZero();
        verify(converter, never()).fromKnowledgePoint(any(KnowledgePoint.class));
    }

    /* ==================== 7. 全部被过滤 ==================== */

    @Test
    @DisplayName("全部被过滤：返回明确的空文档集合与正确统计，无异常")
    void allFilteredReturnsEmptyResult() {
        when(questionService.list()).thenReturn(List.of(
                question(1L, "SUBJECT_2", 1, "解析"),
                question(2L, "SUBJECT_1", 0, "解析"),
                question(3L, "SUBJECT_1", 1, "  ")));
        when(knowledgePointService.list()).thenReturn(List.of(knowledgePoint(1L, null)));

        KnowledgeDocumentAssembler.AssemblyResult r = assembler.assemble();

        assertThat(r.documents()).isNotNull().isEmpty();
        assertThat(r.totalDocumentCount()).isZero();
        assertThat(r.questionReadCount()).isEqualTo(3);
        assertThat(r.questionSkippedByStatusCount()).isEqualTo(1);
        assertThat(r.questionSkippedBySubjectCount()).isEqualTo(1);
        assertThat(r.questionSkippedByBlankAnalysisCount()).isEqualTo(1);
        assertThat(r.knowledgePointReadCount()).isEqualTo(1);
        assertThat(r.knowledgePointSkippedByBlankDescriptionCount()).isEqualTo(1);
        verifyNoInteractions(converter);
    }

    /* ==================== 8. 文档唯一性 ==================== */

    @Test
    @DisplayName("文档唯一性：每条有效记录恰好转换一次，同 ID 跨表不冲突，无重复文档")
    void documentsUniquePerSourceRecord() {
        Question q1 = question(1L, "SUBJECT_1", 1, "解析1");
        Question q2 = question(2L, "SUBJECT_4", 1, "解析2");
        Question q3 = question(3L, "SUBJECT_1", 1, "解析3");
        KnowledgePoint kp1 = knowledgePoint(1L, "描述1");
        KnowledgePoint kp2 = knowledgePoint(2L, "描述2");
        KnowledgePoint kp3 = knowledgePoint(3L, "描述3"); // 与 q3 主键重号
        when(questionService.list()).thenReturn(List.of(q1, q2, q3));
        when(knowledgePointService.list()).thenReturn(List.of(kp1, kp2, kp3));

        KnowledgeDocumentAssembler.AssemblyResult r = assembler.assemble();

        assertThat(r.documents()).hasSize(6);
        assertThat(r.documents()).extracting(KnowledgeDocument::docId).doesNotHaveDuplicates()
                .hasSize(6);
        // 装配器自身不产生重复处理：每条记录恰好一次转换调用
        verify(converter, times(1)).fromQuestion(q1);
        verify(converter, times(1)).fromQuestion(q2);
        verify(converter, times(1)).fromQuestion(q3);
        verify(converter, times(1)).fromKnowledgePoint(kp1);
        verify(converter, times(1)).fromKnowledgePoint(kp2);
        verify(converter, times(1)).fromKnowledgePoint(kp3);
    }

    /* ==================== 9. 异常处理 ==================== */

    @Test
    @DisplayName("服务查询异常：原样向上传播，不伪装成空结果；知识点服务不再被调用")
    void serviceExceptionPropagated() {
        RuntimeException boom = new IllegalStateException("数据库不可用");
        when(questionService.list()).thenThrow(boom);

        assertThatThrownBy(() -> assembler.assemble()).isSameAs(boom);
        verify(knowledgePointService, never()).list();
        verifyNoInteractions(converter);
    }

    @Test
    @DisplayName("转换器异常：原样向上传播，不静默吞掉")
    void converterExceptionPropagated() {
        Question q = question(1L, "SUBJECT_1", 1, "解析");
        when(questionService.list()).thenReturn(List.of(q));
        RuntimeException boom = new IllegalStateException("转换失败");
        when(converter.fromQuestion(q)).thenThrow(boom);

        assertThatThrownBy(() -> assembler.assemble()).isSameAs(boom);
    }

    @Test
    @DisplayName("服务返回 null：抛 IllegalStateException，不以空列表掩盖异常状态")
    void nullServiceResultRejected() {
        when(questionService.list()).thenReturn(null);

        assertThatThrownBy(() -> assembler.assemble())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QuestionService.list()");
        verifyNoInteractions(converter);
    }

    /* ==================== 10. 转换器调用参数 ==================== */

    @Test
    @DisplayName("转换器参数：只接收通过全部过滤的记录，且为原始实体本身")
    void converterReceivesOnlyAcceptedRecords() {
        Question accepted = question(1L, "SUBJECT_4", 1, "有效解析");
        Question disabled = question(2L, "SUBJECT_1", 0, "解析");
        Question outOfScope = question(3L, "SUBJECT_2", 1, "解析");
        Question blank = question(4L, "SUBJECT_1", 1, " ");
        when(questionService.list()).thenReturn(List.of(accepted, disabled, outOfScope, blank));

        assembler.assemble();

        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        verify(converter, times(1)).fromQuestion(captor.capture());
        assertThat(captor.getAllValues()).containsExactly(accepted); // 同一实例，未被替换/复制
        verify(converter, never()).fromQuestion(disabled);
        verify(converter, never()).fromQuestion(outOfScope);
        verify(converter, never()).fromQuestion(blank);
        verify(converter, never()).fromKnowledgePoint(any(KnowledgePoint.class));
    }

    /* ============================== 辅助 ============================== */

    private static Question question(Long id, String subjectCode, Integer status, String analysis) {
        Question q = new Question();
        q.setId(id);
        q.setSubjectCode(subjectCode);
        q.setStatus(status);
        q.setAnalysis(analysis);
        q.setContent("题干" + id);
        q.setAnswer("A");
        q.setKnowledgePointId(1L);
        q.setQuestionType("SINGLE");
        return q;
    }

    private static KnowledgePoint knowledgePoint(Long id, String description) {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setId(id);
        kp.setSubjectCode("SUBJECT_1");
        kp.setParentId(0L);
        kp.setName("知识点" + id);
        kp.setLevel(1);
        kp.setDescription(description);
        return kp;
    }

    private static KnowledgeDocument stubDoc(KnowledgeDocType type, Long id) {
        return new KnowledgeDocument(type.name() + ":" + id, type, id, "SUBJECT_1",
                "标题" + id, "正文", Map.of());
    }
}
