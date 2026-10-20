package com.driving.exam.server.chat.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.pojo.dto.ChatSendDTO;
import com.driving.exam.pojo.entity.ChatMessage;
import com.driving.exam.pojo.entity.ChatSession;
import com.driving.exam.pojo.entity.CoachStudent;
import com.driving.exam.pojo.entity.User;
import com.driving.exam.pojo.vo.ChatMessageVO;
import com.driving.exam.pojo.vo.ChatSessionVO;
import com.driving.exam.server.chat.ChatService;
import com.driving.exam.server.mapper.ChatMessageMapper;
import com.driving.exam.server.mapper.ChatSessionMapper;
import com.driving.exam.server.mapper.CoachStudentMapper;
import com.driving.exam.server.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 学员-教练聊天 Service 实现。
 *
 * <h3>关键边界</h3>
 * <ol>
 *   <li><b>身份只信 JWT</b>：所有 senderId / senderRole 都由 Controller 从 JWT 取出后传入。</li>
 *   <li><b>会话级越权统一 5001</b>：会话不存在、不属于当前用户、角色不匹配，都返回
 *       {@code CHAT_SESSION_NOT_FOUND}，不泄露会话是否存在。</li>
 *   <li><b>发送必须有效绑定</b>：{@code coach_student.status = 1}，否则 3102；
 *       历史消息仍可读取，解绑后 {@code canSend = false}。</li>
 *   <li><b>发送用行锁串行化</b>：同一会话的发送事务先 {@code SELECT ... FOR UPDATE}，
 *       再写消息、更新 last_message / last_message_time、接收方未读数，避免并发丢失更新。</li>
 *   <li><b>消息不物理删除</b>：只按自增 id 游标查询；已读只更新接收方消息。</li>
 * </ol>
 */
@Service
public class ChatServiceImpl implements ChatService {

    private static final String ROLE_STUDENT = "STUDENT";
    private static final String ROLE_COACH = "COACH";

    private static final int BIND_STATUS_ACTIVE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_CONTENT_LENGTH = 1000;
    private static final int MAX_LAST_MESSAGE_LENGTH = 500;

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final CoachStudentMapper coachStudentMapper;
    private final UserMapper userMapper;

    public ChatServiceImpl(ChatSessionMapper chatSessionMapper,
                           ChatMessageMapper chatMessageMapper,
                           CoachStudentMapper coachStudentMapper,
                           UserMapper userMapper) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.coachStudentMapper = coachStudentMapper;
        this.userMapper = userMapper;
    }

    /* ------------------------------ 获取 / 创建 ------------------------------ */

    @Override
    public ChatSessionVO getOrCreateForStudent(Long studentId, Long coachId) {
        User coach = requirePeer(coachId, ROLE_COACH);
        requireActiveBinding(studentId, coachId);
        ChatSession session = getOrCreateSession(studentId, coachId);
        return toSessionVO(session, ROLE_STUDENT, coach, true);
    }

    @Override
    public ChatSessionVO getOrCreateForCoach(Long coachId, Long studentId) {
        User student = requirePeer(studentId, ROLE_STUDENT);
        requireActiveBinding(studentId, coachId);
        ChatSession session = getOrCreateSession(studentId, coachId);
        return toSessionVO(session, ROLE_COACH, student, true);
    }

    /* --------------------------------- 列表 --------------------------------- */

    @Override
    public List<ChatSessionVO> listForStudent(Long studentId) {
        List<ChatSession> sessions = chatSessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getStudentId, studentId)
                        .orderByDesc(ChatSession::getLastMessageTime)
                        .orderByDesc(ChatSession::getId));
        if (sessions.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> activeCoachIds = coachStudentMapper.selectList(
                        new LambdaQueryWrapper<CoachStudent>()
                                .eq(CoachStudent::getStudentId, studentId)
                                .eq(CoachStudent::getStatus, BIND_STATUS_ACTIVE))
                .stream()
                .map(CoachStudent::getCoachId)
                .collect(Collectors.toSet());

        Map<Long, User> peers = loadUsers(sessions.stream()
                .map(ChatSession::getCoachId)
                .collect(Collectors.toSet()));
        return sessions.stream()
                .map(session -> toSessionVO(session, ROLE_STUDENT,
                        peers.get(session.getCoachId()), activeCoachIds.contains(session.getCoachId())))
                .collect(Collectors.toList());
    }

    @Override
    public List<ChatSessionVO> listForCoach(Long coachId) {
        List<ChatSession> sessions = chatSessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getCoachId, coachId)
                        .orderByDesc(ChatSession::getLastMessageTime)
                        .orderByDesc(ChatSession::getId));
        if (sessions.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> activeStudentIds = coachStudentMapper.selectList(
                        new LambdaQueryWrapper<CoachStudent>()
                                .eq(CoachStudent::getCoachId, coachId)
                                .eq(CoachStudent::getStatus, BIND_STATUS_ACTIVE))
                .stream()
                .map(CoachStudent::getStudentId)
                .collect(Collectors.toSet());

        Map<Long, User> peers = loadUsers(sessions.stream()
                .map(ChatSession::getStudentId)
                .collect(Collectors.toSet()));
        return sessions.stream()
                .map(session -> toSessionVO(session, ROLE_COACH,
                        peers.get(session.getStudentId()), activeStudentIds.contains(session.getStudentId())))
                .collect(Collectors.toList());
    }

    /* ------------------------------- 消息查询 ------------------------------- */

    @Override
    public List<ChatMessageVO> messagesForStudent(Long studentId, Long sessionId,
                                                  Long lastId, Long beforeId, Integer pageSize) {
        return messages(studentId, ROLE_STUDENT, sessionId, lastId, beforeId, pageSize);
    }

    @Override
    public List<ChatMessageVO> messagesForCoach(Long coachId, Long sessionId,
                                                Long lastId, Long beforeId, Integer pageSize) {
        return messages(coachId, ROLE_COACH, sessionId, lastId, beforeId, pageSize);
    }

    /* --------------------------------- 发送 --------------------------------- */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageVO sendFromStudent(Long studentId, Long sessionId, ChatSendDTO dto) {
        return send(studentId, ROLE_STUDENT, sessionId, dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageVO sendFromCoach(Long coachId, Long sessionId, ChatSendDTO dto) {
        return send(coachId, ROLE_COACH, sessionId, dto);
    }

    /* --------------------------------- 已读 --------------------------------- */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markReadForStudent(Long studentId, Long sessionId) {
        markRead(studentId, ROLE_STUDENT, sessionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markReadForCoach(Long coachId, Long sessionId) {
        markRead(coachId, ROLE_COACH, sessionId);
    }

    /* -------------------------------- 内部实现 ------------------------------- */

    private ChatSession getOrCreateSession(Long studentId, Long coachId) {
        ChatSession existing = selectByPair(studentId, coachId);
        if (existing != null) {
            return existing;
        }
        // INSERT IGNORE 依赖 uk_student_coach，并发创建时失败方返回 0，不抛异常。
        chatSessionMapper.insertIgnore(studentId, coachId);
        ChatSession created = selectByPair(studentId, coachId);
        if (created == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        return created;
    }

    private ChatSession selectByPair(Long studentId, Long coachId) {
        return chatSessionMapper.selectOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getStudentId, studentId)
                .eq(ChatSession::getCoachId, coachId));
    }

    private User requirePeer(Long peerId, String expectedRole) {
        if (peerId == null) {
            throw new BusinessException(ErrorCode.ROLE_MISMATCH);
        }
        User peer = userMapper.selectById(peerId);
        if (peer == null || !expectedRole.equals(peer.getRole())) {
            throw new BusinessException(ErrorCode.ROLE_MISMATCH);
        }
        if (peer.getStatus() == null || peer.getStatus() != 1) {
            // 目标账号被禁用后，不允许继续新建/进入可发送会话；历史会话仍可列表查看
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        return peer;
    }

    private void requireActiveBinding(Long studentId, Long coachId) {
        Long count = coachStudentMapper.selectCount(new LambdaQueryWrapper<CoachStudent>()
                .eq(CoachStudent::getStudentId, studentId)
                .eq(CoachStudent::getCoachId, coachId)
                .eq(CoachStudent::getStatus, BIND_STATUS_ACTIVE));
        if (count == null || count == 0L) {
            throw new BusinessException(ErrorCode.BINDING_NOT_FOUND);
        }
    }

    private List<ChatMessageVO> messages(Long userId, String role, Long sessionId,
                                         Long lastId, Long beforeId, Integer pageSize) {
        requireParticipantSession(userId, role, sessionId, false);
        validateCursors(lastId, beforeId);
        int limit = normalizePageSize(pageSize);

        List<ChatMessage> messages;
        if (lastId != null && lastId > 0L) {
            messages = chatMessageMapper.selectAfter(sessionId, lastId, limit);
        } else if (beforeId != null) {
            messages = chatMessageMapper.selectBefore(sessionId, beforeId, limit);
            messages = new ArrayList<>(messages);
            Collections.reverse(messages);
        } else {
            messages = chatMessageMapper.selectLatest(sessionId, limit);
            messages = new ArrayList<>(messages);
            Collections.reverse(messages);
        }
        return messages.stream().map(this::toMessageVO).collect(Collectors.toList());
    }

    private ChatMessageVO send(Long userId, String role, Long sessionId, ChatSendDTO dto) {
        String content = dto == null ? null : dto.getContent();
        content = content == null ? null : content.trim();
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "消息内容不能为空");
        }
        if (content.codePointCount(0, content.length()) > MAX_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "消息内容不能超过1000字");
        }

        // 先锁会话行，再校验归属/绑定并写消息与摘要，保证同一会话的发送串行。
        ChatSession session = chatSessionMapper.selectByIdForUpdate(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.CHAT_SESSION_NOT_FOUND);
        }
        checkParticipant(session, userId, role);
        requireActiveBinding(session.getStudentId(), session.getCoachId());

        LocalDateTime now = LocalDateTime.now();
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setSenderId(userId);
        message.setSenderRole(role);
        message.setContent(content);
        message.setIsRead(0);
        message.setCreateTime(now);
        chatMessageMapper.insert(message);

        session.setLastMessage(truncate(content, MAX_LAST_MESSAGE_LENGTH));
        session.setLastMessageTime(now);
        if (ROLE_STUDENT.equals(role)) {
            session.setCoachUnread(nvl(session.getCoachUnread()) + 1);
        } else {
            session.setStudentUnread(nvl(session.getStudentUnread()) + 1);
        }
        chatSessionMapper.updateById(session);

        return toMessageVO(message);
    }

    private void markRead(Long userId, String role, Long sessionId) {
        ChatSession session = chatSessionMapper.selectByIdForUpdate(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.CHAT_SESSION_NOT_FOUND);
        }
        checkParticipant(session, userId, role);

        chatMessageMapper.markRead(sessionId, role);
        if (ROLE_STUDENT.equals(role)) {
            session.setStudentUnread(0);
        } else {
            session.setCoachUnread(0);
        }
        chatSessionMapper.updateById(session);
    }

    private ChatSession requireParticipantSession(Long userId, String role,
                                                  Long sessionId, boolean forUpdate) {
        ChatSession session = forUpdate
                ? chatSessionMapper.selectByIdForUpdate(sessionId)
                : chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.CHAT_SESSION_NOT_FOUND);
        }
        checkParticipant(session, userId, role);
        return session;
    }

    private void checkParticipant(ChatSession session, Long userId, String role) {
        boolean participant = ROLE_STUDENT.equals(role)
                ? Objects.equals(session.getStudentId(), userId)
                : Objects.equals(session.getCoachId(), userId);
        if (!participant) {
            throw new BusinessException(ErrorCode.CHAT_SESSION_NOT_FOUND);
        }
    }

    private void validateCursors(Long lastId, Long beforeId) {
        if (lastId != null && beforeId != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "lastId 与 beforeId 不能同时使用");
        }
        if (lastId != null && lastId < 0L) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "lastId 不能为负数");
        }
        if (beforeId != null && beforeId <= 0L) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "beforeId 必须大于0");
        }
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private ChatSessionVO toSessionVO(ChatSession session, String role,
                                      User peer, boolean canSend) {
        ChatSessionVO vo = new ChatSessionVO();
        vo.setId(session.getId());
        vo.setPeerUserId(ROLE_STUDENT.equals(role) ? session.getCoachId() : session.getStudentId());
        if (peer != null) {
            vo.setPeerName(StringUtils.hasText(peer.getRealName())
                    ? peer.getRealName() : peer.getUsername());
            vo.setPeerPhone(peer.getPhone());
        }
        vo.setLastMessage(session.getLastMessage());
        vo.setLastMessageTime(session.getLastMessageTime());
        vo.setUnreadCount(ROLE_STUDENT.equals(role)
                ? nvl(session.getStudentUnread())
                : nvl(session.getCoachUnread()));
        vo.setCanSend(canSend);
        return vo;
    }

    private ChatMessageVO toMessageVO(ChatMessage message) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(message.getId());
        vo.setSessionId(message.getSessionId());
        vo.setSenderId(message.getSenderId());
        vo.setSenderRole(message.getSenderRole());
        vo.setContent(message.getContent());
        vo.setIsRead(message.getIsRead() != null && message.getIsRead() == 1);
        vo.setCreateTime(message.getCreateTime());
        return vo;
    }

    private Map<Long, User> loadUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, User> result = new HashMap<>();
        for (User user : users) {
            result.put(user.getId(), user);
        }
        return result;
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private static String truncate(String value, int maxCodePoints) {
        if (value == null || value.codePointCount(0, value.length()) <= maxCodePoints) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }
}
