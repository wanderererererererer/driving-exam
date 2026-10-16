# 刷题模块 · 接口测试用例

> 模块：学员端刷题（顺序 / 章节 / 随机练习 + 判分 + 错题归集）
> 前置数据：`sql/seed-question.sql`（18 题）+ `sql/seed-question-extra.sql`（18 题）→ **合计 36 题**；知识点 4 个
> 工具：Apifox / Postman

---

## 〇、前置准备

### 0.1 启动与基础地址

```
Base URL: http://localhost:8080
所有刷题接口前缀: /api/student/study
```

### 0.2 执行种子数据

在 DataGrip 执行 `sql/seed-question.sql`（脚本末尾 3 条自检 SELECT 应显示：单选 7 / 多选 4 / 判断 7，选项 **44** 行 —— 判断题不存选项）。

### 0.3 可选：清空该学员的历史作答（想从干净状态开始时执行）

```sql
-- 把 <UID> 换成 student01 的 id
DELETE FROM answer_record WHERE user_id = <UID>;
DELETE FROM wrong_book    WHERE user_id = <UID>;
```

### 0.4 登录拿 token

```
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{ "username": "student01", "password": "{{testPassword}}", "role": "STUDENT" }
```

返回 `data.token`。后续所有请求加请求头：

```
Authorization: Bearer <token>     ← Bearer 后有一个空格
```

---

## 一、**公共检查项**（每个查询接口都适用）

| 检查项 | 说明 |
|---|---|
| 统一返回 | 响应体形如 `{ "code": 200, "message": "操作成功", "data": ... }` |
| **防作弊** | 题目相关返回里**不得出现** `answer`、`analysis`、`options[].isCorrect` |
| 分页结构 | `data` 含 `total / pages / pageNum / pageSize / records` |
| 判断题选项 | **JUDGE 题目的 `options` 是空数组**（按设计不存选项，前端用两个固定按钮） |

---

## 二、接口用例

### TC-01 知识点树 ✅ 正常

```
GET /api/student/study/knowledge-points?subjectCode=SUBJECT_1
```

**预期**：`code=200`，`data` 为**2 个根节点**：

| id | name | questionCount | children |
|---|---|---|---|
| 1 | 交通信号 | **22** | [2(交通信号灯), 3(交通标志标线)] |
| 4 | 安全行车常识 | **14** | [] |

**关键验证点**：`id=1` 自身只挂 **2** 题，但 `questionCount=22` = 2(自身) + 12(信号灯) + 8(标志标线) → **证明"含子孙累加"生效**。

---

### TC-02 知识点树 ❌ 缺必填参数

```
GET /api/student/study/knowledge-points
```

**预期**：`code=1001`，`message` 含 `subjectCode`（走全局异常处理器）。

---

### TC-03 顺序练习 ✅ 默认分页

```
GET /api/student/study/questions/sequence?subjectCode=SUBJECT_1
```

**预期**：`code=200`
- `data.total = 36`
- `data.pageNum = 1`，`data.pageSize = 10` ← **未传分页参数，默认值生效**
- `data.pages = 4`
- `data.records.length = 10`
- 每条记录：无 `answer` / `analysis`；`options` 内无 `isCorrect`

---

### TC-04 顺序练习 ✅ 指定分页

```
GET /api/student/study/questions/sequence?subjectCode=SUBJECT_1&pageNum=2&pageSize=5
```

**预期**：`total=36`，`pages=8`，`pageNum=2`，`pageSize=5`，`records.length=5`。

---

### TC-05 章节练习 ✅ 父知识点（含子孙）

```
GET /api/student/study/questions/chapter?knowledgePointId=1
```

**预期**：`data.total = 22`（= 自身 2 题 + 章节 2 的 12 题 + 章节 3 的 8 题，含子孙）

对比：
```
GET /api/student/study/questions/chapter?knowledgePointId=2   → total = 12
GET /api/student/study/questions/chapter?knowledgePointId=4   → total = 14
```

**关键验证点**：同一个"章节练习"接口，传父节点拿到的是**整个子树**的题，传子节点只拿自己的。

---

### TC-06 章节练习 ❌ 知识点不存在

```
GET /api/student/study/questions/chapter?knowledgePointId=9999
```

**预期**：`code=2001`，`message="知识点不存在"`。

---

### TC-07 随机练习 ✅

```
GET /api/student/study/questions/random?subjectCode=SUBJECT_1&count=5
```

**预期**：`code=200`，`data` 为**数组**，长度 = 5。

**验证点**：连续请求 3 次，题目组合/顺序应**不固定**（随机生效）。

---

### TC-08 随机练习 ✅ count 超过题库

```
GET /api/student/study/questions/random?subjectCode=SUBJECT_1&count=100
```

**预期**：`data.length = 18`（不会报错，取满为止）。

---

### TC-09 题目详情 ✅

```
GET /api/student/study/questions/1
```

**预期**：`code=200`
- `data.id=1`，`data.questionType="SINGLE"`
- `data.options` 有 **4** 个，含 `optionKey`/`optionContent`
- ⚠️ **不得有** `answer`、`analysis`、`options[].isCorrect`
- 有 `knowledgePointName`（="交通信号灯"）

---

### TC-10 题目详情 ❌ 不存在

```
GET /api/student/study/questions/9999
```

**预期**：`code=2002`，`message="题目不存在"`。

---

### TC-11 提交作答 ✅ 单选正确

```
POST /api/student/study/answer
Content-Type: application/json

{ "questionId": 1, "userAnswer": "A" }
```

**预期**：`code=200`
```json
{ "questionId": 1, "isCorrect": true, "userAnswer": "A", "correctAnswer": "A", "analysis": "红灯亮时禁止通行..." }
```

**落库验证**：`answer_record` 新增 1 条，`question_id=1`、`user_answer='A'`、`is_correct=1`、`source='PRACTICE'`、`exam_id` 为 NULL。

---

### TC-12 提交作答 ✅ 小写容错

```
{ "questionId": 1, "userAnswer": "a" }
```

**预期**：`isCorrect = true`。

---

### TC-13 提交作答 ❌ 答错 → **写错题本**

```
{ "questionId": 1, "userAnswer": "B" }
```

**预期**：`isCorrect=false`，`correctAnswer="A"`，`analysis` 有内容。

**落库验证**：
- `answer_record` 该条 `is_correct=0`
- `wrong_book` **新增**一条：`question_id=1`、`wrong_count=1`、`mastered=0`

---

### TC-14 重复答错 → **错题次数累加**（upsert）

```
{ "questionId": 1, "userAnswer": "C" }
```

**预期**：`isCorrect=false`。

**落库验证**：`wrong_book` 里 `question_id=1` **仍然只有 1 行**，但 `wrong_count` 从 1 变成 **2**（`ON DUPLICATE KEY UPDATE` 生效，没有重复插入）。

---

### TC-15 全角归一化（NFKC）✅

第 6 题的标准答案在种子里**故意写成全角 `Ａ`**。

```
{ "questionId": 6, "userAnswer": "A" }     → isCorrect = true   ← 半角答全角
{ "questionId": 6, "userAnswer": "Ａ" }    → isCorrect = true   ← 全角答全角
{ "questionId": 6, "userAnswer": "a" }     → isCorrect = true
```

**关键验证点**：若**没做 NFKC**，`"A"` 会判错；现在全对 → NFKC 生效。

---

### TC-16 多选分隔符归一化 ✅

第 9 题的标准答案在种子里**故意写成 `A,B`**。

```
{ "questionId": 9, "userAnswer": "AB" }    → true   （答案含逗号，用户不含）
{ "questionId": 9, "userAnswer": "BA" }    → true   （顺序颠倒）
{ "questionId": 9, "userAnswer": "A,B" }   → true   （全角/半角分隔符）
{ "questionId": 9, "userAnswer": "A" }     → false  （少选，全对才算对）
```

---

### TC-17 多选 ❌ 多选

```
{ "questionId": 8, "userAnswer": "ABC" }   // 正确答案是 AB
```

**预期**：`isCorrect=false`（多选也算错，不给部分分）。

---

### TC-18 判断 ✅ 同义映射

```
{ "questionId": 12, "userAnswer": "对" }   → true    （标准答案 = 对）
{ "questionId": 12, "userAnswer": "T" }    → true    （T 映射为 对）
{ "questionId": 12, "userAnswer": "√" }    → true    （√ 映射为 对）
{ "questionId": 12, "userAnswer": "错" }   → false
{ "questionId": 12, "userAnswer": "F" }    → false   （F 映射为 错）
```

---

### TC-19 ❌ 参数校验

```
POST /api/student/study/answer
{ }                          → code=1001（questionId 不能为空 / 答案不能为空）
{ "questionId": 1 }          → code=1001（答案不能为空）
```

---

### TC-20 ❌ 鉴权（越权防护）

| 场景 | 预期 |
|---|---|
| 不带 `Authorization` 头 | **HTTP 401** + `{"code":401,"message":"未认证或登录已失效"}` |
| 用 **ADMIN/COACH** 的 token 访问 `/api/student/**` | **HTTP 403** + `{"code":403,"message":"无权限访问"}` |
| 乱写一个 Bearer token | HTTP 401 |

---

## 三、落库核对 SQL

```sql
-- 换 <UID> 为 student01 的 id

-- 1. 答题记录（按时间倒序看最近几条）
SELECT id, user_id, subject_code, question_id, user_answer, is_correct, source, exam_id, duration, answer_time
  FROM answer_record WHERE user_id = <UID> ORDER BY id DESC LIMIT 10;

-- 2. 错题本
SELECT id, user_id, subject_code, question_id, wrong_count, mastered, last_wrong_time
  FROM wrong_book WHERE user_id = <UID> ORDER BY id DESC;

-- 3. 抽查：某题的作答是否正确（应与接口返回一致）
SELECT COUNT(*) AS total, SUM(is_correct) AS correct_cnt
  FROM answer_record WHERE user_id = <UID> AND question_id = 1;
```

---

## 四、验收标准（全部通过即联调达标）

- [ ] 6 个接口全部返回 `code=200`（错误用例返回对应业务码）
- [ ] 题目相关返回**不含** `answer` / `analysis` / `isCorrect`
- [ ] 分页默认值与自定义值都正确
- [ ] 章节练习**含子孙**取题（kp1=22、kp2=12）
- [ ] TC-15 全角、TC-16 分隔符**判对**（归一化生效）
- [ ] 答错写 `wrong_book`，重复答错 `wrong_count` 累加而非新增行
- [ ] 无 token 401、跨角色 403
- [ ] `answer_record.source` 恒为 `PRACTICE`
