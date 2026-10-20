package com.driving.exam.server.chat;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.driving.exam.pojo.entity.ChatMessage;
import com.driving.exam.pojo.entity.ChatSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 聊天实体与真实 DDL 的字段/注解映射测试（不连接数据库）。
 */
@DisplayName("聊天实体映射（单元）")
class ChatEntityMappingTest {

    @Test
    @DisplayName("chat_session 表名、主键和字段与 DDL 一致，且没有逻辑删除列")
    void chatSessionMatchesDdl() {
        assertThat(ChatSession.class.getAnnotation(TableName.class).value()).isEqualTo("chat_session");

        Field id = field(ChatSession.class, "id");
        TableId tableId = id.getAnnotation(TableId.class);
        assertThat(tableId).isNotNull();
        assertThat(tableId.type()).isEqualTo(IdType.AUTO);

        assertThat(fieldNames(ChatSession.class)).containsExactlyInAnyOrder(
                "id", "studentId", "coachId", "lastMessage", "lastMessageTime",
                "studentUnread", "coachUnread", "createTime", "updateTime");
        assertThat(fieldNames(ChatSession.class)).doesNotContain("deleted", "status");
        assertThat(field(ChatSession.class, "createTime").getAnnotation(TableField.class).fill())
                .isEqualTo(FieldFill.INSERT);
        assertThat(field(ChatSession.class, "updateTime").getAnnotation(TableField.class).fill())
                .isEqualTo(FieldFill.INSERT_UPDATE);
        assertThat(Arrays.stream(ChatSession.class.getDeclaredFields())
                .anyMatch(f -> f.isAnnotationPresent(TableLogic.class))).isFalse();
    }

    @Test
    @DisplayName("chat_message 表名、is_read 列映射和字段与 DDL 一致")
    void chatMessageMatchesDdl() {
        assertThat(ChatMessage.class.getAnnotation(TableName.class).value()).isEqualTo("chat_message");

        Field id = field(ChatMessage.class, "id");
        assertThat(id.getAnnotation(TableId.class).type()).isEqualTo(IdType.AUTO);

        assertThat(fieldNames(ChatMessage.class)).containsExactlyInAnyOrder(
                "id", "sessionId", "senderId", "senderRole", "content", "isRead", "createTime");
        assertThat(field(ChatMessage.class, "isRead").getAnnotation(TableField.class).value())
                .isEqualTo("is_read");
        assertThat(field(ChatMessage.class, "createTime").getAnnotation(TableField.class).fill())
                .isEqualTo(FieldFill.INSERT);
        assertThat(Arrays.stream(ChatMessage.class.getDeclaredFields())
                .anyMatch(f -> f.isAnnotationPresent(TableLogic.class))).isFalse();
    }

    private static Field field(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            throw new AssertionError("缺少字段: " + name, e);
        }
    }

    private static Set<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toSet());
    }
}
