# 【Day 13 工作日志】

## 一、日期

2026-10-06

## 二、今日目标

完成 **AI 问答业务闭环**并**前后端联调**：`SystemPromptBuilder` → `AiChatService`（限额 → 拼 Prompt → 调用 → 落库）→ `AiChatController` → 前端问答页（打字机效果）。联调后按"像 DeepSeek 一样"的体验，把历史从**按记录**改造为**按会话**组织。

## 三、完成的工作

**数据库设计：** 无（复用 `ai_chat_record`，其 `session_id` 列本就存在）。

**后端：** 新增 9 个文件、改动 3 个。

### 1. 实体与 DTO / VO（`pojo` 模块）

- `entity/AiChatRecord`（流水表：无 `@TableLogic`、无 `update_time`，仅 `create_time` 自动填充；**允许物理删除**）。
- `dto/AiChatRequestDTO`：`question`(`@NotBlank` + `@Size(max=500)`) / `subjectCode?` / `sessionId?`。
- `vo/AiAnswerVO`（`answer` / `recordId` / `model` / `promptTokens` / `completionTokens` / `createTime`）。
- `vo/AiRecordVO`（历史条目，含 `sessionId`）。
- `vo/AiSessionVO`（**会话聚合**：`sessionId` / `firstQuestion` / `lastTime` / `messageCount`）。

### 2. `SystemPromptBuilder`（新包 `server.ai`）

固定 system prompt（角色 + 4 条规则）常量；`subjectCode` 可识别时在末尾追加"该学员正在准备科目X考试…"。科目名映射用内部 `Map`，**暂不抽公共枚举**（只有这一处用）。选 `@Component` 而非 static 工具类：与项目既有 Bean 做法一致，且保留将来"提示词版本可配置"的余地。

### 3. `AiChatService` / `Impl`

- `chat(userId, dto)`：限额（当日记录数 ≥ 20 → 4003）→ `List.of(system, user)` → `deepSeekClient.chat(messages)` → 落库（`model` 存**响应返回的**）→ 组装 `AiAnswerVO`。
- `sessions` / `sessionMessages` / `deleteSession`（会话改造后，见下）。
- **刻意不加 `@Transactional`**，限额竞态**记录在案不处理**。

### 4. `AiChatRecordMapper`

前 6 步用 BaseMapper 即可；会话改造后新增两个自定义方法 `selectSessions`（`GROUP BY session_id`）与 `countSessions`。

### 5. `AiChatController`（`/api/student/ai`）

会话改造后的 4 个接口：`POST /chat`、`GET /sessions`、`GET /sessions/{sessionId}`、`DELETE /sessions/{sessionId}`。

### 6. 错误码

`ErrorCode` 新增 `AI_RECORD_NOT_FOUND(4004)`（AI 段 40xx，与 4001/4002/4003 同段；全量错误码去重校验无重复）。

### 7. 测试

`SystemPromptBuilderTest`（`@SpringBootTest` + `@Disabled`，3 个用例仅打印效果）。

**前端：** 新增 7 个文件、改动 2 个。

| 文件 | 说明 |
|---|---|
| `api/ai.ts` | `chatApi` / `getSessionsApi` / `getSessionMessagesApi` / `deleteSessionApi` |
| `utils/session.ts` | `getSessionId()` / `resetSessionId()`，localStorage 单一来源（`ai_chat_session_id`），不进 Pinia |
| `store/aiChat.ts` | 组合式 store：`messages` / `loading` / `sessionList` / `sessionId` + `send` / `loadSessions` / `loadMoreSessions` / `loadSessionMessages` / `loadFromSession` / `deleteSession` / `startNewSession` |
| `views/student/AiChatView.vue` | 问答页容器（`:key="store.sessionId"` 重建列表） |
| `components/ai/ChatMessageList.vue` | 气泡 + **打字机**（30ms/字，setTimeout 递归） |
| `components/ai/ChatInput.vue` | textarea + 发送（Enter 发送 / Shift+Enter 换行） |
| `components/ai/HistoryDrawer.vue` | 会话历史抽屉（列会话、进会话、删会话） |

**文档：**

- `doc/ai-chat-design.md`：新增 §九（RAG 阶段的 `references_json` 改造方式）、§十（会话管理设计），并给 §一/§五 加"已被取代"提示。
- `doc/api-design.md` §4.7：接口清单更新为会话版 4 个接口。

## 四、关键技术决策

| 决策点 | 方案 | 理由 |
|---|---|---|
| `references_json` 是否用 `JacksonTypeHandler` | **不用，保持 `String`** | 见问题 1 |
| `AiChatService` 事务 | **不加 `@Transactional`** | AI 调用 1~5s，包进事务=**长事务占连接**，并发即耗尽连接池；且只写一张表、单条 insert 本就原子 |
| 限额检查的并发竞态 | **不处理，写进 javadoc** | 单用户场景；严谨做法（原子计数/唯一约束）记录在案 |
| AI 失败是否落库 | **不落库** | 一条没有答案的记录对用户无价值，还会虚增"今日调用次数" |
| 打字机放哪 | **组件层**，不放 store | store 只负责完整回答与 `typing` 标记；动画是显示逻辑 |
| 会话 ID 放哪 | **localStorage**，不进 Pinia | 生命周期比页面长（刷新/重开要保持同一会话），与登录态同模式 |
| 进历史会话后能否继续问 | **可以，追加到该会话** | 切 `sessionId` 而非新建；只有"开始新会话"才换新 UUID —— 与 DeepSeek 一致 |
| 会话列表分页 | **独立 count + 显式 offset/limit** | 见问题 3 |

## 五、遇到的问题及解决方案

**问题 1（技术判断）**

- 现象：要求给 `AiChatRecord.referencesJson` 加 `@TableField(typeHandler = JacksonTypeHandler.class)` + 类上 `@TableName(autoResultMap = true)`。
- 分析：该 handler 会把 JSON **反序列化为字段的声明类型**；字段是 `String` 时，① 读 JSON 对象抛 `MismatchedInputException`；② 写字符串会被 `writeValueAsString` **再编码一次**（多套一层引号）。即"**留空时看不出、写入真实 JSON 就炸**"。而 MySQL Connector/J 对 `JSON` 列与 Java `String` 本就**原生互转**，无需 handler。
- 解决：**保持 `String`、不加 handler**，在字段 javadoc 里写明这个坑与将来 RAG 的正确做法（改强类型字段后才配 handler），并把这套结论写进 `ai-chat-design.md` §九。

**问题 2（必踩的中文坑）**

- 现象：`ChatInput` 里按 Enter 发送，中文输入法**选词时按 Enter（上屏）也会触发发送**。
- 解决：`onKeydown` 加 `!e.isComposing` 判断——输入法组合中不发送。这对中文项目是**必须**的。

**问题 3（ChatMessageList 的死角）**

- 背景：历史从"按记录"改"按会话"后，会话列表是 `GROUP BY session_id` 查询。
- 解决：
  - **不用 MyBatis-Plus 的 `IPage` 自动 count**（分组场景下自动 count 口径易歧义），改为 `countSessions` + `selectSessions` **两条独立 SQL**，`total` 就是会话数；
  - `first_question` 用**相关子查询**取该会话最早一条，**不用 `GROUP_CONCAT`**（默认被 1024 字节截断）。

**问题 4（"编译能过、调接口 500"的隐藏 bug，自查发现）**

- 现象：相关子查询里写了 `WHERE r2.user_id = r.user_id`（引用外层未分组列）。
- 原因：MySQL 8 默认开启 `ONLY_FULL_GROUP_BY`，引用未在 `GROUP BY` 中的外层列会被判为非法（nonaggregated column）。
- 解决：子查询改用 `r2.user_id = #{userId}`——外层本已 `WHERE r.user_id = #{userId}`，**同值等价且合法**。若不加这处，`mvn install` 能过，但一调接口就 500。

**问题 5（环境坑）**

- 现象：IDEA 里跑 `SystemPromptBuilderTest`，控制台**中文乱码**（如 `���Ǽݿ���…`）。
- 原因：JDK 18+ 起 `System.out` 的编码由 `stdout.encoding` 决定（**不再跟随 `file.encoding`**），Windows 下默认取系统 ANSI 代码页（GBK）；而 IDEA 控制台按 UTF-8 解码。把 `你是驾考辅导助手，` 按 GBK 编码再按 UTF-8 解码，得到的正是屏幕上的乱码。
- 解决：属**显示问题、不影响功能**。修法：Run 配置 VM options 加 `-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8`（本次决定暂不折腾）。用户确认 3 个测试全部通过。

**问题 6（联调发现的设计问题）**

- 现象：历史抽屉**逐条列出问答记录**，与真实聊天产品（DeepSeek）"会话列表"的体验不符。
- 分析：`ai_chat_record` 本就有 `session_id`、前端一直在传，**只需改查询与展示，不动库表**。
- 解决：新增会话聚合查询与 3 个会话接口，移除原"按记录"的 `/history` 端点；前端抽屉改为会话列表（首问标题 + 最后时间 + 轮数），点击进入会话并把主区 `sessionId` 切过去。

**问题 7（前端"删除当前会话"的一致性）**

- 现象：删除的若是**当前正在看的**会话，主区不能还停留在已删的对话上。
- 解决：`deleteSession` 里判断——若删的是当前 `sessionId`，则清空主消息区并 `resetSessionId()`（下次提问即新会话）；且抽屉里 `loadFromSession` 采用"**先加载成功、再切 `sessionId`**"，避免加载失败却已跳转的错乱。

## 六、产出物清单

**后端（新增 9）**

```
pojo/entity/AiChatRecord.java
pojo/dto/AiChatRequestDTO.java
pojo/vo/AiAnswerVO.java · AiRecordVO.java · AiSessionVO.java
server/ai/SystemPromptBuilder.java · AiChatService.java · ai/impl/AiChatServiceImpl.java
server/controller/AiChatController.java
test/java/…/server/ai/SystemPromptBuilderTest.java
```

**后端（改动 3）**

```
server/mapper/AiChatRecordMapper.java      加 selectSessions / countSessions
common/exception/ErrorCode.java            加 AI_RECORD_NOT_FOUND(4004)
server/ai/…                              （接口/实现随会话改造更新）
```

**前端（新增 7）**

```
src/api/ai.ts · src/utils/session.ts · src/store/aiChat.ts
src/views/student/AiChatView.vue
src/components/ai/ChatMessageList.vue · ChatInput.vue · HistoryDrawer.vue
```

**前端（改动 2）**

```
src/types/index.ts       加 AiAnswerVO / AiRecordVO / AiSessionVO
src/router/index.ts      加 /student/ai 路由
```

**文档**

- `doc/ai-chat-design.md`（§九 RAG 改造、§十 会话管理）
- `doc/api-design.md` §4.7（会话版接口清单）
- `doc/Day/Day13-工作日志.md`（本文）
- `doc/testjson/ai-chat-api.postman_collection.json`

**验证**

- 后端 **`mvn install` BUILD SUCCESS**；
- 前端 **`vue-tsc --noEmit` 0 错误**（每一步都跑）；
- `SystemPromptBuilderTest` **3/3 通过**（本地）；
- **前后端联调通过**：连续 3 问 → 抽屉 1 个会话显示 3 轮 → 点进去回显 3 轮 → 继续提问追加到该会话 → "开始新会话"后出现第 2 个会话。

## 七、遗留问题与明日计划

- 遗留：
  - **流式输出**未实现（打字机是前端假流式，升级点见 `deepseek-integration.md` §七）；
  - 限额检查的**并发竞态**记录在案未处理；
  - 题库管理模块（Day 8 → 推迟至 Day 19）仍未实现；
  - 会话列表标题取"首问"，不随后续问题变化（**与 DeepSeek 一致，属有意选择**）。
- 明日计划（Day 14）：第 3 周启动 —— 学情分析（正确率/进度统计、知识点掌握度 ECharts、成绩趋势）或模拟考试模块。
