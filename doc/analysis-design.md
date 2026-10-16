# 学情分析设计（Day 15）

> 目标：学员端学情分析后端 —— 总览、知识点掌握度、成绩趋势三个接口（ECharts 数据源）
> 归档：`doc/analysis-design.md`
> ⚠️ 本文是**需求梳理**，确认后再写代码

---

## 〇、范围与数据来源

| 项 | 说明 |
|---|---|
| 数据源 | `answer_record`（答题流水）× `question`（难度、知识点）× `wrong_book`（行为信号）× `knowledge_point`（知识点树） |
| 统计口径 | **包含全部来源**：`source` 为 `PRACTICE` 与 `EXAM` 的记录都计入（考试模块 Day16-17 建好后自动纳入） |
| 现有数据规模 | `SUBJECT_1` 共 4 个知识点（2 根 + 2 子）、36 道题 |
| 实时性 | **实时计算**，不落统计表（数据量小；且避免"统计表与流水不一致"的经典问题） |

---

## 一、掌握度公式（核心，答辩追问点）

**掌握度 = 难度加权正确率 × 时间衰减 × 行为修正**，最后 **clamp 到 [0,1]**。

以**知识点**为单位计算。设知识点 K 的答题记录集合为 R（含小题记录，见 §三.2 的聚合口径）。

### 1.1 难度加权正确率 A

$$A = \frac{\sum_i (d_i \cdot c_i)}{\sum_i d_i}$$

- `d_i` = 题目难度 ∈ {1,2,3,4,5}（即权重 = 难度本身；难度 5 的题权重是难度 1 的 5 倍）
- `c_i` = `is_correct` ∈ {0,1}
- 值域 [0,1]；**R 为空时 A = 0**（并标记"未练习"）

> **为什么用难度做权重**：答对一道难题比答对一道简单题更能说明"掌握了"。用难度线性加权，比"一律 1"更能区分"只会做简单题"和"难题也稳"。

### 1.2 时间衰减 D

$$D = 0.5^{\,\Delta / H}$$

- `Δ` = 距该知识点**最近一次答题**的天数（`now − max(t_i)`，可小数）
- `H` = 半衰期 = **14 天**（常数，可调）
- 语义：刚答过 → D≈1；14 天前 → 0.5；28 天前 → 0.25

> **为什么取最近一次而非平均时间**：知识"新鲜度"由最近一次接触决定——今天刚复习过，就不该因为一个月前答错而被判为不熟。
>
> **为什么半衰期 14 天**：驾考备考通常 2~4 周，14 天半衰期意味着"两周没碰的知识点，掌握度打对折"，与直觉一致。

### 1.3 行为修正 B

$$B = 1 + 0.15 \cdot m - 0.25 \cdot h$$

由该知识点关联的**错题本**记录推导（`wrong_book` join `question` 定位到知识点）：

- `W` = 该知识点错题条数；`M` = 其中 `mastered=1` 的条数；`Hc` = 其中（`mastered=0` 且 `wrong_count ≥ 3`）的条数
- `m = W>0 ? M/W : 0`（**已攻克率**）
- `h = W>0 ? Hc/W : 0`（**高频错率**）
- 值域 **[0.75, 1.15]**
- **除零保护**：`W = 0`（该知识点没有任何错题）时 **B = 1.0（中性）**，不参与修正——避免 `0/0`。

> **设计原则：行为修正只是"微调"，不能单独翻转结论**——所以系数取小值（±0.15/0.25），把主要话语权留给"正确率 × 衰减"。
>
> **信号选择**：只用**错题本的"是否已攻克 / 是否高频错"**这一类信号。刻意**不**引入"答题过快疑似蒙对"——`duration` 受网络与设备影响、噪声大，且"快"也常意味着"熟练"，容易误伤。（如需，可作后续增强。）

### 1.4 合成与边界

```
掌握度 = clamp(A × D × B, 0, 1)      // 保留 2 位小数
```

- A、D ≤ 1，B ≤ 1.15 → 乘积最大 1.15，**故必须 clamp**（否则会出现 >100%）。
- **R 为空**（该知识点用户一道题都没答过）→ **不参与计算、也不返回**：
  - 计算器对空输入 **抛 `IllegalArgumentException`**（fail-fast，避免静默产出错误的 0）；
  - **服务层在调用前先剔除**无答题记录的知识点（见 §3.2），因此计算器实际不会收到空列表。
  - 目的：前端图表**不出现"0 掌握度的未学知识点"**（雷达/柱状图上混入未学项会干扰判断）。

### 1.5 算例（便于对照验证）

**KP「交通信号灯」**：5 条记录，`(难度,是否答对)` = (2,✔) (2,✘) (3,✔) (3,✔) (4,✘)，最近一次答题 7 天前；错题本 1 条（`wrong_count=2`，未掌握）。

| 项 | 计算 | 结果 |
|---|---|---|
| A | Σ(d·c)=2·1+2·0+3·1+3·1+4·0 = **8**；Σd = 2+2+3+3+4 = **14** | 0.571 |
| D | 0.5^(7/14) = 0.5^0.5 | 0.707 |
| B | W=1, M=0, Hc=0（wrong_count=2 < 3）→ 1+0.15·0−0.25·0 | 1.000 |
| **掌握度** | 0.571 × 0.707 × 1.000 | **0.40** |

→ < 0.6 且 n=5 ≥ 3 → **短板** ✅

---

## 二、短板判定

```
isWeak = (掌握度 < 0.6 且 答题数 ≥ 3)                          // ① 掌握度偏低
      或 (低难度档答题数 ≥ 2 且 低难度错误率 > 高难度错误率)     // ② 基础不牢
```

- 难度分档：**低难度 = difficulty ∈ {1,2}**，**高难度 = difficulty ∈ {4,5}**；中间难度 3 **不参与**该比较。
- 条件②：**低难度档需 ≥ 2 题**（1 道低难度错题说明不了"基础不牢"）；**高难度档只要有题即可比较、不设下限**（高难度数据本就少，双门槛会让多数 `BASE_WEAK` 判不出来）。任一档为空 → 条件②不成立（数据不足，不硬判）。
- 错误率 = 1 − 该档正确率。
- **判定用原始掌握度**（未四舍五入），四舍五入仅用于展示——否则 `0.5999` 会被舍成 `0.60` 而漏判。

`weakReason` 取值：`LOW_MASTERY` / `BASE_WEAK` / `null`（不短板）。

> **优先级**：同时满足①②时**优先返回 `LOW_MASTERY`**——"掌握度偏低"是更根本的问题，`BASE_WEAK` 只是其可能的原因之一。

> **为什么要第二条**：学生可能"难题蒙对、简单题反而错"——加权正确率会被难题拉高而掩盖基础问题；第二条专门捕捉这种"基础不牢"。

**对照算例**：4 题，`(1,✘)(2,✘)(4,✔)(5,✔)` → A=(0+0+4+5)/12=0.75，若近期答题则掌握度≈0.75（**不受第一条约束**）；但低难度错误率 = 1.0 > 高难度错误率 = 0.0 → **判为短板（BASE_WEAK）** ✅

---

## 三、三个接口详细设计

### 3.1 `GET /api/student/analysis/overview` 学情总览

**入参**：`subjectCode`（必填）

**返回** `AnalysisOverviewVO`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `totalQuestions` | Integer | 该科目启用题数（`question` 中 `status=1 AND deleted=0`） |
| `answeredQuestions` | Integer | **去重**已答题数 `COUNT(DISTINCT question_id)` |
| `correctRate` | Double | 总体正确率（答对次数 / 总答题次数），0~1 |
| `progress` | Double | 练习进度 = 已答题数 / 总题数，0~1 |
| `weakCount` | Integer | 判为短板的知识点数（复用 §二 的计算） |
| `mockExamCount` | Integer\|null | 模拟考次数：为 Day16~17 预留，当前恒为 null |
| `mockExamAvgScore` | Double\|null | 模拟考平均分：同上 |
| `mockExamPassRate` | Double\|null | 模拟考及格率：同上 |

**SQL 思路**（3 条计数查询，均走 `idx_user_subject`）：
```sql
-- ① 答题总次数与答对次数（用于算 correctRate）
SELECT COUNT(*) AS total, COALESCE(SUM(is_correct),0) AS correct
FROM answer_record WHERE user_id=? AND subject_code=?;
-- ② 去重已答
SELECT COUNT(DISTINCT question_id) FROM answer_record WHERE user_id=? AND subject_code=?;
-- ③ 科目总题数
SELECT COUNT(*) FROM question WHERE subject_code=? AND status=1 AND deleted=0;
```
`weakCount` 直接取 §3.2 计算结果的计数（同一次请求内复用，不额外查）。

### 3.2 `GET /api/student/analysis/knowledge` 知识点掌握度

**入参**：`subjectCode`（必填）

**返回**：`List<KPStatVO>`，按 `mastery` **升序**（最薄弱在前，便于前端直接展示"待加强"）。

| 字段 | 类型 | 说明 |
|---|---|---|
| `knowledgePointId` | Long | |
| `knowledgePointName` | String | |
| `level` | Integer | 层级（1=根），前端可按层级分组 |
| `mastery` | Double | **掌握度**（§一），保留 2 位小数 |
| `answeredCount` | Integer | 该知识点（含子孙）答题数 |
| `correctRate` | Double | 该知识点（含子孙）正确率 |
| `isWeak` | Boolean | 短板（§二）。**用包装 `Boolean`**：否则 Lombok 生成 `isWeak()`、Jackson 输出的 JSON 键会变 `weak` 而非 `isWeak` |
| `weakReason` | WeakReason\|null | `LOW_MASTERY` / `BASE_WEAK`，非短板为 null |

**聚合口径（**关键决策**）**：采用 **"含子孙"聚合**——每道题的答题记录同时计入它所属知识点**及其所有祖先**。

- 每条记录先归到题目直接挂的知识点，再沿 `parent_id` 上溯，累加到沿途每个知识点；
- 这样 `交通信号`（根）的掌握度 = 其自身 + 两个子节点的题目**合起来**重算，而不是把子节点的掌握度求平均（**掌握度不可平均**）；
- 叶子节点 = 只含自己的题；根节点 = 含全部子孙。前端用**顶层**点位作图、明细表用全部。

**未练习的知识点不返回**：某知识点（含其子孙）累计答题数为 **0** 时，从结果列表中**剔除**（避免图表出现"0 掌握度的未学项"）。

**SQL 思路**：
```sql
-- ① 知识点树（构建 id→parent 与 id→node）
SELECT id, name, level, parent_id FROM knowledge_point
WHERE subject_code=? AND deleted=0 ORDER BY sort_order;
-- ② 答题原始项（join 题目拿难度与知识点）
SELECT ar.question_id, ar.is_correct, ar.answer_time, q.difficulty, q.knowledge_point_id
FROM answer_record ar JOIN question q ON q.id = ar.question_id
WHERE ar.user_id=? AND ar.subject_code=?;
-- ③ 错题本行为信号（join 题目定位知识点）
SELECT wb.question_id, wb.mastered, wb.wrong_count, q.knowledge_point_id
FROM wrong_book wb JOIN question q ON q.id = wb.question_id
WHERE wb.user_id=? AND wb.subject_code=?;
```
三条查询取回后**在内存中一次遍历完成聚合**（避免 N+1；与刷题/错题本模块的既有做法一致）。掌握度计算抽成**纯函数**（§六 15.1），便于单测。

### 3.3 `GET /api/student/analysis/trend` 成绩趋势

**入参**：`subjectCode`（必填）、`days`（默认 14，上限 90）

**返回**：`List<TrendPointVO>`，按日期**升序**。

| 字段 | 类型 | 说明 |
|---|---|---|
| `date` | String | `yyyy-MM-dd` |
| `correctRate` | Double | 当日正确率，0~1 |
| `answeredCount` | Integer | 当日答题数 |
| `mockScore` | Integer\|null | 当日模拟考分数：Day16~17 前恒为 null |

**SQL 思路**：
```sql
SELECT DATE(answer_time) AS day, COUNT(*) AS answered, COALESCE(SUM(is_correct),0) AS correct
FROM answer_record
WHERE user_id=? AND subject_code=? AND answer_time >= ?
GROUP BY DATE(answer_time) ORDER BY day;
```

> **只返回"有答题"的日期**（不补齐空白日）——折线按真实活动点连接，避免大量 0 值把趋势压平。前端若需连续横轴，自行按日期补点。
>
> **关于模拟考成绩趋势**：`mock_exam.score` 可作为更强的"成绩"信号，但考试模块 Day16-17 才建。本期**先用练习正确率**；将来可扩展为 `TrendPointVO` 加一个 `mockScore`（可空）字段，前端双系列。

---

## 四、DTO / VO 清单（均放 `pojo` 模块）

| 类 | 位置 | 说明 |
|---|---|---|
| `AnalysisOverviewVO` | `pojo/vo` | 总览（§3.1） |
| `KPStatVO` | `pojo/vo` | 知识点掌握度（§3.2） |
| `TrendPointVO` | `pojo/vo` | 趋势点（§3.3） |
| `WeakReason` | `pojo/enums` | 枚举：`LOW_MASTERY` / `BASE_WEAK`。**纯枚举、不带中文**（文案前端映射），与 `WrongBookCategory` 一致 |

> 无需新 DTO：三个接口入参只有 `subjectCode` / `days`，用 `@RequestParam` 即可。

---

## 五、前端 ECharts 数据结构

### 5.1 总览

非图表：`el-card` / 数字卡片展示 `correctRate`、`progress`、`weakCount`；可用一个**仪表盘**显示正确率。数据即 `AnalysisOverviewVO` 原样使用。

### 5.2 知识点掌握度 —— **横向柱状图**（不用雷达图）

`KPStatVO[]` → 横向条形图，按**掌握度升序**（最薄弱的在最上）：

```js
const sorted = [...kps].sort((a, b) => a.mastery - b.mastery)
yAxis.data  = sorted.map(k => k.name)
series.data = sorted.map(k => Math.round(k.mastery * 100))   // 后端 0~1 → 前端 ×100
// 短板项 k.isWeak 用 itemStyle 单独着色
```

再配一张**短板明细表**：筛出 `isWeak === true` 的行，列 = 知识点 / 掌握度 / 答题数 / `weakReason`。

> **为什么不用雷达图**（军师建议，已采纳）：
> ① 本项目知识点只有 **4 个**，雷达维度不够（一般要 3~8 个才成形）；
> ② 知识点是**父子树**，而雷达要求各维度**相互独立**——父子维度天然重叠。
> 横向柱状图更直观，且"短板在上"一眼可见。
>
> **接口不变**，仍返回 `List<KPStatVO>`，图形形态由前端决定。**具体图表选型在 15.3 前端实现时最终确认。**

### 5.3 成绩趋势 —— **折线图**

```js
xAxis.data = trend.map(t => t.date)
series[0] = { name: '正确率(%)', data: trend.map(t => Math.round(t.accuracy * 100)) }
series[1] = { name: '答题数', data: trend.map(t => t.answeredCount), yAxisIndex: 1 }  // 双 Y 轴
```

---

## 六、实现顺序（15.1 ~ 15.6）

```
15.1  server/analysis/MasteryCalculator（★ 纯逻辑：难度加权 + 时间衰减 + 行为修正，可单测）
      + AnswerPoint / WrongBookStats（输入载体，record） + MasteryCalculatorTest
15.2  pojo：AnalysisOverviewVO / KPStatVO / TrendPointVO / WeakReason（枚举）
15.3  server/mapper/AnalysisMapper（3 组自定义查询）
15.4  server/analysis/AnalysisService + Impl（聚合 + 调 MasteryCalculator + 短板判定；★ 无事务）
15.5  server/controller/AnalysisController（3 接口，路径 /api/student/analysis/**）
15.6  文档 + 测试集合（analysis-api.postman_collection.json）+ 前端图表
```

- 掌握度计算放 **`server.analysis`**（与 `server.ai` 同理，自成一体）。
- **15.1 只做掌握度计算**（`computeMastery` / `computeA` / `computeD` / `computeB`）；**短板判定**依赖 `WeakReason` 枚举（属 15.2），放到 **15.4 服务层**编排。
- 掌握度**在计算器内保持全精度**，`保留 2 位小数` 在 15.2/15.4 组装 VO 时进行。
- 15.1 是**纯计算**：`MasteryCalculatorTest` 直接 `new MasteryCalculator()`（不启 Spring），把 §1.5 与 §二 的算例搬进来当用例。

---

## 七、需要你确认的 7 个点

| # | 问题 | 我的建议 |
|---|---|---|
| ① | 时间衰减半衰期 `H` | **14 天** |
| ② | 行为修正的信号与系数（`+0.15 已攻克率 / −0.25 高频错率`） | **就这两项**，系数取小值只作微调；**不引入** duration 类信号 |
| ③ | 知识点聚合粒度 | **"含子孙"聚合，父子都返回**（雷达用顶层、明细用全量） |
| ④ | 掌握度是否 clamp 到 [0,1] | **clamp**（否则可能 >100%） |
| ⑤ | 趋势是否补齐空白日 | **不补**，只返回有答题的日期；`days` 默认 14、上限 90 |
| ⑥ | 总览是否带模拟考字段（`mockExamCount`/`mockExamAvgScore`/`mockExamPassRate`） | **先带上但为 null**（考试模块未建），前端暂不显示 |
| ⑦ | `weakReason` 用枚举还是字符串 | **枚举**（与 `WrongBookCategory` 风格一致） |

确认后即可开工（建议从 15.1 `MasteryCalculator` 起步）。
