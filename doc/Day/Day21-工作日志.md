# 【Day 21 工作日志】

## 一、日期

2026-10-15

**时间线说明（如实记录）**：本文件此前**不存在**——`doc/Day/` 目录只有 `Day1`～`Day20`，且 `Day20-工作日志.md` 末节为「第十六节 · Day 20 · 交卷后展示总成绩」，全部为 Day 20 内容。Day 21 的前序阶段（**模拟考试交卷结算并发安全修复**、**独立测试环境隔离方案设计**）此前均以**对话内只读审查 / 报告**形式进行，**未落盘为日志文件**。故本日志**自「第二阶段 A」起建立**，不覆盖、不伪造任何更早记录。

## 二、Day 21 主题（背景）

Day 21 围绕两条主线：

1. **交卷结算的并发安全**——以条件认领 `claimForSettle`（`UPDATE … WHERE status='ONGOING'`）+ 跨 Bean 事务 `ExamFinalizer` 保证「同一场考试只结算一次、绝不重复写库」；并统一**非法题型**处理（新增 `EXAM_PAPER_INVALID(2106)`，开考前校验 + 结算时严格失败）。
2. **独立测试环境隔离**——建立独立测试库 `driving_exam_test` 与最小权限账号 `drv_test`，并落盘 **test profile 配置** 与 **只读连接守卫**，使后续集成测试可安全地对该独立库做真实读写。

本日志记录的即上述第 2 条中的「**第二阶段 A**」。

## 三、第二阶段 A：独立测试配置与只读连接守卫（实现）

### 3.1 目标

建立**仅 test 作用域**的独立数据源配置与「只读连接守卫」，确保集成测试连接**独立测试库** `driving_exam_test`（账号 `drv_test`，非 root），**杜绝误连正式库** `driving_exam`；且不把仅供测试的安全逻辑泄漏进生产运行路径。

### 3.2 产出文件（新增 2 个，均在 `src/test` 下）

| 文件 | 说明 | 是否入库产物 | 含明文秘密 |
|---|---|---|---|
| `driving-exam-server/src/test/resources/application-test.yml` | test profile 数据源配置 | 否（不打进应用包） | 否 |
| `driving-exam-server/src/test/java/com/driving/exam/server/integration/DbConnectionGuardTest.java` | 只读连接守卫测试 | 否（测试源码） | 否 |

生产代码、主配置、`pom.xml` 均**未改动**。

### 3.3 安全机制（实现要点）

- **无默认值环境变量（fail-fast）**：`application-test.yml` 用 `${TEST_DB_URL}` / `${TEST_DB_USER}` / `${TEST_DB_PASSWORD}` 三个**无默认值**占位符；profile 配置优先级**高于**主 `application.yml`，会覆盖其 `spring.datasource.*`（主配置为 `root@driving_exam`）。占位符缺失时解析失败 → **上下文启动即失败**，不存在回退正式库的路径。
- **`@ActiveProfiles("test")`**：守卫类仅在此 profile 下加载 `application-test.yml`。
- **双层校验**：
  1. 配置层——解析 `spring.datasource.url` 的**目标库名**（去 `?` 查询串后取最后一个 `/` 之后片段），必须**严格等于** `driving_exam_test`；
  2. 实际连接层——用真实 `DataSource` 取连接，执行 `SELECT DATABASE()`（须 `= driving_exam_test`）与 `SELECT CURRENT_USER()`（须 `= drv_test@localhost`，显式排除 root）。
- **只读**：测试内仅执行 `SELECT`，无 INSERT/UPDATE/DELETE/DDL/清理；断言失败信息不含口令。

### 3.4 严格库名校验（本轮审核中的最小修改）

首次实现用 `assertThat(url).contains("driving_exam_test")` 校验，属**子串匹配**，会误放行 `driving_exam_test_evil` 之类名字。审核后改为：

```java
assertThat(extractDatabaseName(url))
        .as("JDBC URL 的目标库名必须严格等于 driving_exam_test（非子串匹配）")
        .isEqualTo(EXPECTED_DATABASE);
```

并新增私有静态方法 `extractDatabaseName(String)`（去 `?` 查询串 → 取最后一个 `/` 之后片段；无库名返回空串 → 守卫失败）。此为**收紧**校验，未放宽任何检查；实际连接的 `SELECT DATABASE()` / `SELECT CURRENT_USER()` 两条断言**保留不变**。

## 四、本机验收结果（**已在用户本机实测通过**）

**执行命令**（用户在本机仓库根执行）：

```
mvn -pl driving-exam-server -am test "-Dtest=DbConnectionGuardTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

**实测结果**：

| 项目 | 结果 |
|---|---|
| 构建 | **BUILD SUCCESS** |
| 测试类 | `DbConnectionGuardTest` 实际执行 |
| Tests run | **1** |
| Failures | **0** |
| Errors | **0** |
| Skipped | **0** |
| 日志证据 | Spring Boot 启用 **test profile**；**Hikari 成功建立连接** |
| 执行时间 | **回执未提供**（未记录，不臆造） |

> 该结果为**本机 Maven 实跑**，是本 Day21 唯一一项**真实运行级**验证（区别于此前的静态审查）。

## 五、已验证 / 未验证（明确区分，勿混）

### 5.1 已验证（本机 `mvn test` 实跑）

- Spring 上下文在 `test` profile 下**成功加载**（未因环境变量缺失而启动失败——说明本机已提供三个环境变量）。
- **Hikari 成功建立连接**（日志确认）。
- 守卫断言**通过**（1 test / 0 failures / 0 errors）——该断言**包含**「配置层库名严格相等」+「`SELECT DATABASE()=='driving_exam_test'`」+「`SELECT CURRENT_USER()=='drv_test@localhost'`」，因此这三项在本次运行中**均实际成立**。

### 5.2 未验证

- **缺少环境变量时的 fail-fast 行为——未实测**。回执未包含「删除某个 `TEST_DB_*` 后重跑」的场景。其行为（占位符解析失败 → 上下文启动失败 → 不回退主配置）目前是**基于 Spring 占位符机制的推断**，**尚未经运行验证**。
- **守卫不会自动保护所有集成测试**——见第六节；未做也无法用单一测试证明。
- **Git diff 未在真实仓库核验**——当前工作视图无 `.git`（见第七节）。
- **`extractDatabaseName` 边界**（如 `driving_exam_test_evil`、无库名 `…/`）为**静态推理 / 等价复算**，未写成专门测试用例实跑。

## 六、限制与未决事项

1. **缺环境变量行为未实测**：如上 §5.2，属推断，待补验证（可后续加一个「故意缺变量、断言上下文启动失败」的用例）。
2. **守卫覆盖范围有限**：`DbConnectionGuardTest` 是**独立的 `@Test` 类**，其断言**只在本类被运行时执行**，**不会自动**保护其他集成测试。仓库内**无共享测试基类**、无强制钩子；全测试树中目前**只有本类**带 `@ActiveProfiles("test")`。另两个 `@SpringBootTest` 类（`SystemPromptBuilderTest`、`DeepSeekClientTest`）**均未带** `@ActiveProfiles` 且**均被 `@Disabled`**——若启用，会落到主配置（`root@driving_exam`）且**不经**守卫校验。**因此后续任何写库集成测试，必须各自显式加 `@ActiveProfiles("test")`。** 是否引入「共享基类 / JUnit 扩展」把守卫强制化，属测试架构决策，**本阶段未擅自改动**。
3. **Git diff 待真实仓库核验**：当前挂载视图 `D:\myself\Integrate` 及其下 `driving-exam/` **均无 `.git` 目录**，`git status` / `git diff` 无法执行；范围核对改用**文件清单 + mtime**（见第七节）。如需 git 层 diff，须在你**本机真实仓库**（含 `.git`）执行。

## 七、本轮文件变更范围核对

**本轮（Day21 第二阶段 A 收尾）本轮新增 / 改动**：

| 文件 | 变更 |
|---|---|
| `doc/Day/Day21-工作日志.md` | **新建**（本文件；此前不存在） |

**本轮未触碰**（以 mtime 为证，均早于本轮）：

| 文件 | mtime | 结论 |
|---|---|---|
| `driving-exam-server/src/main/resources/application.yml` | 2026-10-05 11:08 | 主配置未动 |
| `driving-exam-server/pom.xml` | 2026-10-02 16:25 | 依赖未动 |
| `…/server/grader/AnswerGraderTest.java` | 2026-10-15 13:53 | 其他测试文件未动（早于本轮） |
| `…/server/exam/impl/ExamServiceImpl.java`、`ExamFinalizer.java` | 2026-10-15 13:5x | 业务代码未动 |

`src/test` 目录 6 个文件：`application-test.yml` 与 `DbConnectionGuardTest.java`（Day21 Stage A，17:09）、`AnswerGraderTest.java`、`SystemPromptBuilderTest.java`、`MasteryCalculatorTest.java`、`DeepSeekClientTest.java`（均更早）。**未修改业务代码、主配置、`pom`、数据库结构或其他测试文件。未执行任何数据库写操作。**

## 八、遗留与下一阶段（**未启动，等待审核**）

- **第二阶段 B（结算集成测试矩阵）未启动**：待本阶段审核通过后，在独立测试库 `driving_exam_test` 上编写结算相关集成测试（每个写库测试须自带 `@ActiveProfiles("test")`）。
- 待补验证：缺环境变量时的 fail-fast 行为（见 §6.1）。

---

## 九、Day 21 · 后续阶段 A：结算安全性验证与最小修复

> 补充说明（不改上文）：本阶段开始时仓库**已存在首次提交** `d06f96d`（`Initial project snapshot`，分支 `master`）。§七 当时记录的「无 `.git` 目录」已不再适用；本节以**当前提交为准**重新核实。

### 9.1 只读审查结论（以 `d06f96d` 源码为准）

- **结算调用链**：`ExamController` → `ExamServiceImpl` → `ExamFinalizer`（跨 Bean）。
  真正的**写事务边界只有一处**：`ExamFinalizer.finalize(...)`（`@Transactional(rollbackFor=Exception.class)`）。
  `ExamServiceImpl` 本身**不加事务**——交卷/超时收尾一律经注入的 `examFinalizer` 跨 Bean 调用，避免自调用导致代理失效。
  - 三个结算入口：`submit`（手动交卷）、`start`（开考前检测到旧考试超时→收尾）、`resume`（断点续考发现超时→惰性收尾）。
  - 只读入口：`report`（成绩报告）、`history`（历史）。**无定时任务**，故无第 4 个结算入口。
- **并发幂等**：`finalize` 内先做**条件抢占** `MockExamMapper.claimForSettle`（`UPDATE mock_exam SET status=? WHERE id=? AND status='ONGOING'`）。
  只有影响 1 行的请求继续结算；影响 0 行 → 返回 `null`，调用方在事务外重读后返回既有成绩（`submit`）/ 重读状态（`start`/`resume`）。
  抢占与后续全部写入**同处一个事务**：任一步异常整体回滚、状态回到 `ONGOING`。**结论：并发交卷的重复结算风险已在应用层以条件抢占消除，无需新增唯一键。**
- **异常题型**：`AnswerGrader.isGradeable`（SINGLE/MULTIPLE/JUDGE）为统一判据。
  - 开考：`start` 在建考前 `assertPaperGradeable`，含不可判分题型→拒绝（不建考试）。
  - 结算：`finalize` 遍历**整份试卷（含未作答）**，含不可判分题型→抛 `EXAM_PAPER_INVALID(2106)` 整体回滚，**不产生任何结算数据**。
  - 判题：`judge` 命中不可判分题型→`2106`（避免落到未处理的 500）。
  - 详情：`buildCorrectMap` 对不可判分题型**跳过不判**（答题卡表现为「未判」），但考试本身不可开考、结算会严格失败——**不生成「看似有效」的成绩**。此为**刻意的容错分层**，非不一致缺陷。
- **DB 约束（以 dump DDL 为准）**：`mock_exam_answer.uk_exam_question`、`student_subject_status.uk_student_subject`、`wrong_book.uk_user_question` 均**已存在**；`mock_exam`、`answer_record` 仅有非唯一键——**结算幂等不依赖这两张表的唯一键**（由 `claimForSettle` 保证）。**本阶段无任何 DDL/唯一键变更。**
- **Day 21 测试保护**：`src/test/resources/application-test.yml` 用**无默认值**占位符 `${TEST_DB_*}`（图省事的回退被杜绝）；`DbConnectionGuardTest`（`@ActiveProfiles("test")`）以真实连接断言 `SELECT DATABASE()=driving_exam_test` 且 `CURRENT_USER()=drv_test@localhost`。**结论：隔离与守卫已到位，本阶段不重写。**

### 9.2 本阶段改动（最小）

| 文件 | 变更 | 目的 |
|---|---|---|
| `driving-exam/driving-exam-server/src/test/java/com/driving/exam/server/exam/ExamFinalizerTest.java` | **新增** | 用 Mockito 桩住 Mapper，**单元级**验证结算核心契约：①不可判分题型在抢占前即拒绝（`2106`，且不发生任何抢占/写库）；②未抢到结算→返回 `null` 且零写入；③抢到结算→仅对**已作答**题目写 `answer_record`，未作答计入 `unansweredCount`，并更新考试/清草稿 |

**未改任何业务代码、`pom`、主配置、数据库结构、评分规则；未新增题型、未改断言口径。**

### 9.3 未执行 / 未验证事项（如实记录）

- **本环境无 `mvn`/`mvnw`/`javac`**（仅 Java 11），**未编译、未运行任何测试**。新增测试仅经**静态自检**（括号配平、构造器参数顺序比对、所引用 API 与源码核对），**其能否通过须以本机 `mvn` 运行为准**。
- **无数据库级并发集成验证**：`ExamFinalizerTest` 是**顺序单元测试**，只证明「应用层逻辑只让抢占成功者结算」，**不能**证明「两条真实并发事务在 MySQL 上只有一个 `claimForSettle` 影响 1 行」。本环境不具备可靠并发集成条件，**不将顺序单测冒充并发验证通过**。

### 9.4 仍存在的风险与限制（未解决）

1. **`start` 的并发重复创建**：两个并发 `start` 请求在「无进行中考试」时可能各建一场 `mock_exam`（`findOngoing` 后 `createExam` 之间存在竞态）。`mock_exam` 无「同一 user+paper 仅一条 ONGOING」的唯一键，**不改变数据库结构无法可靠解决**。属**结算之外**的并发点，本阶段只记录、不擅自扩范围。
2. **跨考试的科目进度丢失更新**：`applySubjectStatus` 为「先查后改」`mock_pass_count+1`。同一学员对**不同试卷**并发交卷时可能丢失一次计数。发生在同一 `(student_id, subject_code)` 行上，属次要并发轴，本阶段不处理（可由 `UPDATE ... mock_pass_count = mock_pass_count + 1` 之类原子改法后续改进，仍需单独评审）。
3. **`buildReport` 未再校验题型可判分**：依赖「`finalize` 已保证写入记录均为可判分题型」这一前提；若历史脏数据（旧 `answer_record`）存在，属数据层面问题，不属本阶段。

### 9.5 下一阶段建议（仅建议，未执行）

- 若需**数据库级**并发证明：在独立测试库 `driving_exam_test` 上，用两线程并发调用同一 `examId` 的结算路径，断言 `mock_exam` 最终仅 1 条终态、`answer_record` 无重复、`score` 唯一——**该测试必须自带 `@ActiveProfiles("test")`**，并在执行前后记录数据隔离与清理方式。
- 评估 `start` 并发重复创建与 `applySubjectStatus` 原子性是否纳入后续（可能涉及唯一键/原子 SQL，需单独安全评审）。

---

## 十、Day 21 · 本机单元测试实测结果（2026-10-16 补充）

> 本节是对 §9.3「未执行 / 未验证事项」的**后续补充**：§9.3 记录的是**编码环境的静态自检**（当时无法运行 Maven），原文**保持不动**；本节记录**用户在本机实际执行**的单元测试结果，作为该处"待以本机运行为准"的落地证据。

### 10.1 执行环境与命令

- 执行目录：`D:\myself\Integrate\driving-exam`（用户本机，Windows）。
- 执行命令：

```
mvn -pl driving-exam-server -am test -Dtest=ExamFinalizerTest,AnswerGraderTest,MasteryCalculatorTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

### 10.2 实测结果（本机，**单元测试**）

| 项目 | 结果 |
|---|---|
| 最终构建 | **BUILD SUCCESS** |
| Tests run | **54** |
| Failures | **0** |
| Errors | **0** |
| Skipped | **0** |
| 总耗时 | 约 **18.4 秒** |
| 测试结束时间 | **2026-10-16 15:40:59（+08:00）** |

**分类明细**：

| 测试类 | 通过数 | 类型 |
|---|---|---|
| `ExamFinalizerTest` | 3 | 单元（Mockito，无 Spring / 无 DB） |
| `AnswerGraderTest` | 41 | 单元（纯逻辑） |
| `MasteryCalculatorTest` | 10 | 单元（纯逻辑） |

### 10.3 测试范围与结论声明（**务必据此理解，勿夸大**）

- 本次为**纯单元测试**：均**不启动 Spring、不连接数据库、不产生任何数据读写**；`ExamFinalizerTest` 用 Mockito 桩住 Mapper，仅验证**应用层结算契约**（抢到 / 未抢到 / 非法题型三分支）。
- **本次结果不代表**：数据库集成测试通过、事务行为（提交/回滚）已验证、并发安全已验证。`claimForSettle` 的真实 SQL、行锁与两并发事务行为**均未被执行**（见 §9.4）。
- **本次未运行** `DbConnectionGuardTest` 或任何**需要数据库**的测试；§9.4 记录的并发/集成风险**仍然成立**。

---

## 十一、Day 21 · 第二阶段：模拟考试并发结算集成测试（实现）

> 本节只记录**本阶段实现**；§9、§10 原文保留不动。

### 11.1 覆盖的业务路径与目标

- 目标：**同一学员、同一场 `ONGOING` 考试**，两个线程**并发**调用**真实的** `ExamServiceImpl.submit(...)`，
  断言最终数据库状态满足「**只结算一次**」的不变量。
- 新增文件：`driving-exam/driving-exam-server/src/test/java/com/driving/exam/server/integration/ExamSettlementConcurrencyTest.java`（`@SpringBootTest` + `@ActiveProfiles("test")`，**真实 MySQL 测试库**，**不 Mock `claimForSettle`**）。
- **未修改**任何生产代码 / DDL / 主配置 / 已有测试配置。

### 11.2 关键并发机制（与答辩相关）

- 结算唯一性由 `MockExamMapper.claimForSettle` 的单条**条件更新**
  `UPDATE mock_exam SET status=? WHERE id=? AND status='ONGOING'` 决定：只有影响 **1 行**的请求继续结算，
  其余（影响 **0 行**）在 `finalize` 返回 `null`，由 `submit` 在**事务外重读**后返回既有报告。
- 这正是“以单条原子 SQL 充当结算抢占”的设计——**不新增唯一键、不改表结构**即可获得幂等。
- 因此**仍需数据库级并发测试**：单元测试（Mockito 桩住 `claimForSettle`）只能证明**应用层分支**，
  **不能**证明“真实 MySQL 上两并发事务只有一个抢占成功”。

### 11.3 数据准备与清理策略

- 专用可识别标记 `zz_it_settle_<nanoTime>`：专用学员 `sys_user`、知识点 `knowledge_point`、试卷 `exam_paper`、2 道单选 `question`、`exam_paper_question`、1 场 `mock_exam`(ONGOING)、2 条 `mock_exam_answer` 草稿。
- 数据访问：`DataSource` + JDBC（**物理** insert/delete，规避 `@TableLogic` 的逻辑删语义）；每步插入用 `RETURN_GENERATED_KEYS` **捕获自增 id**。
- 清理：`finally` 中**仅按已捕获的专用 id**、按依赖**反向**顺序 `DELETE`（answer_record → mock_exam_answer → wrong_book → student_subject_status → mock_exam → exam_paper_question → question_option → question → exam_paper → knowledge_point → sys_user）。**不用** `TRUNCATE`、**不用**无 `WHERE` 的 `DELETE`、**不**按名称模糊删。清理失败作为 suppressed 保留、不吞原始异常。
- 断言（期望值，依据源码）：`mock_exam` 唯一行 `status='SUBMITTED'`、`score=2`、`submit_time` 非空；`answer_record(source='EXAM')` **恰好 2 条**且无重复题；`mock_exam_answer` 草稿 **0**；`wrong_book` **0**（全答对）；`student_subject_status` **1 行**、`mock_pass_count=1`、`last_mock_score=2`。

### 11.4 运行与结果（**如实区分**）

- 编码环境**无 `mvn`**，**本阶段未运行任何测试**；新增测试仅经**静态自检**（括号配平、`submit` 签名与 DTO setter、必填字段核对）。
- 待用户在本机执行（**结果待回填**，未通过前不得写成通过）：
  1. 先跑守卫：`mvn -pl driving-exam-server -am test -Dtest=DbConnectionGuardTest "-Dsurefire.failIfNoSpecifiedTests=false"`
  2. 再跑并发测试：`mvn -pl driving-exam-server -am test -Dtest=ExamSettlementConcurrencyTest "-Dsurefire.failIfNoSpecifiedTests=false"`
  3. 回归单元：`mvn -pl driving-exam-server -am test -Dtest=ExamFinalizerTest,AnswerGraderTest,MasteryCalculatorTest "-Dsurefire.failIfNoSpecifiedTests=false"`

### 11.5 概率性与未覆盖边界（务必如实）

- **并发测试是概率性的**：起跑同步（`CyclicBarrier`）只对齐**调用入口**，**无法保证**两线程都恰好通过 `submit` 前置检查后再竞争抢占。本用例断言的是“**本次运行下**库状态满足不变量”，**不等于**“任意调度都不双结算”。
- 本测试**不覆盖**：`start` 的**重复进行中考试**竞态；`applySubjectStatus` 的**跨考试**并发丢失更新——**仍未验证、未解决**。
- 本测试**不**验证错答路径（本用例全答对；`wrong_book` 仅断言为 0）。
- JVM 被强制终止时 `finally` 可能不执行 → 专用测试数据可能残留在 `driving_exam_test`（限测试库内）。

---

## 十二、Day 21 · 本地验证结果与阶段收尾

> 本节为**追加**记录（不改动 §九/§十/§十一）。

### 12.1 验证结果（来自本地 surefire 报告文件）

| 测试 | 结果 | 报告文件 mtime |
|---|---|---|
| `DbConnectionGuardTest` | Tests run **1**，Failures 0，Errors 0，Skipped 0 | 2026-10-16 16:22 |
| `ExamSettlementConcurrencyTest` | Tests run **1**，Failures 0，Errors 0，Skipped 0，耗时 **4.601 s** | 2026-10-16 16:23 |
| `ExamFinalizerTest` | Tests run **3**，0/0/0 | 2026-10-16 16:25 |
| `AnswerGraderTest` | Tests run **41**，0/0/0 | 2026-10-16 16:25 |
| `MasteryCalculatorTest` | Tests run **10**，0/0/0 | 2026-10-16 16:25 |
| **单元回归合计** | **54** 项，Failures 0，Errors 0，Skipped 0（由上面三类相加） | — |

### 12.2 证据来源与口径声明（务必如实理解）

- 以上统计来自本地构建产物 **`driving-exam/driving-exam-server/target/surefire-reports/`** 下的**现有报告文件**（`*.txt` / `*.xml`）；该目录被 `.gitignore`（`target/`）忽略，不入库。
- **Claude 本轮未重新运行测试或构建**；未连数据库、未执行 SQL。上表反映的是**最近一次**对应测试的运行结果。

### 12.3 尚不能声称的内容（边界，勿夸大）

- **不能声称已确定观察到目标并发交错**：本次并发集成测试**通过**，但**未记录两个线程是否都进入了竞争 `claimForSettle` 的关键窗口**。故只能声明“**本次运行下**库状态满足‘只结算一次’的不变量”，**不等于**“任意调度下都不会双重结算”。
- **测试通过不代表所有并发场景安全**。
- **仍未验证、未解决**：`start` 的**重复进行中考试**竞态；`applySubjectStatus` 的**跨考试**并发更新风险；**并发下的错答路径**（本用例全答对，`wrong_book` 仅断言为 0）。
- **残留风险**：JVM 被**强制终止**时，测试的 `finally` 清理可能**不执行** → 专用测试数据可能残留在 `driving_exam_test`（限测试库内，可用 `zz_it_settle_*` 前缀识别）。

### 12.4 阶段收尾

- 本阶段（并发结算集成测试）实现与本地验证**已完成**（结果见 §12.1），并保留了上述**未覆盖边界**（§12.3）。
- 未修改生产代码 / DDL / 配置 / 既有测试代码；未执行/修改 `doc/testsql/coach-student-test-setup.sql`；未处理异常文件 `master)`；未做任何 Git 暂存或提交。
