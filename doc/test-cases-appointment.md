# 预约模块 · 接口测试用例

> 模块：学员端 / 教练端**练车预约闭环**（科二 / 科三共用）
> 接口：`doc/api-design.md` §4.8（学员端 5 个）+ §4.10「预约处理」（教练端 4 个），共 **9 个**
> 设计：`doc/appointment-design.md`
> 集合：`doc/testjson/appointment-api.postman_collection.json`（本文件与集合**编号一一对应**）
> 参考：结构对齐 `doc/test-cases-wrong-book.md`

本文档覆盖 **9 个接口 × 5 个状态 × 7 类错误码（3001~3005 + 1001 + 401/403）**，共 **33 条用例**。
答辩时可用「**33 条 TC 全绿 + 5 状态落库证据**」说明测试完备性。

---

## 〇、前置准备

### 0.1 Base URL 与账号

```
Base URL: http://localhost:8080
登录：POST /api/auth/login
Body: { "username": "<账号>", "password": "{{testPassword}}", "role": "<角色>" }
→ 取 data.token，后续请求头带：Authorization: Bearer <token>
```

| 账号 | 角色 | 用途 | 绑定关系 |
|---|---|---|---|
| `student01` | STUDENT | 主学员（发起预约） | 绑定 `coach01`（status=1） |
| `coach01` | COACH | 教练（approve / reject / complete） | — |
| `student02` | STUDENT | **越权**（AP-23）/ **未绑定空数组**（AP-02） | **无任何有效绑定** |

### 0.2 准备 SQL（清库 + 造账号 + 建绑定）

**见集合 `doc/testjson/appointment-api.postman_collection.json` 的 description**——那里是**唯一真源**，含：

- ① 清库：`DELETE FROM appointment;`（⚠️ **会清空 appointment 全表**，跑前确认无未备份的手动数据）
- ② 幂等造 `coach01` / `student02`（BCrypt 密文由本地生成 / 集合提供，仓库不保存）
- ③ 建立 `student01 ↔ coach01` 有效绑定；置 `student02` 无有效绑定

> 本文档**不重复**这段 SQL，避免与集合产生「两张皮」。

### 0.3 执行顺序与依赖（**必须按集合顺序跑**）

```
组 0 登录 → 组 1 我的教练（取 coachId）→ 组 2 主链路 → 组 3 拒绝链路 → 组 4 错误路径
```

组 4 内有一条**夹具链**，顺序即依赖——中间只夹了不写库的 AP-25，不影响：

```
AP-24a（建 06-03 占用）→ AP-24b（取 fixtureId）→ AP-24（3002）
→ AP-26a（approve → CONFIRMED）→ AP-26（3005）→ AP-27（3004）
```

数据基线：业务日期固定 **2027-06-01 / 06-02 / 06-03**（远未来，避开 `@FutureOrPresent` 过期），过去日期用例用 **2020-01-01**；`coachId` 由 AP-01 动态提取，不硬编码。

---

## 一、组 1：我的教练

### TC-AP-01 有绑定 → 返回教练列表

**前置**：`student01` 有效绑定 `coach01`（status=1）。

```
GET /api/student/appointment/coaches      （Bearer {{studentToken}}）
```

**预期**：`code=200`；`data` 为数组且非空；元素含 `coachId` / `realName` / `phone`；提取 `data[0].coachId` 存入 `{{coachId}}`（供后续创建用）。
> 刻意**不**断言 `realName` 的具体取值——那是数据值、不是系统行为（`coach01` 可能已在库中，姓名以库中为准）。

### TC-AP-02 未绑定 → 空数组（★边界）

**前置**：`student02` 无任何有效绑定。

```
GET /api/student/appointment/coaches      （Bearer {{token02}}）
```

**预期**：`code=200`、`data=[]` —— **空数组，不报错、不抛 3003**（3003 只用于「创建时指定的教练不是我的」）。

---

## 二、组 2：主链路（创建 → 列表 → 详情 → 取消 → 再创建 → approve → complete）

### TC-AP-03 创建预约 · 成功

**前置**：已清库；`{{coachId}}` 已取。

```
POST /api/student/appointment      （Bearer {{studentToken}}）
{ "coachId": {{coachId}}, "subjectCode": "SUBJECT_2",
  "appointmentDate": "2027-06-01", "timeSlot": "08:00-10:00", "remark": "主链路测试" }
```

**预期**：`code=200`、`data=null` —— **返回 Void**（不返回新建 id，前端创建成功后需重新拉列表）。

### TC-AP-04 我的预约列表 · 提取 id（★ PENDING 落点）

**前置**：TC-AP-03 已创建 1 条。

```
GET /api/student/appointment?pageNum=1&pageSize=10      （Bearer {{studentToken}}）
```

**预期**：`records` 非空；`records[0].status="PENDING"`；装配了 `studentName` / `coachName`；排序按 `create_time DESC, id DESC`（最新创建的排最前）→ 取 `records[0].id` 存入 `{{appointmentId}}`。

**落库验证**：

```sql
SELECT id, status, time_slot, coach_remark FROM appointment
 WHERE student_id = (SELECT id FROM sys_user WHERE username='student01')
 ORDER BY id DESC LIMIT 1;
-- 期望：status = 'PENDING'
```

### TC-AP-05 预约详情 · status = PENDING

```
GET /api/student/appointment/{{appointmentId}}      （Bearer {{studentToken}}）
```

**预期**：`status="PENDING"`；字段完整（`id / studentId / coachId / subjectCode / appointmentDate / timeSlot / status`）；`appointmentDate="2027-06-01"`。

### TC-AP-06 取消预约 · PENDING → 成功

```
PUT /api/student/appointment/{{appointmentId}}/cancel      （Bearer {{studentToken}}）
```

**预期**：`code=200`、`data=null`。

### TC-AP-07 详情复核 · status = CANCELLED（★ CANCELLED 落点）

```
GET /api/student/appointment/{{appointmentId}}      （Bearer {{studentToken}}）
```

**预期**：`status="CANCELLED"`（由 PENDING 变更，证明写库生效）。

**落库验证**：

```sql
SELECT id, status FROM appointment WHERE id = <appointmentId>;
-- 期望：status = 'CANCELLED'
```

### TC-AP-08 再创建同一时段 · 应成功（★ 终态释放时段）

**前置**：上一条同（教练 / 日期 / 时段）的预约已 `CANCELLED`（终态）。

```
POST /api/student/appointment      （Bearer {{studentToken}}）
{ "coachId": {{coachId}}, "subjectCode": "SUBJECT_3",
  "appointmentDate": "2027-06-01", "timeSlot": "08:00-10:00", "remark": "终态释放验证" }
```

**预期**：`code=200` —— **终态释放了时段，可再次预约**。
> 若实现把终态也算「占用」（应交集合 `{PENDING, CONFIRMED}` 却含了 CANCELLED），此处会误报 `3002`。

### TC-AP-09 列表 · 提取新 id

```
GET /api/student/appointment?pageNum=1&pageSize=10      （Bearer {{studentToken}}）
```

**预期**：`records[0].status="PENDING"`（刚重建的）；取 `records[0].id` 存入 `{{appointmentId2}}`。

### TC-AP-10 教练侧列表 · 可见该预约 + 学员姓名

```
GET /api/coach/appointments?pageNum=1&pageSize=10      （Bearer {{coachToken}}）
```

**预期**：`records` 非空；最新一条 `status="PENDING"`；**教练端也装配了 `studentName`**（两端共用一个 VO）。

### TC-AP-11 教练 approve · PENDING → 成功

```
PUT /api/coach/appointments/{{appointmentId2}}/approve      （Bearer {{coachToken}}）
```

**预期**：`code=200`、`data=null`。

### TC-AP-12 详情复核 · status = CONFIRMED（★ CONFIRMED 落点）

```
GET /api/student/appointment/{{appointmentId2}}      （Bearer {{studentToken}}）
```

**预期**：`status="CONFIRMED"`。

**落库验证**：

```sql
SELECT id, status FROM appointment WHERE id = <appointmentId2>;
-- 期望：status = 'CONFIRMED'
```

### TC-AP-13 教练 complete · CONFIRMED → 成功

```
PUT /api/coach/appointments/{{appointmentId2}}/complete      （Bearer {{coachToken}}）
```

**预期**：`code=200`、`data=null`。

### TC-AP-14 详情复核 · status = COMPLETED（★ COMPLETED 落点）

```
GET /api/student/appointment/{{appointmentId2}}      （Bearer {{studentToken}}）
```

**预期**：`status="COMPLETED"` —— 主链路终点。

**落库验证**：

```sql
SELECT id, status FROM appointment WHERE id = <appointmentId2>;
-- 期望：status = 'COMPLETED'（同一条记录走过 PENDING→CONFIRMED→COMPLETED）
```

---

## 三、组 3：拒绝链路（创建 → reject → REJECTED）

### TC-AP-15 创建预约 · 成功（2027-06-02 / 10:00-12:00）

```
POST /api/student/appointment      （Bearer {{studentToken}}）
{ "coachId": {{coachId}}, "subjectCode": "SUBJECT_2",
  "appointmentDate": "2027-06-02", "timeSlot": "10:00-12:00", "remark": "拒绝链路测试" }
```

**预期**：`code=200`。

### TC-AP-16 列表 · 提取 appointmentId3

```
GET /api/student/appointment?pageNum=1&pageSize=10      （Bearer {{studentToken}}）
```

**预期**：`records[0].status="PENDING"`；取 `records[0].id` 存入 `{{appointmentId3}}`。

### TC-AP-17 教练 reject · 带拒绝原因

```
PUT /api/coach/appointments/{{appointmentId3}}/reject      （Bearer {{coachToken}}）
{ "coachRemark": "当日有事" }
```

**预期**：`code=200`、`data=null`。拒绝落 **REJECTED**（与学员取消的 CANCELLED 区分）。

### TC-AP-18 详情复核 · REJECTED + coachRemark（★ REJECTED 落点·带原因）

```
GET /api/student/appointment/{{appointmentId3}}      （Bearer {{studentToken}}）
```

**预期**：`status="REJECTED"`（**不是 CANCELLED**）；`coachRemark="当日有事"`。

**落库验证**：

```sql
SELECT id, status, coach_remark FROM appointment WHERE id = <appointmentId3>;
-- 期望：status = 'REJECTED'，coach_remark = '当日有事'
```

### TC-AP-19 再创建同一时段 · 应成功（★ REJECTED 也释放）

```
POST /api/student/appointment      （Bearer {{studentToken}}）
{ "coachId": {{coachId}}, "subjectCode": "SUBJECT_2",
  "appointmentDate": "2027-06-02", "timeSlot": "10:00-12:00", "remark": "REJECTED 释放验证" }
```

**预期**：`code=200` —— REJECTED 同为终态 → 时段释放。

### TC-AP-20a 前置 · 列表取 appointmentId4

```
GET /api/student/appointment?pageNum=1&pageSize=10      （Bearer {{studentToken}}）
```

**预期**：`records[0].status="PENDING"`（TC-AP-19 刚建的）；取 `records[0].id` 存入 `{{appointmentId4}}`。

### TC-AP-20 教练 reject · 不填原因（body = `{}`）

```
PUT /api/coach/appointments/{{appointmentId4}}/reject      （Bearer {{coachToken}}）
{}
```

**预期**：`code=200` —— `coachRemark` 可空，`{}` 即不填原因。
> 注意：`{}`（**有 body、内容为空**）与 AP-33（**完全不传 body**）是**两条不同分支**，见 AP-33。

### TC-AP-21 详情复核 · REJECTED 且 coachRemark = null（★ REJECTED 落点·无原因）

```
GET /api/student/appointment/{{appointmentId4}}      （Bearer {{studentToken}}）
```

**预期**：`status="REJECTED"`；`coachRemark=null`。

**落库验证**：

```sql
SELECT id, status, coach_remark FROM appointment WHERE id = <appointmentId4>;
-- 期望：status = 'REJECTED'，coach_remark IS NULL
```

---

## 四、组 4：错误路径（3001 / 3002 / 3003 / 3004 / 3005 / 1001 / 401 / 403）

> 本组按顺序跑；夹具链见 §0.3。

### TC-AP-22 ❌ 3001 · 不存在的 id

```
GET /api/student/appointment/999999      （Bearer {{studentToken}}）
```

**预期**：**HTTP 200** + `code=3001`。

### TC-AP-23 ❌ 3001 · 越权访问他人预约（与 AP-22 同响应）

**前置**：`{{appointmentId}}` 属于 `student01`。

```
GET /api/student/appointment/{{appointmentId}}      （Bearer {{token02}}）
```

**预期**：HTTP 200 + `code=3001`，与 AP-22 **完全相同**——不泄露「这条确实存在、只是不属于你」。
> 维度说明：本条测**越权**维度（谁的记录）；AP-27 测**状态**维度（能不能操作）。两者分离。

### TC-AP-24a 前置 · 建夹具占用（2027-06-03 / 14:00-16:00）

```
POST /api/student/appointment      （Bearer {{studentToken}}）
{ "coachId": {{coachId}}, "subjectCode": "SUBJECT_2",
  "appointmentDate": "2027-06-03", "timeSlot": "14:00-16:00", "remark": "错误路径夹具" }
```

**预期**：`code=200`（建立一条 PENDING 占用，供 3002 / 3005 / 3004 复用）。

### TC-AP-24b 前置 · 列表取 fixtureId

```
GET /api/student/appointment?pageNum=1&pageSize=10      （Bearer {{studentToken}}）
```

**预期**：`records[0].status="PENDING"`；取 id 存入 `{{fixtureId}}`。

### TC-AP-24 ❌ 3002 · 时段冲突（同时段再创建）

```
POST /api/student/appointment      （Bearer {{studentToken}}）
{ "coachId": {{coachId}}, "subjectCode": "SUBJECT_3",
  "appointmentDate": "2027-06-03", "timeSlot": "14:00-16:00", "remark": "应当冲突" }
```

**预期**：HTTP 200 + `code=3002`（该时段已被占用）。

### TC-AP-25 ❌ 3003 · 未绑定教练（coachId = 999999）

```
POST /api/student/appointment      （Bearer {{studentToken}}）
{ "coachId": 999999, "subjectCode": "SUBJECT_2",
  "appointmentDate": "2027-06-01", "timeSlot": "08:00-10:00", "remark": "未绑定" }
```

**预期**：HTTP 200 + `code=3003`。
> 校验顺序：**绑定校验(3003) 先于 冲突校验(3002)**——本用例证明该顺序。

### TC-AP-26a 前置 · approve 夹具 → CONFIRMED

```
PUT /api/coach/appointments/{{fixtureId}}/approve      （Bearer {{coachToken}}）
```

**预期**：`code=200`（夹具由 PENDING 转 CONFIRMED）。

### TC-AP-26 ❌ 3005 · 非 PENDING 再 approve

```
PUT /api/coach/appointments/{{fixtureId}}/approve      （Bearer {{coachToken}}）
```

**预期**：HTTP 200 + `code=3005`（当前状态不允许此操作）。

### TC-AP-27 ❌ 3004 · 非 PENDING 取消（学员取消已 CONFIRMED）

```
PUT /api/student/appointment/{{fixtureId}}/cancel      （Bearer {{studentToken}}）
```

**预期**：HTTP 200 + `code=3004`（仅可取消待确认的预约）。
> token 必须是 **student01 的 `{{studentToken}}`**——夹具由 student01 创建。若误用 `{{token02}}`，`WHERE id=? AND student_id=?` 收敛到 0 行 → 返回 **3001**（那是越权维度，不是本条要测的状态维度）。

### TC-AP-28 ❌ 1001 · 小写 status（枚举大小写敏感）

```
GET /api/student/appointment?status=pending      （Bearer {{studentToken}}）
```

**预期**：HTTP 200 + `code=1001`（`MethodArgumentTypeMismatchException`，**不是 500**）。
> 大小写敏感是**有意设计**（传错值立刻报错，而不是静默返回全部）。

### TC-AP-29 ❌ 1001 · 非法时段（不在四个固定值内）

```
POST /api/student/appointment      （Bearer {{studentToken}}）
{ "coachId": {{coachId}}, "subjectCode": "SUBJECT_2",
  "appointmentDate": "2027-06-01", "timeSlot": "09:00-11:00", "remark": "非法时段" }
```

**预期**：HTTP 200 + `code=1001`（`@Pattern` 拦截）。
> 固定四值：`08:00-10:00` / `10:00-12:00` / `14:00-16:00` / `16:00-18:00`。

### TC-AP-30 ❌ 1001 · 过去日期（@FutureOrPresent）

```
POST /api/student/appointment      （Bearer {{studentToken}}）
{ "coachId": {{coachId}}, "subjectCode": "SUBJECT_2",
  "appointmentDate": "2020-01-01", "timeSlot": "08:00-10:00", "remark": "过去日期" }
```

**预期**：HTTP 200 + `code=1001`（`@FutureOrPresent` 接受今天，仅拒绝过去）。

### TC-AP-31a ❌ 鉴权 · 无 token → HTTP 401

```
GET /api/coach/appointments      （不带 Authorization）
```

**预期**：**真实 HTTP 401** + `code=401`。
> ⚠️ 见集合 description 的「已知假阳性」——若项目开了「默认断言：状态码=200」，此条会【必然报红】，属工具假阳性。

### TC-AP-31b ❌ 鉴权 · 学员 token 访问教练端 → HTTP 403

```
GET /api/coach/appointments      （Bearer {{studentToken}}，STUDENT）
```

**预期**：**真实 HTTP 403** + `code=403`（`PathRoleRule` 拦截）。同上，注意假阳性。

### TC-AP-32 ❌ 1001 · 非法科目（SUBJECT_1 不属预约范围）

```
POST /api/student/appointment      （Bearer {{studentToken}}）
{ "coachId": {{coachId}}, "subjectCode": "SUBJECT_1",
  "appointmentDate": "2027-06-01", "timeSlot": "08:00-10:00", "remark": "非法科目" }
```

**预期**：HTTP 200 + `code=1001`（预约仅限 `SUBJECT_2 / SUBJECT_3`）。

### TC-AP-33 ❌ 1001 · 不传 body（缺请求体）

```
POST /api/student/appointment      （Bearer {{studentToken}}）
（不带 Content-Type，且不发请求体）
```

**预期**：HTTP 200 + `code=1001`（**不是 500**）。
> ★ 本条覆盖的是 **`HttpMessageNotReadableException` → 1001** 这条分支，**不是** DTO 校验失败的 1001——两者错误码相同、触发路径不同。区别：**完全不传 body**（本条）vs 传 `{}`（AP-20/29/30/32 走 `@Valid` 校验）。

---

## 五、验收清单

### 5.1 功能维度 —— 9 个接口各 ≥ 1 条

- [ ] `GET /api/student/appointment/coaches`（我的教练）—— AP-01（有绑定）、AP-02（未绑定空数组）
- [ ] `POST /api/student/appointment`（创建）—— AP-03 / 08 / 15 / 19 / 24a
- [ ] `GET /api/student/appointment`（我的列表，分页）—— AP-04 / 09 / 16
- [ ] `GET /api/student/appointment/{id}`（详情）—— AP-05 / 07 / 12 / 14 / 18 / 21
- [ ] `PUT /api/student/appointment/{id}/cancel`（取消）—— AP-06 / 27
- [ ] `GET /api/coach/appointments`（教练列表）—— AP-10
- [ ] `PUT /api/coach/appointments/{id}/approve`（通过）—— AP-11 / 26a / 26
- [ ] `PUT /api/coach/appointments/{id}/reject`（拒绝）—— AP-17 / 20
- [ ] `PUT /api/coach/appointments/{id}/complete`（完成）—— AP-13

### 5.2 状态机维度 —— 5 个状态全覆盖（均附落库 SQL）

- [ ] **PENDING** —— AP-04
- [ ] **CONFIRMED** —— AP-12
- [ ] **CANCELLED** —— AP-07
- [ ] **REJECTED** —— AP-18（带原因）、AP-21（无原因）
- [ ] **COMPLETED** —— AP-14
- [ ] 终态释放时段：CANCELLED 后（AP-08）、REJECTED 后（AP-19）均可再创建

### 5.3 错误码维度 —— 全覆盖

- [ ] **3001** 预约不存在 / 不属于本人 —— AP-22、AP-23
- [ ] **3002** 时段已被占用 —— AP-24
- [ ] **3003** 未绑定教练 —— AP-25
- [ ] **3004** 非 PENDING 取消 —— AP-27
- [ ] **3005** 非 PENDING 审批 —— AP-26
- [ ] **1001** 参数错误 —— AP-28（小写 status）、AP-29（非法时段）、AP-30（过去日期）、AP-32（非法科目）、AP-33（缺 body）
- [ ] **401 / 403** 鉴权与越权 —— AP-31a、AP-31b
