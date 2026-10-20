# 【Day 27 工作日志】

## 一、日期

2026-10-19（项目 Day27 阶段；自然日按当前会话记录）

## 二、本阶段目标

1. 补齐 Day25、Day26、Day27 工作日志，建立每日同步记录。
2. 完成管理员端 9 个菜单的真实页面与后端接口代码。
3. 实现 Excel 题库导入（模板、上传、校验、事务）。
4. 创建 Docker 核心部署文件与 README。
5. 执行当前环境可执行的前端/静态检查；明确后端编译与真实联调未执行。

## 三、工作日志补齐

- 新建 `doc/Day/Day25-工作日志.md`：教练学员只读页、管理员端审计、Docker 前置核查。
- 新建 `doc/Day/Day26-工作日志.md`：教练学员写操作、管理员个人中心/预约查看、Excel/Docker 前置核查。
- 新建本文件 `doc/Day/Day27-工作日志.md`。
- `Day23` 仍无原始日志，保持在 Day24 §4.1 的“补记”，未伪造当天日志。
- 从 Day27 起，每天结束前同步更新当天日志。

## 四、管理员端九菜单实现

### 4.1 后端

新增：

- `server/admin/AdminDtos.java`：管理员 DTO/VO；
- `server/admin/AdminService.java`：概览、用户、绑定、知识点、题库、试卷、AI 统计业务；
- `server/admin/AdminQuestionExcelService.java`：Excel 模板与导入；
- `controller/AdminController.java`：`/api/admin/**` 管理接口；
- `controller/AdminAppointmentController.java`：管理员全量预约只读接口；
- `AppointmentService.listAllAppointments(...)` 与实现；
- `AiChatRecordMapper.countDistinctUsers()`；
- `pom.xml` 增加 `poi-ooxml 5.2.5`。

接口范围：

- `/api/admin/dashboard`；
- `/api/admin/users`（列表/创建/编辑/启停用）；
- `/api/admin/bindings`（列表/绑定/软解绑）；
- `/api/admin/knowledge-points`（树/增改删）；
- `/api/admin/questions`（列表/详情/增改/启停/逻辑删除/模板/导入）；
- `/api/admin/papers`（列表/详情/组卷创建编辑/启停）；
- `/api/admin/appointments`（只读）；
- `/api/admin/ai/stats`；
- 个人中心复用 `/api/auth/*`。

保守约束：

- 用户不物理删除；未提供删除/密码重置；
- 绑定软解绑，不取消预约、不删历史；
- 题目已有答题记录时禁止改标准答案或删除；
- 已有模拟考试记录的试卷禁止改题目组成；
- 知识点删除前检查子节点与题目引用。

### 4.2 前端

新增管理员页面：

- `DashboardView.vue`（真实聚合：用户/启用题目/预约/AI 记录）；
- `UsersView.vue`（列表/筛选/创建/编辑/启停用）；
- `BindingsView.vue`（列表/绑定/软解绑）；
- `KnowledgePointsView.vue`（树/增改删）；
- `QuestionsView.vue`（列表/编辑/启停/删除/Excel 导入/模板下载）；
- `PapersView.vue`（列表/组卷创建编辑/启停）；
- `AppointmentsView.vue`（只读、筛选、分页）；
- `AiStatsView.vue`（总量/今日/去重用户）；
- `ProfileView.vue`（资料/密码，复用 auth）。

`frontend/src/api/admin.ts` 增加对应 API；`router/index.ts` 注册 9 个管理员路由；原 `AdminComingSoon` 仅保留为未知路径兜底。

## 五、Excel 题库导入

- 模板列：科目编码、知识点 ID、知识点名称、题型、题干、选项 A~D、正确答案、解析、难度、媒体 URL、状态；
- 校验：科目、知识点存在/科目匹配、题型、单选/多选/判断答案规则、选项、难度/状态；
- 重复：同科目 + 完全相同题干拒绝；文件内重复也拒绝；
- 事务：全部行校验通过后才写入；有错误行则整批不写入并返回行号/字段/原因；
- 前端：模板下载、文件上传、导入结果和错误行表格；
- **尚未实际导入任何题库数据，未执行 Excel 测试。**

## 六、Docker 与 README

新增：

- `docker/backend/Dockerfile`（Maven 构建 + Java 17 运行 + curl 健康检查）；
- `docker/frontend/Dockerfile`（Node 构建 + Nginx 静态托管）；
- `docker/frontend/nginx.conf`（SPA fallback + `/api/` 反代）；
- `docker-compose.yml`（MySQL/backend/frontend，Qdrant 使用 `rag` profile）；
- `docker/.env.example`；
- `docker/mysql/init/90-demo-accounts.sql`（演示账号；文件顶部注明仅演示、需替换密码）；
- `.dockerignore`；
- `README.md`（本地开发、Docker、初始化、测试、限制）。

首版不把 Qdrant/RAG 作为启动前提；Docker 未实际运行，未创建容器或数据卷。

## 七、验证结果

| 检查 | 结果 |
|---|---|
| `./node_modules/.bin/vue-tsc --noEmit` | **exit=0，无输出** |
| `npm run build` | **exit=0**；仅有既有大 chunk 警告 |
| `git diff --check` | **exit=0** |
| `docker-compose.yml` YAML 解析 | **通过**（仅语法；未运行 Docker） |
| 后端编译（Java 17/Maven） | **未执行**：当前环境 Java 11 JRE，无 `javac`/Maven |
| 后端自动化测试 | **未执行** |
| 真实 API 联调 | **未执行** |
| 浏览器页面验证 | **未执行** |
| Docker 构建/启动/健康检查 | **未执行** |
| 数据库写入/初始化 | **未执行** |

## 八、未完成与风险

1. **后端新增代码未编译**：AdminController/AdminService/Excel/POI/`listAllAppointments` 均需本机 Java 17 + Maven 验证。
2. **管理员 9 页未真实联调**：代码、路由、前端构建完成，不等于接口可用。
3. **教练写操作未真实联调**：Day26 代码未在本日重新验证。
4. **Excel 未实测**：模板、重复题、事务回滚均未运行。
5. **Docker 未部署**：MySQL dump 为 schema-only 且含 `DROP TABLE`，只能空卷初始化；演示账号需替换密码。
6. **功能边界**：用户删除/密码重置、试卷删除未实现；题库修改受历史答题记录限制；知识点描述不在树 VO 中返回。
7. **文档债务**：第 1/2 章、`er-diagram.md`（仍写 15 表）、答辩素材尚未同步。
8. **历史测试非本轮结果**：未重跑 Maven/Apifox/浏览器回归。

## 九、下一步

1. 用户本机执行 `mvn -pl driving-exam-server -am -DskipTests package` 编译，修复错误后重启后端。
2. 用 `admin01` 逐项验证 9 个管理员页面；用隔离测试账号验证教练写操作。
3. 在隔离库测试 Excel 导入（模板、错误行、回滚）。
4. Docker 空卷执行 `docker compose up -d --build`，验证 MySQL/后端/Nginx/登录/数据持久化。
5. 完成回归后更新报告、ER/API 文档、答辩素材和 PPT。

## 十、工作区与约束

- 未执行 Git add/commit/reset/clean。
- 未修改数据库、SQL 或核心演示数据；Excel 导入和 Docker 均未实际执行。
- 未启动/停止本机服务；前端 `dist` 为构建产物（被忽略）。

## 十一、Day27 补充：验收准备与静态审查（日期待用户确认）

> 本节为本轮新增记录，不修改 §一~§十 的既有内容。项目阶段编号为 Day27；自然日尚未由用户单独确认。

### 11.1 本轮实际完成的审查

- 核对项目根目录 `D:\myself\Integrate` 没有 Maven 根 `pom.xml`；Maven 根为 `driving-exam/pom.xml`，聚合 `driving-exam-common`、`driving-exam-pojo`、`driving-exam-server`。
- 核对 Spring Boot 3.2.5、Java 17、MyBatis-Plus 3.5.7、Apache POI 5.2.5 的声明位置。
- 静态审查 `AdminController`、`AdminService`、`AdminDtos`、`AdminQuestionExcelService`、`AdminAppointmentController`、`AppointmentService.listAllAppointments`、教练学员写操作和 9 个管理员前端页面/API 路径。
- 静态审查 `docker-compose.yml`、后端/前端 Dockerfile、Nginx、`.env.example`、MySQL 初始化挂载顺序和 README。
- 新建 `doc/Day27-本机验收指南.md`。

### 11.2 静态审查发现（按风险分类）

**阻断构建（需本机验证，当前不能确认）**

- 当前执行环境只有 Java 11 JRE，无 `javac`、无 Maven；无法编译 Spring Boot 3.2.5/Java 17 后端。
- 新增 `AdminController`、`AdminService`、`AdminDtos`、`AdminQuestionExcelService` 以及 `poi-ooxml` 依赖尚未经过实际编译；不能写成“编译通过”。
- Docker 后端镜像需要联网下载 Maven 依赖；未实测。

**阻断运行（需本机验证）**

- 新增 `/api/admin/**` 接口必须重新编译并重启后端才会加载；本轮未重启。
- Docker MySQL 初始化依赖官方镜像对 `MYSQL_DATABASE` 的脚本选择行为；dump 本身没有 `CREATE DATABASE`/`USE`，未实测。
- Docker 首版未启动；Qdrant/RAG 不在首版核心路径，AI 问答不可宣称可用。

**数据风险**

- Excel 导入会写 `question`/`question_option`；必须使用隔离数据库，不得对核心演示题库直接导入。
- 管理员用户/绑定/知识点/题库/试卷写操作会影响现有业务数据；测试需隔离账号。
- 题目答案修改/删除在已有答题记录时被拒绝；已有模拟考试记录的试卷禁止改写题目组成；知识点删除受子节点/题目引用约束。
- Docker 初始化的 `seed-question*.sql` 含固定 ID 的 `DELETE`，只能在空数据卷执行；禁止对已有数据卷 `down -v`。
- `docker/mysql/init/90-demo-accounts.sql` 是演示账号，默认演示密码写在文件注释中；共享/生产环境必须替换。

**非阻断问题**

- 管理员用户删除、密码重置、试卷删除未实现；页面没有这些按钮。
- 知识点树 VO 不返回 `description`，编辑名称时后端保留原描述，但页面不能展示当前描述。
- 管理员绑定/试卷页面使用用户 ID、题目 ID 数字输入，属于最小实现，不是完整选择器。
- 第 1/2 章、`er-diagram.md`（仍写 15 表）、答辩素材尚未同步。
- 教练写操作、管理员 9 页、Excel、Docker 都没有真实浏览器/接口/容器证据。

### 11.3 本轮实际命令结果

| 检查 | 结果 |
|---|---|
| `vue-tsc --noEmit` | **exit=0，无输出** |
| `npm run build` | **exit=0**；仅既有大 chunk 警告 |
| `git diff --check` | **exit=0** |
| 后端 Java/Maven | **不可用**：Java 11 JRE，无 `javac`/Maven |
| 后端编译/测试 | **未执行** |
| API/浏览器/Docker/数据库 | **未执行** |

### 11.4 验收入口

- 本机验收指南：`doc/Day27-本机验收指南.md`。
- 第一步必须是 Java 17 + Maven 编译；在编译成功前，不应把管理员/Excel/Docker 标记为完成。

## 十二、Maven 测试配置错误定位（日期待用户确认）

> 本节为追加记录，不改写 §一~§十一。

### 12.1 用户实际执行结果

用户在本机 `D:\myself\Integrate\driving-exam` 执行：

```text
mvn -pl driving-exam-server -am test
```

结果：`Tests run: 205, Failures: 0, Errors: 2, Skipped: 6`，Maven `BUILD FAILURE`。前一轮 `mvn -pl driving-exam-server -am -DskipTests package` 为 `BUILD SUCCESS`。

### 12.2 实际读取的配置与测试

- `driving-exam-server/src/test/resources/application-test.yml`
- `driving-exam-server/src/test/java/.../integration/DbConnectionGuardTest.java`
- `driving-exam-server/src/test/java/.../integration/ExamSettlementConcurrencyTest.java`
- `driving-exam/pom.xml`、`driving-exam-server/pom.xml`
- `target/surefire-reports/...DbConnectionGuardTest.xml`、`...ExamSettlementConcurrencyTest.xml`

### 12.3 已确认根因

`application-test.yml` 使用三个**无默认值**环境变量：

- `${TEST_DB_URL}`
- `${TEST_DB_USER}`
- `${TEST_DB_PASSWORD}`

`pom.xml` 没有 Surefire/Failsafe 的环境变量注入配置；变量需由操作系统环境或 IDE 运行配置提供。当前运行未提供这些变量，因此：

- `DbConnectionGuardTest.connectionMustTargetIndependentTestDatabase` 在 `Environment.getProperty("spring.datasource.url")` 处报 `Could not resolve placeholder 'TEST_DB_URL'`（测试代码中的属性解析，不是业务代码异常）。
- `ExamSettlementConcurrencyTest.concurrentSubmit_settlesExactlyOnce` 在 Hikari 获取连接时报 `Driver com.mysql.cj.jdbc.Driver claims to not accept jdbcUrl, ${TEST_DB_URL}`；原因是 Hikari 使用了未解析的占位符字符串，不是数据库业务逻辑错误。

两个错误均指向**测试数据库环境变量缺失**；**尚未发现业务代码缺陷**。

### 12.4 安全边界与测试数据库要求

- `DbConnectionGuardTest` 仍是安全保护：期望 URL 目标库名严格等于 `driving_exam_test`，实际连接 `SELECT DATABASE()` 也必须是该库，`CURRENT_USER()` 必须严格为 `drv_test@localhost`（非 root）。
- `ExamSettlementConcurrencyTest` 会写入专用测试数据（`zz_it_settle_` 前缀），并只按生成的 ID 清理；不得指向核心演示库。
- `TEST_DB_USER` 与 `TEST_DB_PASSWORD` 是配套变量；没有 `TEST_DB_USERNAME`。
- 不应为通过测试而添加默认数据库、关闭守卫或跳过测试。

### 12.5 本轮修改与验证

- 修改：`doc/Day27-本机验收指南.md`，增加独立测试库环境变量、检查命令和两个集成测试的运行步骤。
- 未修改：测试代码、`application-test.yml`、`pom.xml`、业务代码、数据库。
- 本轮未重新执行 Maven 测试；结论基于用户提供的实际结果和 Surefire XML 复核。前端 `vue-tsc`/`npm run build` 仍为此前结果（exit 0），与本测试错误无关。

### 12.6 下一步

1. 按指南 §2.5 在**当前 PowerShell 会话**设置 `TEST_DB_URL`、`TEST_DB_USER`、`TEST_DB_PASSWORD`（仅指向独立测试库，不把密码写入仓库）。
2. 确认三个变量显示 `SET`。
3. 先运行不依赖数据库的纯单测，再单独运行 `DbConnectionGuardTest`，最后运行 `ExamSettlementConcurrencyTest`。
4. 通过后再执行完整 `mvn ... test`；保存新的 Surefire 结果。

## 十三、独立测试库准备情况只读调查（日期待用户确认）

> 本节为追加记录，不改写 §一~§十二。

### 13.1 已查证事实

- `driving-exam-server/src/test/resources/application-test.yml` 第 21-23 行仍使用 `${TEST_DB_URL}`、`${TEST_DB_USER}`、`${TEST_DB_PASSWORD}`，无默认值。
- `DbConnectionGuardTest.java` 第 48/51 行要求数据库 `driving_exam_test`、实际账号 `drv_test@localhost`；第 82-86 行用 `SELECT CURRENT_USER()` 严格断言。
- `sql/_localhost-2026_10_08_17_26_16-dump.sql` 是 16 表 schema-only dump：`CREATE TABLE` 16 个、`INSERT INTO` 0 个、`CREATE DATABASE` 0 个；没有 `USE` 数据库语句。
- `sql/seed-question.sql`、`seed-question-extra.sql`、`seed-exam-paper.sql` 含演示数据及固定 ID 的 `DELETE`；不是测试库账号/建库脚本。
- `sql/schema-mock-exam-answer.sql` 只创建 `mock_exam_answer` 表；该表已包含在 dump 中。
- `doc/testsql/*.sql` 是 `driving_exam` 的教练学员测试数据准备/预检查，不是 `driving_exam_test` 测试库脚本；主准备脚本仍有 `<FILL_BCRYPT_HASH>` 占位符。
- 全仓库搜索 `CREATE DATABASE`、`CREATE USER`、`GRANT`、`drv_test`：**没有**创建 `driving_exam_test`、创建 `drv_test` 或授权的可执行脚本。
- `docker-compose.yml` 第 26-38 行初始化的是 `MYSQL_DATABASE`（默认 `driving_exam`）并挂载 dump/seed/演示账号，不是 `driving_exam_test`；没有 `drv_test` 账号。
- `doc/Day21-工作日志.md` 记录过 `driving_exam_test`/`drv_test` 的历史测试通过（例如 §三、§十二），但这是历史记录，不是当前本机状态证据。
- `README.md` §“测试与检查”只给出 `mvn ... test`，未说明 `TEST_DB_*` 前置条件；`doc/Day27-本机验收指南.md` 已补充环境变量说明，并明确仓库没有建库/授权脚本。

### 13.2 独立测试库准备情况

**已具备（仓库内）**

- test profile 配置与只读连接守卫；
- 16 表 schema dump（需在独立空库中导入）；
- 集成测试会创建/清理自己的测试数据，不需要演示 seed。

**缺失**

- `driving_exam_test` 建库脚本；
- `drv_test@localhost` 账号创建和 `GRANT` 脚本；
- 测试库 schema 导入的专用脚本/说明（dump 本身无 `CREATE DATABASE`/`USE`）；
- 当前测试库状态的可验证证据。

**未知**

- 你本机现在是否仍存在 `driving_exam_test`；
- `drv_test` 账号的 host 是否严格为 `localhost`；
- 该账号是否有足够权限执行 `ExamSettlementConcurrencyTest` 的插入/删除；
- 测试库是否已导入完整 16 表 schema。

### 13.3 Docker 与本地测试库的区别

- Docker 初始化数据库：`driving_exam`（默认），包含 dump、seed 和演示账号 `student01/coach01/admin01/student02`。
- 测试 profile：`driving_exam_test`，账号 `drv_test@localhost`，只依赖 schema；测试自己创建专用数据。
- Docker 的数据库卷是 Docker 容器内的独立卷，不等于本机 MySQL 的测试库；不能因为 Docker 初始化脚本存在就认定本机测试库可用。

### 13.4 本轮操作边界

- 未连接 MySQL，未执行 SQL，未创建/删除/初始化数据库、账号或权限。
- 未修改测试代码、`application-test.yml`、`pom.xml`、业务代码或数据库配置。
- 仅修改 `doc/Day27-本机验收指南.md`（补充“仓库没有建库/授权脚本”和 Docker 测试库区别）并追加本日志。
- 未执行 Maven 测试；用户此前的 `205/0/2/6` 结果仍是当前最新记录。

### 13.5 下一步安全准备

1. 先确认你要使用**独立测试库**，不是核心 `driving_exam`，也不是 Docker 的 `driving_exam` 容器库。
2. 由你或 DBA 在隔离 MySQL 实例准备 `driving_exam_test` 和 `drv_test@localhost`，并导入 16 表 schema；本仓库没有可直接执行的建库/授权脚本，不能凭空生成。
3. 在 PowerShell 当前会话设置 `TEST_DB_URL`/`TEST_DB_USER`/`TEST_DB_PASSWORD`，用 `SET/MISSING` 检查确认，不输出值。
4. 先运行 `DbConnectionGuardTest`，通过后再运行 `ExamSettlementConcurrencyTest`，最后运行完整测试。
5. 将测试报告保存到 `target/surefire-reports`，并记录测试库是否为独立库、账号 host、schema 是否完整。

## 十四、独立测试库 DataGrip 只读验证记录（日期待用户确认）

> 本节为追加记录，不改写 §一~§十三。

### 14.1 用户实际只读结果

用户在 DataGrip 中使用 `localhost:3306`、用户名 `drv_test` 的数据源执行只读查询：

- `DATABASE()` = `driving_exam_test`
- `CURRENT_USER()` = `drv_test@localhost`
- `USER()` = `drv_test@localhost`
- `information_schema.TABLES` 只读确认存在以下五张表：
  - `exam_paper`
  - `knowledge_point`
  - `mock_exam`
  - `question`
  - `sys_user`
- DataGrip 界面显示 `driving_exam_test` 有 **16 张表**；
- `driving_exam` 也存在，必须保护；本次未对它执行任何操作；
- 本次验证为 **DataGrip 手动只读查询**，不是 Maven 测试，也不是 `DbConnectionGuardTest` 或 `ExamSettlementConcurrencyTest` 的运行结果。

### 14.2 配置核对

- `driving-exam-server/src/test/resources/application-test.yml` 第 21-23 行仍读取 `TEST_DB_URL`、`TEST_DB_USER`、`TEST_DB_PASSWORD`，无默认值。
- `DbConnectionGuardTest` 第 48 行要求 `driving_exam_test`，第 51 行要求 `drv_test@localhost`，第 82-86 行用 `SELECT CURRENT_USER()` 严格断言。
- 用户 DataGrip 结果显示数据库名和账号身份与守卫要求一致；这支持配置方向正确，但不构成 Maven 测试通过。
- 指南 §2.5 的 PowerShell 变量名、URL 库名和用户名与代码要求一致。

### 14.3 仍未验证

- `driving_exam_test` 其余 11 张表是否全部存在、结构与 dump 完全一致；
- `drv_test@localhost` 是否具备并发测试所需 INSERT/UPDATE/DELETE 权限；
- 当前 PowerShell 会话的 `TEST_DB_*` 是否已设置（此前检查为 MISSING，本次未重新检查）；
- `DbConnectionGuardTest` 是否能在 Maven 中通过；
- `ExamSettlementConcurrencyTest` 是否能在 Maven 中通过；
- 完整 `mvn ... test` 的 205+ 测试结果。

### 14.4 本轮修改与安全边界

- 修改 `doc/Day27-本机验收指南.md`：记录 DataGrip 只读验证结果及仍未验证项。
- 追加本日志。
- 未连接数据库、未执行 SQL、未修改数据库/账号/权限/数据/环境变量。
- 未运行 Maven 测试、未修改测试代码、`application-test.yml`、POM 或业务代码。

### 14.5 下一步最小风险操作

1. 在 PowerShell 当前会话设置 `TEST_DB_URL`（`localhost:3306/driving_exam_test`）、`TEST_DB_USER=drv_test`、`TEST_DB_PASSWORD`（使用你已知的测试库口令，不写入仓库）。
2. 用 `SET/MISSING` 检查三个变量，不打印值。
3. 先运行纯单元测试，再运行 `DbConnectionGuardTest`；通过后再运行 `ExamSettlementConcurrencyTest`，最后运行完整测试。
4. 保存 Surefire 报告；不要对核心库 `driving_exam` 做任何写入。

## 十五、Docker 部署配置第一轮修复（日期待用户确认）

> 本节为追加记录，不改写 §一~§十四。

### 15.1 本轮修改

- `.gitignore`：显式增加 `docker/.env`、`docker/.env.local`（原有 `.env` 已匹配，增加是为了可读性）。
- `.dockerignore`：增加 `.env`、`docker/.env`，以及
  `driving-exam/driving-exam-server/src/main/resources/application.yml`；
  保留 `application.yml.example` 供 Dockerfile 复制。
- `docker/.env.example`：首次验收默认值改为独立数据库 `driving_exam_docker_accept`、
  MySQL 13306、后端 18080、前端 18081、Qdrant 16333/16334；明确应用仍使用 root，生产前必须替换。
- `docker-compose.yml`：注释中的 Compose 命令统一为
  `--env-file docker/.env -p driving-exam-accept`，并补充初始化 SQL 破坏性、root 连接和 `down -v` 警告。
- `README.md`：Docker 命令统一使用 env-file/项目名、独立端口/数据库名，补充初始化与 root 风险。
- `doc/Day27-本机验收指南.md`：Docker 验收命令同步使用 env-file/项目名、独立端口，补充 `.dockerignore` 排除本机配置的说明。

### 15.2 静态验证

- `.env.example` 变量名与 `docker-compose.yml` 引用变量集合完全一致（13 个，无缺失/多余）。
- `docker-compose.yml` YAML 语法可解析；服务为 mysql/backend/frontend/qdrant，卷为 mysql-data/qdrant-data。
- `docker/.env` 被 `.gitignore` 明确匹配。
- `.dockerignore` 精确包含本机 `driving-exam-server/src/main/resources/application.yml`；
  Dockerfile 仍复制 `application.yml.example`。
- 文档中的 `docker compose` 项目命令统一包含 `--env-file docker/.env -p driving-exam-accept`；
  `docker compose version` 为版本检查命令，不含项目参数。
- 未执行 `docker compose config`/`up`/`down`：当前环境无 Docker CLI；未生成真实 `.env`，未猜测秘密。
- 未修改业务代码、业务数据库或测试代码。

### 15.3 未解决

- 应用仍使用 root 连接 MySQL；最小权限账号未实现，作为部署前阻塞项。
- MySQL 官方镜像是否按 `MYSQL_DATABASE` 自动选中数据库并执行无 `CREATE DATABASE`/`USE` 的 dump，未实测。
- `-p driving-exam-accept` 对顶层 `name` 的覆盖、实际卷名、端口占用和镜像构建，未实测。
- 本机 `application.yml` 已从 Docker 构建上下文排除，但未实际构建确认 JAR 内容。

## 十六、Docker 最小权限账号与 JAR 配置修复（日期待用户确认）

> 本节为追加记录，不改写 §一~§十五。

### 16.1 任务 A：应用数据库账号从 root 改为 drv_app

- `docker-compose.yml`：MySQL 服务新增 `APP_DB_USER`、`APP_DB_PASSWORD` 环境变量；
  backend 的 `DB_USERNAME`/`DB_PASSWORD` 改为 `${APP_DB_USER:-drv_app}` / `${APP_DB_PASSWORD:?}`；
  数据库 URL 仍为 `mysql:3306`，未改为 localhost。
- `docker/mysql/init/10-app-user.sh`：首次初始化时使用 root 创建 `drv_app@%`，
  仅授予 `MYSQL_DATABASE` 的 `SELECT, INSERT, UPDATE, DELETE`；
  密码来自 `APP_DB_PASSWORD`，不写入 SQL 文件。
- `docker/.env.example`：新增 `APP_DB_USER`/`APP_DB_PASSWORD` 占位；
  `MYSQL_ROOT_PASSWORD` 仅用于初始化/管理员维护。
- Compose 初始化挂载顺序：schema、预约注释、seed、`10-app-user.sh`、`90-demo-accounts.sql`；
  已有数据卷不会自动重跑该脚本。
- 未执行 SQL/未创建账号/未启动容器。

### 16.2 任务 B：正式 JAR 排除真实 application.yml

- `driving-exam-server/pom.xml` 增加 Maven `resources` 排除：
  `src/main/resources/application.yml`。
- `application.yml.example` 仍保留在资源目录，供 Dockerfile 复制。
- `.dockerignore` 继续排除本机 `application.yml`，并排除 `.env`/`docker/.env`。
- 未重新构建 JAR；现有 `target/driving-exam-server-1.0.0.jar` 仍含修改前的
  `BOOT-INF/classes/application.yml`，不能作为交付物，需新构建后复查。

### 16.3 静态验证

- Compose 的 14 个 `${...}` 变量与 `.env.example` 键集合完全一致，无缺失/多余。
- YAML 可解析；服务仍为 mysql/backend/frontend/qdrant；backend `DB_URL` 使用 `mysql:3306`。
- `10-app-user.sh` 通过 `bash -n`；脚本未执行。
- POM 资源排除规则存在；`application.yml.example` 未被排除。
- `docker/.env` 被 `.gitignore` 排除；未生成真实 `.env`。
- 未执行 Docker、未构建镜像、未运行集成测试；以上均为静态检查。

### 16.4 未解决/待验证

- 应用账号初始化脚本未在 MySQL 官方镜像中实际运行；密码环境变量传递、脚本权限、
  `CREATE USER`/`GRANT` 结果、已有卷行为均待实测。
- 新 JAR 是否确实不含 `application.yml` 只能在新构建后检查；旧 JAR 仍含真实配置。
- 正式构建必须使用 `mvn clean package`，否则旧的 `target/classes/application.yml` 可能残留。
- 应用账号 DML 权限是否覆盖全部业务（Excel、管理员、考试、AI、预约）需真实回归。
- 应用运行时改为 drv_app；root 仍仅用于 MySQL 初始化/健康检查。

## 十七、Docker 首版实际部署与验收（2026-10-19）

> 本节为追加记录，不改写 §一~§十六。§六~§九 记录的是 Day27 代码完成时的“Docker 未部署”状态；本节记录后续用户在本机实际执行的 Docker 验收，两者时间点不同。
>
> 证据来源说明：Docker 配置检查、镜像构建、容器启动、健康状态、浏览器访问和角色登录，均为**用户在本机执行并反馈**；Claude 当前执行环境没有 Docker CLI，未独立复现上述运行结果。仓库内的配置与文档由 Claude 做只读核对。

### 17.1 验收目标与范围

目标：在独立 Compose 项目中部署 MySQL、Spring Boot 后端和 Nginx 前端，验证首版部署可启动、容器健康、前后端链路可访问，并确认不会影响本机原开发数据库。

本次范围：

- 首版三个核心容器：`mysql`、`backend`、`frontend`；
- 独立数据库、独立端口、独立命名卷；
- 容器健康检查；
- 前端页面访问、后端鉴权接口响应、前端 `/api` 代理链路；
- 学员、教练、管理员三个角色的基础登录。

本次**不包含**：全部业务功能回归、Excel 导入实测、RAG/Qdrant 联调、并发/性能测试、生产安全加固。

### 17.2 验收环境与隔离方案

| 项目 | 实际值 |
|---|---|
| Compose 项目名 | `driving-exam-accept` |
| Compose 配置 | `docker-compose.yml` |
| 环境变量文件 | `docker/.env`（不提交 Git，不记录真实值） |
| 前端地址 | `http://localhost:18083` |
| 后端映射端口 | `18080` |
| MySQL 映射端口 | `13306` |
| 验收数据库 | `driving_exam_docker_accept` |
| MySQL 命名卷 | `driving-exam-accept_mysql-data` |
| 启动服务 | `mysql`、`backend`、`frontend`；未启动 `qdrant`（`rag` profile 未启用） |

隔离要点：

- 使用独立的 Compose 项目名 `driving-exam-accept`，避免与默认项目及其他 Compose 项目混用；
- 数据库名使用 `driving_exam_docker_accept`，不使用本机原开发库 `driving_exam`；
- MySQL 数据存放在 Docker 命名卷 `driving-exam-accept_mysql-data`，不挂载宿主机 MySQL 数据目录；
- 宿主机原开发数据库仍使用本机 3306；Docker MySQL 映射到 13306，两者端口和数据目录均隔离；
- 初始化 SQL 只应在空数据卷首次启动时执行；本次未对原开发库执行初始化 SQL，也未删除或重建验收数据卷；
- 应用运行时账号按现有 Compose 配置使用 `drv_app`（仅目标库 DML），root 仅用于初始化和管理维护。

Claude 的仓库只读核对结果：`docker/.env` 存在；其中 `FRONTEND_PORT`、`BACKEND_PORT`、`MYSQL_PORT`、`MYSQL_DATABASE`、`APP_DB_USER` 与上述验收值一致（只比较键值是否匹配，未读取或记录密码、JWT 密钥、API Key）。

### 17.3 部署过程

用户在本机实际执行的关键步骤：

1. 检查 `docker-compose.yml` 和 `docker/.env`，确认项目名、端口、数据库名和变量引用；
2. 确认验收数据库与原开发库隔离，检查初始化 SQL 的 `DROP`/`DELETE` 影响范围；
3. 成功构建前端和后端 Docker 镜像；
4. 执行 `docker compose --env-file docker/.env -p driving-exam-accept up -d`，三个核心容器启动；
5. 发现前端容器健康检查失败，定位为 `localhost` 在容器内解析到不可用的 IPv6 回环地址；
6. 将前端健康检查改为明确访问 `127.0.0.1`，重新创建前端容器后恢复健康；
7. 确认前端、后端、MySQL 三个容器均显示 `healthy`。

本次健康检查修复只修改了 `docker-compose.yml` 中 `frontend.healthcheck.test` 的地址，未修改业务代码、数据库、初始化 SQL、端口或其他环境变量。健康检查定义在 Compose 服务配置中，不在前端镜像内，因此应用该修复不需要重建镜像，只需重建前端容器。

### 17.4 容器健康与连通性验证

| 检查 | 实际结果 | 证据来源 |
|---|---|---|
| `docker compose ... ps` 中三个核心容器 | **均为 `healthy`** | 用户本机执行并确认 |
| 宿主机访问 `http://localhost:18083/` | **HTTP 200**，浏览器可正常打开前端页面 | 用户本机确认 |
| 容器内访问 `http://127.0.0.1/` | **HTTP 200**，退出码 0 | 用户此前实测 |
| 容器内访问 `http://[::1]/` | `Connection refused`，退出码 1 | 用户此前实测 |
| 直接访问 `http://localhost:18080/api/auth/me` | **HTTP 401** | 用户本机确认 |
| 通过前端访问 `http://localhost:18083/api/auth/me` | **HTTP 401** | 用户本机确认 |

结果解释：

- 宿主机 200 和容器内 IPv4 200 说明 Nginx 首页可正常响应；
- 容器内 IPv6 回环拒绝说明原健康检查失败的直接原因是地址族选择，而不是 Nginx 无法提供服务；
- 两个 `/api/auth/me` 均返回 401，符合未带 Token 时受保护接口要求鉴权的预期；这支持“前端 `/api` 代理到后端”的链路判断，但**不等于登录成功、权限控制或全部接口已验收**；
- 401 是未认证请求的预期响应，不能据此宣称已登录业务接口可用。

### 17.5 前端健康检查 IPv6/IPv4 问题与处理

问题现象：宿主机和容器内 IPv4 地址均能访问 Nginx，但前端容器仍为 `unhealthy`。

直接原因：原健康检查命令使用：

```text
wget -q -O - http://localhost/ >/dev/null 2>&1 || exit 1
```

在容器内 `localhost` 的解析可能优先得到 IPv6 回环地址 `::1`，而该地址连接被拒绝，导致健康检查命令失败；Nginx 本身在 IPv4 回环 `127.0.0.1` 上响应正常。

处理方案：仅将健康检查地址改为显式 IPv4：

```text
wget -q -O - http://127.0.0.1/ >/dev/null 2>&1 || exit 1
```

修改文件：`docker-compose.yml` 的 `frontend.healthcheck.test`。未修改 `docker/frontend/Dockerfile` 或 Nginx 配置。

处理结果：用户重新创建前端容器后，前端健康状态恢复；最终三个核心容器均为 `healthy`。

补充：后端健康检查仍使用 `localhost:8080`，本轮没有修改，也没有报告后端出现同类健康检查问题；如后续出现，可另开任务按同样方式核查，不在本次范围内顺手修改。

### 17.6 角色基础登录测试

用户已分别使用学员、教练和管理员账号完成基础登录测试，确认三个角色都能完成登录进入系统。

边界：

- 这里只证明**基础登录流程**可用；
- 未逐项验证各角色的菜单、权限边界、数据可见范围和业务操作；
- 未记录账号、密码或 Token；
- 不能把“三个角色能登录”写成“学员端、教练端、管理员端全部业务功能验收通过”。

### 17.7 验收状态分类

**已完成**

- Docker Compose 配置与环境变量文件检查；
- 独立数据库、独立端口、独立命名卷的隔离方案确认；
- 前端、后端镜像构建；
- `docker compose ... up -d` 启动首版；
- 前端健康检查 IPv6/IPv4 问题定位与修复；
- 前端、后端、MySQL 三个核心容器健康；
- 前端页面访问、前后端 401 连通性检查；
- 学员、教练、管理员基础登录。

**已验证**

- 三个核心容器 `healthy`；
- 宿主机前端页面可访问；
- 容器内 IPv4 回环可访问，IPv6 回环不可访问（问题根因证据）；
- 前端 `/api` 代理链路可达后端；
- 未登录访问受保护接口返回 401；
- 三个角色基础登录成功。

**尚未验证**

- 学员端预约、练习、错题、模拟考试等完整业务流程；
- 教练端学员管理、预约处理等写操作；
- 管理员端 9 个菜单的完整功能与权限边界；
- Excel 模板下载、导入、错误行和事务回滚；
- AI 问答、RAG/Qdrant、Embedding 外部依赖；
- Docker 重启后的数据持久化、空卷重复初始化的可重复性；
- `drv_app` 账号的实际授权范围、已有卷补建账号流程；
- 备份/恢复、生产环境安全加固和性能。

**留待下次**

用户明确决定本次暂不修复验收中发现的功能小问题。由于目前没有准确的问题描述，本节不编造问题清单；记录为：

> 用户反馈仍存在部分功能问题，具体问题待下次逐项复现、记录现象和确认原因后再处理。

### 17.8 答辩可用总结

**问：为什么需要 Docker 部署？**

答：课程设计不能只在开发机的 IDE 和本地环境里运行。Docker 把 MySQL、后端和 Nginx 前端封装成可重复启动的服务，减少“换一台机器就要重新配环境”的问题，也便于演示和验收。

**问：Compose 是如何编排三个服务的？**

答：`docker-compose.yml` 定义 MySQL、Spring Boot 后端和 Nginx 前端三个服务。MySQL 使用命名卷保存数据；后端通过服务名 `mysql:3306` 连接数据库；前端 Nginx 托管静态页面，并把 `/api` 反向代理到 `backend:8080`。Compose 的健康检查和 `depends_on` 让后端等待 MySQL 健康后再启动，前端再等待后端健康。

**问：怎样降低对原开发环境的影响？**

答：使用独立 Compose 项目名 `driving-exam-accept`、独立数据库 `driving_exam_docker_accept`、独立宿主机端口和独立命名卷。Docker MySQL 映射到 13306，不使用本机开发库的 3306；初始化 SQL 只在空卷首次启动时执行。验收期间没有对原开发库 `driving_exam` 做初始化、删除或写入。

**问：怎样验证部署结果？**

答：先看 `docker compose ... ps` 中三个核心容器是否 `healthy`；再访问前端首页确认 Nginx 正常；然后请求 `/api/auth/me`，未登录返回 401 说明受保护接口和代理链路可达；最后分别用学员、教练、管理员账号做基础登录。401 只能说明鉴权生效，不能代替完整业务测试。

**问：本次验收的范围和局限是什么？**

答：本次完成的是首版部署、隔离、容器健康、前后端连通和三角色基础登录，不等于全部业务功能验收。预约、教练写操作、管理员九菜单、Excel 导入、AI/RAG 和持久化复测仍需后续逐项验证；发现的功能问题也留待下次复现后处理。

### 17.9 操作边界与后续

- 未修改业务代码、数据库、初始化 SQL、原开发库数据和 `.env` 内容；
- 未执行 `docker compose down -v`，未删除或重建验收数据卷；
- 未停止、重启或修改其他 Docker 项目；
- 未执行 Git add/commit/reset/clean；
- 未在日志中记录任何密码、JWT 密钥、API Key 或 `.env` 完整内容。

后续建议：在保留现有验收卷的前提下，逐项复现用户反馈的功能问题并记录页面、接口响应和后端日志；确认问题后再决定修复范围，不直接重建数据库或删除卷。
