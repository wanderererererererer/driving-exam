# 【Day 12 工作日志】

## 一、日期

2026-10-05

## 二、今日目标

接入 **DeepSeek V4 Flash**（大模型调用工具类）。今天**只做"工具类 + 协议适配 + 异常降级"**，**不做业务集成**——把 AI 问答业务（提问 → 调模型 → 落库）留到 Day 13。

## 三、完成的工作

**数据库设计：** 无（无新表）。

**后端：** 新增 6 个文件 + 1 个配置文件改动。

### 1. 配置类 `DeepSeekProperties`

`@ConfigurationProperties(prefix = "deepseek")`，字段 `baseUrl` / `apiKey` / `model` / `temperature` / `topP` / `connectTimeout` / `readTimeout`。

- `baseUrl`、`model` 加 `@NotBlank`（**启动即校验**：配错立刻暴露）；
- `apiKey` **故意不加**校验（见决策 ①）；
- `apiKey` 加 `@ToString.Exclude`——**防止密钥被日志打印**。

### 2. 协议 DTO（`integration/deepseek/dto/`，4 个）

| 类 | 说明 |
|---|---|
| `ChatMessage` | `role` + `content`，附 `system()` / `user()` / `assistant()` 工厂方法 |
| `ChatRequest` | 出参：`model` / `messages` / `temperature` / `topP`(@JsonProperty("top_p")) / `stream=false` |
| `ChatResponse` | 入参：`choices[]` / `usage` |
| `ChatResult` | **业务层与 AI 基础设施之间的唯一边界类型**：`content` / `model` / `promptTokens` / `completionTokens` |

### 3. 调用工具类 `DeepSeekClient`

- 两个方法：`chat(String userMessage)`（单轮便捷）+ `chat(List<ChatMessage> messages)`（多轮）。
- **在构造器里用 `RestTemplateBuilder` 构建私有 `RestTemplate`，不注册全局 Bean**（见决策 ②）。
- 异常分级：`SocketTimeoutException` → `AI_TIMEOUT(4002)`；HTTP 429 → `AI_QUOTA_EXCEEDED(4003)`；401/403 → 打 **ERROR** 日志 + `AI_CALL_FAILED(4001)`；其余 4xx/5xx → WARN + `AI_CALL_FAILED`；反序列化失败 → ERROR + `AI_CALL_FAILED`。
- **日志红线：绝不记请求头**（`Authorization` 里有 Key）。

### 4. 配置 `application.yml`

```yaml
deepseek:
  base-url: https://api.deepseek.com
  model: deepseek-chat          # 官方通用别名
  api-key: ${DEEPSEEK_API_KEY:}
  temperature: 1.0
  top-p: 1.0
  connect-timeout: 5000
  read-timeout: 60000
```

### 5. 测试 `DeepSeekClientTest`

`@SpringBootTest` + `@Disabled`（手动运行、真实调用、消耗额度）。3 个用例：`testSimpleChat` / `testMultiMessage` / `testLongAnswerWithinReadTimeout`。

### 6. 错误码

`ErrorCode` 新增 AI 段：`AI_CALL_FAILED(4001)` / `AI_TIMEOUT(4002)` / `AI_QUOTA_EXCEEDED(4003)`。

## 四、关键技术决策

| 决策点 | 方案 | 理由 |
|---|---|---|
| ① `apiKey` 是否启动校验 | **不校验** | Key 常从环境变量注入，CI/本地可能缺失；若启动即抛错，会**连不需要 AI 的接口都起不来**。真正的错误在调用时以 `AI_CALL_FAILED` 暴露，日志级别设为 ERROR 保证可见 |
| ② RestTemplate | **客户端内私有构建，不注册全局 Bean** | 全项目只有它访问 DeepSeek；注册成全局 Bean 会**连累其它可能存在的 HTTP 客户端**共用同一套超时配置，且污染容器 |
| ③ `read-timeout` = 60s | **放宽到 60s** | 非流式请求下，模型**生成期间服务端不返回任何字节**，读超时必须覆盖"整段生成时间"，否则长回答会被误判超时 |
| ④ `model` 配 `deepseek-chat`，但**落库存返回的** | 配置用官方**通用别名**，将来升级无需改配置；DB 里存**服务端实际路由**的模型名（如 `deepseek-flash`），计费统计才准 | 见问题 1 |
| ⑤ 不用 httpclient5 | **只用默认 JDK/Simple 客户端** | 避免引入额外依赖与连接池配置；本项目并发极低，够用 |
| ⑥ 流式输出 | **暂不做**，升级路径写进文档 | 3 倍代码量、收益有限；升级点记录在 `deepseek-integration.md` §七 |
| ⑦ 失败原因对用户 | 只抛错误码，**不把上游 body 透给前端** | 上游报文可能含内部信息；细节只进服务端日志 |

## 五、遇到的问题及解决方案

**问题 1（今天最有价值的发现）**

- 现象：请求里 `model` 写的是 `deepseek-chat`，但**响应里返回的 `model` 是 `deepseek-flash`**。
- 原因：`deepseek-chat` 是官方**别名**，服务端会按当前路由把它解析成具体模型（此处是 flash）。
- 解决：**落库存"响应返回的 model"，不存配置里的别名**——否则计费/统计会与实际不符。`ChatResult.model` 取自响应体，`DeepSeekProperties.model` 只用于发起请求。

**问题 2**

- 现象：`ChatResponse` 反序列化时，DeepSeek 可能返回**我们不认识的字段**，Jackson 默认会抛 `UnrecognizedPropertyException`。
- 解决：`@JsonIgnoreProperties(ignoreUnknown = true)` 加在**外层类 + 嵌套 `Choice` + 嵌套 `Usage` 三处**。只加外层不够——未知字段可能出现在嵌套对象里。

**问题 3**

- 现象：`top_p` 是蛇形命名，Java 字段按驼峰写会绑不上。
- 解决：`@JsonProperty("top_p")` 显式映射。

## 六、产出物清单

**后端（新增）**

```
server/integration/deepseek/DeepSeekProperties.java
server/integration/deepseek/DeepSeekClient.java
server/integration/deepseek/dto/ChatMessage.java · ChatRequest.java · ChatResponse.java · ChatResult.java
test/java/…/integration/deepseek/DeepSeekClientTest.java
```

**后端（改动）**

```
common/exception/ErrorCode.java     新增 AI 段 4001/4002/4003
server/src/main/resources/application.yml   新增 deepseek.* 配置
```

**文档**

- `doc/deepseek-integration.md`（类结构、异常分级、日志红线、降级策略、流式升级路径、联调时机）

**验证**

- **用户实测通过**（curl）：请求 `model: deepseek-chat` → 响应 `model: deepseek-flash`。
- `DeepSeekClientTest.testSimpleChat`：耗时 **1095ms**、`promptTokens=28`、`completionTokens=16`，system 指令生效（多轮 messages 验证成功）。

## 七、遗留问题与明日计划

- 遗留：
  - **今天不做业务集成、不联调**——`DeepSeekClient` 尚无业务调用方，AI 问答闭环待 Day 13；
  - 流式输出未实现（升级点已记录）；
  - 题库管理模块（Day 8 → 推迟至 Day 19）仍未实现。
- 明日计划（Day 13）：AI 问答业务闭环 —— `SystemPromptBuilder` + `AiChatService`（限额 → 拼 Prompt → 调用 → 落库）+ `AiChatController` + 前端问答页（含打字机效果），并完成前后端联调。
