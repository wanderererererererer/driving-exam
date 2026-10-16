# 错题本模块 · 接口测试用例

> 模块：学员端错题本（分类列表 / 统计 / 重练抽题 / 重练作答 / 标记已掌握）
> 前置数据：`sql/seed-question.sql`（18 题）+ `sql/seed-question-extra.sql`（18 题）→ 合计 36 题；知识点 4 个
> 分类口径：`doc/data-conventions.md` §8（**已攻克 > 高频错 > 待攻克**，优先级互斥）
> 参考：刷题模块用例见 `doc/test-cases-practice.md`

---

## 〇、前置准备

### 0.1 Base URL 与登录

```
Base URL: http://localhost:8080
登录：POST /api/auth/login
Body: { "username": "student01", "password": "{{testPassword}}", "role": "STUDENT" }
→ 取 data.token，后续请求头带：Authorization: Bearer <token>
```

### 0.2 造测试数据（**关键前置，必须先做**）

错题本初始为空，**必须先答题答错**才能测。按下表操作，造出三种分类各一条：

| 步骤 | 操作 | 目的 |
|---|---|---|
| ① | 对 **Q1** 提交**错误**答案，重复 **3 次**（正确答案 A，可提交 "B"） | `wrong_count=3 ≥ 阈值` → 造出**高频错** |
| ② | 对 **Q2** 提交**错误**答案 **1 次**（正确答案 B，可提交 "A"） | `wrong_count=1` → 造出**待攻克** |
| ③ | 对 **Q3** 先答错 1 次，再调 `PUT /wrong-book/{id}/mastered`（id 见列表） | 造出**已攻克** |

```sql
-- 用这条 SQL 查 id 与当前状态（把 <UID> 换成 student01 的 id）
SELECT id, question_id, wrong_count, mastered FROM wrong_book WHERE user_id = <UID>;
```

**期望**（造完后）：

| question_id | wrong_count | mastered | 应归属分类 |
|---|---|---|---|
| 1 | 3 | 0 | `HIGH_FREQUENCY` |
| 2 | 1 | 0 | `PENDING` |
| 3 | 1 | 1 | `MASTERED` |

> 想从干净状态重来：`DELETE FROM wrong_book WHERE user_id = <UID>;`（`answer_record` 可一并清）

---

## 一、接口 1：错题列表 `GET /api/student/wrong-book`

### TC-WB-01 不传 category → 返回全部

```
GET /api/student/wrong-book
```

**预期**：`code=200`，`data.total = 3`，`records` 含三条，每条带 `category` 字段。

### TC-WB-02 category=PENDING

```
GET /api/student/wrong-book?category=PENDING
```

**预期**：`total = 1`，唯一一条 `questionId=2`、`category="PENDING"`。

### TC-WB-03 category=HIGH_FREQUENCY

```
GET /api/student/wrong-book?category=HIGH_FREQUENCY
```

**预期**：`total = 1`，`questionId=1`、`category="HIGH_FREQUENCY"`、`wrongCount=3`。

### TC-WB-04 category=MASTERED

```
GET /api/student/wrong-book?category=MASTERED
```

**预期**：`total = 1`，`questionId=3`、`category="MASTERED"`、`mastered=1`。

### TC-WB-05 分页

```
GET /api/student/wrong-book?pageNum=1&pageSize=2
```

**预期**：`total = 3`、`pages = 2`、`records.length = 2`。

> **排序验证**：不传 category 时按 `lastWrongTime` 倒序 → 最近答错的 Q3 应排第一。

---

## 二、接口 2：分类统计 `GET /api/student/wrong-book/summary`

### TC-WB-06 三类计数 + total

```
GET /api/student/wrong-book/summary
```

**预期**：

```json
{ "pending": 1, "highFrequency": 1, "mastered": 1, "total": 3 }
```

**一致性校验**：三项之和必须等于 `total`（互斥分类的必然结果）。

### TC-WB-07 新学员 / 无错题时

（用一个从没答错过的账号，或清空后测）

**预期**：`{"pending":0,"highFrequency":0,"mastered":0,"total":0}` —— **必须是 0，不是 null**
（SQL 里用 `COALESCE` 兜底了 `SUM` 的空集 NULL）。

---

## 三、接口 3：重练抽题 `GET /api/student/wrong-book/retry`

### TC-WB-08 优先高频错（高频够数）

```
GET /api/student/wrong-book/retry?count=1
```

**预期**：`data` 长度为 1，且**必须来自高频错**（当前只有 Q1 是高频率 → 应为 `id=1`）。

### TC-WB-09 高频不够，补待攻克

前置：先执行 `DELETE FROM wrong_book WHERE user_id=<UID> AND question_id=1;`（删掉唯一的错题，避免干扰），
再对 **Q2 提交 3 次错误**造出一条高频错；**Q3 提交 1 次错误**造出一条待攻克。

```
GET /api/student/wrong-book/retry?count=2
```

**预期**：`data` 长度为 **2**，**两条都返回**（1 条高频 + 1 条待攻克都抽到）。

### TC-WB-10 空错题本 → 空数组

（清空 `wrong_book` 后测）

```
GET /api/student/wrong-book/retry?count=10
```

**预期**：`code=200`，`data = []` —— **空数组，不报错、不返回 null**。

### TC-WB-11 防作弊

**预期**：返回的每道题 **无** `answer` / `analysis`，选项 **无** `isCorrect`。

---

## 四、接口 4：重练作答 `POST /api/student/wrong-book/retry/answer`

### TC-WB-12 答对 → **`mastered` 置 1**

前置：确认 Q4（正确答案 B）已在错题本中且 `mastered=0`。

```
POST /api/student/wrong-book/retry/answer
{ "questionId": 4, "userAnswer": "B" }
```

**预期**：`isCorrect = true`。

**落库验证**：

```sql
SELECT question_id, wrong_count, mastered FROM wrong_book WHERE user_id=<UID> AND question_id=4;
-- 期望：mastered = 1，wrong_count 不变
SELECT question_id, user_answer, is_correct, source FROM answer_record WHERE user_id=<UID> ORDER BY id DESC LIMIT 1;
-- 期望：source = 'RETRY'  ← 与普通练习(PRACTICE)区分开
```

### TC-WB-13 答错 → `wrong_count+1`，`mastered` 保持 0

```
POST /api/student/wrong-book/retry/answer
{ "questionId": 2, "userAnswer": "A" }     // 正确答案是 B
```

**预期**：`isCorrect = false`。

**落库验证**：

```sql
SELECT question_id, wrong_count, mastered FROM wrong_book WHERE user_id=<UID> AND question_id=2;
-- 期望：wrong_count 从 1 变 2，mastered 仍为 0，category 仍为 PENDING
```

### TC-WB-14 对比：普通练习答对**不**改 mastered

前置：用一条**未掌握**的错题（Q2，`mastered=0`，正确答案 B）。
**必须从 `mastered=0` 起步**，否则测不出问题（从 1 断言还是 1，说明不了任何事）。

```
POST /api/student/study/answer
{ "questionId": 2, "userAnswer": "B" }     // 答对，但走**普通练习**接口
```

**预期**：`isCorrect = true`，但

```sql
SELECT mastered FROM wrong_book WHERE user_id=<UID> AND question_id=2;
-- 期望：仍为 0  ← 普通练习答对**不会**把它变成已掌握
SELECT source FROM answer_record WHERE user_id=<UID> ORDER BY id DESC LIMIT 1;
-- 期望：source = 'PRACTICE'
```

> 这条用来验证「**只有重练答对才算掌握**」。
> 如果实现有 bug（练习答对也置 `mastered`），这里会看到 `1` —— 而正确答案是 `0`。

---

## 五、接口 5：标记已掌握 `PUT /api/student/wrong-book/{id}/mastered`

### TC-WB-15 标记成功

```
PUT /api/student/wrong-book/{id}/mastered      // {id} 用某条未掌握记录的 id
```

**预期**：`code=200`、`data=null`。

**落库验证**：该条 `mastered` 由 0 变 1；再查 `GET /wrong-book?category=MASTERED` 应包含它。

### TC-WB-16 ❌ 越权保护：操作**别人**的错题

前置：用另一个账号（如 `student02`）造一条错题，拿到它的 `wrong_book.id`。
再用 `student01` 的 token 去 PUT 那个 id。

**预期**：`code=2003`，`message="错题记录不存在"` —— **不是 403**（不泄露"这条记录确实存在、只是不属于你"）。

### TC-WB-17 ❌ 不存在的 id

```
PUT /api/student/wrong-book/999999/mastered
```

**预期**：`code=2003`（与 TC-WB-16 **完全相同的响应**）。

---

## 六、参数与鉴权

### TC-WB-18 ❌ 非法枚举值

```
GET /api/student/wrong-book?category=xxx
```

**预期**：**HTTP 200** + `{"code":1001,"message":"参数类型不匹配：category"}`

> 验证全局异常处理器的 `MethodArgumentTypeMismatchException` 分支生效（**不是 500**）。

### TC-WB-19 ❌ 小写枚举值

```
GET /api/student/wrong-book?category=pending
```

**预期**：同样 `1001`（**枚举大小写敏感**，必须大写 —— 这是有意设计）。

### TC-WB-20 ❌ 鉴权

| 场景 | 预期 |
|---|---|
| 不带 `Authorization` | HTTP **401** + `code=401` |
| 用 ADMIN / COACH 的 token 访问 `/api/student/wrong-book` | HTTP **403** + `code=403` |

---

## 七、验收清单

- [ ] 列表：不传 category 返回全部；三种 category 各自过滤正确
- [ ] 列表排序：高频错按 `wrongCount` 倒序，其余按 `lastWrongTime` 倒序
- [ ] 每条记录都带正确的 `category`（与 §8 规则一致）
- [ ] summary 三项之和 == total；空错题本时全是 0 而非 null
- [ ] retry **优先高频错**，不够才补待攻克；空错题本返回 `[]`
- [ ] retry 返回的题**不含** answer / analysis / isCorrect
- [ ] 重练答对 → `mastered=1`；重练答错 → `wrongCount+1` 且 `mastered=0`
- [ ] 重练的 `answer_record.source='RETRY'`；普通练习为 `'PRACTICE'`
- [ ] 普通练习答对**不影响** `mastered`
- [ ] `PUT /{id}/mastered` 成功；他人/不存在的 id 均返回 `2003`
- [ ] 非法/小写 category → `1001`（HTTP 200，不是 500）
- [ ] 无 token → 401；跨角色 → 403
