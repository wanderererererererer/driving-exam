package com.driving.exam.server.chat.impl;

import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.pojo.dto.ChatSendDTO;
import com.driving.exam.pojo.entity.ChatMessage;
import com.driving.exam.pojo.entity.ChatSession;
import com.driving.exam.pojo.entity.CoachStudent;
import com.driving.exam.pojo.entity.User;
import com.driving.exam.pojo.vo.ChatMessageVO;
import com.driving.exam.pojo.vo.ChatSessionVO;
import com.driving.exam.server.mapper.ChatMessageMapper;
import com.driving.exam.server.mapper.ChatSessionMapper;
import com.driving.exam.server.mapper.CoachStudentMapper;
import com.driving.exam.server.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 聊天 Service 纯 Mockito 单元测试，不连接数据库。
 */
@DisplayName("学员-教练聊天 Service（单元）")
class ChatServiceImplTest {

    private static final Long STUDENT_ID = 100L;
    private static final Long COACH_ID = 200L;
    private static final Long SESSION_ID = 10L;

    private ChatSessionMapper chatSessionMapper;
    private ChatMessageMapper chatMessageMapper;
    private CoachStudentMapper coachStudentMapper;
    private UserMapper userMapper;
    private ChatServiceImpl service;

    @BeforeEach
    void setUp() {
        chatSessionMapper = mock(ChatSessionMapper.class);
        chatMessageMapper = mock(ChatMessageMapper.class);
        coachStudentMapper = mock(CoachStudentMapper.class);
        userMapper = mock(UserMapper.class);
        service = new ChatServiceImpl(chatSessionMapper, chatMessageMapper, coachStudentMapper, userMapper);
    }

    /* ------------------------------ 会话创建 ------------------------------ */

    @Test
    @DisplayName("有效绑定下首次获取会创建会话并返回对方信息")
    void getOrCreateForStudent_createsWhenAbsent() {
        when(userMapper.selectById(COACH_ID)).thenReturn(user(COACH_ID, "COACH", "教练甲"));
        when(coachStudentMapper.selectCount(any())).thenReturn(1L);
        when(chatSessionMapper.selectOne(any())).thenReturn(null, session());
        when(chatSessionMapper.insertIgnore(STUDENT_ID, COACH_ID)).thenReturn(1);

        ChatSessionVO vo = service.getOrCreateForStudent(STUDENT_ID, COACH_ID);

        assertThat(vo.getId()).isEqualTo(SESSION_ID);
        assertThat(vo.getPeerUserId()).isEqualTo(COACH_ID);
        assertThat(vo.getPeerName()).isEqualTo("教练甲");
        assertThat(vo.getCanSend()).isTrue();
        verify(chatSessionMapper).insertIgnore(STUDENT_ID, COACH_ID);
    }

    @Test
    @DisplayName("已有会话时不重复插入，重复调用返回同一会话")
    void getOrCreateForStudent_reusesExistingSession() {
        when(userMapper.selectById(COACH_ID)).thenReturn(user(COACH_ID, "COACH", "教练甲"));
        when(coachStudentMapper.selectCount(any())).thenReturn(1L);
        when(chatSessionMapper.selectOne(any())).thenReturn(session());

        ChatSessionVO vo = service.getOrCreateForStudent(STUDENT_ID, COACH_ID);

        assertThat(vo.getId()).isEqualTo(SESSION_ID);
        verify(chatSessionMapper, never()).insertIgnore(any(), any());
    }

    @Test
    @DisplayName("并发首次创建时 INSERT IGNORE 返回 0，仍读取已存在会话")
    void getOrCreateForStudent_concurrentDuplicateIsIdempotent() {
        when(userMapper.selectById(COACH_ID)).thenReturn(user(COACH_ID, "COACH", "教练甲"));
        when(coachStudentMapper.selectCount(any())).thenReturn(1L);
        when(chatSessionMapper.selectOne(any())).thenReturn(null, session());
        when(chatSessionMapper.insertIgnore(STUDENT_ID, COACH_ID)).thenReturn(0);

        ChatSessionVO vo = service.getOrCreateForStudent(STUDENT_ID, COACH_ID);

        assertThat(vo.getId()).isEqualTo(SESSION_ID);
    }

    @Test
    @DisplayName("无有效绑定不能获取或创建会话，返回 3102")
    void getOrCreate_rejectsInactiveBinding() {
        when(userMapper.selectById(COACH_ID)).thenReturn(user(COACH_ID, "COACH", "教练甲"));
        when(coachStudentMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.getOrCreateForStudent(STUDENT_ID, COACH_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.BINDING_NOT_FOUND.getCode()));
        verify(chatSessionMapper, never()).selectOne(any());
        verify(chatSessionMapper, never()).insertIgnore(any(), any());
    }

    @Test
    @DisplayName("对方账号角色不匹配时返回 1106")
    void getOrCreate_rejectsWrongPeerRole() {
        when(userMapper.selectById(COACH_ID)).thenReturn(user(COACH_ID, "STUDENT", "不是教练"));

        assertThatThrownBy(() -> service.getOrCreateForStudent(STUDENT_ID, COACH_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.ROLE_MISMATCH.getCode()));
    }

    @Test
    @DisplayName("对方账号被禁用时返回 1102，不创建会话")
    void getOrCreate_rejectsDisabledPeer() {
        User coach = user(COACH_ID, "COACH", "教练甲");
        coach.setStatus(0);
        when(userMapper.selectById(COACH_ID)).thenReturn(coach);

        assertThatThrownBy(() -> service.getOrCreateForStudent(STUDENT_ID, COACH_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.ACCOUNT_DISABLED.getCode()));
        verify(chatSessionMapper, never()).insertIgnore(any(), any());
    }

    @Test
    @DisplayName("教练获取会话使用学员作为对方，并复用同一对会话")
    void getOrCreateForCoach_usesStudentPeer() {
        when(userMapper.selectById(STUDENT_ID)).thenReturn(user(STUDENT_ID, "STUDENT", "学员甲"));
        when(coachStudentMapper.selectCount(any())).thenReturn(1L);
        when(chatSessionMapper.selectOne(any())).thenReturn(session());

        ChatSessionVO vo = service.getOrCreateForCoach(COACH_ID, STUDENT_ID);

        assertThat(vo.getPeerUserId()).isEqualTo(STUDENT_ID);
        assertThat(vo.getPeerName()).isEqualTo("学员甲");
        assertThat(vo.getCanSend()).isTrue();
    }

    /* -------------------------------- 会话列表 ------------------------------- */

    @Test
    @DisplayName("学员会话列表按当前学员视角返回对方姓名、未读数与 canSend")
    void listForStudent_assemblesPeerAndUnreadAndBindingState() {
        ChatSession active = session(10L, STUDENT_ID, COACH_ID);
        active.setLastMessage("最近一条");
        active.setStudentUnread(3);
        ChatSession unbound = session(11L, STUDENT_ID, 201L);
        unbound.setLastMessage("已解绑");
        unbound.setStudentUnread(0);

        when(chatSessionMapper.selectList(any())).thenReturn(List.of(active, unbound));
        when(coachStudentMapper.selectList(any())).thenReturn(List.of(bind(COACH_ID)));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(
                user(COACH_ID, "COACH", "教练甲"),
                user(201L, "COACH", "教练乙")));

        List<ChatSessionVO> result = service.listForStudent(STUDENT_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getPeerName()).isEqualTo("教练甲");
        assertThat(result.get(0).getUnreadCount()).isEqualTo(3);
        assertThat(result.get(0).getCanSend()).isTrue();
        assertThat(result.get(1).getPeerUserId()).isEqualTo(201L);
        assertThat(result.get(1).getCanSend()).isFalse();
        verify(userMapper, times(1)).selectBatchIds(any());
    }

    @Test
    @DisplayName("没有会话时返回空列表，不额外查询绑定或用户")
    void listForStudent_emptyShortCircuits() {
        when(chatSessionMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.listForStudent(STUDENT_ID)).isEmpty();
        verify(coachStudentMapper, never()).selectList(any());
        verify(userMapper, never()).selectBatchIds(any());
    }

    @Test
    @DisplayName("教练会话列表按教练视角返回学员未读数")
    void listForCoach_usesCoachUnread() {
        ChatSession session = session(12L, STUDENT_ID, COACH_ID);
        session.setCoachUnread(4);
        when(chatSessionMapper.selectList(any())).thenReturn(List.of(session));
        when(coachStudentMapper.selectList(any())).thenReturn(List.of(bind(COACH_ID)));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(
                user(STUDENT_ID, "STUDENT", "学员甲")));

        List<ChatSessionVO> result = service.listForCoach(COACH_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUnreadCount()).isEqualTo(4);
        assertThat(result.get(0).getPeerName()).isEqualTo("学员甲");
    }

    /* -------------------------------- 消息查询 ------------------------------- */

    @Test
    @DisplayName("首屏查询最近一页，并把数据库倒序反转为 id 正序")
    void messages_initialPageIsAscending() {
        when(chatSessionMapper.selectById(SESSION_ID)).thenReturn(session());
        when(chatMessageMapper.selectLatest(SESSION_ID, 20))
                .thenReturn(List.of(message(3L, "COACH", "第三条"), message(2L, "STUDENT", "第二条")));

        List<ChatMessageVO> result = service.messagesForStudent(STUDENT_ID, SESSION_ID, null, null, 20);

        assertThat(result).extracting(ChatMessageVO::getId).containsExactly(2L, 3L);
        verify(chatMessageMapper).selectLatest(SESSION_ID, 20);
    }

    @Test
    @DisplayName("lastId 游标查询新消息，结果保持正序")
    void messages_afterCursor() {
        when(chatSessionMapper.selectById(SESSION_ID)).thenReturn(session());
        when(chatMessageMapper.selectAfter(SESSION_ID, 5L, 20))
                .thenReturn(List.of(message(6L, "COACH", "新消息"), message(7L, "STUDENT", "更新")));

        List<ChatMessageVO> result = service.messagesForCoach(COACH_ID, SESSION_ID, 5L, null, 20);

        assertThat(result).extracting(ChatMessageVO::getId).containsExactly(6L, 7L);
    }

    @Test
    @DisplayName("beforeId 游标查询更早消息，并反转为正序")
    void messages_beforeCursor() {
        when(chatSessionMapper.selectById(SESSION_ID)).thenReturn(session());
        when(chatMessageMapper.selectBefore(SESSION_ID, 5L, 20))
                .thenReturn(List.of(message(4L, "STUDENT", "第四条"), message(3L, "COACH", "第三条")));

        List<ChatMessageVO> result = service.messagesForStudent(STUDENT_ID, SESSION_ID, null, 5L, 20);

        assertThat(result).extracting(ChatMessageVO::getId).containsExactly(3L, 4L);
        verify(chatMessageMapper).selectBefore(SESSION_ID, 5L, 20);
    }

    @Test
    @DisplayName("lastId 与 beforeId 同时出现返回 1001")
    void messages_rejectsBothCursors() {
        when(chatSessionMapper.selectById(SESSION_ID)).thenReturn(session());

        assertThatThrownBy(() -> service.messagesForStudent(STUDENT_ID, SESSION_ID, 2L, 3L, 20))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
        verify(chatMessageMapper, never()).selectLatest(any(), anyInt());
    }

    @Test
    @DisplayName("pageSize 上限 50，缺省为 20")
    void messages_clampsPageSize() {
        when(chatSessionMapper.selectById(SESSION_ID)).thenReturn(session());
        when(chatMessageMapper.selectLatest(eq(SESSION_ID), anyInt())).thenReturn(List.of());

        service.messagesForStudent(STUDENT_ID, SESSION_ID, null, null, 100);
        service.messagesForStudent(STUDENT_ID, SESSION_ID, null, null, null);

        verify(chatMessageMapper).selectLatest(SESSION_ID, 50);
        verify(chatMessageMapper).selectLatest(SESSION_ID, 20);
    }

    @Test
    @DisplayName("非会话参与者读取消息返回 5001，不泄露会话是否存在")
    void messages_rejectsOtherUser() {
        when(chatSessionMapper.selectById(SESSION_ID)).thenReturn(session());

        assertThatThrownBy(() -> service.messagesForStudent(999L, SESSION_ID, null, null, 20))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CHAT_SESSION_NOT_FOUND.getCode()));
        verify(chatMessageMapper, never()).selectLatest(any(), anyInt());
    }

    @Test
    @DisplayName("会话不存在读取消息返回 5001")
    void messages_rejectsUnknownSession() {
        when(chatSessionMapper.selectById(SESSION_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.messagesForCoach(COACH_ID, SESSION_ID, null, null, 20))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CHAT_SESSION_NOT_FOUND.getCode()));
    }

    /* --------------------------------- 发送 --------------------------------- */

    @Test
    @DisplayName("学员发送消息：trim、写消息、更新摘要和教练未读数")
    void sendFromStudent_updatesSummaryAndCoachUnread() {
        ChatSession session = session();
        when(chatSessionMapper.selectByIdForUpdate(SESSION_ID)).thenReturn(session);
        when(coachStudentMapper.selectCount(any())).thenReturn(1L);
        doAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(88L);
            return 1;
        }).when(chatMessageMapper).insert(any(ChatMessage.class));

        ChatMessageVO vo = service.sendFromStudent(STUDENT_ID, SESSION_ID, dto("  你好，教练  "));

        assertThat(vo.getId()).isEqualTo(88L);
        assertThat(vo.getSenderId()).isEqualTo(STUDENT_ID);
        assertThat(vo.getSenderRole()).isEqualTo("STUDENT");
        assertThat(vo.getContent()).isEqualTo("你好，教练");
        assertThat(vo.getIsRead()).isFalse();

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageMapper).insert(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getContent()).isEqualTo("你好，教练");
        assertThat(messageCaptor.getValue().getIsRead()).isZero();

        ArgumentCaptor<ChatSession> sessionCaptor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionMapper).updateById(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getLastMessage()).isEqualTo("你好，教练");
        assertThat(sessionCaptor.getValue().getCoachUnread()).isEqualTo(1);
        assertThat(sessionCaptor.getValue().getStudentUnread()).isZero();
        assertThat(sessionCaptor.getValue().getLastMessageTime()).isNotNull();
    }

    @Test
    @DisplayName("教练发送消息只增加学员未读数")
    void sendFromCoach_updatesStudentUnread() {
        ChatSession session = session();
        session.setStudentUnread(2);
        session.setCoachUnread(0);
        when(chatSessionMapper.selectByIdForUpdate(SESSION_ID)).thenReturn(session);
        when(coachStudentMapper.selectCount(any())).thenReturn(1L);

        service.sendFromCoach(COACH_ID, SESSION_ID, dto("收到"));

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStudentUnread()).isEqualTo(3);
        assertThat(captor.getValue().getCoachUnread()).isZero();
    }

    @Test
    @DisplayName("发送内容超过 500 字时 last_message 安全截断，完整消息仍落库")
    void send_truncatesLastMessageOnly() {
        ChatSession session = session();
        when(chatSessionMapper.selectByIdForUpdate(SESSION_ID)).thenReturn(session);
        when(coachStudentMapper.selectCount(any())).thenReturn(1L);
        String content = "a".repeat(600);

        service.sendFromStudent(STUDENT_ID, SESSION_ID, dto(content));

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageMapper).insert(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getContent()).hasSize(600);

        ArgumentCaptor<ChatSession> sessionCaptor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionMapper).updateById(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getLastMessage()).hasSize(500);
    }

    @Test
    @DisplayName("空消息返回 1001，且不写消息")
    void send_rejectsBlankContent() {
        assertThatThrownBy(() -> service.sendFromStudent(STUDENT_ID, SESSION_ID, dto("   ")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
        verify(chatMessageMapper, never()).insert(any(ChatMessage.class));
        verify(chatSessionMapper, never()).selectByIdForUpdate(any());
    }

    @Test
    @DisplayName("超过 1000 字的消息返回 1001")
    void send_rejectsOverlongContent() {
        assertThatThrownBy(() -> service.sendFromStudent(STUDENT_ID, SESSION_ID, dto("a".repeat(1001))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("解绑后发送返回 3102，不写消息也不更新摘要")
    void send_rejectsInactiveBinding() {
        when(chatSessionMapper.selectByIdForUpdate(SESSION_ID)).thenReturn(session());
        when(coachStudentMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.sendFromStudent(STUDENT_ID, SESSION_ID, dto("你好")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.BINDING_NOT_FOUND.getCode()));
        verify(chatMessageMapper, never()).insert(any(ChatMessage.class));
        verify(chatSessionMapper, never()).updateById(any(ChatSession.class));
    }

    @Test
    @DisplayName("非会话参与者发送返回 5001")
    void send_rejectsOtherParticipant() {
        when(chatSessionMapper.selectByIdForUpdate(SESSION_ID)).thenReturn(session());

        assertThatThrownBy(() -> service.sendFromStudent(999L, SESSION_ID, dto("你好")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CHAT_SESSION_NOT_FOUND.getCode()));
        verify(chatMessageMapper, never()).insert(any(ChatMessage.class));
    }

    /* --------------------------------- 已读 --------------------------------- */

    @Test
    @DisplayName("学员已读只把教练消息标记为已读并清零自己的未读数")
    void markReadForStudent_resetsOwnUnread() {
        ChatSession session = session();
        session.setStudentUnread(5);
        session.setCoachUnread(2);
        when(chatSessionMapper.selectByIdForUpdate(SESSION_ID)).thenReturn(session);
        when(chatMessageMapper.markRead(SESSION_ID, "STUDENT")).thenReturn(5);

        service.markReadForStudent(STUDENT_ID, SESSION_ID);

        verify(chatMessageMapper).markRead(SESSION_ID, "STUDENT");
        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStudentUnread()).isZero();
        assertThat(captor.getValue().getCoachUnread()).isEqualTo(2);
    }

    @Test
    @DisplayName("解绑后仍可清理历史消息已读状态，不要求有效绑定")
    void markRead_doesNotRequireActiveBinding() {
        ChatSession session = session();
        session.setCoachUnread(1);
        when(chatSessionMapper.selectByIdForUpdate(SESSION_ID)).thenReturn(session);
        when(chatMessageMapper.markRead(SESSION_ID, "COACH")).thenReturn(1);

        service.markReadForCoach(COACH_ID, SESSION_ID);

        verify(coachStudentMapper, never()).selectCount(any());
        verify(chatMessageMapper).markRead(SESSION_ID, "COACH");
    }

    @Test
    @DisplayName("非会话参与者清已读返回 5001")
    void markRead_rejectsOtherParticipant() {
        when(chatSessionMapper.selectByIdForUpdate(SESSION_ID)).thenReturn(session());

        assertThatThrownBy(() -> service.markReadForStudent(999L, SESSION_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CHAT_SESSION_NOT_FOUND.getCode()));
        verify(chatMessageMapper, never()).markRead(any(), any());
    }

    @Test
    @DisplayName("发送和已读方法标注事务，保证消息、摘要、未读数原子更新")
    void writeMethodsAreTransactional() throws Exception {
        assertThat(ChatServiceImpl.class.getMethod(
                "sendFromStudent", Long.class, Long.class, ChatSendDTO.class)
                .getAnnotation(Transactional.class)).isNotNull();
        assertThat(ChatServiceImpl.class.getMethod(
                "sendFromCoach", Long.class, Long.class, ChatSendDTO.class)
                .getAnnotation(Transactional.class)).isNotNull();
        assertThat(ChatServiceImpl.class.getMethod(
                "markReadForStudent", Long.class, Long.class)
                .getAnnotation(Transactional.class)).isNotNull();
        assertThat(ChatServiceImpl.class.getMethod(
                "markReadForCoach", Long.class, Long.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }

    /* -------------------------------- 测试数据 ------------------------------- */

    private ChatSession session() {
        return session(SESSION_ID, STUDENT_ID, COACH_ID);
    }

    private ChatSession session(Long id, Long studentId, Long coachId) {
        ChatSession session = new ChatSession();
        session.setId(id);
        session.setStudentId(studentId);
        session.setCoachId(coachId);
        session.setStudentUnread(0);
        session.setCoachUnread(0);
        session.setCreateTime(LocalDateTime.now().minusDays(1));
        session.setUpdateTime(LocalDateTime.now().minusDays(1));
        return session;
    }

    private ChatMessage message(Long id, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setId(id);
        message.setSessionId(SESSION_ID);
        message.setSenderId("STUDENT".equals(role) ? STUDENT_ID : COACH_ID);
        message.setSenderRole(role);
        message.setContent(content);
        message.setIsRead(0);
        message.setCreateTime(LocalDateTime.now());
        return message;
    }

    private User user(Long id, String role, String realName) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setRealName(realName);
        user.setPhone("1380000" + id);
        user.setRole(role);
        user.setStatus(1);
        return user;
    }

    private CoachStudent bind(Long coachId) {
        CoachStudent bind = new CoachStudent();
        bind.setCoachId(coachId);
        bind.setStudentId(STUDENT_ID);
        bind.setStatus(1);
        return bind;
    }

    private ChatSendDTO dto(String content) {
        ChatSendDTO dto = new ChatSendDTO();
        dto.setContent(content);
        return dto;
    }
}
