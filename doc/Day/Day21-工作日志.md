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
