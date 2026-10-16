package com.driving.exam.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.entity.Question;
import com.driving.exam.pojo.vo.QuestionDetailVO;
import com.driving.exam.pojo.vo.QuestionVO;

import java.util.List;

/**
 * 题目 Service（刷题取题）。
 *
 * <p>所有返回的 VO 均<b>不含 answer / analysis / is_correct</b>，防止答案泄露。</p>
 */
public interface QuestionService extends IService<Question> {

    /** 顺序练习：按题目 id 升序分页 */
    PageResult<QuestionVO> getSequenceQuestions(String subjectCode, int pageNum, int pageSize);

    /** 章节练习：按知识点（含全部子孙知识点）分页 */
    PageResult<QuestionVO> getChapterQuestions(Long knowledgePointId, int pageNum, int pageSize);

    /** 随机练习：从该科目随机抽取 count 道题 */
    List<QuestionVO> getRandomQuestions(String subjectCode, int count);

    /** 题目详情（单题，含选项与知识点名称） */
    QuestionDetailVO getQuestionDetail(Long questionId);

    /**
     * 把题目实体批量装配成 VO（含选项），供其它模块复用（如错题重练）。
     *
     * <p>实现保证：选项用**一次 IN 查询**批量取回后在内存分组装配，不产生 N+1；
     * <b>返回顺序与入参顺序一致</b>（调用方可依赖此点保持随机顺序）。</p>
     *
     * @param questions 题目列表；为 {@code null} 或空时返回空列表，不抛异常
     * @return 与入参同序的 VO 列表，均不含 answer / analysis / options[].isCorrect
     */
    List<QuestionVO> assemble(List<Question> questions);
}
