# 驾考智能辅导系统

基于大语言模型的驾考学习与辅导系统（综合课程设计），包含学员端、教练端、管理员端和 RAG 智能问答。

## 技术栈

- 后端：Spring Boot 3.2.5、Java 17、Maven 多模块、MyBatis-Plus 3.5.7、MySQL 8、JWT
- 前端：Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router、ECharts
- AI/RAG：DeepSeek API、SiliconFlow BGE-M3、Qdrant（默认随 Compose 启动）
- 部署：Docker Compose、MySQL、Spring Boot、Nginx

## 目录结构

```text
driving-exam/                 Maven 后端（common / pojo / server）
frontend/                     Vue 3 前端
sql/                          数据库结构、种子数据、预约状态脚本
docker/                       Docker 构建与 MySQL 初始化文件
docker-compose.yml            核心部署编排
doc/                          设计文档、工作日志、报告材料
```

## 本地开发启动

### 1. 数据库

使用 MySQL 8，创建数据库 `driving_exam`，按顺序执行：

```text
sql/_localhost-2026_10_08_17_26_16-dump.sql   -- 16 表结构（schema only）
sql/alter-appointment-status-comment.sql       -- 预约状态注释五值
sql/seed-question.sql                          -- 种子题目 1~18
sql/seed-question-extra.sql                    -- 种子题目 19~36
sql/seed-exam-paper.sql                        -- 演示试卷
```

> dump 是 schema-only，且包含 `DROP TABLE`；只能用于空库。演示账号可用
> `docker/mysql/init/90-demo-accounts.sql`（Docker）或在现有库自行创建。
> 请不要把测试准备 SQL 当作生产初始化脚本。

### 2. 后端

复制 `driving-exam/driving-exam-server/src/main/resources/application.yml.example`
为同目录 `application.yml`，通过环境变量提供：

- `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`
- `JWT_SECRET`（至少 32 字节）
- 可选：`DEEPSEEK_API_KEY`、`SILICONFLOW_API_KEY`、`QDRANT_API_KEY`
- 可选：`QDRANT_BASE_URL`、`QDRANT_COLLECTION`、`QDRANT_VECTOR_SIZE`（默认 `http://localhost:6333`、`driving_exam_kb_dev`、`1024`）

启动：

```text
cd driving-exam
mvn -pl driving-exam-server -am -DskipTests package
java -jar driving-exam-server/target/driving-exam-server-1.0.0.jar
```

或在 IDEA 中运行 `com.driving.exam.server.DrivingExamApplication`。

### 3. 前端

```text
cd frontend
npm install
npm run dev
```

默认访问 `http://localhost:5173`，Vite 将 `/api` 代理到 `http://localhost:8080`。

## Docker 核心部署（MySQL + 后端 + Nginx 前端 + Qdrant）

> 首版已于 2026-10-19 在用户本机使用 Compose 项目 `driving-exam-accept` 实际构建并启动，
> 前端、后端、MySQL 三个容器均达到 `healthy`；验收记录见 `doc/Day/Day27-工作日志.md` §十七。
> 所有命令必须统一使用 `--env-file docker/.env` 与项目名 `driving-exam-accept`，
> 以隔离默认项目和本机 MySQL 3306。以下命令可用于复现和后续维护。

1. 复制环境模板并修改：

```text
copy docker\.env.example docker\.env
```

`docker/.env.example` 已使用独立数据库名 `driving_exam_docker_accept` 和独立端口
（模板默认 MySQL 13306、后端 18080、前端 18081），避免与本机 MySQL/后端冲突。
本次实际验收的 `docker/.env` 使用前端端口 **18083**，以该文件为准。必须自行设置：

- `MYSQL_ROOT_PASSWORD`：MySQL 初始化/管理员维护；
- `APP_DB_PASSWORD`：应用运行时账号 `drv_app` 的密码；
- 至少 32 字节的 `JWT_SECRET`。

不要把 `docker/.env` 提交到 Git。MySQL root **不作为 Spring Boot 运行时账号**；
应用使用 `drv_app`，仅拥有目标数据库的 SELECT/INSERT/UPDATE/DELETE。

> `.dockerignore` 已排除本机 `driving-exam-server/src/main/resources/application.yml`，
> 且 `driving-exam-server/pom.xml` 已将 `application.yml` 从正式 JAR 资源中排除；
> 保留 `application.yml.example` 供 Dockerfile 复制。不要删除这些规则。
> 旧的 `target/driving-exam-server-1.0.0.jar` 可能包含修改前打包的真实配置，不能作为交付物；
> 下次正式构建必须使用 `mvn clean package`（清掉旧的 `target/classes`），并在构建后检查 JAR。

2. 只解析配置（不启动）：

```text
docker compose --env-file docker/.env -p driving-exam-accept config
```

3. 构建并启动：

```text
docker compose --env-file docker/.env -p driving-exam-accept build
docker compose --env-file docker/.env -p driving-exam-accept up -d
```

4. 查看状态与日志：

```text
docker compose --env-file docker/.env -p driving-exam-accept ps
```

`mysql`、`backend`、`frontend` 均应为 `healthy`；`qdrant` 应处于运行状态（其镜像没有内置健康检查，`ps` 显示 `Up` 即可）。

```text
docker compose --env-file docker/.env -p driving-exam-accept logs backend
```

5. 访问：

- 前端：`http://localhost:18083`（本次验收环境；由 `docker/.env` 的 `FRONTEND_PORT` 控制，模板默认 18081）
- 后端：`http://localhost:18080`
- Docker MySQL：`localhost:13306`（仅本机调试；应用容器通过服务名 `mysql:3306` 连接）

6. 首次初始化（重要）：

- 初始化 SQL 含 `DROP TABLE` 和固定 ID 的 `DELETE`，**只能用于独立、空的 Docker 数据卷**；
- 禁止手工对本机 `driving_exam` 执行，禁止在已有重要数据的 Docker 卷上重放；
- 本次验收数据库为 `driving_exam_docker_accept`，MySQL 数据卷为
  `driving-exam-accept_mysql-data`；宿主机原开发库 `driving_exam`（3306）不参与本次验收；
- Docker Compose 仅应在新卷首次启动时执行 `/docker-entrypoint-initdb.d`；
  本次验收使用独立命名卷；已有卷不会自动重跑初始化；
- `docker/mysql/init/10-app-user.sh` 在首次初始化时用 `APP_DB_USER`/`APP_DB_PASSWORD`
  创建 `drv_app@%` 并仅授予目标数据库 DML；不支持已有卷自动补建账号；
  如果数据卷已存在，需要单独的管理员操作并先确认；
- 演示账号 `student01 / coach01 / admin01 / student02` 由
  `docker/mysql/init/90-demo-accounts.sql` 创建；演示口令见该文件顶部注释，
  **shared/生产环境必须替换**；
- 不要执行 `docker compose --env-file docker/.env -p driving-exam-accept down -v`，除非确认要删除 Docker 项目卷。

7. 停止（保留命名卷）：

```text
docker compose --env-file docker/.env -p driving-exam-accept down
```

只重建某个服务（例如修改健康检查后的前端），不影响其他服务：

```text
docker compose --env-file docker/.env -p driving-exam-accept up -d --no-deps --force-recreate frontend
```

`stop`/`restart` 只影响当前项目容器；`down` 删除容器和网络但保留命名卷；
`down -v` 会删除该项目卷，禁止作为普通清理步骤。保留现有验收卷时，不要重建或清空数据库。

## RAG（Qdrant 默认启动）

Qdrant 随默认 Compose 启动，后端通过 `QDRANT_BASE_URL=http://qdrant:6333` 访问；
collection 默认为 `driving_exam_kb_dev`，向量维度为 `1024`。仍需要外部
`DEEPSEEK_API_KEY`、`SILICONFLOW_API_KEY` 才能完成真实回答和 Embedding。

首次部署或题库变更后，使用管理员账号调用一次知识入库接口（只写 Qdrant，不写业务库）：

```text
POST /api/admin/rag/ingest
Authorization: Bearer <ADMIN_TOKEN>
```

该接口从业务库只读装配题目解析和知识点描述，使用确定性 point ID 做 upsert：
重复执行会覆盖同一条目的旧向量，不会无限重复；不会删除或清空 collection。
如果业务库没有可入库文档，返回 `ingested=false` 且不触网；配置或外部服务失败返回 4006。
AI 问答在检索基础设施失败时返回 4005，不会伪装成普通回答；无检索命中则正常回答且不写引用。

## 测试与检查

后端：

```text
cd driving-exam
mvn -pl driving-exam-server -am test
```

前端：

```text
cd frontend
./node_modules/.bin/vue-tsc --noEmit
npm run build
```

接口测试集合位于 `doc/testjson/`；历史报告在 `doc/Test/`。历史结果不等于当前回归，
详见 `doc/Day/` 工作日志。

## 管理员端功能状态

代码已覆盖：数据概览、用户管理、绑定管理、知识点管理、题库管理（含 Excel 导入）、
试卷管理、预约查看、AI 问答统计、个人中心。Docker 镜像构建已成功，但管理员端
9 个菜单、Excel 导入等业务的真实联调仍不完整；部分高风险操作（用户删除、密码重置、
试卷删除）未提供，具体限制见页面与工作日志。

## 已知限制

- 管理端部分 CRUD 为课程设计范围的最小实现；未提供用户删除、密码重置、试卷删除。
- Excel 导入采用全量校验 + 事务写入；重复题干（同科目）拒绝，不覆盖已有题目。
- RAG 仅在开发集合/有限语料上验证；Hit@1/Hit@5/MRR 是检索指标，不是问答正确率。
- 历史测试报告不是当前回归结果；Docker 已完成首版部署、容器健康和三角色基础登录验收，
  但完整业务功能回归、Excel 导入和 AI/RAG 仍未验证，详见 `doc/Day/Day27-工作日志.md` §十七。
