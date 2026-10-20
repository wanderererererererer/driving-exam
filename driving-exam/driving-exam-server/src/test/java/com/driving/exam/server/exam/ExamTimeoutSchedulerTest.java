package com.driving.exam.server.exam;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 超时扫描调度器的调用契约（离线）。 */
@DisplayName("ExamTimeoutScheduler（离线）")
class ExamTimeoutSchedulerTest {

    @Test
    @DisplayName("调度器按固定批次调用 Service，异常不向外抛出以免终止后续调度")
    void invokesServiceAndSwallowsRuntimeException() {
        ExamService examService = mock(ExamService.class);
        ExamTimeoutScheduler scheduler = new ExamTimeoutScheduler(examService);

        when(examService.finalizeExpiredExams(50)).thenReturn(2);
        scheduler.finalizeExpiredExams();
        verify(examService).finalizeExpiredExams(50);

        when(examService.finalizeExpiredExams(50)).thenThrow(new RuntimeException("db down"));
        assertThatCode(scheduler::finalizeExpiredExams).doesNotThrowAnyException();
    }
}
