/**
 * 共享领域类型（与后端 VO/DTO 一一对应）。
 *
 * <p>整齐放在这里的原因：`QuestionVO` 等被 api/study 与 api/wrongBook 同时使用，
 * 放各自文件会造成交叉 import。</p>
 */

/** 科目编码 */
export type SubjectCode = 'SUBJECT_1' | 'SUBJECT_2' | 'SUBJECT_3' | 'SUBJECT_4'

/** 题型 */
export type QuestionType = 'SINGLE' | 'MULTIPLE' | 'JUDGE'

/** 错题本分类（后端推导，前端只渲染） */
export type WrongBookCategory = 'PENDING' | 'HIGH_FREQUENCY' | 'MASTERED'

/**
 * 练习模式（前端概念）。
 * 前三种走 /student/study/**；RETRY 走 /student/wrong-book/**。
 */
export type PracticeMode = 'SEQUENCE' | 'CHAPTER' | 'RANDOM' | 'RETRY'

/** 分页返回结构（对应后端 PageResult<T>） */
export interface PageResult<T> {
  total: number
  pages: number
  pageNum: number
  pageSize: number
  records: T[]
}

/** 题目选项（**不含 isCorrect** —— 防作弊，后端不下发） */
export interface QuestionOptionVO {
  optionKey: string
  optionContent: string
}

/** 题目（列表 / 刷题用，**不含 answer / analysis**） */
export interface QuestionVO {
  id: number
  subjectCode: string
  questionType: QuestionType
  content: string
  difficulty?: number
  mediaUrl?: string | null
  options?: QuestionOptionVO[]
}

/** 题目详情（含知识点名称，同样不含答案解析） */
export interface QuestionDetailVO extends QuestionVO {
  knowledgePointId?: number
  knowledgePointName?: string
}

/** 知识点树节点 */
export interface KPTreeNodeVO {
  id: number
  name: string
  parentId: number
  level: number
  sortOrder: number
  /** 含子孙知识点的题目合计 */
  questionCount?: number
  children?: KPTreeNodeVO[]
}

/** 提交作答入参（**无 source** —— 来源由调用的接口决定） */
export interface AnswerSubmitDTO {
  questionId: number
  /** 单选 'A' / 多选 'AB' / 判断 '对' 或 '错' */
  userAnswer: string
  /** 答题耗时（秒），可空 */
  duration?: number
}

/** 判分结果（**只有提交后才返回正确答案与解析**） */
export interface AnswerResultVO {
  questionId: number
  isCorrect: boolean
  userAnswer: string
  correctAnswer: string
  analysis?: string | null
}

/** 错题本条目 */
export interface WrongBookItemVO {
  /** 错题记录 ID（用于标记已掌握） */
  id: number
  questionId: number
  questionType: QuestionType
  content: string
  wrongCount: number
  lastWrongTime?: string | null
  /** 1 已掌握 / 0 未掌握 */
  mastered: number
  category: WrongBookCategory
}

/** 错题本分类统计（tab 角标） */
export interface WrongBookSummaryVO {
  pending: number
  highFrequency: number
  mastered: number
  total: number
}

/* ============================== 智能问答（Day 13） ============================== */

/**
 * AI 回答（对应后端 `AiAnswerVO`，POST /student/ai/chat 返回）。
 */
export interface AiAnswerVO {
  answer: string
  /** 落库后的记录 ID（可用于删除该条） */
  recordId: number
  /** 服务端返回的**实际**模型（如 deepseek-flash） */
  model: string
  promptTokens?: number
  completionTokens?: number
  /** 后端格式化为 yyyy-MM-dd HH:mm:ss */
  createTime?: string
}

/**
 * 历史问答条目（对应后端 `AiRecordVO`）。
 *
 * <p>列表返回**完整 `answer`**（本模块无独立"详情"接口），前端展示时自行截断。</p>
 */
export interface AiRecordVO {
  id: number
  question: string
  answer: string
  /** 会话 ID（历史按会话组织的分组键） */
  sessionId?: string | null
  subjectCode?: string | null
  model?: string
  promptTokens?: number
  completionTokens?: number
  createTime?: string
}

/**
 * AI 会话（对应后端 `AiSessionVO`）——历史抽屉的列表元素。
 *
 * <p>一个 `sessionId` 聚合若干轮问答；`firstQuestion` 作列表标题。</p>
 */
export interface AiSessionVO {
  sessionId: string
  firstQuestion: string
  /** 该会话最后一条记录的时间（后端已格式化为 yyyy-MM-dd HH:mm:ss） */
  lastTime: string
  /** 该会话有几轮问答 */
  messageCount: number
}

/* ============================== 学情分析（Day 15） ============================== */

/** 短板原因（与后端 WeakReason 一致；中文文案由前端映射） */
export type WeakReason = 'LOW_MASTERY' | 'BASE_WEAK'

/** 学情总览（对应后端 AnalysisOverviewVO） */
export interface AnalysisOverviewVO {
  totalQuestions: number
  answeredQuestions: number
  /** 总体正确率 0~1；**用户一道都没答过时为 null**（前端显示"-"而非"0%"） */
  correctRate: number | null
  /** 练习进度 0~1 */
  progress: number
  weakCount: number
  /** 以下三个为 Day16~17 预留，当前恒为 null */
  mockExamCount: number | null
  mockExamAvgScore: number | null
  mockExamPassRate: number | null
}

/** 知识点掌握度条目（对应后端 KPStatVO；含子孙聚合） */
export interface KPStatVO {
  knowledgePointId: number
  knowledgePointName: string
  level: number
  /** 掌握度 0~1 */
  mastery: number
  /** 含子孙的答题数（**不可跨知识点相加**，总量请看 overview.answeredQuestions） */
  answeredCount: number
  /** 含子孙的正确率 0~1 */
  correctRate: number
  isWeak: boolean
  /** 非短板时为 null */
  weakReason: WeakReason | null
}

/** 成绩趋势点（对应后端 TrendPointVO） */
export interface TrendPointVO {
  /** yyyy-MM-dd */
  date: string
  /** 当日正确率 0~1 */
  correctRate: number
  answeredCount: number
  /** Day16~17 预留，当前恒为 null */
  mockScore: number | null
}

/* ============================== 模拟考试（Day 17） ============================== */

/** 可用试卷（对应后端 PaperVO） */
export interface PaperVO {
  id: number
  title: string
  subjectCode: string
  /** 题目数量 */
  questionCount: number
  /** 考试时长（分钟） */
  duration: number
  totalScore: number
  passScore: number
  /**
   * 该试卷已有该学员「进行中」考试时，为其 examId；否则 null。
   * 前端据此在试卷卡片上显示「继续考试」。
   */
  ongoingExamId?: number | null
}

/** 考试状态（对应后端 ExamVO.status） */
export type ExamStatus = 'ONGOING' | 'SUBMITTED' | 'TIMEOUT'

/**
 * 考试视图（对应后端 ExamVO，`POST /exam/start` 与 `GET /exam/{examId}` 的返回）。
 *
 * <p>字段与后端一一对应；本阶段（试卷列表 + 开始考试入口）只用到
 * {@link ExamVO.examId} / {@link ExamVO.paperTitle} / {@link ExamVO.status}
 * 与 {@link ExamVO.ongoingExamId}（来自 PaperVO）。`questions` / `answeredMap` /
 * `remainingSeconds` 为后端真实返回字段，供后续「答题页」阶段使用，本阶段不做渲染。</p>
 */
export interface ExamVO {
  examId: number
  paperTitle: string
  status: ExamStatus
  /** 剩余秒数（后端计算）；已交卷/超时可 ≤ 0 */
  remainingSeconds: number | null
  /** 试卷题目（不含答案，防作弊）—— 答题页阶段使用 */
  questions: QuestionVO[]
  /** 已作答题目：questionId → userAnswer（断点续考用）—— 答题页阶段使用 */
  answeredMap: Record<number, string>
  /** 已作答题目：questionId → 是否答对（**服务端计算**，不含标准答案；刷新恢复答题卡正误用） */
  correctMap: Record<number, boolean>
}

/** 单题判题结果（对应后端 ExamJudgeVO；**不含标准答案/解析**） */
export interface ExamJudgeVO {
  questionId: number
  /** 该题是否已有「服务器已保存」的答案；false 时 isCorrect 无意义 */
  answered: boolean
  isCorrect: boolean
}

/** 成绩报告逐题结果（对应后端 ExamReportItemVO；交卷后由交卷接口返回，**含正确答案/解析**） */
export interface ExamReportItemVO {
  questionId: number
  questionType: QuestionType
  content: string
  options: QuestionOptionVO[]
  userAnswer: string | null
  correctAnswer: string
  isCorrect: boolean
  analysis: string | null
}

/** 成绩报告（对应后端 ExamReportVO；交卷 `POST /submit` 的返回） */
export interface ExamReportVO {
  examId: number
  score: number
  totalScore: number
  passScore: number
  isPass: boolean
  correctCount: number
  wrongCount: number
  unansweredCount: number
  items: ExamReportItemVO[]
}

/**
 * 考试历史条目（对应后端 ExamRecordVO）。
 *
 * <p>来自 `GET /api/student/exam/history`，**只含已结束**（SUBMITTED / TIMEOUT）的考试。
 * 字段严格对齐后端：**不含**考试状态字段与详情字段。</p>
 */
export interface ExamRecordVO {
  examId: number
  paperTitle: string
  score: number
  isPass: boolean
  /** 交卷时间（后端已格式化为 `yyyy-MM-dd HH:mm:ss`） */
  submitTime: string
  /** 实际用时（秒） */
  duration: number
}
