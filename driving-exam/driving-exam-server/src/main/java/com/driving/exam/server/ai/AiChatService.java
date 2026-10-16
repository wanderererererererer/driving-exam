package com.driving.exam.server.ai;

import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.dto.AiChatRequestDTO;
import com.driving.exam.pojo.vo.AiAnswerVO;
import com.driving.exam.pojo.vo.AiRecordVO;
import com.driving.exam.pojo.vo.AiSessionVO;

import java.util.List;

/**
 * 智能问答 Service。
 *
 * <p>闭环：提问 → 限额校验 → 组装 Prompt → 调用 {@code DeepSeekClient} → 落库 → 返回。</p>
 *
 * <p><b>AI 调用失败不在此层兜底</b>：{@code DeepSeekClient} 抛出的
 * {@code AI_CALL_FAILED(4001) / AI_TIMEOUT(4002) / AI_QUOTA_EXCEEDED(4003)} 一律<b>向上冒泡</b>，
 * 由全局异常处理器统一转成 {@code ApiResponse}。</p>
 */
public interface AiChatService {

    /**
     * 提问并获取 AI 回答。
     *
     * @param userId 当前登录用户（来自 JWT）
     * @param dto    问题、科目、会话 ID
     * @return AI 回答（含落库后的记录 ID、实际模型、Token 用量）
     */
    AiAnswerVO chat(Long userId, AiChatRequestDTO dto);

    /**
     * 查询当前用户的<b>会话列表</b>（按最后消息时间倒序分页）。
     *
     * <p>一个会话 = 一个 {@code session_id} 聚合的若干轮问答；{@code session_id} 为空的遗留记录不计入。</p>
     *
     * @param pageSize 后端会强制上限（见实现类常量 {@code MAX_PAGE_SIZE}，默认 20）
     */
    PageResult<AiSessionVO> sessions(Long userId, int pageNum, int pageSize);

    /**
     * 查询某个会话的全部消息（按时间<b>升序</b>，供主区顺序回显）。
     *
     * <p>{@code user_id} 参与 WHERE 做越权保护；会话不存在或不属于该用户时返回空列表。</p>
     */
    List<AiRecordVO> sessionMessages(Long userId, String sessionId);

    /**
     * 删除<b>整个会话</b>（该 {@code session_id} 下的全部记录，物理删除，带越权保护）。
     *
     * <p>"会话不存在"与"不属于当前用户"返回<b>同一个</b>错误码
     * {@code AI_RECORD_NOT_FOUND(4004)}，<b>不单独返回 403</b>——避免通过错误码差异探测会话是否存在。</p>
     */
    void deleteSession(Long userId, String sessionId);
}
