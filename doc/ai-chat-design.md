# 智能问答模块设计（Day 13）

> 目标：学员 AI 问答闭环 —— 提问 → 调 DeepSeek → 落库 → 返回；含每日限额、历史查询、删除
> 起点：Day 12 已产出 `DeepSeekClient`（`chat(String)` / `chat(List<ChatMessage>)` → `ChatResult`）
> 归档：`doc/ai-chat-design.md`

---

## 〇、已确认的 6 个决策

| # | 决策 | 结论 |
|---|---|---|
| ① | 每日限额 | **做，20 次/天**，Service 层强制（不靠前端） |
| ② | `sessionId` | **前端 `crypto.randomUUID()` 生成，存 localStorage**（刷新不变） |
| ③ | Prompt | 固定 system prompt + 注入科目上下文 |
| ④ | `AiAnswerVO` | `answer` / `recordId` / `model` / `promptTokens` / `completionTokens` / `createTime` |
| ⑤ | 清空全部历史 | **不做**，接口保持 3 个 |
| ⑥ | 前端 | 基础版 + **前端打字机效果**（非真流式） |

---

## 一、三个接口的详细设计

> ⚠️ **本节已被 §十『会话管理设计』取代**：历史查询由"按记录分页"改为"按会话组织"，
> 接口变为 `GET /sessions`、`GET /sessions/{sessionId}`、`DELETE /sessions/{sessionId}`。
> 本节保留作为演进记录。

### 1.1 `POST /api/student/ai/chat` 提问

**入参**（`AiChatDTO`）：

| 字段 | 必填 | 校验 | 说明 |
|---|---|---|---|
| `question` | ✅ | `@NotBlank` + **`@Size(max = 500)`** | 用户问题。**限长是为了控成本**——TEXT 列能存 64KB，但每多一个字都要付 token |
| `subjectCode` | ❌ | — | 用于注入 Prompt 上下文；不传即通用 |
| `sessionId` | ❌ | — | 前端 UUID；不传则落库为 NULL |

**处理流程**：

```
1. 每日限额检查（Service 层）
     SELECT COUNT(*) FROM ai_chat_record
      WHERE user_id = ? AND create_time >= 今天 0 点
     若 >= 20 → throw BusinessException(AI_QUOTA_EXCEEDED)   // 4003
   ⚠️ "今天 0 点" 用 LocalDate.now().atStartOfDay()——**依赖 JVM 时区**，详见 data-conventions.md §10.3
2. 拼 messages：SystemPromptBuilder.build(subjectCode) → ChatMessage.system(...)
                + ChatMessage.user(question)
3. ChatResult r = deepSeekClient.chat(messages)   // 失败会抛 4001/4002
4. 落库 ai_chat_record（拿到自增 id）
5. 返回 AiAnswerVO
```

**返回** `AiAnswerVO`：`answer` / `recordId` / `model` / `promptTokens` / `completionTokens` / `createTime`

**错误码**：`1001` 参数校验 · `4001` 调用失败 · `4002` 超时 · **`4003` 超限**

### 1.2 `GET /api/student/ai/history` 历史记录

**入参**：`sessionId?`、`pageNum = 1`、`pageSize = 10`（**后端强制上限 20**，防止前端传大值一次返回几百 KB）

**查询**：`WHERE user_id = ? [AND session_id = ?]`
**排序**：`ORDER BY create_time DESC, id DESC`（次级键 `id` 保证分页稳定，与错题本一致）

**返回**：`PageResult<AiRecordVO>`

> ⚠️ **列表必须返回完整 `answer`**——因为**只有 3 个接口、没有"详情"接口**。
> 代价是单页数据量偏大（`answer` 是 MEDIUMTEXT，10 条可能几十 KB），可接受；前端展示时自行截断。

### 1.3 `DELETE /api/student/ai/history/{id}` 删除一条

**SQL**：`DELETE FROM ai_chat_record WHERE id = ? AND user_id = ?`

- `user_id` 参与 WHERE 做**越权保护**
- 影响行数 = 0 → 抛 **`AI_RECORD_NOT_FOUND(4004)`**（需新增，见 1.4）
- **"记录不存在"与"不是我的"返回完全相同的响应（都是 4004）**，**不单独返回 403**——
  若"别人的记录"返 403、"不存在"返 404，攻击者就能据此**探测出哪些 id 真实存在**（存在性泄露）

### 1.4 需要新增的错误码

| 码 | 常量 | 文案 |
|---|---|---|
| **4004** | `AI_RECORD_NOT_FOUND` | 问答记录不存在 |

放在 AI 段（40xx），与 4001/4002/4003 同段。

---

## 二、DTO / VO 结构（均放 `pojo` 模块）

这些都是**业务契约**（会被 Controller 接收/返回），所以放 `pojo`，**不是** `server/integration/`（那是 DeepSeek 协议结构）。

| 类 | 位置 | 字段 |
|---|---|---|
| `AiChatRequestDTO` | `pojo/dto` | `question`(@NotBlank, @Size(max=500))、`subjectCode`、`sessionId` |
| `AiAnswerVO` | `pojo/vo` | `answer`、`recordId`、`model`、`promptTokens`、`completionTokens`、`createTime` |
| `AiRecordVO` | `pojo/vo` | `id`、`question`、`answer`、`subjectCode`、**`sessionId`**、`model`、`promptTokens`、`completionTokens`、`createTime` |

> `AiRecordVO` 多带了 `sessionId`（你只要求了前几项）：历史抽屉将来若要**按会话分组**（毕业设计升级点），前端必须能拿到它。现在返回，零成本。

---

## 三、`SystemPromptBuilder` 设计

**位置**：`com.driving.exam.server.ai.SystemPromptBuilder`（**新包** `server.ai`）

> 为什么不放 `integration/deepseek`：**提示词是业务**（驾考辅导的角色设定），
> 将来换模型供应商、甚至做 RAG，这段提示词仍然沿用。它不属于"DeepSeek 协议适配层"。

> 📦 **AI 模块的包结构（与既有服务分层并存，属有意设计）**：本模块自成一体——`server.ai` 放业务
> （`AiChatService` / `AiChatServiceImpl` / `SystemPromptBuilder`），`server.integration.deepseek` 放模型集成
> （`DeepSeekClient` / 协议 DTO），**与项目既有的 `server.service` / `server.service.impl` 分层并存**。
> 原因是 AI 有 prompt 工程、模型集成等独有内容，独立成包便于将来 RAG 升级时集中管理。
> 因此看到"两套服务分层"是有意为之，不是遗漏。

**形态**：`@Component`，方法 `String buildSystemPrompt(String subjectCode)`

**提示词内容**（按你给的）：

```
你是驾考辅导助手，专门帮助学员准备驾照考试。
规则：
1. 只回答与驾考相关的问题，与驾考无关的礼貌拒绝
2. 回答要简洁准确，控制在 200 字以内
3. 不确定的内容明确说"我不确定"，不要编造答案
4. 如果题目涉及具体法规，优先引用最新规定
```

**科目上下文注入**：`subjectCode` 非空时，在末尾追加一行：

```
该学员正在准备科目一考试，回答时请侧重科目一的知识点。
```

**科目名映射**（内部 `Map<String, String>`）：

| `subject_code` | 名称 |
|---|---|
| `SUBJECT_1` / `SUBJECT_2` / `SUBJECT_3` / `SUBJECT_4` | 科目一 / 二 / 三 / 四 |
| `COMMON` 或 null / 未知 | 不注入上下文 |

> **一个待确认点**：科目名映射目前**只有这一处用**，所以放在 Builder 内部；
> 但将来 `student_subject_status` 相关接口也会用到中文科目名——那时应抽成公共枚举/工具类。**先不抽**。

---

## 四、落库字段与时机

### 4.1 字段映射

| `ai_chat_record` 列 | 来源 |
|---|---|
| `user_id` | JWT（`@RequestAttribute`） |
| `session_id` | `AiChatDTO.sessionId`（前端 UUID，可空） |
| `subject_code` | `AiChatDTO.subjectCode` |
| `question` | `AiChatDTO.question` |
| `answer` | `ChatResult.content` |
| `references_json` | **第一版留空**（RAG 预留） |
| `model` | `ChatResult.model`（**服务端返回的实际模型**，如 `deepseek-flash`） |
| `prompt_tokens` | `ChatResult.promptTokens` |
| `completion_tokens` | `ChatResult.completionTokens` |
| `create_time` | 数据库默认 |

### 4.2 时机：**先调 AI，成功后再落库**

AI **失败就整个失败**，不落库——落一条没有回答的记录对用户毫无价值，还会污染统计（"今日调用次数"会被失败的调用虚增）。

### 4.3 **不加 `@Transactional`**（有意为之）

| 理由 | 说明 |
|---|---|
| **长事务反模式** | AI 调用耗时 **1~5 秒**，若包进事务，会**长时间占用一个数据库连接**。并发一高就是连接池耗尽。**HTTP 调用绝不该在事务内** |
| 本就无需事务 | 只写**一张表**、且是单个 `insert`；单条 SQL 自身原子 |

> 代价：**限额检查与落库之间存在竞态**（并发两次可能都通过检查）。
> 课设是单用户场景，可接受；若要做严谨，应在限额检查上做原子计数或加唯一约束。**记录在案，不处理。**

### 4.4 实体注意点（前置缺失！）

`AiChatRecord` 实体**尚未创建**（同 Day 9 "实体没建"那类问题）。按既有约定：

- 它是**流水表** → **不加 `@TableLogic`**、**无 `update_time`**
- 只有 `create_time` → `@TableField(fill = FieldFill.INSERT)`
- **删除是物理删除**（无 `deleted` 列）

---

## 五、前端页面结构

> ⚠️ 历史抽屉已由"记录列表"改为"会话列表"（点击会话回显其全部消息），详见 §十。

**路由**：`/student/ai`（`StudentLayout` 的菜单里已有"智能问答"入口，指向 `/student/ai`，加路由即可对上）

```
views/student/AiChatView.vue（容器）
├── components/ai/ChatMessageList.vue   消息列表（问/答气泡；答案支持打字机）
├── components/ai/ChatInput.vue         输入框 + 发送按钮（发送中禁用）
└── components/ai/HistoryDrawer.vue     历史抽屉（分页 + 单条删除）

store/aiChat.ts        当前会话消息、发送状态、打字机进度
utils/session.ts       getSessionId()：crypto.randomUUID() + localStorage
api/ai.ts              3 个接口
```

### 5.1 打字机效果（**不是真流式**）

- 拿到**完整回答**后，每 **30ms** 显示一个字
- 显示期间**禁用发送按钮**（防止并发提问）
- **点击回答区域 → 立即显示全文**（跳过动画，避免用户等）
- ⚠️ **定时器必须在 `onUnmounted` 清除** —— Day 11 的 600ms 计时器教训（双跳 bug）

> **为什么优先做这个**：零成本（前端定时器，不增加后端与 API 成本），体验接近流式，**答辩时是加分项**；
> 而真流式要改后端为 SSE + 前端 EventSource，两端约 3 倍代码量（见 `doc/deepseek-integration.md` §七）。

### 5.2 历史抽屉

- 打开时拉第一页；分页加载
- 每条显示：问题（截断）+ 时间 + 删除按钮
- 点击某条 → **把该问答回填到消息列表**（便于查看完整内容）
- 删除后刷新列表

### 5.3 状态划分

| 状态 | 位置 | 理由 |
|---|---|---|
| 当前会话消息、发送中、打字机进度 | **`store/aiChat.ts`** | `ChatMessageList` 与 `ChatInput` 都要读 |
| 历史列表、分页 | `HistoryDrawer` 本地 | 单组件数据 |
| `sessionId` | `utils/session.ts`（localStorage） | 跨页面/刷新保持 |

> `sessionId` **不进 Pinia**——它的生命周期比页面长（存在 localStorage），和登录态同一个模式。

---

## 六、实现顺序（13.1 ~ 13.8）

```
13.1  pojo：AiChatRecord 实体（★ 前置缺失，必须最先）
13.2  pojo：AiChatDTO / AiAnswerVO / AiRecordVO
13.3  AiChatRecordMapper + ErrorCode 加 AI_RECORD_NOT_FOUND(4004)
13.4  server.ai：SystemPromptBuilder
13.5  AiChatService + Impl（限额 → 拼 prompt → 调用 → 落库；★ 不加事务）
13.6  AiChatController（3 接口）
13.7  前端：api/ai.ts + utils/session.ts + store/aiChat.ts
13.8  前端：AiChatView + 3 个子组件 + 路由 + 联调
```

**13.1~13.6 是后端（每步可 `mvn install` 验证）；13.7~13.8 是前端（每步 `vue-tsc`）。**
真正的前后端联调在 **13.8**。

---

## 七、需要你确认的 3 个点

| # | 问题 | 我的建议 |
|---|---|---|
| ① | `question` 加 **`@Size(max=500)`** 限长 | **加**。既是防刷（每字都花钱），也是输入规范。500 字足够描述一道题的疑问 |
| ② | 每日限额的**并发竞态**（先查后写） | **不处理**（课设单用户），但写进文档备查 |
| ③ | `AiRecordVO` 是否带上 `sessionId` | **带上**（为毕业设计的"按会话分组"预留，零成本） |

---

## 八、其他提醒（已内化到设计里）

- ✅ **限额在 Service 层**，不只在前端拦（前端可绕过）
- ✅ 历史查询**按 `user_id` 过滤** + `create_time` 倒序
- ✅ 删除时 **`user_id` 参与 WHERE**（越权保护）
- ✅ AI 失败**不落库**

---

## 九、RAG 阶段的 `references_json` 改造方式

当前 `references_json` **第一版留空**，仅作 RAG 预留。将来接入 RAG（检索增强——把题库 / 法规片段作为"引用来源"随回答一并返回）时，**按下述做法即可，无需改动数据库，也切勿在实体上加 `JacksonTypeHandler`**：

1. **`references_json` 字段保持 `String`**，数据库 `JSON` 列类型**不动**。
   MySQL Connector/J 对 `JSON` 列与 Java `String` 是**原生互转**的（读 `getString` 拿到 JSON 文本，写时传入合法 JSON 文本即可），无需任何 typeHandler 参与。

2. **写入**（Service 层落库前，用 `ObjectMapper` 显式序列化）：
   ```java
   String json = objectMapper.writeValueAsString(references);   // List<XxxVO> → JSON 文本
   record.setReferencesJson(json);
   ```

3. **读取**（用 `ObjectMapper` 反序列化回强类型）：
   ```java
   List<XxxVO> refs = objectMapper.readValue(
           record.getReferencesJson(),
           new TypeReference<List<XxxVO>>() {});
   ```

4. **不要在实体上加 `@TableField(typeHandler = JacksonTypeHandler.class)`**，也不要加 `@TableName(autoResultMap = true)`。
   `JacksonTypeHandler` 会把 JSON 反序列化为**字段的声明类型**；字段是 `String` 时：
   - **读**：JSON 列里是对象（如 `{"a":1}`）→ 抛 `MismatchedInputException`（`Cannot deserialize value of type java.lang.String from Object value`）；
   - **写**：传入字符串被 `writeValueAsString` 再编码一次 → 存成 JSON 字符串而非对象。

   即"**留空时看不出问题，一旦写入真实 JSON 就炸**"。

> ⚠️ 只有当字段的**声明类型本身改为强类型**（如专门的 `AiReferences` POJO 或 `List<Object>`）**时**，`JacksonTypeHandler` + `autoResultMap = true` 才是正确的、也才有必要。**第一版不做此改造。**
>
> **结论**：序列化 / 反序列化的职责放在 **Service 层**，实体只当 `String` 载体。这样将来接手的人有据可循，也不会踩上面的坑。

---

## 十、会话管理设计（Day 13 改造：按会话组织历史）

**背景**：原设计"每次提问 = 一条记录，历史抽屉逐条列出"，与真实聊天产品（如 DeepSeek）的体验不符——用户期望看到的是**会话列表**，点进去才是该会话的多轮问答。

**不改库表**：`ai_chat_record` 本就有 `session_id` 列、前端一直在传，只需改**查询与展示**。

### 10.1 会话的定义

一个**会话** = 一个 `session_id` 聚合的若干轮问答。会话是**派生视图**，不额外建表。

| 项 | 口径 |
|---|---|
| 列表排序 | `last_time = MAX(create_time)` 倒序 |
| 标题 | 该会话 `create_time, id` **最小**的一条的 `question`（首问） |
| 轮数 | `message_count = COUNT(*)` |
| 过滤 | `session_id IS NOT NULL`——早期未传会话 ID 的遗留数据不显示 |

### 10.2 四个接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/student/ai/chat` | 提问（落库归属 `sessionId`） |
| GET | `/api/student/ai/sessions` | 会话列表（分页，`pageSize` 上限 20） |
| GET | `/api/student/ai/sessions/{sessionId}` | 某会话全部消息（`create_time ASC, id ASC`） |
| DELETE | `/api/student/ai/sessions/{sessionId}` | 删除整个会话 |

> 原 `GET /history`、`DELETE /history/{id}` **已移除**，避免"按记录"与"按会话"两套接口并存造成混淆。
> 删除会话影响 0 行时仍返回 `AI_RECORD_NOT_FOUND(4004)`（存在性不泄露）。

### 10.3 分页的取舍（为什么不直接用 `IPage`）

会话列表用**显式 offset/limit + 独立 count**（`countSessions` + `selectSessions` 两条 SQL），未用 MyBatis-Plus 的 `IPage` 自动 count：本查询含 `GROUP BY`，自动 count 优化在分组场景下口径容易产生歧义，两条独立 SQL 的结果完全确定。

`first_question` 用**相关子查询**而非 `GROUP_CONCAT`——后者默认会被 1024 字节截断。

### 10.4 进入某会话后能否继续提问

**可以**。点击历史会话 → 前端把主区 `sessionId` **切到该会话**，之后的新提问追加到同一会话（`chat` 接口照常传该 `sessionId`）。只有点"开始新会话"才真正换 `sessionId`（新 UUID）。这样"接着上次话题继续问"才自然。

### 10.5 前端改动要点

- `api/ai.ts`：`getSessionsApi` / `getSessionMessagesApi` / `deleteSessionApi`（原 `getHistoryApi` / `deleteRecordApi` 移除）
- `types/index.ts`：新增 `AiSessionVO { sessionId, firstQuestion, lastTime, messageCount }`
- `store/aiChat.ts`：`historyList` → `sessionList`；`loadSessions()` / `loadSessionMessages(id)` / `deleteSession(id)` / `loadFromSession(record)`
- `HistoryDrawer.vue`：列表项由"记录"改为"会话"（首问 + 最后时间 + 轮数），删除按钮改为"删除整个会话"
