# 数据约定（Data Conventions）

> 项目：基于大语言模型的驾考智能辅导系统
> 用途：固化跨模块共享的数据规则，避免同一事实出现两份真相
> 维护：新增字段/枚举时同步更新本文件

---

## 1. 判分真相唯一性

**唯一真相（Single Source of Truth）：`question.answer`**

- 判分代码**永远只读 `question.answer`**，不读任何其它字段来取标准答案。
- 三种题型的答案都存这一个字段：单选 `'A'`、多选 `'AB'`、判断 `'对'`/`'错'`。

**派生字段：`question_option.is_correct`**

- 它是**从 `answer` 推导出来的**，由**后端在保存题目时自动生成**，**不接受管理员手动填写**。
- 推导规则：

  ```
  归一化(option_key) 属于 归一化(answer) 的字母集合  →  is_correct = 1
  否则                                              →  is_correct = 0
  ```

- 该字段的作用是**题库编辑与展示**（管理员能看到"哪个选项是对的"），**不参与判分**。
- 判断题没有 `question_option` 行，本规则对它无意义。

> ⚠️ **推导必须复用判分同一套归一化。**
> 例：题目 6 的标准答案是全角 `'Ａ'`，若用朴素的字符串包含判断，`'A'` 会被判为不在集合里 → `is_correct` 算错。
> 因此推导时也要先做 NFKC + 大写（见 §3），与 `AnswerGrader` 保持一致。

---

## 2. 判断题（JUDGE）提交契约

- **`userAnswer` 传语义文本**：`'对'` 或 `'错'`，**不是**选项标识 `'A'`/`'B'`。
- **判断题不存 `question_option`**。前端按 `questionType === 'JUDGE'` 渲染两个固定按钮，提交时直接传 `'对'`/`'错'`。
- 后端判分做**同义映射**：

  | 归一化为 | 接受的写法 |
  |---|---|
  | `对` | 对 / 正确 / 是 / T / TRUE / Y / YES / √ / ✓ |
  | `错` | 错 / 错误 / 否 / F / FALSE / N / NO / × / ✗ / X |

  > 注意：**不支持 `1` / `0`**（避免与选项标识、数字输入混淆）。

- 判断题的答案存在 `question.answer`（`'对'`/`'错'`），与单选/多选的存储位置一致。

---

## 3. `user_answer` 存储约定

- **存归一化后的值**，便于后续按字母统计与学情分析。
- **若归一化结果为空**（有内容但全是非字母字符，如 `'???'`），**回退存 `trim()` 后的原始输入**——保留"确实作答过"的事实。
- 归一化步骤（顺序固定）：

  ```
  1) NFKC 归一化   全角/兼容字符折叠为半角（Ａ→A、，→,）
  2) trim          去首尾空白
  3) 转大写
  4) 按题型处理：
       SINGLE   → 只保留 A-Z 字母
       MULTIPLE → 只保留 A-Z 字母 → 去重 → 排序   （故 "BA"/"A,B"/"AAB" 均归一为 "AB"）
       JUDGE    → 同义映射为 "对" / "错"（见 §2）
  ```

- **为什么用 NFKC**（答辩会问）：NFKC 会把全角字母、全角标点等"表现变体"折叠成标准半角形态，
  让判分不受全角/半角输入差异影响；对普通 ASCII 与中文汉字无副作用，成本仅一次字符扫描。

---

## 4. `subject_code` 枚举

| 取值 | 含义 |
|---|---|
| `SUBJECT_1` | 科目一 |
| `SUBJECT_2` | 科目二 |
| `SUBJECT_3` | 科目三 |
| `SUBJECT_4` | 科目四 |
| `COMMON` | 通用（如 AI 问答不限定科目时） |

- 刷题相关（知识点、题目、试卷、答题记录、错题本、模拟考试）：科目一存 `SUBJECT_1`，科目四存 `SUBJECT_4`。
- 预约相关（appointment）：科目二存 `SUBJECT_2`，科目三存 `SUBJECT_3`。
- **科目一/科目四共用刷题逻辑，科目二/科目三共用预约逻辑**，均由该字段区分。

---

## 5. 逻辑删除约定

采用「**主数据逻辑删除、流水表不删**」。

| 类别 | 表 | 带 `deleted` |
|---|---|---|
| 主数据 | `sys_user`、`knowledge_point`、`question`、`exam_paper`、`appointment` | ✅ 有 |
| 流水/日志/关系 | `answer_record`、`wrong_book`、`mock_exam`、`question_option`、`exam_paper_question`、`ai_chat_record`、`chat_session`、`chat_message`、`coach_student`、`student_subject_status` | ❌ 无 |

- `deleted`：`0` 未删、`1` 已删，由 MyBatis-Plus `@TableLogic` 显式声明（仅上述 5 张主数据表）。
- 流水表**没有 `deleted` 列**，因此它们**不存在"逻辑删除"这回事**。

### 5.1 删除边界：哪些数据能删、哪些不能

> ⚠️ "流水表不删"**不等于**"永远不能删除"。按**数据性质**分四类：

| 表 | 性质 | 能否删除 | 理由 |
|---|---|---|---|
| `answer_record` | **统计依据** | ❌ **不删** | 学情分析、正确率、掌握度全部依赖完整答题历史，删一条就失真 |
| `wrong_book` | **错题归集依据** | ❌ 不删，**用 `mastered` 标记** | 错题本的价值在"错过多少次"；清了就看不出反复错的题 |
| `ai_chat_record` | **用户数据** | ✅ **允许物理删除** | 用户自己的对话内容，**用户有权删除**（隐私）；且它不参与任何统计口径，删了不影响数据完整性 |
| `chat_message` | 沟通记录 | ❌ 第一版不删 | 无删除接口，保留完整沟通记录 |

**判断标准**：这条数据**是不是某种统计的事实来源**？

- **是** → 不能删，用状态位表达变化（`wrong_book` 用 `mastered`）；
- **不是，且归属于用户本人** → 可以物理删除（`ai_chat_record`）。

---

## 6. 状态枚举

**`appointment.status`**（预约状态）

| 取值 | 含义 |
|---|---|
| `PENDING` | 待确认 |
| `CONFIRMED` | 已确认（教练审批通过） |
| `CANCELLED` | 已取消（**学员**主动取消） |
| `REJECTED` | 已拒绝（**教练**拒绝，原因记在 `coach_remark`） |
| `COMPLETED` | 已完成（教练标记完成） |

> **流转**：`PENDING` --学员取消--> `CANCELLED`；`PENDING` --教练拒绝--> `REJECTED`；
> `PENDING` --教练确认--> `CONFIRMED`；`CONFIRMED` --教练完成--> `COMPLETED`。
> **终态**：`CANCELLED` / `REJECTED` / `COMPLETED`。
>
> **取消 / 拒绝规则**：仅 `PENDING` 可取消、可拒绝；**`CONFIRMED` 之后双方都不能取消**
> （需联系教练处理）。拒绝与取消是**两个不同状态**，不合并——前端据此区分展示。
>
> **时段占用**：该教练该日该时段"是否被占用"只看 `status ∈ {PENDING, CONFIRMED}`；
> **终态会释放时段**（取消 / 拒绝 / 完成后，同一时段可被重新预约）。

**`mock_exam.status`**（模拟考试状态）

| 取值 | 含义 |
|---|---|
| `ONGOING` | 进行中 |
| `SUBMITTED` | 已交卷 |
| `TIMEOUT` | 超时自动交卷 |

**`student_subject_status.status`**（学员科目进度）

| 取值 | 含义 |
|---|---|
| `NOT_STARTED` | 未开始 |
| `LEARNING` | 学习中 |
| `READY_FOR_EXAM` | 已具备考试条件 |
| `EXAM_PASSED` | 考试通过 |
| `EXAM_FAILED` | 考试未通过 |

---

## 7. 其它共享枚举（补充）

| 字段 | 取值 |
|---|---|
| `sys_user.role` | `STUDENT` / `COACH` / `ADMIN` |
| `question.question_type` | `SINGLE`（单选）/ `MULTIPLE`（多选）/ `JUDGE`（判断） |
| `answer_record.source` | `PRACTICE`（普通练习）/ `RETRY`（错题重练）/ `EXAM`（考试）；**由调用的接口决定，请求体不传** |
| `wrong_book` 分类 `WrongBookCategory`（**派生值，不落库**） | `PENDING`（待攻克）/ `HIGH_FREQUENCY`（高频错）/ `MASTERED`（已攻克）；推导规则见 §8，**前端不自行计算** |
| `exam_paper.paper_type` | `MOCK`（模拟卷）/ `CHAPTER`（章节卷） |
| `student_subject_status.exam_ready_suggest` | `READY` / `NEED_MORE` / `NEED_HELP` |
| `student_subject_status.coach_confirm_status` | `CONFIRMED_READY` / `NOT_READY` / `NEED_HELP` |
| `student_subject_status.exam_result` | `PASS` / `FAIL` |
| 通用状态位（`sys_user.status`、`question.status`、`exam_paper.status`、`coach_student.status`） | `1` 有效 / `0` 无效 |
| 通用布尔位（`wrong_book.mastered`、`chat_message.is_read`、`question_option.is_correct`） | `1` 是 / `0` 否 |

---

## 8. 错题本分类（优先级互斥，由 `mastered` + `wrong_count` 推导）

错题本条目按**优先级从高到低**判定，**每条错题只属于一类**（不重不漏）：

| 优先级 | 分类（code） | 判定条件 | 含义 |
|---|---|---|---|
| 1 | `MASTERED` | `mastered = 1` | **已攻克**（含"曾经错很多次、后来攻克"的题），可回看 |
| 2 | `HIGH_FREQUENCY` | `mastered = 0` 且 `wrong_count >= 3` | **高频错**：反复错且**尚未攻克** —— 最该练的 |
| 3 | `PENDING` | `mastered = 0` 且 `wrong_count < 3` | **待攻克**：新错、尚待攻克 |

**实现约定：**

- **不新增数据库字段**：分类完全由 `wrong_book.mastered` 与 `wrong_book.wrong_count` 推导，
  避免出现"第二份真相"与不一致。
- **由后端推导**并通过 `WrongBookItemVO.category` 返回；前端只按分类渲染 tab，**不自行计算**。
- 阈值 `HIGH_FREQ_THRESHOLD = 3` 定义在 **Service 层的常量**（`WrongBookServiceImpl`），便于调整。
- 枚举 `WrongBookCategory` 放在 `pojo.enums`（VO 在 pojo 模块，pojo 不能反向依赖 server）。

**两个关键性质：**

- `wrong_count` 是**历史累计值，永不清零**——它是"高频错"的判定依据。
- `mastered` **不是终态**：已攻克的题**再答错**会因 `upsertWrong` 被重置为 `0`，
  即从「已攻克」**退回**「待攻克 / 高频错」。

**为什么"已攻克"优先级最高：** 一道题只要被攻克，哪怕它曾错过 10 次，也应归入「已攻克」；
否则「高频错」会永久堆积已掌握的题，失去"最该练"的指向意义。

---

## 9. 已知取舍（记录在案，暂不处理）

### 9.1 错题本列表 `total` 与 `records` 可能对不上

- **触发条件**：题库中有题目被**逻辑删除**，且该题恰好出现在该学员的错题本里。
- **表现**：`GET /wrong-book` 返回的 `total` 由 SQL 分页算出（**包含**已删题），
  而 `records` 在**内存装配阶段把已删题过滤掉了**（不展示已删题题干）→
  可能出现 `total = 10` 而 `records` 只有 9 条。
- **彻底修法**：把错题列表改为**自定义 SQL 做 JOIN**
  （`wrong_book wb LEFT JOIN question q ON wb.question_id = q.id AND q.deleted = 0`），
  将"过滤已删题"下推到 SQL，使 `total` 与 `records` 口径一致。
- **为什么不修**：课设阶段几乎不会触发（需"管理员删题" + "学员恰好错过该题"同时成立）；
  代价是要为一处分页单独写自定义 SQL，**收益低于维护成本**。故记录备查，评估后再决定。

### 9.2 错题重练抽题的返回值可能少于 `count`

同类原因：抽题时按 `wrong_book` 取候选，装配前会过滤掉**已停用（`status=0`）或已删除**的题目，
因此极端情况下返回条数会略少于请求的 `count`（最多不溢出，不会报错）。

---

## 10. 时间与时区

### 10.1 时间字段有两个来源

| 字段 | 谁填 | 用时区 |
|---|---|---|
| `create_time` / `update_time`（由实体自动填充） | **JVM**（`LocalDateTime.now()`） | **JVM 默认时区** |
| `answer_time`、`last_wrong_time`（数据库 `DEFAULT CURRENT_TIMESTAMP` / `NOW()`） | **MySQL** | **MySQL 服务器时区** |

**若 JVM 与 MySQL 时区不一致，同一张表里两类时间会相差若干小时**，且表面上不易发现。

### 10.2 本项目已核实（2026-10-03）

- 系统时区为 `China Standard Time (UTC+08:00)`；`user.timezone` 为空 → **JVM 跟随系统**，即东八区；
- MySQL `NOW()` 与本地时间一致 → **无偏差**；
- 结论：**当前无需任何额外配置**。

### 10.3 ⚠️ 依赖 JVM 时区的业务逻辑

代码中凡用 `LocalDate.now()` / `LocalDateTime.now()` 取"今天"的地方，**语义由 JVM 时区决定**。目前有一处：

- **AI 每日限额**：以 `create_time >= LocalDate.now().atStartOfDay()` 界定"当日"。
  若部署到海外服务器，"今天"会按服务器本地时区算，**与用户感知的"今天"不一致**。

**结论**：课设在本机（东八区）运行无影响；**若将来部署到海外，需显式指定时区**
（如启动参数 `-Duser.timezone=Asia/Shanghai`）。

