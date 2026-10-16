package com.driving.exam.server.analysis.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.driving.exam.pojo.entity.AnswerRecord;
import com.driving.exam.pojo.entity.KnowledgePoint;
import com.driving.exam.pojo.entity.Question;
import com.driving.exam.pojo.entity.WrongBook;
import com.driving.exam.pojo.enums.WeakReason;
import com.driving.exam.pojo.vo.AnalysisOverviewVO;
import com.driving.exam.pojo.vo.KPStatVO;
import com.driving.exam.pojo.vo.TrendPointVO;
import com.driving.exam.server.analysis.AnalysisService;
import com.driving.exam.server.analysis.AnswerPoint;
import com.driving.exam.server.analysis.MasteryCalculator;
import com.driving.exam.server.analysis.WrongBookStats;
import com.driving.exam.server.mapper.AnalysisMapper;
import com.driving.exam.server.mapper.AnswerRecordMapper;
import com.driving.exam.server.mapper.KnowledgePointMapper;
import com.driving.exam.server.mapper.QuestionMapper;
import com.driving.exam.server.mapper.WrongBookMapper;
// TODO(第4周收尾): HIGH_FREQ_THRESHOLD 跨包引用 WrongBookServiceImpl，
//  建议提到 common 常量类或 WrongBookCategory 枚举（需评估是否破坏 pojo 不承担业务阈值的原则）
import com.driving.exam.server.service.impl.WrongBookServiceImpl;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 学情分析 Service 实现。
 *
 * <p><b>核心是"含子孙聚合"</b>：一条答题记录同时计入它所属知识点**及其所有祖先**，
 * 这样根节点的掌握度由子孙题目**合起来重算**，而不是把子节点掌握度求平均
 * （掌握度不可平均）。聚合全部在内存一次遍历完成（无 N+1）。</p>
 *
 * <p><b>只读，不加 {@code @Transactional}</b>。</p>
 *
 * <p>口径详见 {@code doc/analysis-design.md} §一~§三。</p>
 */
@Service
public class AnalysisServiceImpl implements AnalysisService {

    /** 趋势默认天数 */
    private static final int DEFAULT_TREND_DAYS = 14;
    /** 趋势天数上限 */
    private static final int MAX_TREND_DAYS = 90;

    /** 短板条件①：掌握度阈值 */
    private static final double WEAK_MASTERY_THRESHOLD = 0.6;
    /** 短板条件①：最少答题数 */
    private static final int WEAK_MIN_ANSWER_COUNT = 3;
    /** 短板条件②：低难度档最少答题数（1 道说明不了"基础不牢"） */
    private static final int BASE_WEAK_MIN_LOW_COUNT = 2;
    /** 低难度档：难度 ∈ [1, 2] */
    private static final int LOW_DIFFICULTY_MAX = 2;
    /** 高难度档：难度 ∈ [4, 5] */
    private static final int HIGH_DIFFICULTY_MIN = 4;

    private final AnalysisMapper analysisMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final QuestionMapper questionMapper;
    private final AnswerRecordMapper answerRecordMapper;
    private final WrongBookMapper wrongBookMapper;
    private final MasteryCalculator masteryCalculator;

    public AnalysisServiceImpl(AnalysisMapper analysisMapper,
                               KnowledgePointMapper knowledgePointMapper,
                               QuestionMapper questionMapper,
                               AnswerRecordMapper answerRecordMapper,
                               WrongBookMapper wrongBookMapper,
                               MasteryCalculator masteryCalculator) {
        this.analysisMapper = analysisMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.questionMapper = questionMapper;
        this.answerRecordMapper = answerRecordMapper;
        this.wrongBookMapper = wrongBookMapper;
        this.masteryCalculator = masteryCalculator;
    }

    /* ============================== 总览 ============================== */

    @Override
    public AnalysisOverviewVO overview(Long userId, String subjectCode) {
        int total = analysisMapper.countTotalQuestions(subjectCode);
        int answered = analysisMapper.countAnsweredQuestions(userId, subjectCode);

        AnalysisOverviewVO vo = new AnalysisOverviewVO();
        vo.setTotalQuestions(total);
        vo.setAnsweredQuestions(answered);
        // 正确率可能为 null（用户一道都没答过）→ 保留 null，前端显示"未开始"而非"0%"
        Double rate = analysisMapper.avgCorrectRate(userId, subjectCode);
        vo.setCorrectRate(rate == null ? null : round2(rate));
        // 进度：没答过时天然为 0.0；并夹取到 [0,1] 防"已答题数 > 总题数"（题目被删后可能出现）
        vo.setProgress(total > 0 ? round2(MasteryCalculator.clamp((double) answered / total, 0, 1)) : 0.0);

        // 短板数复用知识点聚合（保证与 knowledge 接口口径完全一致）
        List<KPStatVO> stats = aggregateKpStats(userId, subjectCode, LocalDateTime.now());
        vo.setWeakCount((int) stats.stream().filter(s -> Boolean.TRUE.equals(s.getIsWeak())).count());

        // mockExam* 三个字段保持 null（考试模块 Day16~17 才建）
        return vo;
    }

    /* ============================== 知识点掌握度 ============================== */

    @Override
    public List<KPStatVO> knowledge(Long userId, String subjectCode) {
        return aggregateKpStats(userId, subjectCode, LocalDateTime.now());
    }

    /**
     * 含子孙聚合的核心：一次取回四份数据，内存中把每条记录上溯累加到所有祖先，再逐知识点算掌握度。
     */
    private List<KPStatVO> aggregateKpStats(Long userId, String subjectCode, LocalDateTime now) {
        // 1) 四份数据（题目/答题/错题均只投影所需列，避免拉回 TEXT 大字段）
        List<KnowledgePoint> kps = knowledgePointMapper.selectList(
                new LambdaQueryWrapper<KnowledgePoint>()
                        .eq(KnowledgePoint::getSubjectCode, subjectCode));
        if (kps.isEmpty()) {
            return new ArrayList<>();
        }
        List<Question> questions = questionMapper.selectList(
                new LambdaQueryWrapper<Question>()
                        .eq(Question::getSubjectCode, subjectCode)
                        .eq(Question::getStatus, 1)
                        .select(Question::getId, Question::getKnowledgePointId, Question::getDifficulty));
        List<AnswerRecord> records = answerRecordMapper.selectList(
                new LambdaQueryWrapper<AnswerRecord>()
                        .eq(AnswerRecord::getUserId, userId)
                        .eq(AnswerRecord::getSubjectCode, subjectCode)
                        .select(AnswerRecord::getQuestionId, AnswerRecord::getIsCorrect, AnswerRecord::getAnswerTime));
        List<WrongBook> wrongs = wrongBookMapper.selectList(
                new LambdaQueryWrapper<WrongBook>()
                        .eq(WrongBook::getUserId, userId)
                        .eq(WrongBook::getSubjectCode, subjectCode)
                        .select(WrongBook::getQuestionId, WrongBook::getMastered, WrongBook::getWrongCount));

        // 2) 索引：parentId 树、questionId → kpId / difficulty
        Map<Long, Long> parentOf = new HashMap<>();
        for (KnowledgePoint kp : kps) {
            parentOf.put(kp.getId(), kp.getParentId());
        }
        Map<Long, Long> qKp = new HashMap<>();
        Map<Long, Integer> qDiff = new HashMap<>();
        for (Question q : questions) {
            qKp.put(q.getId(), q.getKnowledgePointId());
            qDiff.put(q.getId(), q.getDifficulty());
        }

        // 3) 上溯累加：每条答题记录 → AnswerPoint 计入所属知识点及所有祖先
        Map<Long, List<AnswerPoint>> acc = new HashMap<>();
        for (AnswerRecord r : records) {
            Long kpId = qKp.get(r.getQuestionId());
            Integer diff = qDiff.get(r.getQuestionId());
            if (kpId == null || diff == null) {
                continue; // 题目已删/停用/不在本科目
            }
            AnswerPoint point = new AnswerPoint(diff, Integer.valueOf(1).equals(r.getIsCorrect()), r.getAnswerTime());
            accumulate(acc, parentOf, kpId, point);
        }

        // 3') 错题本行同样上溯累加（口径与答题记录一致）
        Map<Long, WrongBookStats> wrongAcc = new HashMap<>();
        for (WrongBook w : wrongs) {
            Long kpId = qKp.get(w.getQuestionId());
            if (kpId == null) {
                continue;
            }
            boolean mastered = Integer.valueOf(1).equals(w.getMastered());
            boolean highFreq = !mastered
                    && w.getWrongCount() != null
                    && w.getWrongCount() >= WrongBookServiceImpl.HIGH_FREQ_THRESHOLD;
            accumulateWrong(wrongAcc, parentOf, kpId, mastered, highFreq);
        }

        // 4) 逐知识点出结果
        List<KPStatVO> result = new ArrayList<>();
        for (KnowledgePoint kp : kps) {
            List<AnswerPoint> pts = acc.get(kp.getId());
            if (pts == null || pts.isEmpty()) {
                continue; // 未练习 → 不返回
            }
            WrongBookStats stats = wrongAcc.getOrDefault(kp.getId(), WrongBookStats.EMPTY);
            double rawMastery = masteryCalculator.computeMastery(pts, stats, now);
            long correct = pts.stream().filter(AnswerPoint::correct).count();

            KPStatVO vo = new KPStatVO();
            vo.setKnowledgePointId(kp.getId());
            vo.setKnowledgePointName(kp.getName());
            vo.setLevel(kp.getLevel());
            vo.setMastery(round2(rawMastery));                        // 展示用四舍五入
            vo.setAnsweredCount(pts.size());
            vo.setCorrectRate(round2((double) correct / pts.size()));
            WeakReason reason = judgeWeak(pts, rawMastery);           // 判定用原始值
            vo.setIsWeak(reason != null);
            vo.setWeakReason(reason);
            result.add(vo);
        }
        // 掌握度升序（最薄弱在前）
        result.sort(Comparator.comparingDouble(KPStatVO::getMastery));
        return result;
    }

    /* ============================== 趋势 ============================== */

    @Override
    public List<TrendPointVO> trend(Long userId, String subjectCode, int days) {
        int d = (days <= 0) ? DEFAULT_TREND_DAYS : Math.min(days, MAX_TREND_DAYS);
        // days=14 → 覆盖"今天 + 过去 13 天"
        LocalDateTime from = LocalDate.now().minusDays(d - 1L).atStartOfDay();
        List<TrendPointVO> points = analysisMapper.selectDailyTrend(userId, subjectCode, from);
        // 只做四舍五入，不重新组装（Mapper 已返回 VO）
        for (TrendPointVO p : points) {
            if (p.getCorrectRate() != null) {
                p.setCorrectRate(round2(p.getCorrectRate()));
            }
        }
        return points;
    }

    /* ============================== 辅助 ============================== */

    /**
     * 把一条答题点沿祖先链累加到每个知识点（含自身）。
     *
     * <p>{@code visited} 防环：知识点树理论上无环，但脏数据下防环是低成本保险。</p>
     */
    private void accumulate(Map<Long, List<AnswerPoint>> acc, Map<Long, Long> parentOf,
                            Long kpId, AnswerPoint point) {
        Set<Long> visited = new HashSet<>();
        Long cur = kpId;
        while (cur != null && cur != 0L && visited.add(cur)) {
            acc.computeIfAbsent(cur, k -> new ArrayList<>()).add(point);
            cur = parentOf.get(cur);   // 根节点的 parentId = 0 → 下一轮退出
        }
    }

    /** 同上，累加错题三元计数 [total, mastered, highFreq]。 */
    private void accumulateWrong(Map<Long, WrongBookStats> acc, Map<Long, Long> parentOf,
                                 Long kpId, boolean mastered, boolean highFreq) {
        Set<Long> visited = new HashSet<>();
        Long cur = kpId;
        while (cur != null && cur != 0L && visited.add(cur)) {
            WrongBookStats s = acc.getOrDefault(cur, WrongBookStats.EMPTY);
            acc.put(cur, new WrongBookStats(
                    s.total() + 1,
                    s.masteredCount() + (mastered ? 1 : 0),
                    s.highFreqCount() + (highFreq ? 1 : 0)));
            cur = parentOf.get(cur);
        }
    }

    /**
     * 短板判定。
     *
     * <p>① 掌握度偏低：**用原始掌握度**（非四舍五入后）&lt; 0.6 且答题数 ≥ 3；
     * ② 基础不牢：低难度档答题数 ≥ 2 且低难度错误率 &gt; 高难度错误率。</p>
     *
     * <p>同时满足时**优先返回 {@code LOW_MASTERY}**（"掌握度偏低"是更根本的问题）。</p>
     *
     * @param rawMastery 未四舍五入的掌握度
     */
    private WeakReason judgeWeak(List<AnswerPoint> pts, double rawMastery) {
        if (rawMastery < WEAK_MASTERY_THRESHOLD && pts.size() >= WEAK_MIN_ANSWER_COUNT) {
            return WeakReason.LOW_MASTERY;
        }

        int lowTotal = 0;
        int lowWrong = 0;
        int highTotal = 0;
        int highWrong = 0;
        for (AnswerPoint p : pts) {
            if (p.difficulty() <= LOW_DIFFICULTY_MAX) {
                lowTotal++;
                if (!p.correct()) {
                    lowWrong++;
                }
            } else if (p.difficulty() >= HIGH_DIFFICULTY_MIN) {
                highTotal++;
                if (!p.correct()) {
                    highWrong++;
                }
            }
        }
        // 低难度档需 ≥2 题（1 题说明不了问题）；高难度档只要有题即可比（不设下限）
        if (lowTotal >= BASE_WEAK_MIN_LOW_COUNT && highTotal > 0) {
            double lowErrorRate = (double) lowWrong / lowTotal;
            double highErrorRate = (double) highWrong / highTotal;
            if (lowErrorRate > highErrorRate) {
                return WeakReason.BASE_WEAK;
            }
        }
        return null;
    }

    /** 保留 2 位小数（HALF_UP）。用 BigDecimal 而非 Math.round，避免二进制浮点误差。 */
    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
