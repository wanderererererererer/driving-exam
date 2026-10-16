# 【Day 15 工作日志】

## 一、日期

2026-10-07

## 二、今日目标

完成**学情分析模块**（第 3 周第一个亮点，数据可视化）：后端掌握度计算 + 三个接口，前端学情分析页（横向柱状图 + 折线图 + 短板表格）。

## 三、完成的工作

**数据库设计：** 无（复用 `answer_record` / `question` / `wrong_book` / `knowledge_point`，实时计算、不落统计表）。

**后端：** 新增 12 个文件、改动 1 个。

### 1. 掌握度计算（`server/analysis`，纯逻辑）

- `MasteryCalculator`（`@Component`，无 Spring 依赖，可 `new` 单测）：
  - `computeMastery(answers, stats, now)` = A × D × B，clamp [0,1]
  - `computeA` / `computeD` / `computeB` 三个子项各自公开（便于单独断言）
  - 常数全部 `public static final`（半衰期 14、系数 0.15 / 0.25）
- 载体（`record`）：`AnswerPoint(difficulty, correct, answerTime)`、`WrongBookStats(total, masteredCount, highFreqCount)`。

### 2. Service（`server/analysis` + `impl`）

- `AnalysisService` / `AnalysisServiceImpl`（`@Service`，构造器注入 4 Mapper + 计算器，**只读、不加 `@Transactional`**）。
- 私有 `aggregateKpStats(...)` 承载"含子孙聚合"，`knowledge()` 直接返回它，`overview()` 复用它只取短板计数。

### 3. Mapper / VO / 错误码（`pojo` + `server/mapper`）

- `AnalysisMapper`（4 条聚合/投影查询：`countTotalQuestions` / `countAnsweredQuestions` / `avgCorrectRate` / `selectDailyTrend`）。
- `pojo/vo`：`AnalysisOverviewVO` / `KPStatVO` / `TrendPointVO`；`pojo/enums`：`WeakReason`。
- 无新增错误码（三个接口都是只读，无业务错误码需求）。

### 4. Controller

- `AnalysisController`（`/api/student/analysis`）：`overview` / `knowledge` / `trend` 三接口。

### 5. 测试

- `MasteryCalculatorTest`（JUnit 5 + AssertJ，11 个用例）：含算例、空记录抛异常、全对/全错、clamp、除零、难度加权等。

**前端：** 新增 4 个文件、改动 2 个（另装依赖 `echarts@6.1.0`）。

| 文件 | 说明 |
|---|---|
| `api/analysis.ts` | 三个接口封装 |
| `types/index.ts` | 加 `AnalysisOverviewVO` / `KPStatVO` / `TrendPointVO` / `WeakReason` |
| `components/analysis/MasteryBarChart.vue` | 知识点掌握度**横向柱状图**（升序、短板标红） |
| `components/analysis/TrendLineChart.vue` | 正确率趋势**折线图** |
| `views/student/AnalysisView.vue` | 学情分析页 |
| `router/index.ts` | 加 `/student/analysis` |
| `views/student/SubjectHomeView.vue` | 科目首页加「学情分析」卡片 |

**前端图表选型（已确认）**：知识点掌握度用**横向柱状图**、趋势用**折线图**、短板用**表格**，**不用雷达图**——本项目知识点只有 4 个（雷达维度不够），且知识点是父子树、雷达要求各维度相互独立（父子维度重叠）。

**文档 / 测试集合：**

- `doc/analysis-design.md`（掌握度公式、短板判定、三接口、ECharts 数据结构、实现顺序）
- `doc/testjson/analysis-api.postman_collection.json`（11 请求 / 113 断言行）

## 四、关键技术决策

| 决策点 | 方案 | 理由 |
|---|---|---|
| 难度加权正确率 `A` | `Σ(dᵢ·cᵢ) / Σ(dᵢ)`，权重取难度本身（1~5） | 答对难题比答对简单题更有说服力；线性加权比"一律 1"更能区分"只会做简单题"与"难题也稳" |
| 时间衰减 `D` | `0.5^(Δ / 14)`，Δ = **距该知识点最近一次答题**的天数 | 知识新鲜度由**最近一次接触**决定：今天复习过，就不该因一个月前的错而被判不熟。半衰期 14 天贴合 2~4 周备考周期 |
| 行为修正 `B` | `1 + 0.15·已攻克率 − 0.25·高频错率`，值域 [0.75, 1.15] | 行为只作**微调**（小系数），话语权留给"正确率×衰减"；信号**只用错题本的"是否已攻克/高频错"**，刻意不引入 `duration`（噪声大、"快"常意味着熟练） |
| 合成 | `掌握度 = clamp(A·D·B, 0, 1)` | A、D ≤ 1、B ≤ 1.15，乘积可达 1.15，**必须 clamp** |
| 短板条件① | 掌握度 < 0.6 且答题数 ≥ 3 | 样本太少不硬判 |
| 短板条件② | 低难度({1,2})错误率 > 高难度({4,5})错误率，且**低难度档 ≥ 2 题** | 捕捉"难题蒙对、简单题反而错"的基础不牢；1 道低难度错题说明不了问题，但门槛不能高（高难度数据本就少） |
| 短板优先级 | 同时满足 → `LOW_MASTERY` | "掌握度偏低"是更根本的问题 |
| 含子孙聚合 | 一条记录归属到所属知识点 **+ 所有祖先**（沿 `parent_id` 上溯）；`visited` 防环、`parent_id=0` 根哨兵 | 根节点掌握度由子孙题目**合起来重算**，而非子节点掌握度**求平均**（掌握度不可平均） |
| 聚合落库？ | **不建表，实时计算** | 数据量小；避免"统计表与流水不一致"的经典问题 |
| 精度 | 判定用**原始值**、展示用 `BigDecimal.setScale(2, HALF_UP)` | 见问题 2 |
| 防 N+1 | `knowledge` 一次查 4 份数据，内存一次遍历聚合 | 见问题 3 |
| 一致性 | `overview` 复用 `aggregateKpStats` | 避免"两个接口算出不同短板数"的口径漂移（一致性 > 省几条查询） |
| 载体 | `AnswerPoint`（record）而非 `AnswerRecord` 实体 | 实体**无难度字段**（难度在 `question` 表），掌握度必须按难度加权，故查询期合成载体 |

## 五、遇到的问题及解决方案

**问题 1（前端图表的坑）**

- 现象：横向柱状图按掌握度**升序**排列，但画出来**最弱的却在最下面**。
- 原因：ECharts 类别轴默认把 index 0 画在**底部**；"升序数组"的第 0 个反而落在最下。
- 解决：`yAxis.inverse = true`，让 index 0 显示在**最上方**。这样"数组升序"才真正对应"最弱在最上、一眼可见"。

**问题 2（"四舍五入导致漏判"的坑）**

- 背景：掌握度要 `< 0.6` 判短板，又在 VO 里保留 2 位小数。
- 陷阱：若用**四舍五入后**的值去比 `< 0.6`，`0.5999` 会被舍成 `0.60`，**反而漏判**了最该报的短板。
- 解决：**判定用原始值、展示用舍入值**——`judgeWeak(pts, rawMastery)` 收未舍入值，`vo.setMastery(round2(rawMastery))` 只用于展示。

**问题 3（除零与空集语义）**

- 除零：行为修正 `B` 的"已攻克率/高频错率"分母是"该知识点错题总数"；为 0 时会 `0/0`。解决：`total = 0` 时 **B = 1.0（中性）**，不参与修正。
- 空集：`AVG(is_correct)` 在**无答题记录**时返回 NULL。解决：区分两种语义——用户答过（哪怕全错）→ `0.0`；**完全没答过 → null**，前端显示"-"而非"0%"，由 `AnalysisMapper.avgCorrectRate` 返回 `Double` 承载、VO 保留 null。
- 新知识点的空 `AnswerPoint` 列表：计算器对空输入**抛 `IllegalArgumentException`**（fail-fast），服务层在调用前先剔除"未练习"的知识点（`pts == null || pts.isEmpty() → continue`），因此前端**不会出现"0 掌握度的未学知识点"**。

**问题 4（查询正确率的两条 SQL 之争）**

- 方案 A：`SUM(is_correct)` 与 `COUNT(*)` 各查一次再相除。
- 方案 B（采用）：直接 `SELECT AVG(is_correct)`——0/1 列的均值即正确率，一条 SQL 得出，且**空集返回 NULL**，天然区分"未答过"。

**问题 5（跨包耦合，记录在案）**

- 现象：`AnalysisServiceImpl` 为复用"高频错阈值"（`wrong_count >= 3`）而 `import WrongBookServiceImpl.HIGH_FREQ_THRESHOLD`，使 `analysis` 包依赖 `service.impl` 包。
- 处理：方向不循环、当前可用，**现在不改**；在这行 import 上加了 `TODO(第4周收尾)`，建议届时提到 common 常量类或 `WrongBookCategory` 枚举（需评估是否破坏"pojo 不承担业务阈值"的原则）。

**问题 6（编码导致的"假失败"，复现第 5 次）**

- 现象：Apifox 跑学情分析集合时，"失败"仅 1 条，且是 Apifox 自己加的**「HTTP 状态码 = 200」默认校验**撞上了我们**刻意设计返回 401** 的鉴权用例（自己写的两条断言全过）。
- 结论：**工具假阳性**，非代码问题。这是本项目第 5 次遇到同一现象（错题本 2 次、AI 1 次、本次 1 次，历次同因）。测试结论：**11 请求 / 54 断言 / 实际失败 0**。

> 附：`AnalysisMapper` 里 `date` 是 MySQL 关键字，趋势查询用反引号 `` AS `date` `` 转义，避免解析歧义。

## 六、产出物清单

**后端（新增 12）**

```
pojo/enums/WeakReason.java
pojo/vo/AnalysisOverviewVO.java · KPStatVO.java · TrendPointVO.java
server/analysis/AnswerPoint.java · WrongBookStats.java · MasteryCalculator.java
server/analysis/AnalysisService.java · impl/AnalysisServiceImpl.java
server/mapper/AnalysisMapper.java
server/controller/AnalysisController.java
test/java/…/analysis/MasteryCalculatorTest.java
```

**前端（新增 4）**

```
src/api/analysis.ts
src/components/analysis/MasteryBarChart.vue · TrendLineChart.vue
src/views/student/AnalysisView.vue
```

**前端（改动 2）**

```
src/types/index.ts            加 4 个类型
src/router/index.ts           加 /student/analysis 路由
src/views/student/SubjectHomeView.vue   加「学情分析」卡片
package.json                  加 echarts@6.1.0
```

**文档 / 测试集合**

- `doc/analysis-design.md`
- `doc/testjson/analysis-api.postman_collection.json`（11 请求 / 113 断言行，只读、可重复跑）

**验证**

- 后端 `MasteryCalculatorTest` **Tests run: 11, Failures: 0**（用户本机 `mvn test`）。
- 后端 `mvn install` BUILD SUCCESS。
- 前端 **`vue-tsc --noEmit` 0 错误** + **`vite build` 构建成功**。
- **Apifox 联调**：11 请求 / 54 断言 / 实际失败 0（唯一"失败"为工具假阳性）。
- 前端走查 6 条清单通过（图表显示、范围切换、空态、`correctRate` 为 null 显示"-"、窗口缩放自适应）。

## 七、遗留问题与明日计划

- 遗留：
  - **`all-api.postman_collection.json` 合并集合**推迟到 **Day 17 之后**——等模拟考试接口也加入后再一次性合并，避免重复劳动；
  - `HIGH_FREQ_THRESHOLD` 跨包耦合，已加 `TODO`，**第 4 周收尾统一处理**；
  - 题库管理模块（原 Day 8 → 推迟至 Day 19）仍未实现；
  - 趋势的 `mockScore`、总览的 `mockExam*` 三个字段当前恒为 null，待模拟考试模块（Day16~17）填充；
  - 前端 `AnalysisView` chunk 因引入全量 echarts 达 1.13 MB（gzip 381 KB），但为**路由级懒加载**、不影响首屏，暂不做按需引入。
- 明日计划（Day 16~17）：**模拟考试模块** —— 试卷列表、开始考试、断点续考、考试中保存答案、交卷判分、成绩报告、考试历史；届时把 `mock_exam` 成绩接入学情分析的 `mockScore` / `mockExam*` 字段，并生成合并测试集合。
