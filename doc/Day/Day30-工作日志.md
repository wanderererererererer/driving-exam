# 【Day 30 工作日志】

## 一、日期

2026-10-20

## 二、本阶段目标

修复系统剩余功能排查报告中列出的第一优先级问题：让 AI/RAG 在默认项目部署配置下
具备完整、可验证的运行路径。范围限定为 AI/RAG 相关代码、Compose、配置说明和测试，
不修改模拟考试、预约、管理员业务逻辑，不改数据库结构，不执行 Docker 或业务写入。

## 三、原因与证据

### 1. Qdrant 默认部署缺失

修复前 `docker-compose.yml` 中 Qdrant 放在 `profiles: ["rag"]` 下，默认
`docker compose up -d` 只启动 mysql/backend/frontend；后端虽然已有
`QDRANT_BASE_URL` 配置，但默认没有 Qdrant 服务可连。

实际影响：AI 问答在检索阶段失败，用户看到的是通用错误；排查报告已将其列为 P1。

### 2. 已有入库组件但没有运行时入口

仓库中已有：

- `KnowledgeDocumentAssembler`：只读装配题目解析/知识点描述；
- `KnowledgeIngestService`：分块、Embedding、Qdrant upsert；
- `ChunkPointMapper`：确定性 point ID；
- `DevKbIngestIT`：手动集成测试入口。

但没有 Controller、定时任务或启动 Runner 调用 `KnowledgeIngestService`；
`DevKbIngestIT` 只能通过测试命令执行，不是正常部署路径。因此新增管理员手动入库接口，
复用现有组件，不另写一套分块/Embedding 逻辑。

### 3. 检索故障与无命中语义

- `KnowledgeRetriever.retrieve` 有命中时返回结果，成功无命中返回空列表；
- Embedding/Qdrant 异常原样传播；
- `AiChatServiceImpl` 在检索异常时原本不 catch，Qdrant 故障会落到通用 500，前端只能看到
  “系统异常”，与“知识库无匹配”不可区分。

本次修复保留“无命中 = 正常回答”的语义，将检索基础设施异常统一转换为 4005，
不把故障伪装成正常回答，也不在没有检索依据时生成引用。

## 四、修改文件

| 文件 | 修改目的 |
| --- | --- |
| `driving-exam/driving-exam-common/.../ErrorCode.java` | 新增 4005 `AI_RAG_UNAVAILABLE`、4006 `RAG_INGEST_FAILED`，区分检索故障和入库故障 |
| `driving-exam/driving-exam-server/.../ai/impl/AiChatServiceImpl.java` | 捕获检索路径的 BusinessException/IllegalArgumentException/IllegalStateException，记录服务端日志并返回 4005；无命中仍正常回答 |
| `driving-exam/driving-exam-server/.../controller/AiChatController.java` | 同步注释，说明 4005 和无命中语义 |
| `driving-exam/driving-exam-server/.../controller/AdminRagController.java` | 新增 `POST /api/admin/rag/ingest`，由 `/api/admin/**` 现有 ADMIN 权限保护 |
| `driving-exam/driving-exam-server/.../rag/ingest/KnowledgeBaseIngestService.java` | 编排装配 → 守卫 → ensureCollection → 现有入库服务，处理空文档和失败统计 |
| `driving-exam/driving-exam-server/.../rag/ingest/KnowledgeBaseIngestReport.java` | 入库接口返回统计（过滤计数、文档/分块/批次/写入点数），不返回题库正文 |
| `docker-compose.yml` | Qdrant 移出 `rag` profile，默认启动；backend 依赖 Qdrant；显式传入 Qdrant base-url/collection/vector-size |
| `docker/.env.example` | 说明 Qdrant 默认启动、入库入口和 Qdrant 配置项（不写入真实 Key） |
| `README.md` | 同步 Docker/Qdrant 启动方式和入库使用方法，删除“Qdrant 默认不启动”“无运行时入库接口”的旧说法 |
| `driving-exam/.../test/.../AiChatServiceImplTest.java` | 更新检索失败断言为 4005，新增配置异常仍严格失败的测试 |
| `driving-exam/.../test/.../KnowledgeBaseIngestServiceTest.java` | 新增入库编排测试：正常、空文档、错误集合、底层失败 |

### 关键行为变化

1. **默认 Compose 会启动 Qdrant**：`qdrant` 不再需要 `--profile rag`。
2. **知识入库有正式入口**：管理员可重复调用 `POST /api/admin/rag/ingest`。
3. **入库幂等**：`ChunkPointMapper` 的 point ID 由 `sourceType/sourceId/chunkIndex` 决定，
   重跑覆盖同一批点，不无限增加重复向量；不删除、不清空 collection。
4. **AI 故障语义明确**：
   - 有命中：注入检索上下文，正常回答并保存引用；
   - 无命中：正常回答，不注入上下文，`references_json` 为空；
   - 检索服务/Embedding/Qdrant/配置故障：返回 4005，不调用 DeepSeek、不落库。
5. **不修改任何数据库表、SQL 或业务数据**：入库只读业务库、写 Qdrant。

## 五、知识入库的实际使用方法

### 前置条件

1. Compose 使用 `--env-file docker/.env -p driving-exam-accept`，Qdrant 随默认 `up` 启动。
2. 配置 `SILICONFLOW_API_KEY`（Embedding）和 `DEEPSEEK_API_KEY`（问答）。
3. 后端已启动并通过健康检查。
4. 当前入库守卫只允许集合 `driving_exam_kb_dev`，默认 Compose/application 配置均一致。
   如果要切换正式集合，需要同时调整 `QDRANT_COLLECTION` 和守卫策略，本阶段未开放任意集合写入。

### 调用方式

先以管理员登录，获得 token（不要把 token 写入仓库或日志）：

```powershell
$adminToken = $env:ADMIN_TOKEN   # 由本地安全方式设置，不在代码/文档中保存
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:18080/api/admin/rag/ingest" `
  -Headers @{ Authorization = "Bearer $adminToken" }
```

如果使用前端反向代理端口，则地址为 `http://localhost:18083/api/admin/rag/ingest`；
以 `docker/.env` 的 `BACKEND_PORT`/`FRONTEND_PORT` 为准。

### 返回解释

- `ingested=false`：业务库没有可入库文档，未调用 Embedding/Qdrant；
- `ingested=true`：已执行入库；
- `questionReadCount/questionSkipped.../knowledgePoint...`：装配过滤统计；
- `documentCount/chunkCount/batchCount/writtenPointCount`：实际入库统计；
- 重跑时 `writtenPointCount` 是本次 upsert 点数，不等于 collection 总点数；确定性 point ID
  会覆盖旧点，所以 `points_count` 的变化可能小于本次 upsert 点数。

### 失败行为

- 集合/向量配置不符合守卫：4006，且在任何 Qdrant/Embedding 调用前失败；
- Embedding 或 Qdrant 失败：4006，错误消息保留已写入批次/点数说明；
- AI 问答检索失败：4005；无匹配仍正常回答，不写假引用。

## 六、测试与构建结果

### 已实际执行并通过

在临时 JDK 17 + Maven 3.9.9 环境执行：

```text
mvn -pl driving-exam-server -am test \
  -Dtest=AiChatServiceImplTest,KnowledgeBaseIngestServiceTest,KnowledgeRetrieverTest,KnowledgeIngestServiceTest,EmbeddingClientTest,QdrantClientTest,QdrantClientSearchTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：

- **81 tests, 0 failures, 0 errors, 0 skipped**
- `BUILD SUCCESS`

覆盖范围：

- AI 问答：有命中、无命中、检索 BusinessException、检索配置异常、限额、DeepSeek 异常；
- 知识检索：正常映射、空结果、参数/配置错误、Embedding/Qdrant 异常；
- 知识入库编排：正常入库、空文档不触网、错误集合被守卫拒绝、底层入库失败转 4006；
- 低层 Embedding/Qdrant 客户端：既有的 Mock 测试全部通过。

### 已实际执行并通过的构建

```text
mvn -pl driving-exam-server -am -DskipTests package
```

结果：`BUILD SUCCESS`，四模块构建成功。

前端：

```text
cd frontend
npm run type-check
```

结果：exit 0；本轮没有修改前端源码，保持现有 AI 问答调用兼容。

### 仅静态检查

- 使用 Python YAML 解析 `docker-compose.yml`，确认：
  - services 包含 `mysql/backend/frontend/qdrant`；
  - `qdrant.profiles` 为 null（默认启动）；
  - backend 同时依赖 mysql 与 qdrant；
  - `QDRANT_BASE_URL/QDRANT_COLLECTION/QDRANT_VECTOR_SIZE` 已配置。
- 未执行 `docker compose config`，因为当前环境没有 Docker CLI。

### 因环境限制未执行

- 未执行 Docker 镜像构建、`docker compose up`、Qdrant 启动、真实 collection 创建；
- 未执行真实 Embedding/DeepSeek 调用；
- 未执行知识入库 HTTP 接口；
- 未执行 `DevKbIngestIT`（需要 MySQL、Qdrant、SiliconFlow Key 和 `RAG_DEV_INGEST=true`）；
- 未执行浏览器 AI 问答验收。

这些不能写成已通过。

## 七、Docker 环境恢复后的最少验收步骤

```powershell
cd D:\myself\Integrate

# 1. 查看配置和现有容器，不删除卷
docker compose --env-file docker/.env -p driving-exam-accept config
docker compose --env-file docker/.env -p driving-exam-accept ps

# 2. 只重建/启动受影响服务（backend 与新增 qdrant）；不要 down -v/prune
docker compose --env-file docker/.env -p driving-exam-accept up -d --build backend qdrant

# 3. 确认 Qdrant 与 backend 状态
docker compose --env-file docker/.env -p driving-exam-accept ps
docker compose --env-file docker/.env -p driving-exam-accept logs --tail=100 backend
```

然后：

1. 以管理员身份调用 `POST /api/admin/rag/ingest`，确认返回 `ingested=true`、
   `documentCount>0`、`writtenPointCount>0`；
2. 直接只读查询 Qdrant（不要写入）：`GET http://localhost:<QDRANT_HTTP_PORT>/collections/driving_exam_kb_dev`
   确认 collection 存在且 `points_count` 符合预期；
3. 以学员登录，调用 `POST /api/student/ai/chat` 提一个题库相关问题，确认返回 200 且回答非空；
4. 提一个明显无关的问题，确认无命中时仍能正常回答，且不出现伪造引用；
5. 临时停止 Qdrant（只停止 Qdrant，不删除卷）或使用错误 Qdrant 地址验证 AI 返回 4005，
   确认没有返回伪造回答；验证后恢复；
6. 在浏览器打开 `/student/ai` 做一次真实对话，检查前端不会把 4005 显示为普通回答。

## 八、遗留问题与阻塞

1. **未执行真实运行验收**：当前环境没有 Docker CLI/socket、Qdrant、MySQL 或外部 AI Key，
   因此不能声称 AI/RAG 已经端到端通过。
2. **入库守卫限定开发集合**：当前只允许 `driving_exam_kb_dev`。这避免了误写正式集合，
   但如果要部署到正式 collection，需要单独设计正式的集合/权限配置。
3. **未做旧点清理**：入库是增量 upsert，题库删除的旧文档不会自动从 Qdrant 删除；
   本阶段未引入删除/重建策略，避免误删数据。
4. **未增加管理员前端页面**：入库入口目前是受 ADMIN 权限保护的 API，页面不是本阶段范围；
   管理员可以使用登录后的 token 调用，后续可在管理员端增加“知识库维护”页面。
5. **现有 collection 维度不一致未被主动探测**：`ensureCollection` 只在 404 时创建，
   如果已有集合维度/距离配置不匹配，实际 upsert 会返回 4006；验收时应检查 Qdrant 日志。
