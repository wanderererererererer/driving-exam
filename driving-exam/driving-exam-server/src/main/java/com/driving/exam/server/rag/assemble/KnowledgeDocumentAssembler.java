package com.driving.exam.server.rag.assemble;

import com.driving.exam.pojo.entity.KnowledgePoint;
import com.driving.exam.pojo.entity.Question;
import com.driving.exam.server.rag.KnowledgeDocument;
import com.driving.exam.server.rag.KnowledgeDocumentConverter;
import com.driving.exam.server.service.KnowledgePointService;
import com.driving.exam.server.service.QuestionService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 业务库 → {@link KnowledgeDocument} 装配器（R2 装配层，**只读**）。
 *
 * <p>职责：经现有 {@link QuestionService}/{@link KnowledgePointService} <b>只读</b>加载原始记录，
 * 按已确认规则过滤，再通过现有 {@link KnowledgeDocumentConverter} 转换为知识文档，
 * 并返回可核验的统计结果。装配结果可直接交给 {@code KnowledgeIngestService.ingest(...)}，
 * 但本类<b>不</b>执行分块、Embedding、Qdrant 写入或检索。</p>
 *
 * <h3>题目过滤规则（互斥，先命中先归类）</h3>
 * <ol>
 *   <li><b>状态</b>：仅 {@code question.status = 1} 纳入；{@code 0}、其他值、{@code null} 一律排除，
 *       计入 {@code questionSkippedByStatusCount}；</li>
 *   <li><b>科目</b>：仅 {@code SUBJECT_1} / {@code SUBJECT_4} 纳入；其他科目（含 {@code null}/空白）
 *       计入 {@code questionSkippedBySubjectCount}；</li>
 *   <li><b>空白解析</b>：{@code analysis} 为 {@code null}、空串或纯空白字符时跳过，
 *       计入 {@code questionSkippedByBlankAnalysisCount}；<b>不</b>用题干/答案兜底。</li>
 * </ol>
 * <p>同一题目若同时违反多条规则，只按<b>最靠前</b>的规则归类一次，保证统计互斥、不重复计数，
 * 且满足不变式：{@code 题目读取数 = 状态过滤 + 科目过滤 + 空白解析 + 题目文档数}。</p>
 *
 * <h3>知识点规则</h3>
 * <p>知识点实体不存在 {@code status} 字段（以 DDL 为准），本阶段<b>不</b>引入状态或科目过滤；
 * 仅跳过 {@code description} 为 {@code null}/空串/纯空白的记录（计入
 * {@code knowledgePointSkippedByBlankDescriptionCount}），<b>不</b>用名称兜底。
 * 不变式：{@code 知识点读取数 = 空白描述跳过数 + 知识点文档数}。</p>
 *
 * <h3>安全与边界</h3>
 * <ul>
 *   <li>只调用 {@code list()} 读取；不新增 Mapper/SQL；依赖 MyBatis-Plus {@code @TableLogic}
 *       自动排除逻辑删除行——"读取总数"指服务实际返回的记录数。</li>
 *   <li>服务或转换器抛出的异常<b>原样向上传播</b>，不捕获、不伪装为空结果；
 *       服务返回 {@code null} 视为异常状态并立即抛 {@link IllegalStateException}。</li>
 *   <li>不做去重：{@code docId} 由转换器按 {@code 类型:主键} 生成，主键唯一保证文档唯一；
 *       若上游数据异常导致重复 docId，后续 {@code KnowledgeIngestService} 会显式拒绝而非静默去重。</li>
 *   <li>已知规模限制：一次性 {@code list()} 全表加载，当前开发库数据量为几十条，足够；
 *       数据量增长后需改为分页加载（不在本阶段范围内）。</li>
 * </ul>
 */
@Component
public class KnowledgeDocumentAssembler {

    /** 题目纳入口径：仅启用状态。 */
    private static final int ENABLED_STATUS = 1;

    /** 题目纳入口径：仅科目一 / 科目四。 */
    private static final Set<String> QUESTION_SCOPE_SUBJECTS = Set.of("SUBJECT_1", "SUBJECT_4");

    private final QuestionService questionService;
    private final KnowledgePointService knowledgePointService;
    private final KnowledgeDocumentConverter converter;

    public KnowledgeDocumentAssembler(QuestionService questionService,
                                      KnowledgePointService knowledgePointService,
                                      KnowledgeDocumentConverter converter) {
        this.questionService = questionService;
        this.knowledgePointService = knowledgePointService;
        this.converter = converter;
    }

    /**
     * 执行一次装配。文档顺序：先题目（按服务返回顺序），后知识点（按服务返回顺序）。
     *
     * @return 装配结果（文档集合 + 分项统计）；全部被过滤时为明确的空集合与零计数
     * @throws IllegalStateException 任一服务返回 {@code null}（异常状态，不以空列表掩盖）
     */
    public AssemblyResult assemble() {
        List<Question> questions = requireList(questionService.list(), "QuestionService.list()");
        List<KnowledgePoint> knowledgePoints =
                requireList(knowledgePointService.list(), "KnowledgePointService.list()");

        List<KnowledgeDocument> documents = new ArrayList<>(questions.size() + knowledgePoints.size());

        int questionSkippedByStatus = 0;
        int questionSkippedBySubject = 0;
        int questionSkippedByBlankAnalysis = 0;
        int questionDocumentCount = 0;

        for (Question question : questions) {
            // 过滤顺序固定：状态 → 科目 → 空白解析（互斥统计，先命中先归类）
            if (!isEnabled(question)) {
                questionSkippedByStatus++;
                continue;
            }
            if (!isInQuestionSubjectScope(question.getSubjectCode())) {
                questionSkippedBySubject++;
                continue;
            }
            if (isBlank(question.getAnalysis())) {
                questionSkippedByBlankAnalysis++;
                continue;
            }
            documents.add(converter.fromQuestion(question));
            questionDocumentCount++;
        }

        int knowledgePointSkippedByBlankDescription = 0;
        int knowledgePointDocumentCount = 0;

        for (KnowledgePoint knowledgePoint : knowledgePoints) {
            if (isBlank(knowledgePoint.getDescription())) {
                knowledgePointSkippedByBlankDescription++;
                continue;
            }
            documents.add(converter.fromKnowledgePoint(knowledgePoint));
            knowledgePointDocumentCount++;
        }

        return new AssemblyResult(
                documents,
                questions.size(),
                questionSkippedByStatus,
                questionSkippedBySubject,
                questionSkippedByBlankAnalysis,
                questionDocumentCount,
                knowledgePoints.size(),
                knowledgePointSkippedByBlankDescription,
                knowledgePointDocumentCount);
    }

    /* ============================== 规则判定 ============================== */

    private static boolean isEnabled(Question question) {
        // status 为 null 视为未启用（DDL 中 NOT NULL，出现 null 属异常数据，按非启用处理并计入统计）
        return question.getStatus() != null && question.getStatus() == ENABLED_STATUS;
    }

    private static boolean isInQuestionSubjectScope(String subjectCode) {
        return subjectCode != null && QUESTION_SCOPE_SUBJECTS.contains(subjectCode);
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static <T> List<T> requireList(List<T> list, String source) {
        if (list == null) {
            throw new IllegalStateException(source + " 返回 null，属异常状态，拒绝以空列表掩盖");
        }
        return list;
    }

    /**
     * 一次装配的结果统计。
     *
     * <p>统计口径（互斥、可解释）：</p>
     * <ul>
     *   <li>{@code questionReadCount = questionSkippedByStatusCount + questionSkippedBySubjectCount
     *       + questionSkippedByBlankAnalysisCount + questionDocumentCount}</li>
     *   <li>{@code knowledgePointReadCount = knowledgePointSkippedByBlankDescriptionCount
     *       + knowledgePointDocumentCount}</li>
     *   <li>{@code totalDocumentCount() = documents().size()}</li>
     * </ul>
     *
     * @param documents                                     装配生成的知识文档（不可变副本）
     * @param questionReadCount                             服务实际返回的题目记录数（未过滤前）
     * @param questionSkippedByStatusCount                  因状态非 1（含 0 / 其他值 / null）被排除的题目数
     * @param questionSkippedBySubjectCount                 状态通过后、因科目不在 {@code SUBJECT_1/SUBJECT_4}
     *                                                      被排除的题目数
     * @param questionSkippedByBlankAnalysisCount           状态与科目均通过后、因空白解析被跳过的题目数
     * @param questionDocumentCount                         最终生成的题目文档数
     * @param knowledgePointReadCount                       服务实际返回的知识点记录数（未过滤前）
     * @param knowledgePointSkippedByBlankDescriptionCount  因空白描述被跳过的知识点数
     * @param knowledgePointDocumentCount                   最终生成的知识点文档数
     */
    public record AssemblyResult(
            List<KnowledgeDocument> documents,
            int questionReadCount,
            int questionSkippedByStatusCount,
            int questionSkippedBySubjectCount,
            int questionSkippedByBlankAnalysisCount,
            int questionDocumentCount,
            int knowledgePointReadCount,
            int knowledgePointSkippedByBlankDescriptionCount,
            int knowledgePointDocumentCount) {

        public AssemblyResult {
            documents = List.copyOf(documents);
        }

        /** 最终生成的文档总数（= {@code documents().size()}）。 */
        public int totalDocumentCount() {
            return questionDocumentCount + knowledgePointDocumentCount;
        }
    }
}
