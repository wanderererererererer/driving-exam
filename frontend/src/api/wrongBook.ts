import { http } from '@/utils/request'
import type {
  AnswerResultVO,
  AnswerSubmitDTO,
  PageResult,
  QuestionVO,
  WrongBookCategory,
  WrongBookItemVO,
  WrongBookSummaryVO,
} from '@/types'

/** 错题本模块接口前缀 */
const BASE = '/student/wrong-book'

/**
 * 1. 错题列表（分页）。
 *
 * @param category 为 undefined 表示不过滤（返回全部）；取值必须**大写**，传小写会被后端拦成 1001
 */
export function getWrongBookListApi(params: {
  subjectCode?: string
  category?: WrongBookCategory
  pageNum?: number
  pageSize?: number
}) {
  return http.get<PageResult<WrongBookItemVO>>(BASE, params)
}

/** 2. 各分类数量统计（tab 角标） */
export function getWrongBookSummaryApi(subjectCode?: string) {
  return http.get<WrongBookSummaryVO>(`${BASE}/summary`, { subjectCode })
}

/** 3. 抽取重练题集（后端优先抽高频错，不够再补待攻克） */
export function getRetryQuestionsApi(subjectCode?: string, count = 10) {
  return http.get<QuestionVO[]>(`${BASE}/retry`, { subjectCode, count })
}

/**
 * 4. 错题重练作答。
 *
 * <p>与普通练习的唯一区别：答对会把该题标记为已掌握。</p>
 */
export function submitRetryAnswerApi(data: AnswerSubmitDTO) {
  return http.post<AnswerResultVO>(`${BASE}/retry/answer`, data)
}

/** 5. 标记某条错题已掌握（不删除记录，转入「已攻克」） */
export function markMasteredApi(id: number) {
  return http.put<void>(`${BASE}/${id}/mastered`)
}
