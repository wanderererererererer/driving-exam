package com.driving.exam.server.controller;

import com.driving.exam.common.result.ApiResponse;
import com.driving.exam.server.coach.CoachStudentService;
import com.driving.exam.server.coach.LearningDataVO;
import com.driving.exam.server.interceptor.JwtInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestAttribute;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 教练端学习数据 Controller 的薄层测试：只验证参数透传与认证属性来源，不启动 Spring。
 */
@DisplayName("教练端学习数据 Controller（单元）")
class CoachStudentControllerLearningDataTest {

    @Test
    @DisplayName("coachId 来自 JWT 请求属性，并原样透传给 Service")
    void learningData_usesAuthenticatedCoachId() throws Exception {
        CoachStudentService service = mock(CoachStudentService.class);
        CoachStudentController controller = new CoachStudentController(service);
        LearningDataVO data = new LearningDataVO();
        when(service.getLearningData(101L, 401L, "SUBJECT_1", 1, 10)).thenReturn(data);

        ApiResponse<LearningDataVO> response =
                controller.getLearningData(101L, 401L, "SUBJECT_1", 1, 10);

        assertThat(response.getData()).isSameAs(data);
        verify(service).getLearningData(101L, 401L, "SUBJECT_1", 1, 10);

        // 控制器不接收前端 coachId：第一个参数必须是 JwtInterceptor 写入的 userId 属性
        Method method = CoachStudentController.class.getMethod(
                "getLearningData", Long.class, Long.class, String.class, int.class, int.class);
        Parameter coachIdParam = method.getParameters()[0];
        RequestAttribute attribute = coachIdParam.getAnnotation(RequestAttribute.class);
        assertThat(attribute).isNotNull();
        assertThat(attribute.value()).isEqualTo(JwtInterceptor.ATTR_USER_ID);
    }
}
