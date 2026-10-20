# 【Day 22 工作日志】

## 一、日期

2026-10-17

## 二、本阶段目标

RAG 第一阶段：将已确认可用的**题库解析**（`question.analysis`）与**知识点描述**（`knowledge_point.description`）转换为**统一知识文档**，并实现**可测试的基础分块**逻辑。

**本阶段不做**：真实 Embedding 接入、Qdrant collection、相似度检索、Prompt 注入、修改 AI 问答行为、修改数据库结构。

## 三、实施前依据（以当前仓库源码/DDL 为准）

- `question` 字段：`id / subject_code / knowledge_point_id / question_type / content / answer / analysis(TEXT) / difficulty / media_url / status`。
- `knowledge_point` 字段：`id / subject_code / parent_id / name / level / sort_order / description(varchar 500)`。
- 语料现状：种子 SQL 约 36 题（多数含 `analysis`）+ `SUBJECT_1` 知识点含 `description`；**法规语料尚不存在**（不编造、不导入）。
- 外部条件：SiliconFlow BGE-M3、Qdrant 的**本机可用性仍未验证**；`ai_chat_record.references_json` 为后续引用存储候选（本阶段不改）。

## 四、新增文件与用途（模块 `driving-exam-server`，新包 `com.driving.exam.server.rag`）

| 文件 | 用途 |
|---|---|
| `rag/KnowledgeDocType.java` | 来源类型枚举：`QUESTION_ANALYSIS` / `KNOWLEDGE_POINT` |
| `rag/KnowledgeDocument.java` | 统一知识文档 record：`docId/sourceType/sourceId/subjectCode/title/text/metadata` |
| `rag/DocumentChunk.java` | 分块 record：`chunkId/docId/sourceType/sourceId/chunkIndex/text/metadata` |
| `rag/KnowledgeDocumentConverter.java` | `Question`/`KnowledgePoint` → 统一文档（`@Component`，无状态，可 `new` 单测） |
| `rag/DocumentChunker.java` | 基础分块（`@Component`，无状态，可 `new` 单测） |
| `test/.../rag/KnowledgeDocumentConverterTest.java` | 转换单测（4 项） |
| `test/.../rag/DocumentChunkerTest.java` | 分块单测（5 项） |

**未修改**任何既有业务代码、Controller/Service、Mapper、DTO/VO、`pom.xml`、配置、SQL、数据库结构。

## 五、文档结构与分块规则

- **docId 稳定且跨类型唯一**：`<sourceType>:<sourceId>`（如 `QUESTION_ANALYSIS:12`、`KNOWLEDGE_POINT:3`），避免题目与知识点 ID 重号冲突。
- **chunkId**：`<docId>#<chunkIndex>`（`chunkIndex` 从 0 起），稳定可追溯。
- **文本来源**：题目文本 = `analysis`；知识点文本 = `description`；均取真实字段，缺失则为空串（不编造）。
- **元数据**：题目含 `knowledgePointId/questionType/answer`；知识点含 `parentId/level`（均为真实字段）。
- **分块规则**：
  - 空/全空白文本 → **空列表**（不产出空块）；
  - 长度 ≤ `MAX_CHUNK_CHARS(=800)` → **单块**（短解析/描述不切碎）；
  - 超长 → 按上限切分，优先在句子/换行边界（`。！？；!?;\n`）断开，找不到边界则按上限硬切；
  - 各块是原文的**连续片段首尾相接、无缝无重叠** → “所有分块拼接 == 原文”（内容不丢失）；**本阶段不做 overlap**。
  - **字符数 ≠ token 数**：`MAX_CHUNK_CHARS` 是字符上限，**不是** BGE-M3 的 8192 token 上限；取值保守，避免把字符数当 token 数。

## 六、测试与编译

- **本机/编码环境**：沙箱**无 `mvn`/`javac`**，故**本阶段未运行编译或测试**；新增代码仅经**静态自检**（括号配平、实体 getter 与字段核对）。
- 供本机执行的命令（结果待回填，未通过前不得写成通过）：
  ```
  cd D:\myself\Integrate\driving-exam
  mvn -pl driving-exam-server -am test -Dtest=KnowledgeDocumentConverterTest,DocumentChunkerTest "-Dsurefire.failIfNoSpecifiedTests=false"
  ```
- 单测覆盖：题目/知识点转换、来源类型与原始 ID 保留、空文本、短文本单块、超长多块、拼接无丢失、chunkId 稳定性与跨来源区分。

## 七、已知限制与下一阶段依赖

1. **未接入 Embedding / Qdrant**：本阶段只产出“文档 + 分块”的内存结构，尚无向量与检索。
2. **语料规模小**（约 36 题 + 少量知识点），**缺法规语料**——检索价值受限，需后续确认法规来源。
3. **BGE-M3 可用性、Qdrant 本机 Docker 可用性未验证**——下一阶段（Embedding 接入 / 向量入库）的前置条件。
4. **字符≠token**：分块上限为字符口径，后续接入 BGE-M3 时需按真实 token 化再校准。
5. 尚未把“题库/知识点 → 文档”接入任何 Service 调用链（本阶段不做 DB 读取）。

## 八、工作区保护

- 新增 7 个文件（上文），均为 `test` 或 `main` 下**新包 `rag`**，不触碰既有文件。
- 既有未提交/未跟踪文件保持原样：`doc/Day/Day21-工作日志.md`（` M`）、`doc/testsql/coach-student-test-setup.sql`、`ExamFinalizerTest.java`、`ExamSettlementConcurrencyTest.java`、`master)`——**均未删除/修改/暂存**。
- 未执行数据库写、未调用真实 Embedding/大模型 API、未启动 Docker、未做 Git 暂存或提交。

---

## 九、R1 本机单元测试验证（追加记录）

> 本节为**追加**，§一～§八原文保留不改。

### 9.1 验证方式与结果

- **验证方式**：由用户在**本机 IntelliJ IDEA** 中运行（**不是 Maven 命令行**）。
- `KnowledgeDocumentConverterTest`：**全部测试通过**。
- `DocumentChunkerTest`：**全部测试通过**。

> 用户**未提供具体测试数量和耗时**，故本节**不记录、不编造**测试数与耗时；如需精确统计，请以本机 IDE/报告为准。

### 9.2 能证明 / 不能证明（务必如实区分）

- ✅ **已证明**：R1 的核心逻辑（题库解析/知识点 → 统一文档、基础分块）及**新增的两个单元测试**已在本机 **IntelliJ IDEA** 中**通过**。
- ❌ **未验证**：**整个 Maven 多模块构建是否成功**（本次未用 Maven 命令行执行；`-am` 全链路构建/打包未确认）。
- ❌ **未涉及**：**未接入 BGE-M3 或 Qdrant，未调用任何外部 API**；未做数据库/SQL 操作；未改变 AI 问答行为。

### 9.3 阶段状态

- R1（语料标准化与分块）**代码实现 + 本机单元测试**已完成。
- 下一阶段（R2：BGE-M3 Embedding 接入 / Qdrant 向量入库）**尚未开始**，其前置条件（SiliconFlow Key、Qdrant 本机可用性）仍未验证。

---

## 十、R2.4 真实集成验证：Embedding + Qdrant（2026-10-18 补充）

> 本节为**追加**记录（不改动 §一～§九）。§九末已把“R2：BGE-M3 Embedding 接入 / Qdrant 向量入库”列为下一阶段，本节记录其**最小真实集成验证**结果。

### 10.1 执行与结果（本机实测）

| 项 | 事实 |
|---|---|
| 执行时间 | 2026-10-18 13:58 |
| 执行命令 | `mvn -pl driving-exam-server -am test -Dtest=R24QdrantIntegrationIT "-Dsurefire.failIfNoSpecifiedTests=false"` |
| 工作目录 | `D:\myself\Integrate\driving-exam` |
| 测试文件 | `driving-exam-server/src/test/java/com/driving/exam/server/integration/qdrant/R24QdrantIntegrationIT.java` |
| 结果 | Tests run **1**，Failures **0**，Errors **0**，Skipped **0**；整体 **BUILD SUCCESS** |
| Surefire 报告 | `driving-exam-server/target/surefire-reports/…R24QdrantIntegrationIT.txt`（mtime 2026-10-18 13:58:17） |

### 10.2 有日志证据支持的实际链路

- **Embedding**：模型 `BAAI/bge-m3`；日志“Embedding 调用成功 耗时=585ms model=BAAI/bge-m3 count=1” → 成功、1 条；本次返回维度 **1024**（测试断言）。
- **Qdrant upsert**：日志“Qdrant upsert 成功 collection=driving_exam_kb_it points=1 耗时=43ms” → 向 `driving_exam_kb_it` 写入 **1** 个点。
- **写后回读**：测试通过（0 失败）；据测试断言，校验了回读结果的点 **ID**、payload（`testOnly/source/text/marker`）与向量维度 **1024**。（回读使用原始 `RestTemplate`，**无独立 HTTP 日志行**；其成功由“测试全绿 + 回读断言位于其后”推证。）

### 10.3 环境

- 本机 Qdrant **1.19.2**，REST 地址 `127.0.0.1:6333`；测试源码**显式固定**地址 `http://localhost:6333` 与集合 `driving_exam_kb_it`，并在写入前断言（不依赖默认配置）。

### 10.4 首次失败与修复（重要，避免误读）

- **首次执行失败**：测试在写入阶段收到 Qdrant HTTP 400，原因是测试点 ID 带 `r24-it-` 前缀，**不是合法 Qdrant point ID**（Qdrant 只接受 unsigned int 或 UUID）。
- **修复**：测试点 ID 改为**合法 UUID**（`UUID.randomUUID().toString()`），将 `r24-it` 标记移入 payload（并在回读中断言）；随后测试通过。
- **口径**：这是**测试夹具（测试用例）问题**，**不是**生产端 ID 生成逻辑的问题——生产端 `ChunkPointMapper` 用 `UUID.nameUUIDFromBytes(...).toString()`，生成的即为合法 UUID。

### 10.5 边界与遗留（如实区分事实 / 推断）

- **事实**：测试源码显式配置并使用**独立测试集合** `driving_exam_kb_it`（写入前断言地址/集合名/维度 1024/距离 Cosine/`points_count==0`）；不调用 `ensureCollection`、不调用业务入库服务、不访问数据库、不启动 Spring。
- **未独立验证**：**未**独立读取正式集合 `driving_exam_kb` 以证明其完全未受影响（仅能说明测试源码显式指向测试集合）。
- **非完整业务入库**：本次只验证“固定文本 → 向量化 → 写入测试集合 → 按 ID 回读”的**最小闭环**；**检索、提示词注入、`references_json`、真实知识文档批量入库均尚未验证**。
- **遗留数据**：测试集合中**可能残留 1 个测试点**（合法 UUID，payload 含 `marker=r24-it`、`source=R2.4-integration-test`），按要求**本次未清理**。

## 十一、RAG 正式问答链路真实联调（2026-10-18 晚补充：R3 检索 + T4 接入）

> 说明：§10.5 中"检索、提示词注入、`references_json` 尚未验证"为当日 14:08 时点的状态；此后 R3 完成检索能力（客户端层 + `KnowledgeRetriever`）、T4 完成问答接入。本节记录其后的**首次真实端到端联调**。

### 11.1 前置与执行（事实）

- **前置**：T1 配置收尾（本机 `application.yml` 含 `embedding`/`qdrant` 段，构建副本为 UTF-8，`Started DrivingExamApplication` 启动成功）；T4 将 `KnowledgeRetriever` 接入 `AiChatServiceImpl`（限额 → 检索 → 上下文 → DeepSeek → 落库）；离线单测 `RetrievalContextBuilderTest` 6/6、`AiChatServiceImplTest` 6/6 通过（本机 Surefire 报告 17:25）。
- **执行**：用户本机启动后端（Tomcat 8080），以学员账号登录取得 Token，调用 `POST /api/student/ai/chat`（`question=红灯亮时应当怎样通行`、`subjectCode=SUBJECT_1`、`sessionId` 为新建 UUID）。该次请求的 Qdrant/Embedding 客户端日志细节未留存。

### 11.2 落库记录事实（本机本次测试记录）

> `id = 6` 为**本机当前数据库的本条测试记录**，不是所有环境固定存在的 ID。

| 字段 | 值 |
|---|---|
| `user_id` | 1 |
| `subject_code` | `SUBJECT_1` |
| `model` | `deepseek-flash` |
| `prompt_tokens` / `completion_tokens` | 462 / 59 |
| `references_json` | 5 条引用（见下表） |
| `create_time` | 2026-10-18 18:14:33 |

| 序 | `sourceId` | `chunkId` | `score` |
|---|---|---|---|
| 1 | 2 | `QUESTION_ANALYSIS:2#0` | 0.8090747 |
| 2 | 1 | `QUESTION_ANALYSIS:1#0` | 0.76406825 |
| 3 | 20 | `QUESTION_ANALYSIS:20#0` | 0.7469356 |
| 4 | 3 | `QUESTION_ANALYSIS:3#0` | 0.7454778 |
| 5 | 14 | `QUESTION_ANALYSIS:14#0` | 0.7454778 |

- 5 条引用 `subjectCode` 均为 `SUBJECT_1`、`truncated` 均为 `false`；均为单块（`#0`）。与"解析文本 < 800 字符分块上限、预算 1200 未触发截断"的规则一致（依据分块/预算规则推断）。
- `model=deepseek-flash` 是**服务端响应返回的实际模型名**；请求使用的配置别名是 `deepseek-chat`（"落库存返回值"的口径见 Day12 日志与 `doc/deepseek-integration.md`，不是配置被覆盖）。

### 11.3 回答与来源一致性（人工核对）

- **真实回答**（原文）："红灯亮时，车辆应在停止线外停车等候，禁止通行。但右转弯车辆在不妨碍被放行的车辆、行人通行的情况下，可以通行。（注意：右转能否通行还要看路口是否有禁止右转信号或标志，以实际标志标线为准。）"
- 与来源对照：**ID 1**（"红灯亮时禁止通行，车辆应在停止线外停车等候。"）与 **ID 20**（"红灯亮时，右转弯车辆在不妨碍被放行的车辆、行人通行的情况下可以通行。"）与回答的核心规则**一致**；ID 2（绿灯规则）、ID 3/14（黄灯规则）未在回答中作为主答案出现。
- 回答末尾"右转还需看路口禁止右转标志"一句**不在 5 条来源中**，属模型自身补充（来源无法佐证，仅记录观察）。

### 11.4 单样本观察（不是检索质量结论）

1. **Top-1 不是最相关项**：排在第一位（0.8090747）的是绿灯规则（ID 2），与问题意图最匹配的红灯一般规则（ID 1，0.76406825）列第 2。
2. **语料重复导致重复召回**：ID 3 与 ID 14 的解析文本逐字相同（均为黄灯规则），因而得分相同（0.7454778），同时占用 TopK 两个名额——5 条引用中实际不同的资料只有 4 条。
3. 以上均为**单次、单查询**的观察；**不能**据此推断整体排序质量，也不能推断"top-1 偏差"是否普遍。

### 11.5 未验证事项与表述边界

- **未做系统性检索质量评估**：无多样查询集、无人工相关性标注、无 recall@k / MRR 等指标；未评估相似度阈值（当前未启用）。
- **不能声称**"检索质量已达标""召回率已验证""引用完全正确"；当前能准确声称的是"**引用链路真实跑通，且本单样本的回答与关键来源（ID 1/20）一致**"。
- 历史问答的引用读取、API 引用字段与前端展示**仍未实现**（不在 T4 范围）。
- 本次未留存请求级 Qdrant/Embedding 日志；"命中开发集合 `driving_exam_kb_dev`"依据运行配置与引用来源一致性作出，**属推断**。
- 本次测试记录（`id=6`）未清理，保留与否待决定。

### 11.6 工作区与约束（本节）

- 本节仅为文档追加；未修改代码、配置、数据库、种子数据或 Qdrant 数据；未发起新的 AI 请求。


## 十二、R4 系统性检索质量评估基线（2026-10-18，方案 A：从 Surefire XML 归档）

### 12.1 来源与执行事实

- **数据来源**：`driving-exam-server/target/surefire-reports/TEST-com.driving.exam.server.integration.rag.R4RetrievalEvalIT.xml` 的 `<system-out>`（该报告生成于 2026-10-18 18:49:30）。本节为**对已有运行结果的归档，未重跑评估**。
- **Surefire 事实**：tests=1、failures=0、errors=0、skipped=0、time=5.442s。
- **评估集**：冻结版 R4 v1（`driving-exam-server/src/test/resources/rag-eval/eval-set-v1.json`，sha256 前缀 `33027bab2ea6`），27 条 = 26 gold + 1 负样本。
- **评估范围（重要）**：当前 `SUBJECT_1` 语料 + 开发集合 `driving_exam_kb_dev`（评估时 points_count=39）的**单次基线**；**不构成**"全系统检索质量已达标/召回率已验证"的结论；不预设通过线。本次 ERROR=0、空结果=0。

运行头两行输出（原样）：

```
集合核验通过：collection=driving_exam_kb_dev status=green size=1024 distance=Cosine points_count=39
评估集加载：total=27 gold=26 negative=1
```

### 12.2 逐条结果（E01–E27，含 Top5 来源 ID/分数与 DUP 重复标记）

> 每行格式：`[样本] 类别 | query | gold | #排名 id=来源ID score=分数 [DUP]`。E27 为负样本：先输出返回内容观察行，再输出汇总观察行。

```
[E01] 信号灯 | query=红灯亮了应该怎么走 | gold=[1] | results: #1 id=2 score=0.7643 #2 id=1 score=0.7310 #3 id=14 score=0.7221 #4 id=3 score=0.7221 DUP #5 id=31 score=0.7172
[E02] 信号灯 | query=路口黄灯一直闪，还能通过吗 | gold=[21] | results: #1 id=3 score=0.7737 #2 id=14 score=0.7737 DUP #3 id=21 score=0.7384 #4 id=30 score=0.7144 #5 id=2 score=0.7105
[E03] 信号灯 | query=黄灯亮时车已经越过停止线怎么办 | gold=[3, 14, 30] | results: #1 id=3 score=0.8687 #2 id=14 score=0.8687 DUP #3 id=30 score=0.8102 #4 id=1 score=0.7556 #5 id=2 score=0.7220
[E04] 信号灯 | query=红灯时右转可以吗 | gold=[20] | results: #1 id=15 score=0.7716 #2 id=20 score=0.7540 #3 id=2 score=0.7181 #4 id=4 score=0.6662 #5 id=1 score=0.6563
[E05] 信号灯 | query=交通信号灯有哪几种颜色，各表示什么 | gold=[25] | results: #1 id=2 score=0.7910 #2 id=25 score=0.7808 #3 id=19 score=0.6788 #4 id=1 score=0.6526 #5 id=22 score=0.6504
[E06] 信号灯 | query=红色叉形灯亮起时本车道能不能走 | gold=[4] | results: #1 id=4 score=0.8164 #2 id=2 score=0.7176 #3 id=14 score=0.7157 #4 id=3 score=0.7157 DUP #5 id=20 score=0.6885
[E07] 信号灯 | query=绿色箭头灯指示的方向可以通行吗 | gold=[31] | results: #1 id=31 score=0.8523 #2 id=2 score=0.7350 #3 id=4 score=0.7017 #4 id=3 score=0.6743 #5 id=14 score=0.6743 DUP
[E08] 标志标线 | query=警告标志一般是什么形状 | gold=[5] | results: #1 id=5 score=0.7766 #2 id=3 score=0.7115 #3 id=10 score=0.7011 #4 id=26 score=0.6392 #5 id=22 score=0.6305
[E09] 标志标线 | query=禁令标志是什么颜色 | gold=[6] | results: #1 id=6 score=0.6911 #2 id=3 score=0.6674 #3 id=10 score=0.6112 #4 id=26 score=0.5591 #5 id=4 score=0.5511
[E10] 标志标线 | query=蓝底白图案的指示标志是什么意思 | gold=[22] | results: #1 id=22 score=0.7591 #2 id=6 score=0.5912 #3 id=3 score=0.5824 #4 id=2 score=0.5752 #5 id=5 score=0.5647
[E11] 标志标线 | query=道路中间的黄色虚线可以跨越超车吗 | gold=[23] | results: #1 id=23 score=0.7740 #2 id=32 score=0.7606 #3 id=33 score=0.6941 #4 id=3 score=0.6813 #5 id=14 score=0.6813 DUP
[E12] 标志标线 | query=黄色实线能不能压 | gold=[32] | results: #1 id=32 score=0.6784 #2 id=23 score=0.6013 #3 id=14 score=0.5298 #4 id=3 score=0.5298 DUP #5 id=2 score=0.4911
[E13] 标志标线 | query=白色虚线表示什么 | gold=[33] | results: #1 id=33 score=0.7287 #2 id=23 score=0.5524 #3 id=32 score=0.4848 #4 id=22 score=0.4598 #5 id=2 score=0.4521
[E14] 安全常识 | query=开车系安全带有什么作用 | gold=[7] | results: #1 id=7 score=0.7554 #2 id=13 score=0.6967 #3 id=4 score=0.6179 #4 id=12 score=0.5427 #5 id=16 score=0.5421
[E15] 安全常识 | query=开车时打电话算违法吗 | gold=[8, 16] | results: #1 id=16 score=0.8845 #2 id=8 score=0.8103 #3 id=35 score=0.5985 #4 id=29 score=0.5802 #5 id=13 score=0.5724
[E16] 安全常识 | query=雨天开车要注意什么 | gold=[9] | results: #1 id=9 score=0.7443 #2 id=11 score=0.6329 #3 id=27 score=0.6196 #4 id=35 score=0.5951 #5 id=2 score=0.5753
[E17] 安全常识 | query=高速上能见度低时怎么限速 | gold=[24] | results: #1 id=24 score=0.7085 #2 id=2 score=0.6178 #3 id=4 score=0.5973 #4 id=9 score=0.5945 #5 id=17 score=0.5820
[E18] 安全常识 | query=开车感觉疲劳怎么办 | gold=[18, 27] | results: #1 id=27 score=0.7147 #2 id=18 score=0.6829 #3 id=9 score=0.5932 #4 id=12 score=0.5666 #5 id=11 score=0.5641
[E19] 安全常识 | query=夜间行车应该注意什么 | gold=[27] | results: #1 id=27 score=0.7920 #2 id=11 score=0.6266 #3 id=9 score=0.6124 #4 id=2 score=0.5987 #5 id=12 score=0.5964
[E20] 安全常识 | query=前车排队时能借道超车吗 | gold=[17] | results: #1 id=17 score=0.8176 #2 id=11 score=0.5978 #3 id=2 score=0.5931 #4 id=3 score=0.5739 #5 id=14 score=0.5739 DUP
[E21] 安全常识 | query=向车窗外扔东西违法吗 | gold=[35] | results: #1 id=35 score=0.7587 #2 id=16 score=0.5907 #3 id=8 score=0.5575 #4 id=15 score=0.5504 #5 id=2 score=0.5345
[E22] 安全常识 | query=倒车时要注意什么 | gold=[36] | results: #1 id=36 score=0.7251 #2 id=11 score=0.5997 #3 id=17 score=0.5865 #4 id=27 score=0.5842 #5 id=34 score=0.5720
[E23] 交通信号总概念 | query=交通信号都包括哪些内容 | gold=[19] | results: #1 id=19 score=0.7935 #2 id=25 score=0.7375 #3 id=29 score=0.6991 #4 id=1 score=0.6768 #5 id=2 score=0.6736
[E24] 交叉路口通行与让行 | query=没有交通信号灯控制的路口怎样通行 | gold=[11, 34] | results: #1 id=34 score=0.7145 #2 id=2 score=0.6803 #3 id=4 score=0.6528 #4 id=31 score=0.6394 #5 id=3 score=0.6296
[E25] 交叉路口通行与让行 | query=无控制路口要让哪边车先行 | gold=[11, 34] | results: #1 id=34 score=0.8110 #2 id=11 score=0.6831 #3 id=2 score=0.6524 #4 id=17 score=0.6451 #5 id=4 score=0.6198
[E26] 多来源组合 | query=红灯亮时应当怎样通行 | gold=[1, 20] | results: #1 id=2 score=0.8091 #2 id=1 score=0.7641 #3 id=20 score=0.7469 #4 id=3 score=0.7455 #5 id=14 score=0.7455 DUP
[E27] 负样本 | query=推荐一款好用的行车记录仪 | gold=[] | results: #1 id=16 score=0.5088 #2 id=2 score=0.5008 #3 id=9 score=0.4942 #4 id=27 score=0.4910 #5 id=4 score=0.4774
[E27] 负样本观察：returned=5 scores=[0.5088, 0.5008, 0.4942, 0.4910, 0.4774]
```

### 12.3 总体指标与分类统计（原样转录）

```
===== 汇总 =====
样本总数=27 gold样本=26 负样本=1
查询成功=27 查询失败(ERROR)=0（ERROR 不计入命中指标分母，与未命中严格区分）
gold 成功样本 M=26（其中空结果=0，返回 1~4 条=0）
Hit@1=21/26=0.808
Hit@5=26/26=1.000
MRR=0.8974（分母 M=26）
内容命中@1=21/26=0.808；内容命中@5=26/26=1.000
重复（微平均）：重复条目=9 / 返回条目=130 = 0.069
重复（查询级）：出现重复的查询=9 / 返回≥2条的查询=26
===== 分类统计（gold 样本） =====
信号灯：total=7 success=7 hit@1=3 hit@5=7 MRR=0.6905
标志标线：total=6 success=6 hit@1=6 hit@5=6 MRR=1.0000
安全常识：total=9 success=9 hit@1=9 hit@5=9 MRR=1.0000
交通信号总概念：total=1 success=1 hit@1=1 hit@5=1 MRR=1.0000
交叉路口通行与让行：total=2 success=2 hit@1=2 hit@5=2 MRR=1.0000
多来源组合：total=1 success=1 hit@1=0 hit@5=1 MRR=0.5000
R4RetrievalEvalIT 完成（本输出为评估基线，不预设通过线）。
```

### 12.4 口径与边界（如实区分）

- 主指标按**来源 ID**计算（Hit@1 / Hit@5 / MRR，分母 M=gold 且查询成功数）；"内容命中"按解析原文比对；重复率含微平均与查询级两个口径；负样本（E27）仅观察、不进入任何命中分母。
- **单次基线观察（非质量结论）**：Hit@1 失分集中在"信号灯"（3/7）与"多来源组合"（0/1）——E26 的 top1 为绿灯规则（与 §十一 真实测试现象一致）；重复召回主要由 ID 3/14 文本逐字重复造成（9/26 查询出现重复，微平均 6.9%）。以上为单次评估的观察，不据此推断普适排序质量或因果。
- 原始 `system-out` 中还包含 Embedding/Qdrant 客户端 INFO 日志；本节仅收录评估输出行，未逐行收录客户端日志。原 XML 位于 `target/`，`mvn clean` 会删除——如需长期保留原始全文，应另行归档。
- 本节仅为文档追加：未修改代码、配置、数据库、种子数据或 Qdrant 数据；未发起任何外部请求。


## 十三、Qdrant 开发集合指定 9 点只读一致性核验（2026-10-18 收尾补充）

### 13.1 目的与方式

- **目的**：核对 `driving_exam_kb_dev` 中与 R4 E01–E07 相关的 9 个来源点，其 payload 文本是否与种子 SQL 的 `question.analysis` 一致。
- **方式（只读）**：`POST /collections/driving_exam_kb_dev/points`（`ids` 指定 9 点，`with_payload=true`、`with_vector=false`，不取向量）；pointId 由 `ChunkPointMapper` 的确定性规则生成（`UUID.nameUUIDFromBytes("QUESTION_ANALYSIS" + \u001F + sourceId + \u001F + 0)`），映射经离线自检：`QUESTION_ANALYSIS:1#0` 的计算值与历史运行日志中的 `a59fc217-8e4e-3c1c-942a-bf3dadcf8ffb` 一致；HTTP 响应按**原始字节 UTF-8 解码**（解决 PowerShell 中文乱码）。连接或集合状态失败即停止，不重试。

### 13.2 核验结果（事实）

- 指定 9 点 **9/9 回读成功**；实际 `sourceId` 与预期映射完全一致（1 / 2 / 3 / 14 / 15 / 20 / 21 / 25 / 30）；`sourceType=QUESTION_ANALYSIS`、`chunkId=QUESTION_ANALYSIS:<id>#0`、`subjectCode=SUBJECT_1` 均符合预期。
- 9 条 `payload.text` 与种子 SQL 对应 `question.analysis` **逐字一致**（离线脚本比对，含标点；无截断、无空白/换行差异）。
- **ID 3 与 ID 14 的 payload 文本逐字相同**——此前基于已保存检索结果"重复召回"的判断，现获得库内文本证据。
- **ID 15 在库内确为概括性文本**："符合《道路交通安全法实施条例》关于右转的规定。"（不含具体规则解释）。
- 这 9 个指定来源**未发现种子 SQL 与 Qdrant payload 漂移**。

### 13.3 边界（不得外推）

- 本次仅核验 **9/39 个 point**；**不能声称全量无漂移**，也不能推广到其余评估样本的候选文本。
- 核验时集合信息为 `green / size=1024 / distance=Cosine / points_count=39`（集合基本信息在 §十二 12.1 已记录，此处不重复展开）。
- 本核验为**只读**操作：未修改代码、Qdrant 数据、数据库或任何文件（本节本身为文档追加）。
- 与 §十二 R4 指标的关系：本节只支持"E01/E04/E05 分析所依赖的 9 条文本确实存在于库中且与种子一致"以及"ID 3/14 库内重复"的事实判断；**不改变**已归档的基线指标，也不构成对检索质量或全量语料一致性的结论。
