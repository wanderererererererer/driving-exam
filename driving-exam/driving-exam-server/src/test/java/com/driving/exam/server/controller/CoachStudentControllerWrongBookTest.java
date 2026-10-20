package com.driving.exam.server.controller;

import com.driving.exam.common.exception.GlobalExceptionHandler;
import com.driving.exam.common.result.ApiResponse;
import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.enums.WrongBookCategory;
import com.driving.exam.pojo.vo.WrongBookItemVO;
import com.driving.exam.server.coach.CoachStudentService;
import com.driving.exam.server.interceptor.JwtInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 教练端错题 Controller 的薄层测试：验证参数透传、认证属性来源与分类参数类型。
 */
@DisplayName("教练端学员错题 Controller（单元）")
class CoachStudentControllerWrongBookTest {

    @Test
    @DisplayName("coachId 来自 JWT 属性，科目/分类/分页原样透传")
    void listStudentWrongBook_passesAuthenticatedCoachIdAndFilters() {
        CoachStudentService service = mock(CoachStudentService.class);
        CoachStudentController controller = new CoachStudentController(service);
        PageResult<WrongBookItemVO> page = new PageResult<>(0L, 0L, 1L, 10L, List.of());
        when(service.listStudentWrongBook(
                101L, 401L, "SUBJECT_1", WrongBookCategory.MASTERED, 1, 10))
                .thenReturn(page);

        ApiResponse<PageResult<WrongBookItemVO>> response = controller.listStudentWrongBook(
                101L, 401L, "SUBJECT_1", WrongBookCategory.MASTERED, 1, 10);

        assertThat(response.getData()).isSameAs(page);
        verify(service).listStudentWrongBook(
                101L, 401L, "SUBJECT_1", WrongBookCategory.MASTERED, 1, 10);
    }

    @Test
    @DisplayName("方法签名：coachId 用 JWT 属性，category 用枚举类型接收")
    void listStudentWrongBook_signatureUsesAuthAttributeAndEnumCategory() throws Exception {
        Method method = CoachStudentController.class.getMethod(
                "listStudentWrongBook",
                Long.class, Long.class, String.class, WrongBookCategory.class, int.class, int.class);

        Parameter coachIdParam = method.getParameters()[0];
        RequestAttribute attribute = coachIdParam.getAnnotation(RequestAttribute.class);
        assertThat(attribute).isNotNull();
        assertThat(attribute.value()).isEqualTo(JwtInterceptor.ATTR_USER_ID);

        Parameter categoryParam = method.getParameters()[3];
        assertThat(categoryParam.getType()).isEqualTo(WrongBookCategory.class);
        RequestParam requestParam = categoryParam.getAnnotation(RequestParam.class);
        assertThat(requestParam).isNotNull();
        assertThat(requestParam.required()).isFalse();
    }

    @Test
    @DisplayName("非法 category：沿用全局异常处理返回业务码 1001")
    void invalidCategory_returns1001() throws Exception {
        CoachStudentService service = mock(CoachStudentService.class);
        CoachStudentController controller = new CoachStudentController(service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/api/coach/students/401/wrong-book")
                        .requestAttr(JwtInterceptor.ATTR_USER_ID, 101L)
                        .param("category", "INVALID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }
}
