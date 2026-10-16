import { http } from '@/utils/request'
import type { AnalysisOverviewVO, KPStatVO, TrendPointVO } from '@/types'

/** 学情分析模块接口前缀 */
const BASE = '/student/analysis'

/** 1. 学情总览 */
export function getAnalysisOverviewApi(subjectCode: string) {
  return http.get<AnalysisOverviewVO>(`${BASE}/overview`, { subjectCode })
}

/** 2. 知识点掌握度（含子孙聚合，按掌握度升序） */
export function getKnowledgeStatsApi(subjectCode: string) {
  return http.get<KPStatVO[]>(`${BASE}/knowledge`, { subjectCode })
}

/** 3. 成绩趋势（days 默认 14，后端上限 90） */
export function getTrendApi(subjectCode: string, days = 14) {
  return http.get<TrendPointVO[]>(`${BASE}/trend`, { subjectCode, days })
}
