package com.driving.exam.server.controller;

import com.driving.exam.common.exception.GlobalExceptionHandler;
import com.driving.exam.common.result.ApiResponse;
import com.driving.exam.server.coach.AiSuggestVO;
import com.driving.exam.server.coach.CoachConfirmDTO;
import com.driving.exam.server.coach.CoachStudentService;
import com.driving.exam.server.interceptor.JwtInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestAttribute;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 教练端 AI 建议/确认 Controller 的薄层测试。
 */
@DisplayName("教练端 AI 建议与确认 Controller（单元）")
class CoachStudentControllerAiSuggestTest {

    @Test
    @DisplayName("ai-suggest：coachId 来自 JWT 属性，subjectCode 原样透传")
    void generateAiSuggest_usesAuthenticatedCoachId() throws Exception {
        CoachStudentService service = mock(CoachStudentService.class);
        CoachStudentController controller = new CoachStudentController(service);
        AiSuggestVO data = new AiSuggestVO();
        when(service.generateAiSuggest(101L, 401L, "SUBJECT_1")).thenReturn(data);

        ApiResponse<AiSuggestVO> response =
                controller.generateAiSuggest(101L, 401L, "SUBJECT_1");

        assertThat(response.getData()).isSameAs(data);
        verify(service).generateAiSuggest(101L, 401L, "SUBJECT_1");

        Method method = CoachStudentController.class.getMethod(
                "generateAiSuggest", Long.class, Long.class, String.class);
        Parameter coachIdParam = method.getParameters()[0];
        RequestAttribute attribute = coachIdParam.getAnnotation(RequestAttribute.class);
        assertThat(attribute).isNotNull();
        assertThat(attribute.value()).isEqualTo(JwtInterceptor.ATTR_USER_ID);
    }

    @Test
    @DisplayName("GET ai-suggest：读取已保存建议时 coachId 仍来自 JWT 属性")
    void getSavedAiSuggest_usesAuthenticatedCoachId() {
        CoachStudentService service = mock(CoachStudentService.class);
        CoachStudentController controller = new CoachStudentController(service);
        AiSuggestVO data = new AiSuggestVO();
        when(service.getSavedAiSuggest(101L, 401L, "SUBJECT_1")).thenReturn(data);

        ApiResponse<AiSuggestVO> response = controller.getSavedAiSuggest(101L, 401L, "SUBJECT_1");

        assertThat(response.getData()).isSameAs(data);
        verify(service).getSavedAiSuggest(101L, 401L, "SUBJECT_1");
    }

    @Test
    @DisplayName("confirm：coachId 来自 JWT 属性，确认值原样透传")
    void confirmSuggestion_usesAuthenticatedCoachId() {
        CoachStudentService service = mock(CoachStudentService.class);
        CoachStudentController controller = new CoachStudentController(service);
        CoachConfirmDTO dto = new CoachConfirmDTO();
        dto.setConfirmStatus("CONFIRMED_READY");

        ApiResponse<Void> response = controller.confirmSuggestion(101L, 401L, "SUBJECT_1", dto);

        assertThat(response.getCode()).isEqualTo(200);
        verify(service).confirmSuggestion(101L, 401L, "SUBJECT_1", "CONFIRMED_READY");
    }

    @Test
    @DisplayName("confirm：非法 confirmStatus 由 @Valid + 全局异常处理返回 1001")
    void invalidConfirmStatus_returns1001() throws Exception {
        CoachStudentService service = mock(CoachStudentService.class);
        CoachStudentController controller = new CoachStudentController(service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(put("/api/coach/students/401/confirm")
                        .requestAttr(JwtInterceptor.ATTR_USER_ID, 101L)
                        .param("subjectCode", "SUBJECT_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmStatus\":\"INVALID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }
}
