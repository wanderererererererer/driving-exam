package com.driving.exam.server.service.impl;

import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.pojo.dto.AnswerSubmitDTO;
import com.driving.exam.pojo.entity.AnswerRecord;
import com.driving.exam.pojo.entity.Question;
import com.driving.exam.pojo.vo.AnswerResultVO;
import com.driving.exam.server.enums.AnswerSource;
import com.driving.exam.server.grader.AnswerGrader;
import com.driving.exam.server.mapper.AnswerRecordMapper;
import com.driving.exam.server.mapper.QuestionMapper;
import com.driving.exam.server.mapper.WrongBookMapper;
import com.driving.exam.server.service.PracticeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 答题编排实现：查题 → 判分 → 写答题记录 → 按场景处理错题本（普通练习 / 错题重练）。
 */
@Service
public class PracticeServiceImpl implements PracticeService {

    private final QuestionMapper questionMapper;
    private final AnswerRecordMapper answerRecordMapper;
    private final WrongBookMapper wrongBookMapper;
    private final AnswerGrader answerGrader;

    public PracticeServiceImpl(QuestionMapper questionMapper,
                               AnswerRecordMapper answerRecordMapper,
                               WrongBookMapper wrongBookMapper,
                               AnswerGrader answerGrader) {
        this.questionMapper = questionMapper;
        this.answerRecordMapper = answerRecordMapper;
        this.wrongBookMapper = wrongBookMapper;
        this.answerGrader = answerGrader;
    }

    /**
     * 提交作答。
     *
     * <p><b>事务说明：</b>写 {@code answer_record} 与错题本变更（upsertWrong / markMastered）
     * 必须在同一事务内，否则可能出现"答题记录写成功、错题本没改"的数据不一致。
     * {@code rollbackFor = Exception.class} 保证任何异常（含受检异常）都回滚。</p>
     *
     * @param scene 作答场景：普通练习答对不动错题本；错题重练答对则标记已掌握
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnswerResultVO submitAnswer(Long userId, AnswerSubmitDTO dto, AnswerSource scene) {
        // 1. 查题取标准答案与解析；不存在或已停用按"题目不存在"处理
        Question question = questionMapper.selectById(dto.getQuestionId());
        if (question == null || question.getStatus() == null || question.getStatus() != 1) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }

        // 2. 判分（纯逻辑，直接 new 也无副作用）
        boolean correct = answerGrader.isCorrect(
                question.getQuestionType(), dto.getUserAnswer(), question.getAnswer());

        // 3. 归一化用户答案后存库（便于后续按字母统计）
        //    归一化结果为空（纯脏输入）时回退存原始输入的裁剪值，避免丢失"确实作答过"的事实
        String normalized = answerGrader.normalize(dto.getUserAnswer(), question.getQuestionType());
        String userAnswerStored = normalized.isEmpty() ? dto.getUserAnswer().trim() : normalized;

        // 4. 写答题记录（answer_time / create_time 由数据库默认值或自动填充兜底）
        AnswerRecord record = new AnswerRecord();
        record.setUserId(userId);
        record.setSubjectCode(question.getSubjectCode());
        record.setQuestionId(question.getId());
        record.setUserAnswer(userAnswerStored);
        record.setIsCorrect(correct ? 1 : 0);
        record.setSource(scene.name());
        record.setDuration(dto.getDuration());
        answerRecordMapper.insert(record);

        // 5. 按场景处理错题本
        if (correct) {
            // 错题重练答对 → 标记该错题已掌握；普通练习答对不做任何额外操作
            if (scene == AnswerSource.RETRY) {
                wrongBookMapper.markMastered(userId, question.getId());
            }
        } else {
            // 答错（无论普通练习还是重练）→ 归集 / 累加错题（wrong_count+1，mastered 重置为 0）
            wrongBookMapper.upsertWrong(userId, question.getSubjectCode(), question.getId());
        }

        // 6. 组装返回
        AnswerResultVO vo = new AnswerResultVO();
        vo.setQuestionId(question.getId());
        vo.setIsCorrect(correct);
        vo.setUserAnswer(userAnswerStored);
        vo.setCorrectAnswer(question.getAnswer());
        vo.setAnalysis(question.getAnalysis());
        return vo;
    }
}
