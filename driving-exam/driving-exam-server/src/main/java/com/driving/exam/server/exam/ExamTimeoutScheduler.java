package com.driving.exam.server.exam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时考试收尾定时任务。
 *
 * <p>前端倒计时只负责提示；即使客户端关闭、刷新或网络请求丢失，本任务也会按
 * {@code mock_exam.start_time + exam_paper.duration} 扫描并结算已超时的进行中考试。
 * 与手动交卷并发时，结算由 {@link ExamFinalizer} 的 {@code ONGOING → 终态} 条件抢占保证只有一方生效。</p>
 *
 * <p>测试环境可用 {@code exam.timeout-scan-enabled=false} 关闭，避免 Spring 集成测试产生额外数据库查询。</p>
 */
@Component
@ConditionalOnProperty(name = "exam.timeout-scan-enabled", havingValue = "true", matchIfMissing = true)
public class ExamTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExamTimeoutScheduler.class);

    /** 单轮最多处理场次数，避免一次占用过久。 */
    private static final int BATCH_LIMIT = 50;

    private final ExamService examService;

    public ExamTimeoutScheduler(ExamService examService) {
        this.examService = examService;
    }

    /**
     * 默认每 60 秒扫描一次；首次延迟 30 秒，避免与应用启动/初始化竞争。
     * 可通过 {@code exam.timeout-scan-ms} / {@code exam.timeout-scan-initial-ms} 调整。
     */
    @Scheduled(
            fixedDelayString = "${exam.timeout-scan-ms:60000}",
            initialDelayString = "${exam.timeout-scan-initial-ms:30000}")
    public void finalizeExpiredExams() {
        try {
            int settled = examService.finalizeExpiredExams(BATCH_LIMIT);
            if (settled > 0) {
                log.info("超时考试定时收尾完成 settled={}", settled);
            }
        } catch (RuntimeException e) {
            // 定时任务异常不能终止后续调度；详细错误留给 Service 内部逐场日志
            log.error("超时考试定时收尾失败", e);
        }
    }
}
