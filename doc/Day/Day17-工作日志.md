# 【Day 17 工作日志】

## 一、日期

2026-10-08

**时间线说明**：本日跳过 **Day14** 与 **Day16**——① Day14 原计划是"前后端联调 + 第 3 周启动"，实际未单独做（联调分散在各模块完成时测掉），其内容由 Day15 承接；② 计划里"模拟考试"是 **Day16~Day17 一整块**，实际被当作一天做完，统一记为 **Day17**。因此 `doc/Day/` 下**没有** Day14、Day16 日志，也不需要补。Day15 日志"明日计划"里写的"Day16~17：模拟考试模块…"，指的就是本篇内容。

## 二、今日目标

完成**模拟考试模块**：试卷列表、开始考试、断点续考、考试中保存答案、交卷判分、成绩报告、考试历史（7 个接口）；完成后做**接口级联调**并生成测试集合。

## 三、完成的工作

**数据库设计：** 新增 **1 张表** `mock_exam_answer`（考试暂存答案，断点续考用）；另产出 2 个种子/建表 SQL 文件。

- `sql/schema-mock-exam-answer.sql`：`id / exam_id / question_id / user_answer / create_time / update_time`，唯一键 `uk_exam_question(exam_id, question_id)`、索引 `idx_exam_id`；`CREATE TABLE IF NOT EXISTS`，可重复执行。
- `sql/seed-exam-paper.sql`：科一演示卷（`exam_paper` id 固定 = 1：36 题 / 20 分钟 / 总分 36 / 及格 32），并用 `ROW_NUMBER() OVER (ORDER BY q.id)` 生成 `sort_order`、每题 1 分，关联现有 36 题；附 3 条验证 SQL。

**后端：** 新增 **26 个文件**、改动 **4 个**。

### 1. 实体 + Mapper（`pojo` + `server/mapper`）

- **实体 5 个**：`ExamPaper`（主数据表，`@TableLogic`）、`ExamPaperQuestion`（纯关联表，无 deleted、无时间字段）、`MockExam`（流水表，无 deleted / updateTime，`start_time` **无库默认值、插入必须显式赋值**）、`MockExamAnswer`、`StudentSubjectStatus`（**该表无 deleted 列 → 刻意不加 `@TableLogic`**）。
- **Mapper 5 个**：`ExamPaperMapper` / `ExamPaperQuestionMapper` / `MockExamMapper` / `StudentSubjectStatusMapper`（均仅 BaseMapper）、`MockExamAnswerMapper`（含自定义 `upsertAnswer`，`INSERT ... ON DUPLICATE KEY UPDATE`）。

### 2. DTO / VO（`pojo`）

- **DTO 3 个**：`StartExamDTO`（`paperId` `@NotNull`）、`ExamAnswerDTO`（`questionId` `@NotNull` + `userAnswer` `@NotBlank`；`/answer` 与 `submit.answers[]` 两处复用）、`ExamSubmitDTO`（仅 `answers`，**examId 走路径不放请求体**，可空表示从草稿读）。
- **VO 6 个**：`PaperVO`（含 `ongoingExamId`）、`ExamVO`（`examId / paperTitle / status / remainingSeconds / questions / answeredMap`）、`ExamAnswerItemVO`、`ExamReportVO`、`ExamReportItemVO`（含 `questionType` 与 `options`，供报告渲染选项）、`ExamRecordVO`。
- **两个布尔字段用包装类型 `Boolean`**：`isPass`（`ExamReportVO` / `ExamRecordVO`）与 `isCorrect`（`ExamReportItemVO`）——否则 Lombok 生成 `isPass()`，Jackson 输出的 JSON 键会变成 `pass` / `correct`，与前端约定不符。

### 3. Service（`server/exam` + `impl`，自成一包）

- `ExamService`（7 方法，**不 extends IService**）+ `ExamServiceImpl`（`@Service`，构造器注入 4 Mapper + `ExamFinalizer` + `QuestionService`）。
- 7 个私有工具：`loadOwnedExam` / `loadPaper` / `loadPaperItems` / `computeRemainingSeconds` / `isTimeout` / `loadAnsweredMap` / `createExam` / `findOngoing` / `buildExamVO`。
- `PaperQuestionItem`（`record`：`question / score / sortOrder`）承载"一道考试题 + 它的分值与顺序"，交卷判分与报告组装共用。

### 4. 独立事务 Bean：`ExamFinalizer`（本模块的核心）

- `server/exam/ExamFinalizer`，两个入口：
  - `finalize(exam, paperDurationMinutes, paperQuestions, answers, isTimeout)` —— **写**：逐题判分 → 写 `answer_record(source=EXAM)` → 答错进 `wrong_book` → 委托 `buildReport` 产出报告 → 更新 `mock_exam` 与 `student_subject_status` → 清空草稿。标注 `@Transactional(rollbackFor = Exception.class)`。
  - `buildReport(exam, paperQuestions)` —— **读**：从 `answer_record` 还原报告，供 `GET /report` 与 `submit` 的幂等分支复用。
- `ExamStatus` 枚举：`ONGOING / SUBMITTED / TIMEOUT`（替掉散落的魔法字符串）。

### 5. 改动既有文件（4 个）

| 文件 | 改动 |
|---|---|
| `common/exception/ErrorCode.java` | 新增 `EXAM_NOT_FOUND(2104)` 与 `EXAM_ONGOING(2105)`（21xx 段，与 2101~2103 同段） |
| `common/exception/GlobalExceptionHandler.java` | 新增 `HttpMessageNotReadableException` → `1001`（见问题 4） |
| `server/enums/AnswerSource.java` | 新增 `EXAM`（区分练习 / 重练 / 考试三条提交路径） |
| `server/service/impl/QuestionServiceImpl.java` | 把私有 `loadOptions` **原样抽出**为 `QuestionOptionAssembler`（`@Component`），改用注入调用（见问题 1 附注） |

### 6. Controller

- `ExamController`（`/api/student/exam`）：`papers` / `start` / `{examId}` / `{examId}/answer` / `{examId}/submit` / `{examId}/report` / `history` 共 7 个接口；`submit` 用 `@RequestBody(required = false)`（body 可空，从草稿读）。

### 7. 测试集合

- `doc/testjson/exam-api.postman_collection.json`：组 0~7，**21 请求 / 113 断言行**；答案**内联**在请求体里；含"黄金标准"断言与一条断点续考用例（TC-EX-03k）。

**前端：** （本日不涉及）——本日只做后端与接口级联调，考试前端页面尚未开工，已记入遗留。

**文档 / 设计：**

- `doc/exam-design.md`（模拟考试设计文档，Day15 末 ~ Day17 期间撰写）：7 接口、状态机、数据驱动试卷参数、断点续考、实现顺序、待确认点。
- 另（**非模块产出**，为对外对接整理）：`doc/项目交接简报.md`、`doc/计划书对齐说明.md`。

## 四、关键技术决策

| 决策点 | 方案 | 理由 |
|---|---|---|
| 组卷策略 | **预置试卷**（用 `exam_paper_question` 固定题目），不随机抽题 | 随机抽题会让每份卷子难度不同，**考试不公平** |
| 试卷参数 | **数据驱动**：时长 / 题数 / 总分 / 及格分一律读 `exam_paper` 字段，代码**不写死 100/45/90** | 题库扩充后**只改数据、不改代码** |
| 防作弊 | 考试中下发 `QuestionVO`（**不含 `answer` / `analysis`**）；答案与解析**只在报告里给** | 与刷题模块统一口径 |
| 判分真相唯一性 | 复用 **`AnswerGrader`**（以 `question.answer` 为唯一真源），NFKC 归一化 + 大写与练习**完全同一套** | 刷题与考试绝不能有两套判分 |
| 考试中是否判分 | **不判分**，交卷时统一判分 | 防止考试过程中泄露正误 |
| 事务边界 | **只有"交卷 / 超时收尾"加 `@Transactional`**，且落在**独立 Bean** `ExamFinalizer` | ① 写库只在最终一段；② **同类自调用不走 AOP 代理**，抽独立 Bean 事务才真正生效；只读接口一律不加 |
| 断点续考 | 新增 `mock_exam_answer` 暂存表（唯一键 + upsert 覆盖）；`GET /{examId}` 返回 `status` / `remainingSeconds` / `answeredMap` | 交卷前的答案必须有落点；**剩余时间由后端算**（`duration*60 − (now − start_time)`），刷新即校准、不受本地计时漂移影响 |
| 超时处理 | **后端为准**（`now − start_time > duration*60`），**惰性判定**：在 `start` / `resume` / `answer` / `submit` 触发收尾；前端倒计时仅作提示 | 防篡改；不引入定时任务（课设可接受，记录在案） |
| 状态机与幂等 | `ExamStatus`：`ONGOING / SUBMITTED / TIMEOUT`；交卷**幂等**（已交卷直接返回既有报告） | 语义清晰；前端重试不产生重复写库 |
| 报告组装 | `finalize` **委托只读的 `buildReport`**；`report()` 与 `submit` 幂等分支都复用它 | "报告组装"**只有一份实现**，避免两处口径漂移 |
| 来源与副作用 | `answer_record.source=EXAM` + `exam_id`；答错进 `wrong_book`；**未作答题不写库**；交卷后**清空草稿** | 与练习口径统一；**未答 ≠ 答错**；避免重考携带旧答案 |

## 五、遇到的问题及解决方案

**问题 1（接口设计有"考试中保存答案"，但现有三表无处存）**

- 现象：`POST /{examId}/answer`（考试中暂存单题答案、断点续考要恢复）在设计里存在，但 `mock_exam` 没有答案字段、`exam_paper_question` 是试卷定义——**现有三张表里没有落点**。
- 原因：初版表设计只考虑了"交卷后写 `answer_record`"，没考虑"交卷前"。
- 解决：新增 **`mock_exam_answer` 暂存表**，唯一键 `(exam_id, question_id)` + `INSERT ... ON DUPLICATE KEY UPDATE`。**交卷后清空该表**（答案已进 `answer_record`，草稿无用）。
  > 附注：顺带把 `QuestionServiceImpl` 的私有 `loadOptions` **原样抽成** `QuestionOptionAssembler`（`@Component`），因为考试报告也要装配选项——**避免第二份"一次 IN + 内存 group by"** 的重复实现。抽完重跑刷题集合回归，**断言失败 0**。

**问题 2（`@Transactional` 被"同类自调用"绕过）**

- 现象：超时收尾不只在 `submit` 发生——`start`（返回前收尾旧的超时考试）、`resume`、`answer` 都可能触发"判分 + 落库"。
- 原因：若这些入口用 `this.xxx()` 调同类里的 `@Transactional` 方法，**不走 Spring AOP 代理，事务静默失效**，四张表可能写一半就中断。
- 解决：把"判分 + 落库"抽成**独立 Bean `ExamFinalizer`**，各入口**跨 Bean 注入调用**。这样事务**只有一处、且真正生效**。

**问题 3（报告接口对"进行中"的考试抛了语义不符的错误码）**

- 现象：`report()` 对"考试仍在进行中"抛 `EXAM_SUBMITTED(2102)`，而它的文案是"**考试已交卷**"——对着一个还没交卷的考试说"已交卷"，措辞不贴切。
- 解决：新增 **`EXAM_ONGOING(2105, "考试尚未结束")`**，`report()` 改抛它；`answer()` 与 `submit()` 的幂等分支仍抛 2102（那两个场景确实是"已交卷"）。

**问题 4（缺请求体返回 500，而不是 1001）**

- 现象：对任意 POST 接口发**空 body**，返回的是 `code=500`，而不是参数错误 `1001`。
- 原因：`GlobalExceptionHandler` 处理了 `MethodArgumentNotValid / Bind / MissingServletRequestParameter / MethodArgumentTypeMismatch`，**唯独没有 `HttpMessageNotReadableException`**（body 缺失或 JSON 非法时抛的正是它），于是落到兜底 `Exception` 分支返回 500。
- 解决：补一个 `HttpMessageNotReadableException` → **`1001`** 的处理器。这是**全项目通用缺口**（任何接口的 body 格式错都给 500），一次修好、全线受益；改完重跑三个既有集合（刷题 / 错题本 / 学情）确认未破坏。

**问题 5（题量不足，组不出真实规格的卷；且总分必须"整数可分"）**

- 现象：真实规格是科一 100 题 / 45 分钟 / 90 分及格，但库里 `SUBJECT_1` **只有 36 题**、`SUBJECT_4` **0 题**；且 `exam_paper_question.score` 是 **`int`**，36 题**凑不出 90 分**（每题不能 2.5 分）。
- 解决：① **试卷参数数据驱动**（一律读 `exam_paper`），题库扩充只改数据；② 建"**科一演示卷**"：**36 题 / 20 分钟 / 总分 36 / 及格 32**（每题 1 分，约 88.9%）；③ 科四**本期不出卷**。**答辩若被追问"为什么不是 100 题 90 分"，答案就是"整数分值可拼出"这一条约束。**

**问题 6（Apifox 副本与仓库不一致，造成"假失败"）**

- 现象 A：回归刷题集合时出现 9 条失败，全是"**期望 18、实得 36**"这类计数不符。
  - 原因：Apifox 里跑的是**种子从 18 题扩到 36 题之前导入的旧副本**，而仓库 `.json` 早已更新（用例名里带着期望值，报告与实际文件对不上，是铁证）。
  - 解决：**重新导入**；并在该集合描述里补上"数据基线（36 题 / 各知识点含子孙题数）+ **本文件是唯一真源、改完必须重导**"。
- 现象 B：新增的 `TC-EX-02b` 在考试集合里失败（`ongoingExamId` 期望 3、实得 null）。
  - 原因：**覆盖导入**时 Apifox 把**新增用例追加到集合末尾**，导致它跑到 **交卷之后**——此时考试已 `SUBMITTED`，`ongoingExamId` 必为 null。**后端无问题**。
  - 解决：改用**先删旧集合再导入**；并在该用例名与描述上加"**★须在交卷前执行**"的提示。

## 六、产出物清单

**后端（新增 26）**

```
pojo/entity/ExamPaper.java · ExamPaperQuestion.java · MockExam.java · MockExamAnswer.java · StudentSubjectStatus.java
pojo/dto/StartExamDTO.java · ExamAnswerDTO.java · ExamSubmitDTO.java
pojo/vo/PaperVO.java · ExamVO.java · ExamAnswerItemVO.java · ExamReportVO.java · ExamReportItemVO.java · ExamRecordVO.java
server/enums/ExamStatus.java
server/exam/PaperQuestionItem.java · ExamService.java · ExamFinalizer.java · impl/ExamServiceImpl.java
server/assembler/QuestionOptionAssembler.java
server/mapper/ExamPaperMapper.java · ExamPaperQuestionMapper.java · MockExamMapper.java · MockExamAnswerMapper.java · StudentSubjectStatusMapper.java
server/controller/ExamController.java
```

**后端（改动 4）**

```
common/exception/ErrorCode.java               加 EXAM_NOT_FOUND(2104) / EXAM_ONGOING(2105)
common/exception/GlobalExceptionHandler.java  加 HttpMessageNotReadableException → 1001
server/enums/AnswerSource.java                加 EXAM
server/service/impl/QuestionServiceImpl.java  注入 QuestionOptionAssembler（原私有 loadOptions 抽出）
```

**SQL（新增 2）**

```
sql/schema-mock-exam-answer.sql   断点续考暂存表
sql/seed-exam-paper.sql           科一演示卷（36 题 / 20 分钟 / 总分 36 / 及格 32）
```

**文档 / 测试集合**

- `doc/exam-design.md`（模拟考试设计文档，Day15 末 ~ Day17 期间）
- `doc/testjson/exam-api.postman_collection.json`（21 请求 / 113 断言行）
- 另有**非模块产出**（当日为对外对接整理）：`doc/项目交接简报.md`、`doc/计划书对齐说明.md`

**验证**

- 后端 `mvn install` **BUILD SUCCESS**。
- **接口级联调（Apifox）**：**21 请求 / 83 断言 / 断言失败 0**。唯一那条"失败"是 Apifox 自动加的「HTTP 状态码 = 200」默认校验，撞上了我们**刻意返回 401** 的鉴权用例（自己写的两条断言全过）——**工具假阳性**，非代码问题。
- **三条黄金标准全部通过**：① `mock_exam.score` == `report.score`；② `report.score` == Σ(答对题的 score)；③ **人工构造"10 题答对 7 题" → score 恰好 = 7**（证明"读库重算"真的算对，而非两处一起错）。
- **断点续考**验证：答 10 题后 `GET /{examId}`，`answeredMap` 恢复 10 条（含 Q1、Q8）。
- **种子数据自洽**：题库 36 题 / 试卷关联 36 / `Σscore = 36` == `exam_paper.total_score`。
- 幂等交卷、无 body → 1001、无 token → 401、历史列表含本次（score=7）均通过。

## 七、遗留问题与明日计划

- 遗留（按优先级）：
  1. **预约模块（科二 / 科三）待做**——`appointment` / `coach_student` 表已就绪，但**实体 / Mapper / 接口均未建**；设计文档 `api-design.md` §4.8 / §4.10 已定接口。
  2. **考试前端页面未做**——试卷列表 / 考试进行 / 成绩报告 / 考试历史四页，及 `api/exam.ts`、路由与菜单；何时做待排期。
  3. **`mockScore` / `mockExam*` 未回填进学情分析**——`AnalysisServiceImpl` 里这三个字段（趋势的 `mockScore`、总览的 `mockExamCount` / `mockExamAvgScore` / `mockExamPassRate`）**仍恒为 null**，Day15 时承诺的"考试接入学情分析"**本日未做**，需单独排期。
  4. **Day18 教练端待做**——学员管理 / 学情查看 / 错题查看 / AI 建议 / 预约确认。
  5. **Day19 管理端待做**——含**被推迟的题库管理**（CRUD + Excel 导入），以及用户管理、数据概览。
  6. **`all-api.postman_collection.json` 合并测试集合延后**——等预约 / 教练端接口也加入后再一次性合并，避免重复劳动。
  7. **第 4 周待做**：测试 / 部署 / 课程设计报告。
- 明日计划（Day 18）：**教练端** —— 学员管理（绑定 / 创建 / 解绑）、学员状态详情、模拟考与错题数据查看、AI 考试建议、教练确认；先出设计梳理方案，确认后再写代码。
