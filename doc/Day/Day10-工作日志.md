# 【Day 10 工作日志】

## 一、日期

2026-10-03

## 二、今日目标

两件事：**把判分核心的测试做扎实**（这是全项目技术含量最高、答辩最爱问的地方）；**实现错题本模块**，把"答错自动归集"升级为可查看、可重练、可标记掌握的完整闭环。

## 三、完成的工作

**数据库设计：** 无新增表。

**后端：**

### 一、判分核心的测试深化（`AnswerGrader`）

以 **JUnit 5 + AssertJ** 编写，**共 38 个用例**，覆盖：

| 分组 | 覆盖点 |
|---|---|
| 单选 | 正确 / 错误 / 小写 / 前后空格 / **全角字母** |
| 多选 | 全对 / **顺序颠倒** / 少选 / 多选 / 半角逗号 / **全角逗号** / 空格 / 顿号 / 小写 / **重复字母去重** / **标准答案本身带分隔符** |
| 判断 | 对错 / `T` `√` `正确` 等同义写法映射 / 小写 |
| 边界与异常 | `null` / 空串 / 全空白 / 只有分隔符 / **双方都空不得判对** / 标准答案缺失 / **未知题型抛异常** / 题型大小写不敏感 |
| 脏输入 | `AB!`、`AB1` → 归一为 `AB`（只保留 A–Z） |

**归一化的三步**（答辩可讲）：

```
1) NFKC 归一化：全角/兼容字符折叠为半角（Ａ→A、，→,）
2) trim + 转大写
3) 按题型处理：
     SINGLE   → 只保留 A-Z
     MULTIPLE → 只保留 A-Z → 去重 → 排序
     JUDGE    → 同义映射为「对」/「错」
```

**为什么用 NFKC**（写进代码注释）：中文输入法常产出全角字符，NFKC 把 `ＡＢ` 统一成 `AB`，让判分不受全角/半角影响；对汉字无副作用，成本仅一次字符扫描。

### 二、错题本模块（3.9.1 ~ 3.9.7）

- **`WrongBookCategory`**（`pojo.enums`）：`MASTERED` / `HIGH_FREQUENCY` / `PENDING`，**枚举声明顺序即优先级**。
- **分类规则（不落库，纯推导）**：

  | 优先级 | 分类 | 条件 |
  |---|---|---|
  | 1 | **已攻克** `MASTERED` | `mastered = 1` |
  | 2 | **高频错** `HIGH_FREQUENCY` | `mastered = 0` 且 `wrong_count >= 3` |
  | 3 | **待攻克** `PENDING` | `mastered = 0` 且 `wrong_count < 3` |

  **"已攻克"排最前**：一道题只要攻克，哪怕曾错过 10 次也归「已攻克」；否则「高频错」会永久堆积已掌握的题，失去"最该练"的指向。
- **新增 VO**：`WrongBookItemVO`（+ `category`）、`WrongBookSummaryVO`（三类计数 + total）。
- **`AnswerSource` 枚举**（`server.enums`）：`PRACTICE` / `RETRY`；`PracticeService.submitAnswer` 增加 `scene` 参数，**只有重练答对才把 `mastered` 置 1**。
- **`WrongBookMapper`**：新增 `markMasteredById`（按错题记录 id + `user_id` 越权保护）与 `summarize`（**一条 SQL 用 `CASE WHEN` 同时算出三类**）。
- **`WrongBookService`**：列表（分类过滤 + **按 category 固定排序**）、统计、抽题、标记掌握。
  - **重练抽题：优先高频错，不够补待攻克**（用户点重练最想练反复错的，纯随机可能练不到高频错）。
  - 排序：`HIGH_FREQUENCY` 按 `wrongCount` 倒序；其余按 `lastWrongTime` 倒序；**次级键统一 `id` 倒序**保证分页稳定。
- **`StudentWrongBookController`**：5 个接口。
- **`JacksonConfig`**：全局统一 `LocalDateTime` 的序列化/反序列化格式为 `yyyy-MM-dd HH:mm:ss`（**为什么全局配而不是逐个字段加 `@JsonFormat`**：时间字段会越来越多，逐个加容易漏）。

**前端：** 无。

**文档/设计：**

- `doc/data-conventions.md` 补 §8（错题本分类规则）、§9（已知取舍）
- `doc/api-design.md` §4.4 更新为 5 个接口 + 约定
- `doc/test-cases-wrong-book.md`（20 条用例）+ `doc/testjson/wrong-book-api.postman_collection.json`（22 请求）

## 四、关键技术决策

| 决策点 | 方案 |
|---|---|
| 归一化 | **NFKC** + 转大写 + 按题型处理；判断题做**同义映射**（`T/√/正确` → 对） |
| 多选判分 | 集合相等（**全对才算对，不设部分分**）——符合驾考真实规则 |
| 判分唯一真相 | `question.answer`；`question_option.is_correct` 为派生字段 |
| 重练识别 | **独立接口 + 显式 `AnswerSource` 参数**，请求体**不传 source**（防前端伪造副作用） |
| 错题分类 | **不落库、纯推导**（`mastered` + `wrong_count`），避免"第二份真相" |
| 抽题策略 | 优先高频错，不够补待攻克 |
| 并发安全 | `INSERT ... ON DUPLICATE KEY UPDATE`（利用唯一键 `uk_user_question`） |
| 越权保护 | `UPDATE ... WHERE id = ? AND user_id = ?`；影响行数为 0 时，"不存在"与"不是我的"**返回同一响应**（2003），不泄露存在性 |

## 五、遇到的问题及解决方案

**问题 1**

- 现象：多选答案 `"BA"` 与正确答案 `"AB"` 被判错；`"Ａ"`（全角）与 `"A"` 也判别。
- 原因：字符串直接相等比较，对**顺序、大小写、分隔符、全角**都敏感。
- 解决：判分前对**两侧**都做归一化（NFKC + 大写 + 去重排序）。**双向归一化**很关键——标准答案本身也可能是 `"A,B"` 这种脏格式。

**问题 2**

- 现象：用户提交的答案与标准答案**都是空**时，归一化后相等 → **误判为"正确"**。
- 原因：只比较了"相等"，没先判空。
- 解决：在比较**之前**加空值守卫：任一侧为空（未作答 / 脏数据 / 无法识别）**一律判错**。测试里专门有一条 `MULTIPLE, "", "" → false` 守住这个边界。

**问题 3**

- 现象：错题本的三个分类与前端的 tab 出现**重叠归属**——一道题既可能算"经常错"，又可能算"已攻克"。
- 原因：把"是否已攻克"（状态维度）和"错了几次"（频率维度）**当作两组平行的分类**，两个维度交叉必然重叠。
- 解决：改为**优先级互斥判定**（已攻克 > 高频错 > 待攻克），**每条错题只属于一类**；并把"已攻克"放在最高优先级。

**问题 4**

- 现象：给错题**分三个 tab** 时，前端需要在每个 tab 上显示数量；若每个 tab 各查一次数据库，就是 3 次查询。
- 原因：分类是推导值，不能直接 `GROUP BY category`。
- 解决：用 **一条 SQL + 三个 `CASE WHEN`** 一次算出三类；并用 `COALESCE(SUM(...), 0)` 兜底——**空结果集时 `SUM` 返回 NULL，而 MyBatis 的 `IntegerTypeHandler` 会把 NULL 转成 Java `null`**，不兜底新学员会拿到一堆 null。

**问题 5**

- 现象：错题列表返回的 `total` 与 `records` 条数可能**对不上**。
- 原因：`total` 由 SQL 分页算出（含"题目已被逻辑删除"的记录），而 `records` 在**内存装配阶段把已删题过滤掉了**。
- 解决：**记录为"已知取舍"**（`data-conventions.md` §9）——课设阶段几乎不会触发（需"管理员删题"+"学员恰好错过该题"同时成立），彻底修法是把过滤下推到自定义 SQL 的 JOIN，**收益低于维护成本**，暂不处理。

**问题 6**

- 现象：前端传非法的枚举值（如 `category=xxx`）时，接口返回 **500 系统异常**。
- 原因：Spring 转换枚举失败抛 `MethodArgumentTypeMismatchException`，被全局异常处理器的**兜底 `Exception` 分支**接住 → 500。
- 解决：全局异常处理器新增该异常的专属分支 → **HTTP 200 + `1001 参数类型不匹配：category`**。
  - **这条一次修好了一整类隐患**：`knowledgePointId=abc` 这类"数值参数传了非数字"的 500 问题同样被修掉。

## 六、产出物清单

- 测试：`AnswerGraderTest`（38 用例，`driving-exam-server/src/test/`）
- pojo：`WrongBookCategory`、`WrongBookItemVO`、`WrongBookSummaryVO`、`AnswerSource`
- server：`WrongBookMapper`（+2 方法）、`WrongBookService`（+Impl）、`StudentWrongBookController`、`JacksonConfig`、`PracticeService`（+参数与分支）
- 种子：`sql/seed-question-extra.sql`（+18 题，合计 36 题，让分页预加载可验证）
- 文档/测试集：`data-conventions.md`、`api-design.md`、`test-cases-wrong-book.md`、`wrong-book-api.postman_collection.json`
- **联调结果**：错题本 22 请求 / 85 断言 / **断言失败 0**（唯一"失败"是 Apifox 内置「HTTP 状态码=200」默认校验撞上刻意返回的 401，属工具假阳性）

## 七、遗留问题与明日计划

- 遗留：`WrongBookItemVO.category` 已由后端推导返回，但**前端尚未消费**；"标记已掌握"**不可逆**（无取消接口，只能靠再答错打回未掌握）；错题本列表的 `total`/`records` 口径问题按"已知取舍"挂起。
- 明日计划（Day 11）：前端刷题页全套 —— 科目首页、章节选择弹窗、刷题页（四种模式共用）、错题本页。
