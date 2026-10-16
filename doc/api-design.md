# 驾考智能辅导系统 · API 接口设计文档

> 项目：《基于大语言模型的驾考智能辅导系统设计与实现》
> 范围：后端 RESTful API
> 状态：接口清单已评审定稿（Day 2 / Step 2.2）

---

## 1. 通用约定

- 基础前缀 `/api`，按角色划分二级前缀：`/api/auth`、`/api/student`、`/api/coach`、`/api/admin`。
- 认证：除登录外，请求头携带 `Authorization: Bearer <token>`（JWT 无状态认证）。
- 分页入参统一 `pageNum`（从 1 开始）、`pageSize`。
- 时间格式 `yyyy-MM-dd HH:mm:ss`，日期 `yyyy-MM-dd`；编码 UTF-8。
- 聊天轮询：前端每 **3~5 秒** 轮询一次，以 `lastId` 增量拉取；无新消息返回**空数组 `[]`**。

---

## 2. 统一返回结构

### 2.1 ApiResponse&lt;T&gt;（仅 3 字段）

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | Integer | 状态码，200 表示成功，其余见第 3 节 |
| `message` | String | 提示信息，成功默认"操作成功" |
| `data` | T | 业务数据，无数据时为 `null`（**保留字段，不使用 @JsonInclude 剔除**） |

```json
{ "code": 200, "message": "操作成功", "data": { "id": 1 } }
```

### 2.2 PageResult&lt;T&gt;

| 字段 | 类型 | 说明 |
|---|---|---|
| `total` | long | 总记录数 |
| `pages` | long | 总页数（供 el-pagination 使用） |
| `pageNum` | long | 当前页码 |
| `pageSize` | long | 每页条数 |
| `records` | List&lt;T&gt; | 当前页数据 |

分页接口返回 `ApiResponse<PageResult<T>>`：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "total": 128, "pages": 13, "pageNum": 1, "pageSize": 10,
    "records": [ { "id": 1 } ]
  }
}
```

### 2.3 HTTP 状态码策略

- 成功与业务失败：统一 **HTTP 200**，靠 body 中的 `code` 区分。
- `401` 未认证 / `403` 无权限：返回**真实 HTTP 状态码**，供前端 Axios 拦截器自动清 token 并跳转登录。

---

## 3. 错误码规范

**通用段（仅 4 个）**：`200` 成功 · `401` 未认证 · `403` 无权限 · `500` 系统异常。其余全部归入业务段，两段不混用。

| 段位 | 用途 | 示例 |
|---|---|---|
| 1001–1009 | 通用请求错误 | 1001 参数校验失败 · 1002 资源不存在 · 1003 数据已存在/冲突 · 1004 操作不允许 |
| 1101–1199 | 认证与用户 | 1101 用户名或密码错误 · 1102 账号已禁用 · 1103 Token 无效或过期 · 1104 原密码不正确 · 1105 用户名已存在 |
| 2001–2099 | 题库/知识点 | 2001 知识点不存在 · 2002 题目不存在 |
| 2101–2199 | 试卷/考试 | 2101 试卷不存在 · 2102 考试已交卷 · 2103 无权访问该试卷 |
| 3001–3099 | 预约 | 3001 预约不存在 · 3002 该时段已被预约 · 3003 学员未绑定教练 · 3004 仅可取消待确认预约 · 3005 当前预约状态不允许此操作 |
| 3101–3199 | 教练-学员关系 | 3101 该学员已绑定 · 3102 绑定关系不存在 |
| 4001–4099 | AI | 4001 调用失败 · 4002 超时 · 4003 调用超限 |
| 5001–5099 | 聊天 | 5001 会话不存在 · 5002 无权访问该会话 |

落地形式：`ErrorCode` 枚举 + `BusinessException` + 全局异常处理器。

---

## 4. 接口清单

### 4.1 认证与个人中心 `/api/auth`

| 方法 | 路径 | 说明 | 入参 | data |
|---|---|---|---|---|
| POST | `/api/auth/login` | 登录 | username, password | token + 用户信息 |
| POST | `/api/auth/logout` | 退出（前端清 token，后端可空实现） | — | Void |
| GET | `/api/auth/me` | 当前登录用户 | — | UserVO |
| PUT | `/api/auth/profile` | 改个人信息 | realName, phone, avatar | Void |
| PUT | `/api/auth/password` | 改密码 | oldPwd, newPwd | Void |

### 4.2 学员端 · 科目进度 `/api/student`

| 方法 | 路径 | 说明 | data |
|---|---|---|---|
| GET | `/api/student/subjects` | 四科进度概览 | List&lt;SubjectStatusVO&gt; |
| GET | `/api/student/subjects/{code}` | 单科进度详情 | SubjectStatusVO |

### 4.3 学员端 · 学习刷题（科一/科四共用）`/api/student/study`

| 方法 | 路径 | 说明 | 关键入参 | data |
|---|---|---|---|---|
| GET | `/api/student/study/knowledge-points` | 知识点树 | subjectCode | 树形 List&lt;KPTreeNodeVO&gt; |
| GET | `/api/student/study/questions/sequence` | 顺序练习 | subjectCode, pageNum(默认1), pageSize(默认10) | PageResult&lt;QuestionVO&gt; |
| GET | `/api/student/study/questions/chapter` | 章节练习（含子孙知识点） | knowledgePointId, pageNum(默认1), pageSize(默认10) | PageResult&lt;QuestionVO&gt; |
| GET | `/api/student/study/questions/random` | 随机练习 | subjectCode, count | List&lt;QuestionVO&gt; |
| GET | `/api/student/study/questions/{id}` | 题目详情（题干+选项） | — | QuestionDetailVO |
| POST | `/api/student/study/answer` | 提交作答判分 | questionId, userAnswer, source | AnswerResultVO |

> 题目 VO（`QuestionVO` / `QuestionDetailVO`）**不返回** answer 与 analysis；练习/考试统一，提交后由判分接口返回正确答案与解析。

> **`POST /answer` 的 userAnswer 传值契约（重要，前端必读）**
>
> - **JUDGE（判断题）**：传**语义文本** `'对'` 或 `'错'`，**不是**选项标识 `'A'`/`'B'`。
>   判断题不存 `question_option`；前端按 `questionType` 渲染两个固定按钮，提交时直接传 `'对'`/`'错'`。
>   后端判分做同义映射——
>   「对」← 对 / 正确 / 是 / T / TRUE / Y / YES / √ / ✓ ；
>   「错」← 错 / 错误 / 否 / F / FALSE / N / NO / × / X 。
> - **SINGLE / MULTIPLE**：传**选项标识**，如 `'A'`、`'AB'`。
>   判分前会归一化（大小写、顺序、分隔符 `,`/`，`/`、`、空白、全角字符），**全对才算对**，不设部分分。
> - **来源（`answer_record.source`）不在请求体传入**，由**调用的接口**决定：
>   刷题作答为 `PRACTICE`，错题重练作答为 `RETRY`。

### 4.4 学员端 · 错题本 `/api/student/wrong-book`

| # | 方法 | 路径 | 说明 | 关键入参 | data |
|---|---|---|---|---|---|
| 1 | GET | `/api/student/wrong-book` | 错题列表（分页） | subjectCode?, **category?**, pageNum=1, pageSize=10 | PageResult&lt;WrongBookItemVO&gt; |
| 2 | GET | `/api/student/wrong-book/summary` | 各分类数量（tab 角标） | subjectCode? | WrongBookSummaryVO |
| 3 | GET | `/api/student/wrong-book/retry` | 抽重练题集（优先高频错） | subjectCode?, count=10 | List&lt;QuestionVO&gt; |
| 4 | POST | `/api/student/wrong-book/retry/answer` | **重练作答**（答对置 `mastered=1`） | body: AnswerSubmitDTO | AnswerResultVO |
| 5 | PUT | `/api/student/wrong-book/{id}/mastered` | 标记该条已掌握 | — | Void |

> **分类 `category`：大小写敏感，必须大写** —— `PENDING` / `HIGH_FREQUENCY` / `MASTERED`；**不传 = 全部**。
> 判定规则与优先级见 `doc/data-conventions.md` §8。传非法值返回 `1001 参数类型不匹配：category`。

> **列表排序（后端按 category 固定，前端无需传排序参数）**：
> `HIGH_FREQUENCY` 按 `wrongCount` 倒序（错最多的先练）；其余按 `lastWrongTime` 倒序。
> 次级键统一 `id` 倒序，保证分页结果稳定。

> **接口 4 与普通刷题的唯一区别**：答对时额外把该题 `mastered` 置 1（转入「已攻克」）；
> **答错行为完全一致**（`wrongCount+1`、`mastered` 保持 0）。`source` 由接口决定（此处 `RETRY`），请求体**不传**。

> **接口 5 语义**：不删除记录，只把 `mastered` 置 1（前端按钮文案建议"我掌握了"）。
> `user_id` 参与 WHERE 做越权保护；**"记录不存在"与"不属于当前用户"返回同一响应** `2003 错题记录不存在`，不泄露记录是否存在。

> **已知取舍**：列表 `total` 含"题目已被逻辑删除"的记录，而 `records` 会过滤掉它们，
> 极端情况下二者条数不一致。详见 `doc/data-conventions.md` §9.1。

### 4.5 学员端 · 模拟考试 `/api/student/exam`

| 方法 | 路径 | 说明 | 关键入参 | data |
|---|---|---|---|---|
| GET | `/api/student/exam/papers` | 可用试卷列表 | subjectCode | List&lt;PaperVO&gt; |
| POST | `/api/student/exam/start` | 开始考试（建 mock_exam） | paperId | ExamVO |
| GET | `/api/student/exam/{examId}` | **断点续考**：状态 + 已答记录 + 剩余时间 | — | ExamVO |
| POST | `/api/student/exam/{examId}/answer` | 考试中保存单题答案 | questionId, userAnswer | Void |
| POST | `/api/student/exam/{examId}/submit` | 交卷判分 | answers | ExamReportVO |
| GET | `/api/student/exam/{examId}/report` | 成绩报告 | — | ExamReportVO |
| GET | `/api/student/exam/history` | 考试历史 | subjectCode, pageNum, pageSize | PageResult&lt;ExamRecordVO&gt; |

> 交卷时统一判分，写 `answer_record(source=EXAM)` 与 `wrong_book`，更新 `student_subject_status`（mock_pass_count / last_mock_score）。

### 4.6 学员端 · 学情分析 `/api/student/analysis`

| 方法 | 路径 | 说明 | 关键入参 | data |
|---|---|---|---|---|
| GET | `/api/student/analysis/overview` | 学情总览（正确率、进度） | subjectCode | AnalysisOverviewVO |
| GET | `/api/student/analysis/knowledge` | 知识点掌握度（Echarts） | subjectCode | List&lt;KPStatVO&gt; |
| GET | `/api/student/analysis/trend` | 成绩趋势 | subjectCode | List&lt;TrendPointVO&gt; |

### 4.7 学员端 · AI 问答 `/api/student/ai`

> 历史按**会话**组织：一个 `session_id` = 一次连续对话（含多轮问答）。

| 方法 | 路径 | 说明 | 关键入参 | data |
|---|---|---|---|---|
| POST | `/api/student/ai/chat` | 提问（直连 DeepSeek，落库归属该 session） | question, subjectCode, sessionId? | AiAnswerVO |
| GET | `/api/student/ai/sessions` | 会话列表（按最后消息时间倒序） | pageNum, pageSize | PageResult&lt;AiSessionVO&gt; |
| GET | `/api/student/ai/sessions/{sessionId}` | 某会话的全部消息（时间升序） | — | List&lt;AiRecordVO&gt; |
| DELETE | `/api/student/ai/sessions/{sessionId}` | 删除整个会话 | — | Void |

### 4.8 学员端 · 预约（科二/科三共用）`/api/student/appointment`

| 方法 | 路径 | 说明 | 关键入参 | data |
|---|---|---|---|---|
| GET | `/api/student/appointment/coaches` | 我的教练列表 | — | List&lt;CoachVO&gt; |
| POST | `/api/student/appointment` | 创建预约 | coachId, subjectCode, appointmentDate, timeSlot, remark | Void |
| GET | `/api/student/appointment` | 我的预约列表 | status?（五值：PENDING / CONFIRMED / CANCELLED / REJECTED / COMPLETED）, subjectCode?, pageNum, pageSize | PageResult&lt;AppointmentVO&gt; |
| GET | `/api/student/appointment/{id}` | 预约详情 | — | AppointmentVO |
| PUT | `/api/student/appointment/{id}/cancel` | 取消预约（**仅 PENDING**） | — | Void |

### 4.9 学员端 · 聊天 `/api/student/chat`（轮询）

| 方法 | 路径 | 说明 | 关键入参 | data |
|---|---|---|---|---|
| GET | `/api/student/chat/sessions` | 会话列表（含未读数） | — | List&lt;ChatSessionVO&gt; |
| GET | `/api/student/chat/sessions/{id}/messages` | 增量拉取消息 | lastId, pageSize | List&lt;ChatMessageVO&gt; |
| POST | `/api/student/chat/sessions/{id}/messages` | 发送消息 | content | ChatMessageVO |
| PUT | `/api/student/chat/sessions/{id}/read` | 标记已读 | — | Void |

> 会话对象固定为「学员 ↔ 我的教练」，`sessionId` 由后端按 `chat_session` 唯一定位。

### 4.10 教练端 `/api/coach`

**学员管理**

| 方法 | 路径 | 说明 | data |
|---|---|---|---|
| GET | `/api/coach/students` | 我的学员列表 | PageResult&lt;StudentVO&gt; |
| POST | `/api/coach/students` | 绑定或**创建**学员 | Void |
| PUT | `/api/coach/students/{id}` | 改备注 / 科目阶段 | Void |
| DELETE | `/api/coach/students/{id}` | 解绑（coach_student.status=0） | Void |
| GET | `/api/coach/students/{id}/status` | 学员状态详情（各科进度） | List&lt;SubjectStatusVO&gt; |
| GET | `/api/coach/students/{id}/learning-data` | 学员模拟考历史、正确率、错题数、薄弱知识点 | LearningDataVO |
| GET | `/api/coach/students/{id}/wrong-book` | 学员错题详情（分页） | PageResult&lt;WrongBookVO&gt; |
| POST | `/api/coach/students/{id}/ai-suggest` | 生成 AI 考试建议 | AiSuggestVO |
| PUT | `/api/coach/students/{id}/confirm` | 教练确认建议 | Void |

**预约处理**

| 方法 | 路径 | 说明 | data |
|---|---|---|---|
| GET | `/api/coach/appointments` | 预约列表（可按状态筛，status? 五值同 §4.8） | PageResult&lt;AppointmentVO&gt; |
| PUT | `/api/coach/appointments/{id}/approve` | 审批通过 | Void |
| PUT | `/api/coach/appointments/{id}/reject` | 拒绝 | Void |
| PUT | `/api/coach/appointments/{id}/complete` | 标记完成 | Void |

**聊天**：`/api/coach/chat/...` 与 4.9 结构一致（角色互换）。

### 4.11 管理员端 `/api/admin`

| 模块 | 方法 · 路径 | 说明 |
|---|---|---|
| 数据概览 | GET `/api/admin/dashboard` | 用户/题目/预约等聚合指标 |
| 用户管理 | GET/POST/PUT/DELETE `/api/admin/users[/{id}]` | 增删改查（role 过滤） |
| 绑定管理 | GET/POST/DELETE `/api/admin/bindings[/{id}]` | 教练-学员绑定维护 |
| 知识点管理 | GET/POST/PUT/DELETE `/api/admin/knowledge-points[/{id}]` | 树形 CRUD |
| 题库管理 | GET/POST/PUT/DELETE `/api/admin/questions[/{id}]` | 含选项一起提交 |
| 试卷管理 | GET/POST/PUT/DELETE `/api/admin/papers[/{id}]` | 含组卷（选题、设分） |
| 预约查看 | GET `/api/admin/appointments` | 只读列表 |
| AI 问答统计 | GET `/api/admin/ai/stats` | 只出聚合数字，不看明细 |
| 个人中心 | 复用 `/api/auth/*` | 不重复 |

---

## 5. 关键业务边界（评审确认）

1. **教练创建学员**：入参含 `username`、`realName`、`phone`；默认密码由后端生成。
2. **取消预约**：仅 `PENDING` 状态允许取消；`CONFIRMED` 状态需联系教练处理。
3. **模拟考试断点续考**：`GET /api/student/exam/{examId}` 返回已答记录与剩余时间。
4. **题目 VO 不返回答案与解析**；练习与考试统一，提交后由判分接口返回。
5. **聊天轮询**：`lastId` 增量，前端 3~5 秒一次，无新消息返回空数组。
6. **管理员 AI 统计**：只出聚合，不看明细。
7. **可选接口全部保留**：登出、错题删除、AI 记录删除。
