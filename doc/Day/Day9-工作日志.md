# 【Day 9 工作日志】

## 一、日期

2026-10-03

## 二、今日目标

完成**刷题模块后端**：从需求梳理到接口可调，把「顺序 / 章节 / 随机」三种练习模式与「提交判分」这条主链路打通。

## 三、完成的工作

**数据库设计：** 无新增表（复用 Day 1 的 15 张表）。

**后端：**

- **需求梳理（先梳理、后编码）**：产出完整链路图与接口清单，落盘 `doc/practice-flow.md` + `photo/design/practice-flow.png`。核心结论：
  - **两段式防作弊**：拉题返回的 VO 不含 `answer` / `analysis` / `options[].isCorrect`，正确答案只在提交判分后由 `AnswerResultVO` 下发；
  - **判分是唯一写入口**：刷题类接口纯查询，`answer_record` 与 `wrong_book` 的写只发生在 `POST /answer`；
  - **三种模式共用后半段**：差异只在"取题目"这一步。
- **3.1 展示层 VO**：`QuestionOptionVO`（**刻意不含 isCorrect**）、`QuestionVO`、`QuestionDetailVO`（多携带知识点名称）、`KPTreeNodeVO`（含 `questionCount`）。
- **3.2 交互层模型**：`AnswerSubmitDTO`（`questionId` / `userAnswer` / `duration`）、`AnswerResultVO`（`questionId` / `isCorrect` / `userAnswer` / `correctAnswer` / `analysis`）。
- **3.3 持久层**：`KnowledgePointMapper`、`QuestionMapper`、`QuestionOptionMapper`、`AnswerRecordMapper`、`WrongBookMapper`（均 `@Mapper` + `extends BaseMapper<T>`）。
- **3.4 `KnowledgePointService`**：`getKnowledgeTree(subjectCode)` —— **全库仅两次数据库交互**（一次查全部知识点 + 一次 `GROUP BY` 统计题目数），在内存建树并**自底向上累加**出"含子孙"的 `questionCount`。
- **3.5 `QuestionService`**：四个方法
  - `getSequenceQuestions`（按 id 升序分页）；
  - `getChapterQuestions`（**递归收集子孙知识点**后按 `knowledge_point_id IN (...)` 查询）；
  - `getRandomQuestions`（**先取全部 id → 内存洗牌 → 一次 IN 查询**，不用 `ORDER BY RAND()`）；
  - `getQuestionDetail`。
  - **`loadOptions` 是防 N+1 的关键**：收集本批全部 `questionId` → 一次 `IN` 查询取回选项 → 内存按 `question_id` 分组装配，无论一页多少题都只多 1 次查询。
- **3.6 判分核心 `AnswerGrader`**：纯逻辑组件（`@Component`、无状态），`isCorrect(type, userAnswer, correctAnswer)` + 归一化。**按"核心逻辑先写测试"的要求，先写 JUnit 5 + AssertJ 用例再写实现**（测试细节见 Day 10）。
- **3.7 `PracticeService`**：答题编排 —— 查题 → 判分 → 写 `answer_record`（`source` 由服务端决定，不采信前端）→ 答错 `upsert` 错题本；整体 `@Transactional(rollbackFor = Exception.class)`。
- **3.8 `StudentStudyController`**：6 个接口（知识点树 / 顺序 / 章节 / 随机 / 题目详情 / 提交作答），`/api/student/study` 前缀。

**前端：** 无。

**文档/设计：**

- `doc/practice-flow.md`（链路图 + 要点）、`photo/design/practice-flow.png`
- `doc/test-cases-practice.md`（接口测试用例 TC-01 ~ TC-20）
- `sql/seed-question.sql`（18 题种子数据，含**故意写成全角 `'Ａ'`** 与 **`'A,B'`** 的两道题，用于验证归一化）
- `doc/testjson/practice-api.postman_collection.json`（可导入 Apifox 的批量测试集合）

## 四、关键技术决策

| 决策点 | 方案 |
|---|---|
| 防作弊 | 用 **VO 隔离**：拉题不含答案与解析，绝不直接序列化实体 |
| 判分组件形态 | `@Component` + 实例方法（Spring 可注入；单测直接 `new`，不需容器） |
| 章节取题 | **递归收集子孙知识点**（配 `visited` 防环），父节点可练整个子树 |
| 随机抽题 | **先取 id 再 IN 查询**，不用 `ORDER BY RAND()`（后者对全表随机排序，开销高） |
| 防 N+1 | 选项一律**一次 IN 查询 + 内存分组**，绝不循环查库 |
| 事务边界 | 写 `answer_record` 与 upsert `wrong_book` **必须同事务** |
| `source` 来源 | **服务端决定**（接口决定场景），不采信前端传入 |

## 五、遇到的问题及解决方案

**问题 1（前置缺失）**

- 现象：按计划写 5 个 Mapper（`extends BaseMapper<Question>` 等）时**编译不过**——对应的实体类一个都没有。
- 原因：梳理 3.1~3.8 时**漏掉了"实体类"这一步**，计划里只列了 Mapper。
- 解决：按已确认的约定（`@TableLogic` 只给 5 张主数据表、`create_time` 全表填、`update_time` 仅有的表填）**补建 5 个实体**：`KnowledgePoint` / `Question` / `QuestionOption` / `AnswerRecord` / `WrongBook`，再写 Mapper。
  - 沉淀经验：**每个步骤开工前先确认"前置依赖"是否齐全**。

**问题 2（前置缺失）**

- 现象：`getSequenceQuestions` / `getChapterQuestions` 用 `selectPage` 分页，但 `total` 恒为 0、`limit` 不生效。
- 原因：**MyBatis-Plus 的分页拦截器（`PaginationInnerInterceptor`）从未注册**——不注册则 `selectPage` 退化为普通查询。
- 解决：补 `config/MybatisPlusConfig` 注册分页拦截器（`DbType.MYSQL`）。这是**分页能工作**的硬前提。

**问题 3**

- 现象：题目列表查完后，若逐题再查选项，N 道题就是 N 次数据库往返。
- 原因：经典的 **N+1 问题**——最直观的写法（循环里逐题查）恰恰是性能最差的。
- 解决：改为"收集本批 `questionId` → **一次 `IN` 查询** → 内存按 `question_id` 分组"，并在 `loadOptions` 上写了详细的**"为什么不用循环查库"**注释（答辩必问点）。

**问题 4**

- 现象：点击「章节练习」的**父节点**时，只能取到直接挂在父节点下的题；而题库实际把题目挂在**叶子节点**上，导致父节点点进去是空的。
- 原因：知识点是树形结构，题目只挂在叶子，父节点自身没有题目。
- 解决：`collectSelfAndDescendantIds` —— **BFS 收集"自身 + 全部子孙"知识点的 id**，再 `IN` 查询；配 `visited` 集合防止脏数据成环导致死循环。

## 六、产出物清单

- pojo：5 个实体 + 5 个 VO/DTO
- server：5 个 Mapper、2 个 Service（+Impl）、`AnswerGrader`、`PracticeService`（+Impl）、`StudentStudyController`、`MybatisPlusConfig`
- doc：`practice-flow.md`、`test-cases-practice.md`
- sql：`seed-question.sql`
- 测试集：`doc/testjson/practice-api.postman_collection.json`

## 七、遗留问题与明日计划

- 遗留：调度方（前端）未开工；种子数据只有 18 题，不足以验证分页预加载；错题本的**查询侧**（列表 / 重练 / 标记掌握）尚未实现。
- 明日计划（Day 10）：打磨判分核心的单元测试（边界与归一化）；实现错题本模块（分类推导、统计、重练抽题、标记已掌握）。
