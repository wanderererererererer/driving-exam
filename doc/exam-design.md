# 模拟考试设计（Day 17）

> 目标：学员端模拟考试 —— 选卷 → 开考 → 答题（倒计时）→ 交卷判分 → 成绩报告 → 考试历史
> 归档：`doc/exam-design.md`
> 起点：`api-design.md` §4.5 已有 7 个接口清单；`exam_paper` / `exam_paper_question` / `mock_exam` 三表已在库
> ⚠️ 本文是**需求梳理**，确认后再写代码

---

## 〇、先摊开三个硬约束（必须先拍板，否则写不动）

| # | 约束 | 说明 |
|---|---|---|
| **A** | **库里没有任何试卷数据** | `exam_paper` / `exam_paper_question` **无种子数据**，全仓库也没有。开考前必须**新建试卷种子 SQL**（指定题数、时长、总分、及格分，并建立试卷-题目关联）。 |
| **B** | **题量严重不足** | `SUBJECT_1` 仅 **36 题 / 4 知识点**；`SUBJECT_4` **0 题 / 0 知识点**。真实规格（科一 100 题 45 分 / 科四 50 题 30 分）**组不出来**。 |
| **C** | **断点续考的答题无处存** | `POST /{examId}/answer`（考试中暂存单题答案）在现有三表中**没有落点**：`mock_exam` 无答案字段，`exam_paper_question` 是试卷定义。见 §七.1。 |

**A + B 的应对（已确认）**：把试卷参数做成**数据驱动**（后端一律读 `exam_paper.duration / question_count / total_score / pass_score`，代码里**不写死 100/45/90**），然后建"演示卷"：

| 试卷 | 参数 | 组卷来源 |
|---|---|---|
| **科一演示卷** | **36 题 / 20 分钟 / 总分 36 / 及格 32**（每题 1 分；实际取"读 `exam_paper` 字段"，此值即为种子数据） | 现有 36 题全部关联（`exam_paper_question`） |
| **科四** | **本期不出卷** | `SUBJECT_4` 现有 0 题；前端在"科目四"的考试入口做**禁用 + "题库准备中"提示** |

> 将来题库补到 100 题时，**只改数据、不改代码**。
>
> ⚠️ **总分必须是"整数分值可拼出"的数**：`exam_paper_question.score` 是 `int`，36 题凑不出 90 分（每题的分数不能是 2.5）。故演示卷取 **每题 1 分 → 总分 36、及格 32**（约 88.9%）。展示文案："36题 / 20分钟 / 32分及格"。

---

## 一、功能范围

1. **选卷**：按科目列出可用试卷（科目一 / 科目四）。
2. **开考**：生成 `mock_exam` 记录（`status=ONGOING`，记录 `start_time`）。
3. **答题**：复用 `QuestionCard`；**逐题作答**，可经答题卡跳题、回头修改。
4. **倒计时**：前端显示剩余时间；后端以 `start_time + duration` 为权威。
5. **交卷**：手动交卷 + **超时自动交卷**。
6. **成绩报告**：得分、对错数、是否及格、**逐题解析**（含未作答题）。
7. **考试历史**：按科目分页列出历次成绩。

**明确的 5 个关键决策（用户已给建议，本文采纳）**

| # | 决策 | 结论 |
|---|---|---|
| ① | 组卷策略 | **预置试卷**（用 `exam_paper_question`），不随机抽题——随机会导致每份卷子难度不同、不公平 |
| ② | 考试错题是否进 `wrong_book` | **进**，与练习统一口径 |
| ③ | 超时判定 | **前后端都要**：前端倒计时，后端提交时校验 `now - start_time > duration`（防篡改） |
| ④ | 断点续考 | **支持**：`status=ONGOING` 时重进恢复题目 + 已答 + 剩余时间 |
| ⑤ | 来源标记 | `answer_record.source = EXAM`，`exam_id` 记录所属考试 |

---

## 二、接口设计（7 个）

> 统一由 `PathRoleRule` 拦 `/api/student/**` → STUDENT；`userId` 取 `@RequestAttribute`。
> **考试中绝不返回答案/解析**（题目用 `QuestionVO`，与练习同构）；答案与解析只在**报告**里给。

### 2.1 `GET /api/student/exam/papers` 可用试卷

- **入参**：`subjectCode`（必填）
- **返回**：`List<PaperVO>`
- **逻辑**：`exam_paper WHERE subject_code=? AND paper_type='MOCK' AND status=1 AND deleted=0`

### 2.2 `POST /api/student/exam/start` 开始考试

- **入参**：body `StartExamDTO { paperId }`
- **返回**：`ExamVO`
- **逻辑**：
  1. 校验试卷存在/启用（否则 2101）；
  2. **防重复开卷**：该学员该科目已有 `ONGOING` 且**未超时**的考试 → **直接返回它**（不新建）；已有 `ONGOING` 但**已超时** → 先自动交卷，再新建；
  3. 新建 `mock_exam(user_id, subject_code, paper_id, start_time=now, total_score, pass_score, status=ONGOING)`；
  4. 返回 `ExamVO`（题目 + 剩余时间 + 已答=空）。

### 2.3 `GET /api/student/exam/{examId}` 断点续考

- **返回**：`ExamVO`
- **逻辑**：校验归属（非本人 → 视同不存在 2104）；`ONGOING` 时算剩余秒数，**若已超时 → 后端自动交卷**，返回 `status=TIMEOUT`；否则返回题目 + 已答 + 剩余时间。
- **倒计时以服务端为准**：`remainingSeconds = duration*60 − (now − start_time)`，**由后端计算**，前端只用它初始化倒计时。刷新页面重新拉一次即可校准，**不受本地计时漂移或改系统时间影响**。
- > 前端据 `status` 决定：`ONGOING` → 考试页；`SUBMITTED/TIMEOUT` → 报告页。

### 2.4 `POST /api/student/exam/{examId}/answer` 考试中暂存答案

- **入参**：body `ExamAnswerDTO { questionId, userAnswer }`
- **返回**：`Void`
- **逻辑**：校验归属 + `status=ONGOING` + 未超时（超时则先自动交卷并返回 2102）；校验该题属于本试卷；**upsert 暂存表**。
- **⚠️ 不判分、不返回对错**——考试中不能泄露正误。

### 2.5 `POST /api/student/exam/{examId}/submit` 交卷判分

- **入参**：body `ExamSubmitDTO { answers: [{questionId, userAnswer}] }`（前端一次性交全部已答；**可为空**表示全部未答）
- **返回**：`ExamReportVO`
- **逻辑**：
  1. 校验归属；**幂等**：已 `SUBMITTED`/`TIMEOUT` → 直接返回既有报告（不重复写库）；
  2. 合并"请求体答案"与"暂存答案"（请求体优先）；
  3. **以试卷题目为主线**逐个用 `AnswerGrader` 判分（`question.answer` 为真源）；
  4. 得分 = Σ(答对 ? 该题 `score` : 0)；`isPass = score >= pass_score`；
  5. **批量写** `answer_record(source=EXAM, exam_id, is_correct, user_answer)`（只写**答过的**题）；
  6. **答错的写 `wrong_book`**（复用 `upsertWrong`，含未答？见 §七.5）；
  7. 更新 `mock_exam(score, submit_time, duration=now-start, status=SUBMITTED 或 TIMEOUT)`；
  8. 更新 `student_subject_status(mock_pass_count, last_mock_score)`；
  9. 返回报告。

### 2.6 `GET /api/student/exam/{examId}/report` 成绩报告

- **返回**：`ExamReportVO`
- **逻辑**：`ONGOING` → 报错（考试未结束）；否则**以 `exam_paper_question` 为主线**（含 `score`、`sort_order`）**左连接** `answer_record(source=EXAM, exam_id)` + `question`（正确答案、解析）组装逐题结果——**保证未作答题也出现在报告里**。

### 2.7 `GET /api/student/exam/history` 考试历史

- **入参**：`subjectCode`（必填）、`pageNum=1`、`pageSize=10`（后端上限 20）
- **返回**：`PageResult<ExamRecordVO>`
- **逻辑**：`mock_exam WHERE user_id=? AND subject_code=? AND status<>'ONGOING'` 按 `submit_time DESC, id DESC` 分页。

---

## 三、DTO / VO 清单（`pojo` 模块）

| 类 | 位置 | 关键字段 |
|---|---|---|
| `StartExamDTO` | `pojo/dto` | `paperId`（`@NotNull`） |
| `ExamAnswerDTO` | `pojo/dto` | `questionId`（`@NotNull`）、`userAnswer`（`@NotBlank`） |
| `ExamSubmitDTO` | `pojo/dto` | `answers: List<ExamAnswerDTO>`（可空/空数组 = 全未答） |
| `PaperVO` | `pojo/vo` | `id / subjectCode / title / paperType / totalScore / passScore / duration(分钟) / questionCount` |
| `ExamVO` | `pojo/vo` | `examId / paperId / paperTitle / subjectCode / status / totalScore / passScore / durationSeconds / remainingSeconds / questions: List<QuestionVO> / answered: List<ExamAnswerItemVO>` |
| `ExamAnswerItemVO` | `pojo/vo` | `questionId / userAnswer`（**不含对错**） |
| `ExamReportVO` | `pojo/vo` | `examId / paperTitle / subjectCode / score / totalScore / passScore / isPass / correctCount / wrongCount / unansweredCount / durationSeconds / submitTime / status / items: List<ExamReportItemVO>` |
| `ExamReportItemVO` | `pojo/vo` | `questionId / seq / questionType / content / options / userAnswer / correctAnswer / isCorrect / analysis / score` |
| `ExamRecordVO` | `pojo/vo` | `id / paperTitle / subjectCode / score / totalScore / passScore / isPass / status / submitTime / durationSeconds` |

**错误码**：已有 `PAPER_NOT_FOUND(2101)` / `EXAM_SUBMITTED(2102)` / `EXAM_NO_PERMISSION(2103)`；**建议新增 `EXAM_NOT_FOUND(2104, "考试记录不存在")`**（非本人/不存在统一用它，不泄露存在性）。

**枚举**：`AnswerSource` 需**新增 `EXAM`**（现有 PRACTICE / RETRY）。另外考试状态 `ONGOING / SUBMITTED / TIMEOUT` 建议定义 `ExamStatus` 常量或枚举。

---

## 四、SQL 思路

| 步骤 | 思路 |
|---|---|
| 组卷 | `exam_paper_question WHERE paper_id=? ORDER BY sort_order` → 题目 id 列表（`score` 一并取出）→ `question` 批量 `IN` 取题干/选项（**防 N+1**，与刷题/错题本一致） |
| 选项 | 用既有"一次 IN + 内存 group by"的 `loadOptions` 思路（复用 QuestionService） |
| 暂存答案 | `INSERT ... ON DUPLICATE KEY UPDATE`（唯一键 `(exam_id, question_id)`）——单条 SQL 原子，避免并发重复 |
| 提交判分 | 逐题 `AnswerGrader.isCorrect(type, userAnswer, question.answer)`；得分累加 |
| 写答题记录 | 只对**答过的**题逐条 `insert`（`source=EXAM`, `exam_id`）；量小（≤ 一页题数），无需批量优化 |
| 写错题本 | 复用 `WrongBookMapper.upsertWrong`（`ON DUPLICATE KEY UPDATE`） |
| 报告组装 | `exam_paper_question`（含 score/sort_order）**左连接** `answer_record` + `question`，按 `sort_order` 排序 |
| 历史 | `mock_exam` 按 `submit_time DESC, id DESC` 分页 |

**超时判定**：不在 SQL，在 Service —— `LocalDateTime.now().isAfter(startTime.plusSeconds(duration*60))`。惰性判定（用户下次交互时触发），非定时任务（课设可接受，记录在案）。

---

## 五、前端数据结构与页面

### 5.1 新增文件

```
api/exam.ts            7 个接口
store/exam.ts          考试会话状态（题目、已答 Map、当前题号、剩余秒数、计时器）
types/index.ts         加 PaperVO / ExamVO / ExamAnswerItemVO / ExamReportVO / ExamReportItemVO / ExamRecordVO
views/student/ExamPaperListView.vue    试卷列表
views/student/ExamRunView.vue          考试进行页
views/student/ExamReportView.vue       成绩报告页
views/student/ExamHistoryView.vue      考试历史页
components/exam/ExamTimer.vue          倒计时
components/exam/ExamAnswerSheet.vue    答题卡（跳题 + 已答/未答）
```

### 5.2 路由

```
/student/exam?subjectCode=...            试卷列表
/student/exam/run/:examId                考试进行中（断点续考也走这里）
/student/exam/report/:examId             成绩报告
/student/exam/history?subjectCode=...    考试历史
```

### 5.3 关键交互

- **考试进行页**：`QuestionCard`（复用，**考试模式不给对错反馈**，须确认 `QuestionCard` 是否要走"无判分"分支——见 §七.8）+ `ExamTimer`（倒计时到 0 **自动交卷**）+ `ExamAnswerSheet`（答题卡）+「交卷」按钮（二次确认）。
- **答案保存**：每次选择/提交 → 调 `POST /answer`（**节流/去抖**，避免每题都打接口；或离开本题时保存）。**前端保留内存中的 `answers`，交卷时一次性带上**，即使暂存失败也不丢答案。
- **报告页**：顶部得分（`el-progress` 环形 + 是否及格 tag），下方逐题列表（题干、你的答案、正确答案、解析），未答的标"未作答"。
- **计时器必须 `onUnmounted` 清理**（Day 11 教训），并注意切后台/刷新后以**后端剩余秒数**为准（不能用本地累计）。

### 5.4 科目首页入口（**方案 A：考试大卡 + 练习 4 卡**）

把"考试"与"练习"分成两个层次——考试是阶段目标，练习是达成手段（参考驾考宝典首页）：

```
┌───────────────────────────────────────────────┐
│  ┌─────────────────────────────────────────┐  │  ← 模拟考试大卡（横跨整行，高 120~140px）
│  │  [图标] 开始模拟考试        科目一 · 36题 / 20分钟 / 32分及格 │  │     主色调/深色底 + 白字，整卡可点
│  └─────────────────────────────────────────┘  │
│                                                │
│  日常练习                                       │  ← 小标题
│  ┌────────┐ ┌────────┐                        │
│  │ 顺序练习 │ │ 随机练习 │                        │  ← 4 张小卡 2×2（沿用现有预置浅色常量）
│  └────────┘ └────────┘                        │
│  ┌────────┐ ┌────────┐                        │
│  │ 章节练习 │ │ 我的错题 │                        │
│  └────────┘ └────────┘                        │
└───────────────────────────────────────────────┘
```

- 两块间距约 **24px**；**整块（考试大卡 + 练习区）在导航下方垂直居中**（沿用现有 `.sh` 的 min-height + flex 方案）。
- 考试大卡**悬停上浮 + 阴影加深**；参数文案来自试卷数据（不写死）。
- **科目四**：考试大卡**禁用态** + 文案"题库准备中"。
- 悬浮按钮（学情分析）位置不变，仍在右下角。

---

## 六、实现顺序（一步一停）

```
17.1  实体 + Mapper：MockExam / ExamPaper / ExamPaperQuestion（+ 暂存表实体，若采纳 §七.1 方案 A）
17.2  错误码 EXAM_NOT_FOUND(2104) + AnswerSource 加 EXAM（+ ExamStatus）
17.3  pojo：DTO/VO 清单（§三）
17.4  SQL 种子：建"演示卷" exam_paper + exam_paper_question（§〇-A/B）
17.5  ExamService + Impl（papers/start/resume/answer/submit/report/history；★ 交卷用事务，见 §七.9）
17.6  ExamController（7 接口）
17.7  前端：api/exam.ts + types + store/exam.ts
17.8  前端页面：试卷列表 / 考试进行 / 报告 / 历史 + 路由 + 入口
17.9  测试集合：doc/testjson/exam-api.postman_collection.json
```

建议先做 **17.1~17.4**（把数据准备好），再做 17.5 起。

---

## 七、待确认点

### 7.0 确认结论（2026-10-08，全部拍板）

| # | 结论 |
|---|---|
| 1 | **新增 `mock_exam_answer` 草稿表**（服务端暂存，断点续考可靠） |
| 2 | 数据驱动 + 演示卷：**科一 36 题 / 20 分钟 / 总分 36 / 及格 32**（每题 1 分）；**科四本期不出卷**（入口禁用 + "题库准备中"） |
| 3 | 超时**按分数判及格**，`status=TIMEOUT` 只标识"超时交卷" |
| 4 | 考试中**不判分**（不回对错） |
| 5 | 未作答题在报告里以"未作答"出现、按错计 0 分；**不进错题本**（未答 ≠ 答错） |
| 6 | 同科目已有未超时的 `ONGOING` 时，`start` **返回进行中的那份** |
| 7 | `mock_pass_count`：及格 **+1**、不及格**重置 0** |
| 8 | 考试页**加答题卡** `ExamAnswerSheet`（仅考试页，不影响练习页） |
| 9 | 交卷**加 `@Transactional`**（跨 `answer_record`/`wrong_book`/`mock_exam`/`student_subject_status` 四表） |
| 10 | 本期**只做 `MOCK`** 试卷 |
| 11 | 入口：科目首页**方案 A**（考试大卡在上 + 练习 4 卡在下，见 §5.4） |

另：新增错误码 `EXAM_NOT_FOUND(2104)`；`AnswerSource` 加 `EXAM`。

> 下表为梳理时的原始建议，留作演进记录。

| # | 问题 | 我的建议 |
|---|---|---|
| **1** | **断点续考的答案存哪**：现有三表无落点。① **新增 `mock_exam_answer` 草稿表**（`exam_id, question_id, user_answer`，唯一键 `(exam_id,question_id)`）；② 前端 `localStorage` 存（零库改动，但换浏览器/设备即丢，且与 `POST /answer` 服务端暂存的接口设计矛盾） | **① 新增草稿表**——与 `POST /answer` 的接口语义一致、可靠。交卷判分后草稿可保留或清空（建议保留，待报告页读）；报告仍以 `answer_record` 为准 |
| **2** | **试卷参数与题量**：科一只有 36 题、科四 0 题，按真实规格组不出卷 | **建"演示卷"+ 数据驱动**：后端一律读 `exam_paper` 的时长/题数/及格分，代码不写死；科一建一份演示卷（如 20 题 / 20 分钟 / 总分 20 / 及格 18）；**科四本期先不出卷**（无题）。是否要**补齐科一种子题到 100、并补科四题**（工作量大）请定 |
| **3** | **超时后是否按分数判及格** | **按分数判及格，仅 `status` 记 `TIMEOUT`**（更公平）；另选：超时直接不及格 |
| **4** | 考试中是否判分 | **不判分**（不回对错），交卷才判——防作弊 |
| **5** | **未作答题**如何计入 | 报告里**以试卷题目为主线**出现，标"未作答"、按错计 0 分；`unansweredCount` 单独统计。**错题本是否也为未答题建记录**？建议**不建**（未答 ≠ 答错） |
| **6** | 重复开卷 | 同科目已有未超时的 `ONGOING` 考试时，`start` **返回进行中的那份**（不新建） |
| **7** | `mock_pass_count` 更新规则 | 及格 → `+1`；不及格 → **重置为 0**（真实驾考需"连续"及格；课设按此简化） |
| **8** | **答题卡** | Day 11 曾为**练习**取消答题卡；**考试需要它**（回头检查）。本次为**考试页**单独加 `ExamAnswerSheet`，不影响练习页 |
| **9** | 交卷的事务边界 | 交卷要写 `answer_record` + `wrong_book` + `mock_exam` + `student_subject_status` **四张表** → **这里应当加 `@Transactional`**（与 AI 问答不同：无条件外部调用，纯 DB 写，需要原子性）。请确认 |
| **10** | 试卷类型 | 本期**只做 `MOCK`**（`paper_type` 还有 `CHAPTER`，暂不做章节测验卷） |
| **11** | 入口 | 科目首页再加第 5 个入口？还是放到「考试」独立菜单？建议**科目首页加"模拟考试"卡片**（第 5 个，2×2 会变 3+2）|

---

## 八、待你确认后

确认本方案（尤其 §〇 三个硬约束与 §七 的 11 点）后，我从 **17.1 实体 + Mapper** 开始，按 §六 一步一停推进。
