import { http } from '@/utils/request'
import type {
  AnswerResultVO,
  AnswerSubmitDTO,
  KPTreeNodeVO,
  PageResult,
  QuestionDetailVO,
  QuestionVO,
} from '@/types'

/** 刷题模块接口前缀 */
const BASE = '/student/study'

/** 每页题数（顺序 / 章节练习用；预加载按此分页） */
export const STUDY_PAGE_SIZE = 20

/** 1. 知识点树（章节练习导航） */
export function getKnowledgePointsApi(subjectCode: string, signal?: AbortSignal) {
  return http.get<KPTreeNodeVO[]>(`${BASE}/knowledge-points`, { subjectCode }, { signal })
}

/** 2. 顺序练习（按题目 id 升序分页） */
export function getSequenceQuestionsApi(subjectCode: string, pageNum = 1, pageSize = STUDY_PAGE_SIZE) {
  return http.get<PageResult<QuestionVO>>(`${BASE}/questions/sequence`, {
    subjectCode,
    pageNum,
    pageSize,
  })
}

/** 3. 章节练习（含子孙知识点，分页） */
export function getChapterQuestionsApi(knowledgePointId: number, pageNum = 1, pageSize = STUDY_PAGE_SIZE) {
  return http.get<PageResult<QuestionVO>>(`${BASE}/questions/chapter`, {
    knowledgePointId,
    pageNum,
    pageSize,
  })
}

/** 4. 随机练习（不分页） */
export function getRandomQuestionsApi(subjectCode: string, count = 10) {
  return http.get<QuestionVO[]>(`${BASE}/questions/random`, { subjectCode, count })
}

/** 5. 题目详情（当前未使用，保留） */
export function getQuestionDetailApi(id: number) {
  return http.get<QuestionDetailVO>(`${BASE}/questions/${id}`)
}

/**
 * 6. 提交作答（顺序 / 章节 / 随机练习）。
 *
 * <p>注意：错题重练**不走这里**，要走 wrongBook.ts 的 {@code submitRetryAnswerApi}。</p>
 */
export function submitAnswerApi(data: AnswerSubmitDTO) {
  return http.post<AnswerResultVO>(`${BASE}/answer`, data)
}
