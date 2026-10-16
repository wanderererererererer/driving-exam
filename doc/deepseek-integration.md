# DeepSeek 接入设计（Day 12）

> 目标：产出**大模型调用工具类**（HTTP 封装 + 超时 + 错误分级 + 降级），不含业务逻辑
> 已有实测：curl 直连成功（请求 `deepseek-chat`，返回 200 + 回答 + usage）
> 归档：`doc/deepseek-integration.md`

---

## 〇、已确认的四个决策

| # | 决策 | 结论 |
|---|---|---|
| ① | API Key 来源 | 环境变量 + `application.yml` 占位 `${DEEPSEEK_API_KEY:}` |
| ② | 调用方式 | **纯 HTTP（RestTemplate）**，不引入 SDK |
| ③ | 降级 | **超时 + 友好文案 + 日志，不做重试** |
| ④ | `ai_chat_record` 落库 | 留到 Day 13，本日只做工具类 |

**实测补充**：请求 `model = deepseek-chat`，服务端返回的 `model = deepseek-flash`（路由后的实际模型）。配置以 `deepseek-chat` 为准；计划文档中的 `deepseek-v4-flash` 是旧信息。

---

## 一、类结构设计

### 1.1 包与类清单

**新增包**：`com.driving.exam.server.integration.deepseek`

```
server/integration/deepseek/
├── DeepSeekProperties.java      配置绑定（对应 yml 的 deepseek.*）
├── DeepSeekClient.java          ★ 对外唯一入口（@Component）
└── dto/
    ├── ChatMessage.java         一条消息（role + content）
    ├── ChatRequest.java         请求体（model / messages / temperature / top_p / stream）
    ├── ChatResponse.java        响应体（id / model / choices / usage）
    └── ChatResult.java          ★ 对外返回（content + model + 两个 token 数）
```

> **为什么这些 DTO 不放 `pojo` 模块**：`pojo` 存放的是**业务领域模型**（`Question`、`WrongBookItemVO` 等），
> 而 DeepSeek 的请求/响应是**基础设施内部的传输结构**，业务层不该看到它。
> 业务与 AI 的边界是 `ChatResult` —— 它是本包唯一被业务层消费的类型。

### 1.2 `DeepSeekProperties`（配置类）

- 注解：`@ConfigurationProperties(prefix = "deepseek")` + `@Component`（或由 `@EnableConfigurationProperties` 注册）
- 字段：

  | 字段 | 类型 | yml 键 |
  |---|---|---|
  | `apiKey` | String | `api-key` |
  | `baseUrl` | String | `base-url` |
  | `model` | String | `model` |
  | `temperature` | Double | `temperature` |
  | `topP` | Double | `top-p` |
  | `connectTimeout` | int（毫秒） | `connect-timeout` |
  | `readTimeout` | int（毫秒） | `read-timeout` |

- 建议加 `@Validated` + `@NotBlank` 校验 `apiKey` / `baseUrl` / `model`：
  **启动时就报配置缺失**，而不是等到第一次调用才失败。

### 1.3 请求 / 响应 DTO

**`ChatMessage`**：`role`（`system` / `user` / `assistant`）、`content`。

**`ChatRequest`**：
```
model, messages(List<ChatMessage>), temperature, top_p(@JsonProperty("top_p")), stream=false
```

**`ChatResponse`**（对应实测返回结构）：
```
id, object, created, model,
choices: [ { index, message(ChatMessage), finish_reason } ],
usage:   { prompt_tokens, completion_tokens, total_tokens }
```

**两个必须注意的序列化细节**：

1. **下划线字段要显式映射**：`top_p` / `prompt_tokens` / `completion_tokens` / `finish_reason` —— 我们的 Jackson 是驼峰风格，这些必须加 `@JsonProperty("top_p")` 等，否则**字段会静默为 null**（不报错，最难查）。
2. **`@JsonIgnoreProperties(ignoreUnknown = true)` 必须加在两个响应类型上**：DeepSeek 的实际返回**可能带我们没有声明的字段**，不加会在反序列化时抛 `UnrecognizedPropertyException`，直接把一次成功的调用变成失败。

**`ChatResult`（对外返回）**：
```
content            // AI 回答正文
model              // 服务端返回的实际模型（见"待确认点③"）
promptTokens       // 供 Day 13 落库
completionTokens
```

### 1.4 `DeepSeekClient`（工具类）

- `@Component`，注入 `DeepSeekProperties`
- **自带一个私有 `RestTemplate`**（不在全局注册 Bean，理由见 2.1）
- 方法签名（两个，单轮是通用的便捷封装）：

  ```java
  /** 单轮便捷方法：Day 13 学员问答直接用 */
  public ChatResult chat(String userMessage)

  /** 通用方法：接受完整 messages，为 Day 13 多轮对话 / Day 18 教练建议预留 */
  public ChatResult chat(List<ChatMessage> messages)
  ```

- **异常处理**：**失败一律抛 `BusinessException`（复用已有错误码）**，不返回"错误对象"（理由见第六节）

---

## 二、关键设计点

### 2.1 RestTemplate 配置

**结论：不引入额外依赖，用 `RestTemplateBuilder` 构建，超时往小里设。**

- 工程当前**没有** `httpclient5` —— `spring-boot-starter-web` 只自带 `SimpleClientHttpRequestFactory`（**无连接池**）。
- 想要连接池必须加 `org.apache.httpcomponents.client5:httpclient5` 依赖。
- **课设场景下的判断：不值得加。** 并发量是"演示级"（同一时刻 1~2 个请求），连接池的收益为零，而多一个依赖多一份风险。用 `SimpleClientHttpRequestFactory` + 显式超时即可。
- **构建方式**：注入 Spring Boot 的 `RestTemplateBuilder`，`.setConnectTimeout(...)` / `.setReadTimeout(...)`，这样**将来若真加了 httpclient5，会自动切换过去，代码不用改**。

**⚠️ 超时值的坑（这条建议改你给的默认值）**

| 项 | 你给的 | 我建议 | 理由 |
|---|---|---|---|
| `connect-timeout` | 5000 | **5000** ✅ | 建连很快，5s 足够 |
| `read-timeout` | 30000 | **60000** | 见下 |

**为什么 read-timeout 要放大**：我们用的是**非流式**请求——模型**生成期间服务端不会返回任何字节**，整个 body 在生成完成后一次性下发。
所以 `readTimeout` 实际衡量的是"**从发完请求到模型生成结束**"的整段空闲时间，它必须**大于最坏情况的生成耗时**。
30s 对"解释一个知识点"这种中等长度回答**可能刚好卡在边界**（尤其网络抖动时），一旦超时用户看到的是"AI 服务超时"而不是答案。
建议 **60s**。等 Day 13 真正跑起来后，可以按实测 P95 再回调。

### 2.2 异常分级

| 场景 | 捕获类型 | 日志级别 | 对外错误码 |
|---|---|---|---|
| 连接失败（DNS 失败 / 拒绝连接 / 网络不可达） | `ResourceAccessException`，cause 为 `ConnectException` 等 | **WARN** | `AI_CALL_FAILED(4001)` |
| **读超时** | `ResourceAccessException`，cause 为 `SocketTimeoutException` | **WARN** | `AI_TIMEOUT(4002)` |
| **401 / 403**（Key 无效 / 无权限） | `RestClientResponseException`，status 401/403 | **ERROR** | `AI_CALL_FAILED(4001)` |
| **429**（限流 / 额度不足） | `RestClientResponseException`，status 429 | WARN | `AI_QUOTA_EXCEEDED(4003)` |
| 其它 4xx | `RestClientResponseException` | WARN | `AI_CALL_FAILED(4001)` |
| 5xx（对方服务异常） | `RestClientResponseException`，status ≥ 500 | WARN | `AI_CALL_FAILED(4001)` |
| 响应结构异常（`choices` 为空 / `content` 为空） | 自行判空 | **ERROR** | `AI_CALL_FAILED(4001)` |

**两个原则**：

1. **401/403 用 ERROR 级**：这是**我们自己的配置问题**（Key 过期/填错），必须有人立刻看到并处理。其余（网络抖动、对方 5xx）是**可预期的外部故障**，WARN 足够。
2. **对用户只说"AI 服务调用失败"**，**绝不把 "invalid api key" 这类内部信息透出去**——它既不解决问题，又暴露了系统实现。原始 status 与响应体**只进日志**。

### 2.3 日志的"红线"

- ✅ 记：请求 URL、HTTP 状态码、**响应体**、耗时、`model`、token 数
- ❌ **绝不记请求头**（`Authorization: Bearer <API Key>` 就在里面）
- 日志级别：成功 DEBUG（或 INFO 带 usage）；失败按 2.2 的表

### 2.4 usage 字段解析（Day 13 落库用）

实测返回含 `usage`，解析后填入 `ChatResult`：

| 返回字段 | → `ai_chat_record` 列 |
|---|---|
| `usage.prompt_tokens` | `prompt_tokens` |
| `usage.completion_tokens` | `completion_tokens` |
| `model` | `model`（见待确认点③） |

**注意**：`usage` 在**异常响应里不存在**，所以解析必须**判空**，不能假设一定有。

### 2.5 是否支持单轮多消息

**支持。** `messages` 本来就是数组，多轮只是往数组里多塞几条。

- `chat(String)`：内部包装成 `[{role:"user", content:...}]`，**Day 13 学员单轮问答直接用**
- `chat(List<ChatMessage>)`：**Day 13 多轮对话 / Day 18 教练建议**用

**`stream` 固定传 `false`**：第一版不做流式（前端也不是打字机效果），留到毕业设计。

---

## 三、`application.yml` 配置结构

```yaml
deepseek:
  api-key: ${DEEPSEEK_API_KEY:}          # 环境变量，缺失时启动校验会报错
  base-url: https://api.deepseek.com
  model: deepseek-chat                   # 官方通用别名（原 deepseek-v4-flash 为过时信息）
  temperature: 1.0
  top-p: 1.0
  connect-timeout: 5000                  # 毫秒
  read-timeout: 60000                    # 毫秒（建议由 30000 上调，理由见 2.1）
```

**当前 yml 需要两处改动**：① `model` 由 `deepseek-v4-flash` 改为 `deepseek-chat`；② **新增** `connect-timeout` / `read-timeout`（现在是空的）。

---

## 四、降级策略

| 场景 | 对外文案（`message`） | 错误码 |
|---|---|---|
| 读超时 | **AI 服务响应超时，请稍后重试** | 4002 |
| 连接失败 | **AI 服务调用失败，请稍后重试** | 4001 |
| 401 / 403（Key 无效） | **AI 服务调用失败，请稍后重试**（**不提 Key**） | 4001 |
| 429（限流） | **AI 调用次数超限，请稍后再试** | 4003 |
| 5xx | **AI 服务调用失败，请稍后重试** | 4001 |
| 响应结构异常 | **AI 服务调用失败，请稍后重试** | 4001 |

**错误码复用已有的三个**（`AI_CALL_FAILED(4001)` / `AI_TIMEOUT(4002)` / `AI_QUOTA_EXCEEDED(4003)`）—— 这正是 Day 1 定错误码规范时预留的段位，**不新增**。

**不做重试**（按决策③）：重试会让用户多等一个超时周期，而 LLM 调用本身耗时较长，体验更差；外部故障重试也大概率仍失败。

**"降级"的本质是"由调用方决定"**：
- **学员问答（Day 13）**：失败就直接返回错误文案（**不要编一个假回答**，那是欺骗用户）；
- **教练端 AI 建议（Day 18）**：可以 `catch` 后**用规则判断的文本兜底**（因为那里本来就是"规则 + AI"两者结合）。
- 所以工具类**只负责抛异常**，是否兜底交给业务层 —— 这是刻意的分工。

---

## 五、测试策略

**结论：写一个 `@SpringBootTest` 的测试类，但加 `@Disabled`，需要时手动跑。**

| 方案 | 评价 |
|---|---|
| curl（已完成） | ✅ 已经验证过连通性，作为基线 |
| **`DeepSeekClientTest` + `@SpringBootTest` + `@Disabled`**（推荐） | ✅ 能验证 **Spring 装配**（属性绑定是否正确、Bean 能否注入、RestTemplate 超时是否生效）——这是 curl 测不到的；`@Disabled` 保证**不会污染 `mvn test`** |
| 直接写 `main` 方法 | ❌ 绕过了 Spring，**测不到配置绑定**（而属性绑定恰恰是最容易错的地方，如 `top-p` 绑不上 `topP`） |
| 不加 `@Disabled` 直接进 `mvn test` | ❌ 常规构建**依赖外部付费服务 + 网络**，不稳定、会花钱、CI 会挂 |

**测试内容**：发一个短 prompt（例如"用一句话解释什么是红灯"），断言 `content` 非空、`usage.promptTokens > 0`，并把返回的 `model` 打出来（用来确认路由后的实际模型名）。

**为什么不建议 mock**：本次要验证的是**真实连通性**，mock 掉 HTTP 就失去意义；相反，判分那种**纯逻辑**才适合 mock/单测。

---

## 六、返回格式（对外契约）

**结论：成功返回 `ChatResult`，失败抛 `BusinessException`。不做"双返回"。**

```java
public ChatResult chat(String userMessage)                 throws BusinessException
public ChatResult chat(List<ChatMessage> messages)         throws BusinessException
```

**为什么不用 `ChatResult { boolean success; String errorMessage; ... }` 这种"双返回"**：

1. **与项目全局异常体系一致**：全项目都是 `ErrorCode` + `BusinessException` + `GlobalExceptionHandler` 三件套，Controller 层**不需要写任何 `if (result.isSuccess())`**。若 AI 单独搞一套返回风格，就成了唯一的"例外"，维护时要记两条规则。
2. **失败是"中止"而不是"降级"**：本项目不做假回答（见第四节），失败就是失败，用异常表达更准确。
3. **`ChatResult` 的字段更干净**：只有成功语义，不需要 `success` 标志位，也不会诱导调用方"忘了判 success 就用 content"。

**调用方（Day 13）的样子**：正常写就可以，异常由全局处理器兜底：

```java
ChatResult r = deepSeekClient.chat(question);   // 失败会抛 BusinessException
record.setAnswer(r.getContent());
record.setPromptTokens(r.getPromptTokens());
```

---

## 七、流式输出升级路径（**暂不实现**）

**决策：第一版用非流式（`stream = false`），流式留到第 5 周缓冲期再做。**

**为什么现在不做**：

| 成本 | 说明 |
|---|---|
| 后端 | 要把 `RestTemplate` 换成 `WebClient` 或 `SseEmitter`，**响应解析方式完全不同**（要处理 `data: {...}` 分块与 `[DONE]` 结束标记） |
| 前端 | 要把 `axios` 换成 `EventSource`，并实现打字机渲染、断流处理 |
| 工作量 | 两端合计约 **3 倍代码量** |
| 评分 | 流式是**体验优化项**，不在课程设计评分核心 |

**改造点（答辩可指着讲，说明"预留了升级路径"）**：

1. **配置层**：`ChatRequest.stream` 字段已预留（当前固定 `false`），改配置即可打开。
2. **客户端层**：`DeepSeekClient` 增加一个 `chatStream(...)` 方法，返回
   `Flux<String>`（WebClient）或写入 `SseEmitter`；**现有 `chat(...)` 方法保持不变**，
   两种模式可并存。
3. **控制器层**：新增一个 SSE 端点（如 `GET` + `text/event-stream`），
   与现有 `POST` 单轮接口并存——**前端可自行选择用哪个**。
4. **前端**：新增 `EventSource` 版本；现有的非流式分支**不用动**。
5. **降级**：流式中断时的处理（已输出部分是否保留、如何提示）需要额外设计。

> **一句话**：接口契约与配置都已为流式留好位置，**属于"加方法"而不是"改架构"**。

---

## 八、前后端联调时机（避免后续遗漏）

| 阶段 | 是否联调 | 说明 |
|---|---|---|
| **Day 12（本日）** | ❌ **不联调** | 只产出后端**工具类**（`DeepSeekProperties` / `DeepSeekClient` / DTO），**没有 HTTP 接口**，前端也无从调用 |
| **Day 13** | ✅ **联调** | 做智能问答业务时才前后端打通：后端 `AiChatController`（`/api/student/ai/chat`、`/history`）+ `ai_chat_record` 落库；前端智能问答页面 |

> **提醒**：Day 12 结束时**不应该**出现"没法测"的困惑——它的验收方式就是
> **跑一次 `@Disabled` 的 `DeepSeekClientTest`**，而不是 Postman 或前端页面。

---

## 九、需要你确认的 4 个点

| # | 问题 | 我的建议 |
|---|---|---|
| ① | **`read-timeout` 用 30s 还是 60s** | **60s**。非流式请求下 readTimeout 覆盖"整段生成时间"，30s 可能不够（见 2.1） |
| ② | 是否引入 `httpclient5` 拿连接池 | **不引入**。课设并发量为零收益，多一个依赖多一份风险；将来真需要再加 |
| ③ | `ai_chat_record.model` 存**请求的**还是**返回的**模型？ | 存**返回的**（`deepseek-flash`）。理由：token 计费与统计按**实际路由模型**算；字段语义本就是"模型ID" |
| ④ | 测试类命名与形态 | `DeepSeekClientTest` + `@SpringBootTest` + `@Disabled`，手动跑 |

---

## 十、实施步骤（待确认后执行）

```
12.1  DeepSeekProperties（配置绑定 + 启动校验）
12.2  dto：ChatMessage / ChatRequest / ChatResponse / ChatResult
12.3  DeepSeekClient（RestTemplate + 异常分级 + 日志红线）
12.4  application.yml 改 model + 补两个超时   ← 已完成
12.5  DeepSeekClientTest（@Disabled，手动跑一次验证连通）
```
