package com.driving.exam.server.chat;

import com.driving.exam.pojo.dto.ChatSendDTO;
import com.driving.exam.pojo.dto.CoachChatSessionCreateDTO;
import com.driving.exam.pojo.dto.StudentChatSessionCreateDTO;
import com.driving.exam.server.controller.CoachChatController;
import com.driving.exam.server.controller.StudentChatController;
import com.driving.exam.server.interceptor.JwtInterceptor;
import jakarta.validation.Valid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 聊天 Controller 契约测试：不启动 Spring，只验证路径、JWT 身份来源和请求体边界。
 */
@DisplayName("聊天 Controller 契约（单元）")
class ChatControllerContractTest {

    @Test
    @DisplayName("学员端路径为 /api/student/chat，身份参数来自 JWT 请求属性")
    void studentControllerContract() throws Exception {
        assertThat(StudentChatController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/student/chat");

        Method create = StudentChatController.class.getMethod(
                "getOrCreateSession", Long.class, StudentChatSessionCreateDTO.class);
        assertThat(create.getAnnotation(PostMapping.class).value()).containsExactly("/sessions");
        assertRequestAttribute(create.getParameters()[0]);
        assertRequestBody(create.getParameters()[1]);

        Method send = StudentChatController.class.getMethod(
                "sendMessage", Long.class, Long.class, ChatSendDTO.class);
        assertThat(send.getAnnotation(PostMapping.class).value())
                .containsExactly("/sessions/{id}/messages");
        assertRequestAttribute(send.getParameters()[0]);
        assertThat(send.getParameters()[1].getAnnotation(PathVariable.class)).isNotNull();
        assertRequestBody(send.getParameters()[2]);

        Method read = StudentChatController.class.getMethod(
                "markRead", Long.class, Long.class);
        assertThat(read.getAnnotation(PutMapping.class).value())
                .containsExactly("/sessions/{id}/read");
        assertRequestAttribute(read.getParameters()[0]);
    }

    @Test
    @DisplayName("教练端路径为 /api/coach/chat，身份参数来自 JWT 请求属性")
    void coachControllerContract() throws Exception {
        assertThat(CoachChatController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/coach/chat");

        Method create = CoachChatController.class.getMethod(
                "getOrCreateSession", Long.class, CoachChatSessionCreateDTO.class);
        assertThat(create.getAnnotation(PostMapping.class).value()).containsExactly("/sessions");
        assertRequestAttribute(create.getParameters()[0]);
        assertRequestBody(create.getParameters()[1]);

        Method send = CoachChatController.class.getMethod(
                "sendMessage", Long.class, Long.class, ChatSendDTO.class);
        assertRequestAttribute(send.getParameters()[0]);
        assertRequestBody(send.getParameters()[2]);
    }

    @Test
    @DisplayName("发送请求体不包含 senderId / senderRole，身份只从 JWT 获取")
    void sendDtoHasNoSenderIdentity() {
        List<String> fields = Arrays.stream(ChatSendDTO.class.getDeclaredFields())
                .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toList());
        assertThat(fields).containsExactly("content");
    }

    private static void assertRequestAttribute(Parameter parameter) {
        RequestAttribute annotation = parameter.getAnnotation(RequestAttribute.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(JwtInterceptor.ATTR_USER_ID);
    }

    private static void assertRequestBody(Parameter parameter) {
        assertThat(parameter.getAnnotation(RequestBody.class)).isNotNull();
        assertThat(parameter.getAnnotation(Valid.class)).isNotNull();
    }
}
