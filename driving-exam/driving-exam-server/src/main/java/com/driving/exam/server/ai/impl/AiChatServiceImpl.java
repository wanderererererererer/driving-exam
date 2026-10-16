package com.driving.exam.server.ai.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.dto.AiChatRequestDTO;
import com.driving.exam.pojo.entity.AiChatRecord;
import com.driving.exam.pojo.vo.AiAnswerVO;
import com.driving.exam.pojo.vo.AiRecordVO;
import com.driving.exam.pojo.vo.AiSessionVO;
import com.driving.exam.server.ai.AiChatService;
import com.driving.exam.server.ai.SystemPromptBuilder;
import com.driving.exam.server.integration.deepseek.DeepSeekClient;
import com.driving.exam.server.integration.deepseek.dto.ChatMessage;
import com.driving.exam.server.integration.deepseek.dto.ChatResult;
import com.driving.exam.server.mapper.AiChatRecordMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 智能问答 Service 实现。
 *
 * <h3>为什么<b>不加</b> {@code @Transactional}</h3>
 * AI 调用耗时 1~5 秒，若把它包进事务，事务会<b>长时间占着一条数据库连接</b>，
 * 并发一高即连接池耗尽——这是典型的长事务反模式。而本方法只写<b>一张表</b>、且是单个
 * {@code insert}，单条 SQL 自身原子，本就不需要事务。
 *
 * <h3>已知局限：限额检查存在竞态（有意不处理）</h3>
 * "先查当天次数、再插入"之间没有原子性，并发两次提问可能都通过检查、从而突破每日 20 次上限。
 * 课设是单用户场景，可接受；若要严谨，应改为原子计数或加唯一约束。**记录在案，不处理。**
 *
 * <h3>时区</h3>
 * "今天 0 点"用 {@link LocalDate#now()} 计算，<b>依赖 JVM 时区</b>，详见
 * {@code doc/data-conventions.md} §10。
 */
@Service
public class AiChatServiceImpl implements AiChatService {

    /** 每日调用上限（Service 层强制，不依赖前端拦截）。 */
    public static final int DAILY_QUOTA = 20;

    /** 历史分页每页条数上限：`answer` 是 MEDIUMTEXT，须防止一次拉回过多内容。 */
    public static final int MAX_PAGE_SIZE = 20;

    /** 未指定或非法 pageSize 时的默认值。 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final AiChatRecordMapper aiChatRecordMapper;
    private final DeepSeekClient deepSeekClient;
    private final SystemPromptBuilder systemPromptBuilder;

    public AiChatServiceImpl(AiChatRecordMapper aiChatRecordMapper,
                             DeepSeekClient deepSeekClient,
                             SystemPromptBuilder systemPromptBuilder) {
        this.aiChatRecordMapper = aiChatRecordMapper;
        this.deepSeekClient = deepSeekClient;
        this.systemPromptBuilder = systemPromptBuilder;
    }

    /* ============================== 提问 ============================== */

    @Override
    public AiAnswerVO chat(Long userId, AiChatRequestDTO dto) {
        // 1) 每日限额校验（竞态见类注释）
        long todayCount = aiChatRecordMapper.selectCount(new LambdaQueryWrapper<AiChatRecord>()
                .eq(AiChatRecord::getUserId, userId)
                .ge(AiChatRecord::getCreateTime, LocalDate.now().atStartOfDay()));
        if (todayCount >= DAILY_QUOTA) {
            throw new BusinessException(ErrorCode.AI_QUOTA_EXCEEDED);
        }

        // 2) 组装 messages：system（含科目上下文）+ user
        List<ChatMessage> messages = List.of(
                ChatMessage.system(systemPromptBuilder.buildSystemPrompt(dto.getSubjectCode())),
                ChatMessage.user(dto.getQuestion()));

        // 3) 调用 AI —— 失败异常（4001/4002/4003）在此<b>不 catch</b>，直接冒泡给全局异常处理器
        ChatResult result = deepSeekClient.chat(messages);

        // 4) 落库（成功后 insert；自增 id 会回填到 record）
        AiChatRecord record = new AiChatRecord();
        record.setUserId(userId);
        record.setSessionId(dto.getSessionId());
        record.setSubjectCode(dto.getSubjectCode());
        record.setQuestion(dto.getQuestion());
        record.setAnswer(result.getContent());
        record.setModel(result.getModel());               // 存服务端返回的实际模型，不是配置里的 deepseek-chat
        record.setPromptTokens(result.getPromptTokens());
        record.setCompletionTokens(result.getCompletionTokens());
        aiChatRecordMapper.insert(record);

        // 5) 组装返回：createTime 由 MetaObjectHandler 在 insert 时填充，此处已非空
        AiAnswerVO vo = new AiAnswerVO();
        vo.setAnswer(record.getAnswer());
        vo.setRecordId(record.getId());
        vo.setModel(record.getModel());
        vo.setPromptTokens(record.getPromptTokens());
        vo.setCompletionTokens(record.getCompletionTokens());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }

    /* ============================== 会话列表 ============================== */

    @Override
    public PageResult<AiSessionVO> sessions(Long userId, int pageNum, int pageSize) {
        int current = Math.max(pageNum, 1);
        int size = (pageSize < 1) ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        long offset = (long) (current - 1) * size;

        // count 与数据两条独立 SQL（GROUP BY 场景不用 IPage 自动 count，口径更确定）
        long total = aiChatRecordMapper.countSessions(userId);
        List<AiSessionVO> records = (total == 0)
                ? Collections.emptyList()
                : aiChatRecordMapper.selectSessions(userId, offset, size);

        long pages = (total + size - 1) / size; // total=0 时 pages=0
        return new PageResult<>(total, pages, current, size, records);
    }

    /* ============================== 单个会话的消息 ============================== */

    @Override
    public List<AiRecordVO> sessionMessages(Long userId, String sessionId) {
        List<AiChatRecord> list = aiChatRecordMapper.selectList(new LambdaQueryWrapper<AiChatRecord>()
                .eq(AiChatRecord::getUserId, userId)
                .eq(AiChatRecord::getSessionId, sessionId)
                // 升序回显；次级键 id 保证同秒消息的顺序稳定
                .orderByAsc(AiChatRecord::getCreateTime)
                .orderByAsc(AiChatRecord::getId));
        return list.stream().map(this::toRecordVO).collect(Collectors.toList());
    }

    /* ============================== 删除整个会话 ============================== */

    @Override
    public void deleteSession(Long userId, String sessionId) {
        // user_id 与 session_id 同时进 WHERE：只能删自己的会话；AiChatRecord 无 @TableLogic，故为物理删除
        int affected = aiChatRecordMapper.delete(new LambdaQueryWrapper<AiChatRecord>()
                .eq(AiChatRecord::getUserId, userId)
                .eq(AiChatRecord::getSessionId, sessionId));
        if (affected == 0) {
            // "不存在"与"不属于当前用户"共用同一错误码，不泄露存在性
            throw new BusinessException(ErrorCode.AI_RECORD_NOT_FOUND);
        }
    }

    /* ============================== 转换 ============================== */

    private AiRecordVO toRecordVO(AiChatRecord record) {
        AiRecordVO vo = new AiRecordVO();
        vo.setId(record.getId());
        vo.setQuestion(record.getQuestion());
        vo.setAnswer(record.getAnswer());
        vo.setSessionId(record.getSessionId());
        vo.setSubjectCode(record.getSubjectCode());
        vo.setModel(record.getModel());
        vo.setPromptTokens(record.getPromptTokens());
        vo.setCompletionTokens(record.getCompletionTokens());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }
}
