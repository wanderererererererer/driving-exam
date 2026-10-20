# Day27 本机验收指南（Windows）

> 本指南用于在你自己的 Windows 电脑上验证 Day26–Day27 新增代码。
> 当前 Claude 执行环境只有 Java 11 JRE，没有 `javac`、Maven 或 Docker；
> 因此以下命令**尚未由 Claude 执行**，不能把本指南当成“已验证通过”。
> Docker 首版已于 2026-10-19 由用户在本机实际构建并启动，验收记录见
> `doc/Day/Day27-工作日志.md` §十七；本指南第 8 节保留为复核步骤。
>
> 原则：先编译，再重启，再接口/页面，再 Excel，最后 Docker。
> 不要使用 `student01` 等核心演示账号做解绑、改学情或 Excel 写入测试。

## 0. 前置约定

- 项目根目录：`D:\myself\Integrate`
- 后端目录：`D:\myself\Integrate\driving-exam`
- 前端目录：`D:\myself\Integrate\frontend`
- 数据库：MySQL 8；管理员账号：`admin01`；教练测试账号：建议使用隔离账号。
- 需要保存一份当前数据库备份；Docker 测试必须使用**空的专用数据卷**。

---

## 1. 检查 Java 和 Maven

### 1.1 检查 Java 版本

作用：确认默认 Java 是 17。项目 `pom.xml` 要求 Java 17；Java 11 无法编译本项目。  
改变：无。  
目录：任意 PowerShell 目录。

```powershell
java -version
```

期望：输出 `17.x`。如果是 11 或其他版本，先切换 JDK 17。

### 1.2 检查 Java 编译器

作用：确认 `javac` 存在。只有 JRE 没有 `javac` 时无法命令行编译。  
改变：无。

```powershell
javac -version
```

期望：输出 `javac 17.x`。

### 1.3 检查 Maven

作用：确认 Maven 可用，并确认使用的 JDK。  
改变：无。

```powershell
mvn -version
```

期望：Maven 3.8+，且 `Java version: 17`。

---

## 2. 确认项目路径与模块

### 2.1 进入项目根目录

作用：确认当前操作位置。  
改变：只切换 PowerShell 当前目录。

```powershell
cd D:\myself\Integrate
```

### 2.2 查看根目录

作用：确认 `driving-exam`、`frontend`、`README.md`、`docker-compose.yml` 存在。  
改变：无。

```powershell
Get-ChildItem
```

### 2.3 进入后端目录

作用：后续 Maven 命令必须在包含后端聚合 `pom.xml` 的目录执行。  
改变：只切换目录。

```powershell
cd D:\myself\Integrate\driving-exam
```

### 2.4 确认 Maven 模块

作用：确认 `driving-exam-common`、`driving-exam-pojo`、`driving-exam-server` 三个模块存在。  
改变：无。

```powershell
Get-ChildItem
```

---

## 2.5 配置独立测试数据库（仅运行集成测试时）

`driving-exam-server/src/test/resources/application-test.yml` 在 `@ActiveProfiles("test")` 下读取三个**无默认值**环境变量：

- `TEST_DB_URL`
- `TEST_DB_USER`
- `TEST_DB_PASSWORD`

测试要求：

- 数据库名必须严格为 `driving_exam_test`；
- 实际连接账号必须严格为 `drv_test@localhost`（不是 root，也不是其他 host 形式）；
- 不得使用核心演示库 `driving_exam`；
- 测试库需要已有表结构；`ExamSettlementConcurrencyTest` 会插入并清理带 `zz_it_settle_` 前缀的专用测试数据，`DbConnectionGuardTest` 只执行 `SELECT`。

**仓库当前没有**创建 `driving_exam_test`、创建 `drv_test`、授权或导入测试库 schema 的现成 SQL 脚本。不要用 Docker 初始化出来的 `driving_exam` 数据库代替测试库；Docker 与本机测试库是两套不同的数据库/账号。测试库的表结构可由 `sql/_localhost-2026_10_08_17_26_16-dump.sql` 的 16 表 schema 提供，但该 dump 没有 `CREATE DATABASE`/`USE`，必须先在一个**独立的空测试库**中创建并选中该库再导入；不要导入到核心演示库。由于仓库没有经过验证的建库/授权脚本，本指南不提供未经核验的 `CREATE DATABASE`/`CREATE USER`/`GRANT` 命令，具体创建方式需要你或数据库管理员确认。

如果变量缺失，`DbConnectionGuardTest` 会在读取 `spring.datasource.url` 时直接报 `Could not resolve placeholder 'TEST_DB_URL'`；`ExamSettlementConcurrencyTest` 会在 Hikari 获取连接时报 `Driver ... claims to not accept jdbcUrl, ${TEST_DB_URL}`。这是**有意的 fail-fast**，不是业务代码缺陷。

**已确认的本机只读状态（DataGrip，日期待用户确认）**：

- 数据源连接 `localhost:3306`，用户名 `drv_test`；
- `DATABASE()` = `driving_exam_test`；
- `CURRENT_USER()` = `drv_test@localhost`；
- `USER()` = `drv_test@localhost`；
- DataGrip 界面显示 `driving_exam_test` 有 **16 张表**；
- 通过 `information_schema.TABLES` 只读确认 `exam_paper`、`knowledge_point`、`mock_exam`、`question`、`sys_user` 五张表存在；
- `driving_exam` 也存在，必须保护；本次没有对它执行任何操作。

这证明测试库名和账号身份与守卫要求一致，但**不等于完整 schema 兼容性、写权限或 Maven 测试已通过**。尚未确认其余 11 张表是否全部存在、`drv_test` 是否具备并发测试所需写权限。

### 2.5.1 在当前 PowerShell 会话设置（只影响当前终端）

逐条执行；把尖括号内容替换为你自己的测试库信息，不要把真实口令提交到仓库或聊天：

```powershell
$env:TEST_DB_URL = 'jdbc:mysql://localhost:3306/driving_exam_test?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true'
```

```powershell
$env:TEST_DB_USER = 'drv_test'
```

```powershell
$env:TEST_DB_PASSWORD = '<你的测试库口令>'
```

当前会话关闭后失效，不会永久修改系统环境；推荐这种方式，尤其是密码。

### 2.5.2 确认变量已设置但不打印值

```powershell
if ($env:TEST_DB_URL) { 'TEST_DB_URL=SET' } else { 'TEST_DB_URL=MISSING' }
```

```powershell
if ($env:TEST_DB_USER) { 'TEST_DB_USER=SET' } else { 'TEST_DB_USER=MISSING' }
```

```powershell
if ($env:TEST_DB_PASSWORD) { 'TEST_DB_PASSWORD=SET' } else { 'TEST_DB_PASSWORD=MISSING' }
```

三个都应为 `SET`。不要把变量值或密码粘贴到报告、聊天或仓库。

### 2.5.3 永久环境变量（不推荐保存密码）

`[Environment]::SetEnvironmentVariable(..., 'User')` 会写入当前 Windows 用户的环境；密码也会被持久化，存在泄露风险。除非你明确需要，否则不要永久设置 `TEST_DB_PASSWORD`，只在当前 PowerShell 会话设置。

### 2.5.4 先运行不依赖真实数据库的测试

```powershell
mvn -pl driving-exam-server -am -Dtest=AnswerGraderTest,MasteryCalculatorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

先确认这些纯单元测试能通过；它们不连接测试数据库。

---

## 3. 后端编译

### 3.1 编译后端（跳过测试运行）

作用：编译三个 Maven 模块并生成可执行 jar；不启动服务、不连接数据库。  
改变：更新各模块 `target/` 目录。  
不会改变：数据库、源代码、前端文件。  
目录：`D:\myself\Integrate\driving-exam`。

```powershell
mvn -pl driving-exam-server -am -DskipTests package
```

期望：`BUILD SUCCESS`，并生成：

```text
driving-exam-server\target\driving-exam-server-1.0.0.jar
```

如果失败：保存**第一个** `ERROR` 及其上方的编译文件/行号；不要只看最后的 `BUILD FAILURE`。

### 3.2 运行后端测试

作用：运行现有单元/集成测试；不会启动 Tomcat，也不应写业务数据库（以测试配置为准）。  
改变：生成 `target/surefire-reports`。  
目录：`D:\myself\Integrate\driving-exam`。

```powershell
mvn -pl driving-exam-server -am test
```

如果失败：保存失败测试类名、断言信息、`target/surefire-reports` 中对应 `.txt`/`.xml`，以及第一个异常堆栈。

> 当前已执行的基线（用户本机）：`Tests run: 205, Failures: 0, Errors: 2, Skipped: 6`；两个错误都是 `TEST_DB_*` 未设置导致，不是业务断言失败。

### 3.3 只运行两个数据库集成测试

前提：已完成 §2.5 的独立测试库变量设置，且已确认不是核心演示库。  
第一条命令只读校验配置和实际连接；第二条会写入并清理 `driving_exam_test` 中的专用测试数据。

```powershell
mvn -pl driving-exam-server -am -Dtest=DbConnectionGuardTest -Dsurefire.failIfNoSpecifiedTests=false test
```

```powershell
mvn -pl driving-exam-server -am -Dtest=ExamSettlementConcurrencyTest -Dsurefire.failIfNoSpecifiedTests=false test
```

如果失败：保存 `target/surefire-reports` 对应 XML，不要删除守卫、不要改用 `driving_exam` 核心库。

---

## 4. 安全重启后端

### 4.1 停止当前后端

在 IDEA 中点击 Stop；如果使用命令行启动，关闭对应 PowerShell 窗口或按 `Ctrl+C`。  
不要使用会误杀其他 Java 进程的强制命令。

### 4.2 启动新编译版本

作用：让新增 `/api/admin/**` 接口生效。  
改变：启动本机 8080 服务。  
不会改变：数据库结构或数据。  
目录：`D:\myself\Integrate\driving-exam`。

```powershell
java -jar driving-exam-server\target\driving-exam-server-1.0.0.jar
```

或在 IDEA 中运行 `DrivingExamApplication`。

### 4.3 确认服务启动

作用：确认 8080 可访问。未带 Token 时 `/api/auth/me` 返回 401 是正常的。  
改变：无。

```powershell
Invoke-WebRequest http://localhost:8080/api/auth/me
```

期望：服务可访问（通常 401）；如果连接拒绝，后端未启动或端口被占用。

---

## 5. 管理员九个菜单验收

先登录管理员账号 `admin01`，依次检查以下页面：

1. `http://localhost:5173/admin/dashboard`：数字概览不是 `"--"`，数字来自后端。
2. `/admin/users`：用户列表、角色筛选、创建测试用户、编辑、启停用。
3. `/admin/bindings`：绑定列表、选择教练/学员 ID 创建绑定、软解绑。
4. `/admin/knowledge-points`：科目树、添加根/子节点、编辑、删除约束。
5. `/admin/questions`：题目列表、编辑、启停、删除约束、模板下载、Excel 导入。
6. `/admin/papers`：试卷列表、组卷创建/编辑、启停。
7. `/admin/appointments`：全量预约只读列表、状态筛选、分页；**没有**审批/取消按钮。
8. `/admin/ai-stats`：真实累计/今日/去重用户数字。
9. `/admin/profile`：资料和密码修改；修改后刷新页面确认后端数据已变化。

每个页面至少记录：加载中、空数据、错误态、成功提交后的刷新结果。  
不要把“页面能打开”当成“业务通过”。

---

## 6. 教练学员写操作验收（隔离账号）

建议使用隔离测试学员/教练，不要使用 `student01` 进行解绑或改学情。  
先记录测试账号当前绑定和科目进度，便于恢复核对。

1. 登录教练账号，进入 `/coach/students`；
2. 绑定一个已有测试学员（只输入用户名）；
3. 用新用户名创建测试学员并绑定；记录返回结果；
4. 修改测试学员的学情阶段和备注；刷新页面确认列表/学情变化；
5. 解绑该测试绑定；确认提示内容、列表变化；
6. 检查历史预约、学情、错题记录没有被删除（用只读 SQL 或页面核对）；
7. 重复提交/错误用户名/重复绑定分别检查错误提示；
8. 用另一个教练账号尝试操作不属于自己的绑定，确认 3102。

---

## 7. Excel 导入验收（隔离数据库）

### 7.1 下载模板

在 `/admin/questions` 点击“下载模板”。  
检查模板表头顺序是否为：

```text
科目编码 | 知识点ID | 知识点名称 | 题型 | 题干 | 选项A | 选项B | 选项C | 选项D | 正确答案 | 解析 | 难度 | 媒体URL | 状态
```

### 7.2 正常导入

在**隔离测试库**中准备 1~2 条测试题，填写模板并上传。  
期望：提示成功数量；题目列表出现新题；选项和正确答案与 Excel 一致。

### 7.3 错误行

准备一个包含非法题型或缺失必填列的文件并上传。  
期望：返回行号、字段、原因；**不能写入任何题目**。

### 7.4 重复题

用同一科目 + 完全相同题干再次导入。  
期望：被拒绝并提示重复；不得静默覆盖原题。

### 7.5 事务回滚

准备一行合法 + 一行非法的文件并导入。  
期望：整批不写入；数据库中合法行也没有新增。  
导入前后用只读 SQL 对比 `question` 和 `question_option` 数量。

> 不要在核心演示题库上测试 Excel 写入；使用隔离库或先备份。

---

## 8. Docker 验收（空专用数据卷）

### 8.1 检查 Docker

作用：确认 Docker Desktop 和 Compose 可用。  
改变：无。

```powershell
docker version
```

```powershell
docker compose version
```

### 8.2 准备环境文件

作用：复制环境模板；随后用编辑器填写数据库密码和 JWT Secret。  
改变：创建本机 `docker\.env`（已被 `.gitignore` 忽略）。  
不会改变：源代码、数据库。

目录：`D:\myself\Integrate`。

```powershell
Copy-Item docker\.env.example docker\.env
```

用编辑器打开 `docker\.env`，填写：

- `MYSQL_ROOT_PASSWORD`：MySQL 初始化/管理员维护密码；
- `APP_DB_PASSWORD`：应用运行时账号 `drv_app` 的密码；
- `JWT_SECRET`：至少 32 字节随机字符串；
- 默认数据库名 `driving_exam_docker_accept`、模板端口 13306/18080/18081 已独立设置；
- 本次验收的 `docker/.env` 实际前端端口为 **18083**，以现有文件为准；
- 其他值保持默认；不要提交该文件。

所有 Compose 命令必须显式使用 `--env-file docker/.env` 和 `-p driving-exam-accept`。
Compose 默认从项目根目录读取 `.env`，不会自动读取 `docker/.env`。
`.dockerignore` 已排除本机 `application.yml`，不要删除该排除规则，否则本机数据源凭据可能进入镜像。
`driving-exam-server/pom.xml` 也已将真实 `application.yml` 从正式 JAR 资源中排除。
下次正式构建 JAR 必须使用 `mvn clean package`，否则旧的 `target/classes/application.yml` 可能残留；构建后检查 JAR 内容。

### 8.3 只检查 Compose 配置（不启动）

作用：解析并显示最终配置，不创建容器、不连数据库。  
改变：无。  
目录：`D:\myself\Integrate`。

```powershell
docker compose --env-file docker/.env -p driving-exam-accept config
```

检查输出中的数据库名应为 `driving_exam_docker_accept`，宿主机端口应为 13306/18080/18083
（本次 `docker/.env`；模板默认前端 18081），
backend 的 `DB_URL` 应包含 `mysql:3306`，`DB_USERNAME` 应为 `drv_app`，不是 root，也不是 `localhost`。
不要把完整配置中的密码粘贴到报告。

### 8.4 启动首版

作用：MySQL + 后端 + Nginx 前端；仅在空数据卷上初始化 SQL。  
改变：创建 `driving-exam-accept` 项目的容器、网络和卷，初始化 Docker 数据库。  
不会改变：宿主机现有 MySQL 数据库（未挂载宿主机数据目录）。  
目录：`D:\myself\Integrate`。

```powershell
docker compose --env-file docker/.env -p driving-exam-accept build
```

```powershell
docker compose --env-file docker/.env -p driving-exam-accept up -d
```

初始化 SQL 含 `DROP TABLE` 和固定 ID 的 `DELETE`，只能用于独立空卷；禁止对本机 `driving_exam` 或已有重要数据的 Docker 卷执行。
`docker/mysql/init/10-app-user.sh` 会在首次初始化时创建 `drv_app`，仅授予目标数据库的 SELECT/INSERT/UPDATE/DELETE；
已有卷不会自动补建该账号，需要单独的管理员操作并先确认。

### 8.5 查看服务状态

作用：检查容器健康状态。  
改变：无。  
目录：`D:\myself\Integrate`。

```powershell
docker compose --env-file docker/.env -p driving-exam-accept ps
```

本次实际验收结果：`mysql`、`backend`、`frontend` 三个核心容器均为 `healthy`；未启动 `rag` profile 时没有 `qdrant` 属于预期。

### 8.6 查看后端日志

作用：确认 Spring Boot 启动和数据库连接。  
改变：无。  
目录：`D:\myself\Integrate`。

```powershell
docker compose --env-file docker/.env -p driving-exam-accept logs backend
```

### 8.7 访问

- `http://localhost:18083`：前端登录页（本次验收实际端口；模板默认 18081）；
- `http://localhost:18080`：后端；
- Docker MySQL：`localhost:13306`（只用于本机调试；应用容器通过 `mysql:3306` 连接）；
- 使用 Docker SQL 中的演示账号登录（仅演示，部署后必须改密码）。

### 8.8 停止

作用：停止并删除容器，但保留数据卷。  
改变：停止服务；**不删除数据卷**。  
目录：`D:\myself\Integrate`。

```powershell
docker compose --env-file docker/.env -p driving-exam-accept down
```

> **禁止**在未确认数据无需保留时执行 `docker compose --env-file docker/.env -p driving-exam-accept down -v`；它会删除 Docker 项目卷。不要把它作为普通清理步骤。

---

## 9. 验收证据保存

每项测试保存：

- 命令输出（尤其是 `BUILD SUCCESS`、测试汇总）；
- 接口响应（脱敏，不含 Token/密码）；
- 页面截图（管理员 9 页、教练写操作前后、Excel 导入结果）；
- `docker compose --env-file docker/.env -p driving-exam-accept ps`、后端日志（脱敏）；
- Excel 导入前后只读 SQL 的题目/选项数量对比。

不要保存真实密码、JWT Secret、DeepSeek/Embedding Key 或完整 Token。
