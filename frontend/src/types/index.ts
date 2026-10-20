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
  /** 知识点描述 */
  description?: string | null
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

/* ============================== 预约（科二/科三共用） ============================== */

/** 预约状态（后端 `AppointmentStatus` 枚举 name；请求参数必须全大写，小写会 1001） */
export type AppointmentStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'REJECTED' | 'COMPLETED'

/** 预约时段（后端 DTO `@Pattern` 固定的四个值；前端只能下拉选择，不得自由输入） */
export type AppointmentTimeSlot = '08:00-10:00' | '10:00-12:00' | '14:00-16:00' | '16:00-18:00'

/** 教练简要信息（对应后端 `CoachVO`；`GET /api/student/appointment/coaches`） */
export interface CoachVO {
  coachId: number
  /** 真实姓名（账号资料可空） */
  realName: string | null
  /** 手机号（账号资料可空） */
  phone: string | null
}

/**
 * 预约（对应后端 `AppointmentVO`，学员端 / 教练端共用）。
 *
 * <p>姓名 / 电话为 `null` 表示对方账号已被逻辑删除——后端刻意**保留记录**（历史完整性），
 * 因此这里不能用非空类型。</p>
 */
export interface AppointmentVO {
  id: number
  studentId: number
  studentName: string | null
  studentPhone: string | null
  coachId: number
  coachName: string | null
  coachPhone: string | null
  /** 科目（预约接口只会写入 SUBJECT_2 / SUBJECT_3） */
  subjectCode: string
  /** 预约日期（`yyyy-MM-dd`） */
  appointmentDate: string
  timeSlot: AppointmentTimeSlot
  status: AppointmentStatus
  /** 学员备注（可空） */
  remark: string | null
  /** 教练备注（拒绝原因，可空） */
  coachRemark: string | null
  /** 创建时间（后端已格式化为 `yyyy-MM-dd HH:mm:ss`） */
  createTime: string
  /** 更新时间（同上格式） */
  updateTime: string
}

/** 创建预约入参（对应后端 `AppointmentCreateDTO`；日期为 `yyyy-MM-dd`，今天或将来） */
export interface AppointmentCreateParams {
  coachId: number
  subjectCode: 'SUBJECT_2' | 'SUBJECT_3'
  appointmentDate: string
  timeSlot: AppointmentTimeSlot
  /** 学员备注（可空，后端上限 255） */
  remark?: string
}

/** 拒绝预约入参（对应后端 `AppointmentRejectDTO`；body 必须存在，至少传 `{}`） */
export interface AppointmentRejectParams {
  /** 拒绝原因（可空，后端上限 255） */
  coachRemark?: string
}

/* ============================== 教练端 · 学员管理（只读阶段） ============================== */

/**
 * 教练名下学员（对应后端 `StudentVO`）。
 *
 * <p>`bindId` 是 `coach_student.id`，用于后续按绑定记录查看学情；
 * 它与 `studentId`（`sys_user.id`）不是同一个 ID，前端不要混用。</p>
 */
export interface StudentVO {
  /** 绑定记录 ID（`coach_student.id`） */
  bindId: number
  /** 学员用户 ID（`sys_user.id`） */
  studentId: number
  /** 学员登录名 */
  username: string
  /** 真实姓名（账号被逻辑删除时可能为 null） */
  realName: string | null
  /** 手机号（可能为 null） */
  phone: string | null
  /** 绑定时间（后端已格式化为 `yyyy-MM-dd HH:mm:ss`） */
  bindTime: string
  /** 当前学习科目（后端按学情状态推导）；无进行中科目时为 null */
  currentSubjectCode: SubjectCode | null
  /** 当前学习科目的阶段；无进行中科目时为 null */
  currentPhaseStatus: SubjectPhaseStatus | null
  /** 当前学习科目的教练确认状态；未设置或无当前科目时为 null */
  coachConfirmStatus: CoachConfirmStatus | null
}

/** 学员科目学习阶段（对应 `student_subject_status.status` 枚举名） */
export type SubjectPhaseStatus =
  | 'NOT_STARTED'
  | 'LEARNING'
  | 'READY_FOR_EXAM'
  | 'EXAM_PASSED'
  | 'EXAM_FAILED'

/**
 * 学员单科学情（对应后端 `SubjectStatusVO`）。
 *
 * <p>后端固定返回 `SUBJECT_1`~`SUBJECT_4` 四项；库中缺失的科目由后端在内存中
 * 补为 `NOT_STARTED`（`mockPassCount = 0`、`lastMockScore = null`），不会写库。</p>
 */
export interface SubjectStatusVO {
  subjectCode: SubjectCode
  status: SubjectPhaseStatus
  /** 模拟考连续及格次数 */
  mockPassCount: number
  /** 最近一次模拟考成绩；从未模考时可能为 null */
  lastMockScore: number | null
}

/**
 * 绑定/创建学员入参（对应后端 `StudentBindDTO`）。
 *
 * <p>两种模式共用：`username` 已存在时只绑定；不存在时创建账号并绑定，
 * 此时 `realName` / `phone` / `password` 必填（由后端 Service 判空）。</p>
 */
export interface StudentBindParams {
  username: string
  realName?: string
  phone?: string
  password?: string
}

/**
 * 修改学情/备注入参（对应后端 `StudentUpdateDTO`）。
 *
 * <p>`subjectCode` 必填；`phaseStatus` 与 `coachRemark` 至少传一个；
 * 未修改的可选字段应省略，空字符串会被后端按“未传”处理。</p>
 */
export interface StudentUpdateParams {
  subjectCode: SubjectCode
  phaseStatus?: SubjectPhaseStatus
  /** 教练备注（后端上限 500；当前没有读取当前备注的接口） */
  coachRemark?: string
}

/* ============================== 教练端 · 学员学习详情 ============================== */

/** 系统建议值（student_subject_status.exam_ready_suggest） */
export type ExamReadySuggest = 'READY' | 'NEED_MORE' | 'NEED_HELP'

/** AI 建议来源：AI 成功 / 规则降级 */
export type AiSuggestSource = 'AI' | 'RULE'

/** AI 降级原因（后端 AiSuggestVO.degradedReason） */
export type AiDegradedReason = 'AI_TIMEOUT' | 'AI_CALL_FAILED' | 'AI_QUOTA_EXCEEDED'

/** 教练确认状态（student_subject_status.coach_confirm_status） */
export type CoachConfirmStatus = 'CONFIRMED_READY' | 'NOT_READY' | 'NEED_HELP'

/** 教练端学习数据（对应后端 LearningDataVO） */
export interface LearningDataVO {
  /** 学员用户 ID（后端由绑定记录反查） */
  studentId: number
  /** 统计科目 */
  subjectCode: SubjectCode
  /** 学情总览 */
  overview: AnalysisOverviewVO
  /** 错题分类统计 */
  wrongBookSummary: WrongBookSummaryVO
  /** 薄弱知识点（已由后端过滤 isWeak=true） */
  weakKnowledgePoints: KPStatVO[]
  /** 已结束模拟考试历史 */
  mockExamHistory: PageResult<ExamRecordVO>
}

/** AI 学习建议（对应后端 AiSuggestVO） */
export interface AiSuggestVO {
  studentId: number
  subjectCode: SubjectCode
  /** 历史异常行可能为 null */
  examReadySuggest: ExamReadySuggest | null
  /** AI 文本；读取历史规则建议时为 null（当时只保存了等级） */
  suggestion: string | null
  source: AiSuggestSource
  aiSuccess: boolean
  degraded: boolean
  degradedReason: AiDegradedReason | null
  /** AI 生成时间；规则降级为 null */
  aiSuggestTime: string | null
  /** 当前确认状态；新生成建议后后端重置为 null */
  coachConfirmStatus: CoachConfirmStatus | null
}

/* ============================== 教练-学员聊天 ============================== */

/** 聊天发起方视角：用于复用同一套前端 API 与页面组件 */
export type ChatRole = 'student' | 'coach'

/** 消息发送者角色（后端 chat_message.sender_role） */
export type ChatSenderRole = 'STUDENT' | 'COACH'

/**
 * 会话列表项（对应后端 `ChatSessionVO`）。
 *
 * <p>`peerUserId` 是对方用户 ID；`canSend` 由后端按当前有效绑定计算，
 * 仅用于前端交互提示，最终权限仍以服务端响应为准。</p>
 */
export interface ChatSessionVO {
  id: number
  peerUserId: number
  /** 对方真实姓名；对方账号被删除时可能为 null */
  peerName: string | null
  peerPhone: string | null
  /** 最后一条消息摘要；空会话为 null */
  lastMessage: string | null
  /** 后端已格式化为 yyyy-MM-dd HH:mm:ss；空会话为 null */
  lastMessageTime: string | null
  /** 当前登录角色自己的未读数 */
  unreadCount: number
  canSend: boolean
}

/** 聊天消息项（对应后端 `ChatMessageVO`） */
export interface ChatMessageVO {
  id: number
  sessionId: number
  senderId: number
  senderRole: ChatSenderRole
  content: string
  /** 接收方是否已读 */
  isRead: boolean
  /** 后端已格式化为 yyyy-MM-dd HH:mm:ss */
  createTime: string
}
