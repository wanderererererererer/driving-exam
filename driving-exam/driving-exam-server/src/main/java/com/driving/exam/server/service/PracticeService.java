package com.driving.exam.server.service;

import com.driving.exam.pojo.dto.AnswerSubmitDTO;
import com.driving.exam.pojo.vo.AnswerResultVO;
import com.driving.exam.server.enums.AnswerSource;

/**
 * 答题编排 Service（普通练习 / 错题重练共用）。
 */
public interface PracticeService {

    /**
     * 提交一次作答：判分 → 写答题记录 → 按场景处理错题本。
     *
     * @param userId 当前登录用户 id（来自 JWT，不采信前端）
     * @param dto    作答入参
     * @param scene  作答场景（由调用的接口决定，不来自请求体）：
     *               普通练习答对不动错题本；错题重练答对则标记已掌握
     * @return 判分结果（含正确答案与解析）
     */
    AnswerResultVO submitAnswer(Long userId, AnswerSubmitDTO dto, AnswerSource scene);
}
