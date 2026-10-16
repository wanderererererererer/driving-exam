# 教练端 · 学员管理 接口测试用例

> 模块：教练端学员管理（我的学员 / 绑定或创建 / 改学情 / 解绑 / 四科状态）
> 接口：`doc/api-design.md` §4.10「学员管理」；设计：`doc/coach-student-design.md`
> 集合：`doc/testjson/coach-student-api.postman_collection.json`（**编号一一对应**）
> 准备 SQL：`doc/testsql/coach-student-test-setup.sql`（**草案，需人工审阅后手动执行**）；独立只读预检查：`doc/testsql/coach-student-precheck.sql`
> 参考：结构对齐 `doc/test-cases-appointment.md`

> **状态说明**：本集合已于 **2026-10-13** 由用户在本地**执行完毕**（Apifox v2.8.40，报告 `doc/Test/coach-student.html`）。**执行结果**：HTTP 请求 **49** 个（其中 3 个为预期的鉴权用例 `TC-CS-16a/16b/16c`，实际 HTTP 401/403）、**断言 133 条全部通过、0 条失败**；详见 `doc/Day/Day19-工作日志.md` §六。下方"预期"保留为**代码推导**口径。

---

## 〇、前置准备

### 0.1 Base URL 与账号

```
Base URL: http://localhost:8080
登录：POST /api/auth/login   Body: { "username": "<账号>", "password": "{{testPassword}}", "role": "<角色>" }
→ 取 data.token 放入集合变量（见下表）。登录口令取自本地配置（集合变量 testPassword），仓库不保存真实口令。
```

| 账号 | 角色 | 用途 | 集合变量 | 现状（测试执行前） |
|---|---|---|---|---|
| `coach01` | COACH | 教练A：主流程 | `coachToken` | 已存在（预约测试在用） |
| `coach02` | COACH | 教练B：越权夹具持有者 | `coachBToken` | 已存在（准备 SQL 已建立） |
| `coach03` | COACH | 教练C：空列表 | `coachCToken` | 已存在（**无任何绑定**） |
| `student01` | STUDENT | 403 用例；**既有绑定受保护** | `studentToken` | 已存在 |
| `student02` | STUDENT | "绑定已有学员"目标 | —— | 已存在（预约测试在用） |
| `student_cs01` / `student_cs02` | STUDENT | 专用学员 | —— | 已存在（准备 SQL 已建立） |

> **当前绑定（测试执行前已确认）**：`coach01 ↔ student01`（bind_id=1）、`coach01 ↔ student_cs01`、`coach02 ↔ student_cs02` 均 `status=1`；`coach03` **无绑定**；`student_cs01` **无学情关联记录**；`cs_new_01` **不存在**（集合中 `TC-CS-06` 会尝试创建）。准备 SQL 若重跑，其账号/绑定 INSERT 均为 **no-op（存在即跳过 / 仅组合不存在时插入）**。

> **⚠️ 硬约束**：`coach01 ↔ student01` 是**既有有效绑定**（bind_id=1），本模块用例**全程不得修改、解绑或删除**它；准备 SQL 也刻意不碰它（对**任何**既有绑定都不改、不解绑、不删除、不复活）。集合准备组 `00e` 会断言它仍存在。

### 0.2 准备 SQL

准备脚本：`doc/testsql/coach-student-test-setup.sql`；**独立只读预检查**：`doc/testsql/coach-student-precheck.sql`（**仅 SELECT、可整段执行**）。

**执行流程（务必遵守）**：① 先**整段执行独立预检查文件**并核对全部「期望」→ ② 回到主脚本，**仅分块选中**执行**第 1 步**、**第 2 步**（**禁止对主脚本用 Execute All**）→ ③ 最后单独运行**第 4 步**只读自检。

要点（**执行前请审阅两文件全文**）：

- **不触碰 `appointment` 表**（不 INSERT/UPDATE/DELETE/TRUNCATE/DROP）；
- **不清空 `coach_student` 全表**，对**任何既有绑定行不修改、不解绑、不删除、不复活**（含 `coach01↔student01`、`coach01↔student02` 及全部既有绑定）；
- 账号创建**幂等**（`WHERE NOT EXISTS`），**绝不覆盖**既有账号资料；同名但角色不符 → **停止并人工处理**；
- **绑定前校验账号有效性**：两条绑定 INSERT 同时校验两侧账号 **`role` 正确、`status=1`、`deleted=0`**（教练 `COACH` / 学员 `STUDENT`）；**角色不符、被禁用（`status≠1`）或被逻辑删除的账号一律不会被建绑定**，需**人工处理**（脚本**不自动改角色、不改状态、不恢复逻辑删除账号**）；
- **强制执行流程**：写步骤（**第 1、2 步**）**不得直接整段执行**——必须先整段运行**独立预检查文件** `coach-student-precheck.sql`，人工核对其中每个专用用户名的「存在 / `role` / `status` / `deleted`」与**两组测试绑定是否已存在**（全部通过）后，再分块执行第 1、2 步；SQL 文件本身**无法**强制阻止「Execute All」，该流程**靠人工遵守**；
- 建立两组绑定：`coach01↔student_cs01`（主）、`coach02↔student_cs02`（越权夹具）——**仅当该「教练 + 学员」组合尚无绑定行时插入**；若已存在（`status=0` 或 `1`）则**什么都不做**、**绝不把 `status=0` 自动改回 1**；`coach03` **不建绑定**（空列表前置）；
- **测试绑定已存在时的处理**：独立预检查文件的 **P4** 会列出两组测试绑定的实际 `status`；若见到 `status=0`，**停止**并**人工**确认后再决定是否手动置 1（脚本**不代劳**）；
- **【可选 · 默认已禁用】复位 `student02` 的绑定（`status=0`）**——该语句在准备 SQL 第 3 步中已被**整体注释**，**不是本模块测试的通用前置**；仅 `TC-CS-04` 需要它。若确需启用，必须先确认 `student02` 的绑定关系，并把影响范围**限定到明确的「教练 + 学员」组合**，**不得**仅凭 `student_id` 停用该学员的全部绑定（同时**不得**以其它写操作替代、不得删除或重置既有绑定）；
- **清理段默认禁用**，且只按专用用户名定向清理。

### 0.3 执行顺序（必须按集合顺序跑）

```
0 · 准备（登录 ×4 → 前置守卫 00e/00f/00g）
→ 1 · 学员列表 → 2 · 绑定/创建（含守卫 00h → TC-CS-06）→ 3 · 更新学情 → 4 · 解绑与重绑 → 5 · 学情 → 6 · 鉴权
```

**顺序即依赖**：`00e` 提取 `bindId`（`coach_student.id`）供组 3/4/5 使用，`00f` 提取 `bindIdB` 供越权用例使用；`00h` 必须在 `TC-CS-06` **之前**执行。中间组不可乱序。

> **★ 停止规则**：若任一**登录请求**或**前置守卫（`00e` / `00f` / `00g` / `00h`）**失败，请**立即停止本轮执行**，先处理根因（最常见是未执行准备 SQL），**不要**继续跑后续业务用例——否则后续请求会因集合变量为空而**级联误报**。
> **★ 提取健壮性**：`00e` / `00f`（以及登录与 `TC-CS-14a`）在**提取失败时会清空**对应集合变量（`bindId` / `student_cs01_uid` / `protectedBindId` / `bindIdB` / `notFoundBody` / 四个 token），**绝不沿用上一次运行的旧值**。

> **`bindId` ≠ `studentId`**：`bindId` 是**绑定记录** id（`coach_student.id`），`studentId` 是**学员用户** id（`sys_user.id`）。PUT / DELETE / status 三个接口的 `{id}` **一律是 `bindId`**。

### 0.4 已知工具假阳性

`TC-CS-16a / 16b / 16c` 期望**真实 HTTP 401 / 403**。若 Apifox 项目开启了「默认断言：状态码 = 200」，这三条会**必然报红**——属**工具假阳性**，不是系统缺陷。它们计入界面"失败数"，**不计入"断言失败数"**；看结果请分开看。

---

## 一、组 1 · 学员列表（`GET /api/coach/students`）

### TC-CS-01 列表字段齐备（含 `bindId` 与 `studentId`）
- **优先级**：P0
- **前置**：已执行准备 SQL
- **步骤**：`GET /api/coach/students?pageNum=1&pageSize=10`（Bearer `coachToken`）
- **预期**：`code=200`；`records` ≥ 2；每条含 `bindId / studentId / username / bindTime`，且 `bindId`、`studentId` 均为数字
- **数据影响**：只读
- **对应集合请求**：`TC-CS-01`

### TC-CS-01b 排序 `bind_time DESC, id DESC`
- **优先级**：P2 → **前置**：coach01 名下 ≥ 2 条有效绑定
- **步骤**：同上
- **预期**：`records` 的 `bindTime` **单调不增**（同秒时由 `id` 次级键保证全序，列表未返回 `id`，故仅断言时间单调）
- **数据影响**：只读 → **对应请求**：`TC-CS-01b`

### TC-CS-01c `pageSize` 上限（传 100）
- **优先级**：P2 → **步骤**：`?pageNum=1&pageSize=100`
- **预期**：`data.pageSize ≤ 20` 且 `records.length ≤ 20`（上限来自 `MAX_PAGE_SIZE=20`）
- **数据影响**：只读 → **对应请求**：`TC-CS-01c`

### TC-CS-01d / TC-CS-01e 分页非法值
- **优先级**：P2
- **步骤**：`pageSize=0` / `pageNum=-1`
- **预期**：`pageSize` 回落 **10**；`pageNum` 回落 **1**；均 `code=200`
- **数据影响**：只读 → **对应请求**：`TC-CS-01d` / `TC-CS-01e`

### TC-CS-16 列表不泄露敏感/内部字段
- **优先级**：P1
- **预期**：`records[0]` **不含** `password` / `deleted` / `avatar` / 绑定状态 `status`；且**原始响应文本中不含 `$2`**（BCrypt 特征串）
- **数据影响**：只读 → **对应请求**：`TC-CS-16`

---

## 二、组 2 · 绑定 / 创建学员（`POST /api/coach/students`）· **会写库**

### TC-CS-04 绑定已有学员（只传 `username`）
- **优先级**：P0
- **前置**：`student02` 存在，且与 coach01 **无有效绑定**（准备 SQL 第 3 步已复位）
- **步骤**：`POST` body `{"username":"student02"}`
- **预期**：`code=200`、`data=null`
- **数据影响**：**新增/恢复** `coach_student` 中 `coach01↔student02` 的有效绑定
- **对应请求**：`TC-CS-04`

### TC-CS-05 重复绑定 → `3101`
- **优先级**：P1 → **前置**：承接 TC-CS-04
- **预期**：HTTP 200 + `code=3101`（该学员已绑定）→ **数据影响**：无 → **对应请求**：`TC-CS-05`

### TC-CS-08 绑定目标角色不是 STUDENT → `1106`
- **优先级**：P1
- **步骤**：`POST` body `{"username":"coach01"}`（coach01 的 role=COACH）
- **预期**：HTTP 200 + `code=1106`（该账号与所选角色不匹配）
- **数据影响**：无 → **对应请求**：`TC-CS-08`

### TC-CS-06 创建并绑定新学员
- **优先级**：P0
- **前置（★ 必须同时满足两条）**：
  1. **集合内弱守卫 `00h`**：`GET /api/coach/students`（`coachToken`）返回成功，且 `coach01` 名下**不含** `cs_new_01`；
  2. **只读 SQL 全局核验**：`cs_new_01` 在**全局**不存在（**见 §八『TC-CS-06 前置（全局核查）』**）。
- **★ 守卫局限（不可忽视）**：`00h` 用的是**教练列表接口**，它**只返回当前教练的学员列表**，**无法**判断 `cs_new_01` 是否在**全局**已存在。若 `cs_new_01` 已在别处存在（未绑定给 coach01），`00h` 会通过、`TC-CS-06` 会走「账号已存在 → 只绑定」分支，**仍返回 `code=200`** → **假绿**（"创建新账号"路径未被真正测到）。因此**必须**配合 §八 的只读 SQL：只有该 SQL 返回 **0 行**，本用例才是有效的"创建新账号"测试。
- **步骤**：`POST` body `{"username":"cs_new_01","realName":"测试学员","phone":"13900000001","password":"abc123"}`
- **预期**：`code=200`；**响应体不含明文密码 `abc123`**
- **数据影响**：**新增** `sys_user`（role=STUDENT、status=1、密码 BCrypt）+ `coach_student` 绑定
- **SQL 只读验证**：
  ```sql
  SELECT id, username, role, status, password LIKE '$2%' AS pwd_is_bcrypt
    FROM sys_user WHERE username='cs_new_01';
  -- 期望：role='STUDENT'、status=1、pwd_is_bcrypt=1
  ```
- **对应请求**：`TC-CS-06`（前置守卫：`00h`）

### TC-CS-07 / 07b / 07c 创建模式缺必填项 → `1001`
- **优先级**：P1 → **步骤**：分别缺 `realName` / `phone` / `password`
- **预期**：HTTP 200 + `code=1001`（**Service 手写判空**，非 Bean Validation 分组）
- **数据影响**：无 → **对应请求**：`TC-CS-07` / `07b` / `07c`

### TC-CS-07d 手机号格式非法 → `1001`
- **优先级**：P1 → **步骤**：`phone="12345"`
- **预期**：`code=1001`（`StudentBindDTO` 的 `@Pattern`：`1[3-9]\d{9}`）
- **说明**：★ 该手机号格式约束是**本项目新增**的约定，**不是仓库既有统一规范**
- **对应请求**：`TC-CS-07d`

### TC-CS-07e 密码长度不足 → `1001`
- **优先级**：P2 → **步骤**：`password="abc12"`（5 位）
- **预期**：`code=1001` → **说明**：长度 6~64 也是**本次新增规则** → **对应请求**：`TC-CS-07e`

### TC-CS-07f 用户名超长（51 字）→ `1001`
- **优先级**：P2 → **预期**：`code=1001`（`@Size(max=50)`，对应 `varchar(50)`）→ **对应请求**：`TC-CS-07f`

---

## 三、组 3 · 更新学情（`PUT /api/coach/students/{id}`）· **会写库**

> `{id}` = `{{bindId}}`（`coach_student.id`，由准备组 `00e` 提取）

### TC-CS-09 目标科目无记录 → 新建
- **优先级**：P0
- **步骤**：`PUT /{bindId}` body `{"subjectCode":"SUBJECT_1","phaseStatus":"LEARNING","coachRemark":"首次建档"}`
- **预期**：`code=200`、`data=null`
- **数据影响**：`student_subject_status` 为 `student_cs01` **新增** `SUBJECT_1` 行（`upsertCoachFields`）
- **SQL 只读验证**：
  ```sql
  SELECT subject_code, status, coach_remark FROM student_subject_status
   WHERE student_id = (SELECT id FROM sys_user WHERE username='student_cs01');
  -- 期望：SUBJECT_1 / LEARNING / 首次建档
  ```
- **对应请求**：`TC-CS-09`

### TC-CS-10 只传备注 → `status` 保持原值（COALESCE 语义）★
- **优先级**：P0
- **步骤**：`PUT /{bindId}` body `{"subjectCode":"SUBJECT_1","coachRemark":"只改备注"}`
- **预期**：`code=200`
- **SQL 只读验证**（**关键**）：
  ```sql
  SELECT status, coach_remark FROM student_subject_status
   WHERE student_id = (SELECT id FROM sys_user WHERE username='student_cs01')
     AND subject_code = 'SUBJECT_1';
  -- 期望：status 仍为 'LEARNING'（未被覆盖为 NULL）；coach_remark = '只改备注'
  ```
- **对应请求**：`TC-CS-10`（**断言仅覆盖"未被拒"；COALESCE 语义必须靠 SQL 核验**）

### TC-CS-09b 只传阶段 → 备注保持 ★
- **优先级**：P0 → **步骤**：`{"subjectCode":"SUBJECT_1","phaseStatus":"READY_FOR_EXAM"}`
- **预期**：`code=200`
- **SQL 只读验证**：
  ```sql
  -- 期望：status='READY_FOR_EXAM'；coach_remark 仍为 '只改备注'（未被清空）
  ```
- **对应请求**：`TC-CS-09b`

### TC-CS-11 / 11b 两个可选字段都未提供 → `1001`
- **优先级**：P1
- **步骤**：`{"subjectCode":"SUBJECT_1"}`（都不传）／`{"subjectCode":"SUBJECT_1","phaseStatus":"","coachRemark":""}`（都传空串）
- **预期**：均 `code=1001`（Service 用 `StringUtils.hasText` 把空白串按"未传"处理）
- **数据影响**：无 → **对应请求**：`TC-CS-11` / `11b`

### TC-CS-18 / 18b / 18c / 18d 参数校验 → `1001`
- **优先级**：P1
- **步骤**：`subjectCode="SUBJECT_5"` ／ `"subject_1"`（小写）／ `phaseStatus="FINISHED"` ／ `coachRemark` 501 字
- **预期**：均 `code=1001`（`@Pattern` 整串匹配、大小写敏感；`@Size(max=500)` 对应 `varchar(500)`）
- **数据影响**：无 → **对应请求**：`TC-CS-18` / `18b` / `18c` / `18d`

### TC-CS-14a 不存在的 `bindId` → `3102`
- **优先级**：P1 → **步骤**：`PUT /api/coach/students/999999`
- **预期**：HTTP 200 + `code=3102`（**同时把响应体存入变量 `notFoundBody`**，供 TC-CS-14 比对）
- **数据影响**：无 → **对应请求**：`TC-CS-14a`

### TC-CS-14 **越权**：教练A 更新教练B 的绑定 → `3102`（与"不存在"**同响应**）★
- **优先级**：P0
- **前置**：`{{bindIdB}}` = coach02 名下绑定（准备组 `00f` 提取）
- **步骤**：用 **coach01 的 token** 调 `PUT /api/coach/students/{{bindIdB}}`
- **预期**：HTTP 200 + `code=3102`；且**响应 JSON 与 `notFoundBody` 逐字相同**（不泄露"该绑定确实存在、只是不属于你"）
- **数据影响**：无 → **对应请求**：`TC-CS-14`

---

## 四、组 4 · 解绑与重新绑定（`DELETE` + `POST`）· **会写库**

### TC-CS-12 解绑 → `status` 置 0（**行保留**）★
- **优先级**：P0 → **步骤**：`DELETE /api/coach/students/{{bindId}}`
- **预期**：`code=200`、`data=null`
- **SQL 只读验证**（**关键**）：
  ```sql
  -- ① 行仍在、status=0（不是删除行）
  SELECT id, status FROM coach_student WHERE id = <bindIdBeforeUnbind>;
  -- 期望：1 行，status=0
  -- ② 不级联删除预约（本模块不触碰 appointment）
  SELECT COUNT(*) FROM appointment
   WHERE student_id = (SELECT id FROM sys_user WHERE username='student_cs01');
  -- 期望：与解绑前**相同**（该学员本无预约时应为 0）
  ```
- **对应请求**：`TC-CS-12`

### TC-CS-13 / 13b 重复解绑 / 不存在的 id → `3102`
- **优先级**：P1 → **预期**：均 `code=3102` → **数据影响**：无
- **对应请求**：`TC-CS-13` / `TC-CS-13b`

### TC-CS-14c **越权解绑** → `3102`
- **优先级**：P0 → **步骤**：coach01 的 token 解绑 `{{bindIdB}}`
- **预期**：`code=3102`（**若返回 200，说明存在严重越权缺陷**）→ **数据影响**：无
- **对应请求**：`TC-CS-14c`

### TC-CS-13c 已解绑记录不可再访问 → `3102`
- **优先级**：P1 → **步骤**：`GET /{bindId}/status`（此时该绑定已解绑）
- **预期**：`code=3102` → **对应请求**：`TC-CS-13c`

### TC-CS-05b / 05c 解绑后**重新绑定**，应**复用同一条行** ★
- **优先级**：P0
- **步骤**：`POST {"username":"student_cs01"}` → 再 `GET` 列表
- **预期**：绑定成功；**列表中 `student_cs01` 的 `bindId` 与解绑前 `{{bindIdBeforeUnbind}}` 相同**
  （若实现改为"插入新行"，会撞 `uk_coach_student(coach_id, student_id)` 而失败）
- **SQL 只读验证**：
  ```sql
  SELECT id, status, bind_time FROM coach_student WHERE id = <bindIdBeforeUnbind>;
  -- 期望：id 不变、status=1、bind_time 已刷新为最近一次绑定的时间
  ```
- **对应请求**：`TC-CS-05b` / `TC-CS-05c`

---

## 五、组 5 · 学员学情（`GET /{id}/status`）· **只读**

### TC-CS-15 固定四科 + 缺失科目补 `NOT_STARTED` ★
- **优先级**：P0 → **步骤**：`GET /api/coach/students/{{bindId}}/status`
- **预期**：`code=200`；`data.length === 4`；顺序为 `SUBJECT_1..SUBJECT_4`；每条含 `subjectCode/status/mockPassCount/lastMockScore`；**SUBJECT_2~4 为 `NOT_STARTED` / 0 / null**
- **数据影响**：**只读** → **对应请求**：`TC-CS-15`
- **⚠️ 前置提醒**：本条假设 `SUBJECT_2~4` 无记录。若库中该学员已有其他科目记录，需改断言或先确认数据 —— **属数据前置问题，非缺陷**

### TC-CS-15c 不返回 `studyProgress`
- **优先级**：P1 → **预期**：每条均**不含** `studyProgress` 字段
- **说明**：该列当前无任何写入方、恒为 0.00，VO 刻意不返回（设计文档 §3.1 / Q11）
- **对应请求**：`TC-CS-15c`

### TC-CS-14d **越权**查学情 → `3102` ／ TC-CS-15d 不存在 → `3102`
- **优先级**：P0 / P1 → **预期**：均 `code=3102` → **对应请求**：`TC-CS-14d` / `TC-CS-15d`

### TC-CS-15b **只读性核验**（须 SQL，集合内无法自动断言）
- **优先级**：P0（**人工/SQL**）
- **步骤**：记录 `GET {id}/status` **之前**的行数 → 调用 → 记录**之后**的行数
  ```sql
  SELECT COUNT(*) FROM student_subject_status
   WHERE student_id = (SELECT id FROM sys_user WHERE username='student_cs01');
  ```
- **预期**：**前后相同**（查询**绝不因补齐四科而插行**）
- **说明**：集合内的两次 GET 只能证明"响应幂等"，**不能证明未写库** → 必须靠此 SQL 核验

---

## 六、组 6 · 鉴权（401 / 403）

### TC-CS-16a 无 token → 真实 HTTP **401**
- **优先级**：P1 → **步骤**：`GET /api/coach/students`（不带 `Authorization`）
- **预期**：**HTTP 401** + body `code=401`
- **⚠️** 若项目开启「默认断言：状态码=200」，本条报红属**工具假阳性** → **对应请求**：`TC-CS-16a`

### TC-CS-16b / 16c 学员 token 访问教练接口 → 真实 HTTP **403**
- **优先级**：P1 → **步骤**：带 `Bearer {{studentToken}}` 调 `GET` / `POST /api/coach/students`
- **预期**：**HTTP 403** + body `code=403`（`PathRoleRule`：`/api/coach/**` → COACH）
- **数据影响**：无（拦截器在 Controller 之前阻断）→ **对应请求**：`TC-CS-16b` / `16c`

---

## 七、前置守卫（含 `TC-CS-19` 空列表用例）

| 集合请求 | 所在组 | 内容 | 失败含义 |
|---|---|---|---|
| `00e` | 组 0 | coach01 列表**应含 `student_cs01`**；提取 `{{bindId}}` / `{{student_cs01_uid}}`；并断言 **`student01` 的绑定仍在**（存 `{{protectedBindId}}`） | 未执行准备 SQL，或保护对象被破坏 |
| `00f` | 组 0 | coach02 列表**应含 `student_cs02`**；提取 `{{bindIdB}}` | 未执行准备 SQL |
| `00g` | 组 0 | **coach03 列表应为空**（`total=0`、`records=[]`、`code=200`） | **即 TC-CS-19 空列表用例**：coach03 被误建了绑定 |
| `00h` | **组 2**（`TC-CS-06` **之前**） | `GET /api/coach/students`（`coachToken`）成功，且 **`coach01` 名下不含 `cs_new_01`** | 上次运行的 `cs_new_01` 残留（或异常绑定）→ **先人工核查后再重跑，勿继续**；★ 该守卫**只读**，**不删除账号** |

> **`00h` 的局限**：它是**弱守卫**——列表接口只返回**当前教练**的学员，**只能**排除"已将 `cs_new_01` 绑定给 coach01"，**不能**证明"全局不存在"。完整前置见 §二 `TC-CS-06` 与 §八『TC-CS-06 前置（全局核查）』，**必须**配合只读 SQL。

---

## 八、需 SQL 只读核验的场景（集合内无法自动断言）

### TC-CS-06 前置（全局核查）

`00h` 只能证明 `cs_new_01` **未绑定给 coach01**；要在"创建新账号"分支上得到**可信结论**，执行 `TC-CS-06` **之前**必须先跑这条**只读** SQL：

```sql
-- cs_new_01 是否已在【全局】存在？期望：0 行
SELECT id, username, role, status, deleted
  FROM sys_user WHERE username = 'cs_new_01';
```

- **返回 0 行** → 前置成立，`TC-CS-06` 是一次有效的"创建新账号"测试；
- **返回 ≥1 行** → **停止**：说明残留账号（可能是上次运行时由接口创建）。**不得**由脚本自动删除；请人工核查其引用（准备 SQL 清理段的"引用检查"）后再决定处理方式，然后重跑。
- ⚠️ 现有集合接口**无法**做全局核查（只暴露"当前教练的学员列表"）——此限制**如实登记**，**不新增测试专用业务接口**。

| 编号 | 核验内容 | SQL 要点 |
|---|---|---|
| TC-CS-06 | **① 前置：全局不存在（见上）**；② 新账号落库正确 | 前置：`WHERE username='cs_new_01'` **0 行**；落库：`sys_user` 的 `role='STUDENT'`、`status=1`、`password LIKE '$2%'` |
| TC-CS-10 / 09b | **COALESCE 语义** | 两次更新后 `status` 与 `coach_remark` **各自保留未被覆盖的值** |
| TC-CS-12 | **软解绑**：行在、`status=0`、未级联删预约 | `coach_student` 与 `appointment` 前后对比 |
| TC-CS-05b/05c | 重绑**复用同一行** | `id` 不变、`status=1`、`bind_time` 已刷新 |
| TC-CS-15b | 学情查询**只读** | `student_subject_status` 行数前后一致 |
| **全程** | **保护性核对** | `SELECT cs.id, cs.status FROM coach_student cs JOIN sys_user s ON s.id=cs.student_id WHERE s.username='student01';` —— `id` 与 `status=1` 全程不变 |

## 九、人工确认项（无法安全自动化）

| 项 | 原因 |
|---|---|
| `cs_new_01` 的**清理** | 删除 `sys_user` 行会让其历史数据成为悬空引用（本库无物理外键、不会报错但会留下脏引用）。准备 SQL 的清理段**默认禁用**，启用前需先跑引用检查 |
| `cs_new_01` 的**全局存在性前置** | 集合接口**无**全局账号查询能力（`00h` 只是弱守卫）；执行 `TC-CS-06` 前须**人工**跑 §八 的只读 SQL |
| **`student02` 绑定复位**（准备 SQL 第 3 步） | **默认禁用**（语句已注释）。启用须**人工**确认 `student02` 的绑定关系，并把影响范围**限定到明确的「教练 + 学员」组合**，**不得**仅凭 `student_id` 停用其全部绑定 |
| **逻辑删除 / 角色不符的专用账号** | 准备 SQL 第 2 步**不会**为 `deleted=1` 或角色不符的账号建绑定；此类账号需**人工处理**，脚本**不自动改角色、不恢复逻辑删除** |
| **测试绑定已存在且 `status=0`** | 第 2 步**仅在该「教练+学员」组合尚无绑定行时插入**，**不会**把既有的 `status=0` 自动改回 1；须**人工**按独立预检查 **P4** 确认后再决定是否手动置 1（脚本不代劳） |
| **空列表场景的完全自动化** | 本文用**专用 `coach03`** 实现（不破坏任何既有数据）✓；若你不愿新建 coach03，则该条需**手动准备** |
| `TC-CS-02` 式"coach01 无绑定"场景 | **刻意不做**——否则必须解绑 `student01`，会破坏既有绑定与预约测试前置 |

## 十、验收清单

- [ ] 守卫全绿：组 0 的 `00e/00f/00g`（`bindId` / `bindIdB` 已提取；`student01` 绑定仍在）+ 组 2 的 `00h`（coach01 名下不含 `cs_new_01`）
- [ ] `TC-CS-06` 前置**双满足**：`00h` 通过 **且** §八 的只读 SQL 返回 **0 行**（否则该用例无证据力）
- [ ] 列表：字段齐备（含 `bindId`+`studentId`）、排序正确、分页上限与非法值回落、不泄露敏感字段
- [ ] 绑定：已有账号绑定成功、重复绑定 `3101`、非学员账号 `1106`
- [ ] 创建：新账号建号成功且密码为 BCrypt、响应无明文；缺字段/手机号非法/密码过短/用户名超长 `1001`
- [ ] 更新：新建行成功；**COALESCE 语义**（只改一个字段时另一个不被覆盖）经 SQL 确认
- [ ] 参数：`subjectCode` 非法/小写、`phaseStatus` 非法、备注超长、两字段皆空 → 均 `1001`
- [ ] 归属：不存在 `3102`；**越权与不存在响应逐字相同**
- [ ] 解绑：`200` 且**行保留、`status=0`、不级联删预约**（SQL 确认）
- [ ] 重绑：**复用同一条 `coach_student` 行**（`bindId` 不变，SQL 确认 `bind_time` 刷新）
- [ ] 学情：**固定 4 科**、缺失补 `NOT_STARTED`、**不返回 `studyProgress`**、**查询不写库**（SQL 确认）
- [ ] 鉴权：无 token `401`、学员 token `403`（注意已知工具假阳性）
- [ ] **保护性核对**：全程 `coach01 ↔ student01`（bind_id=1）未被修改
- [ ] 回归：`doc/testjson/appointment-api.postman_collection.json` **仍全绿**（本模块未触碰预约相关表与绑定）

---

## 附：本文件的边界

- 本集合已由用户在本地**执行完毕**（见顶部「状态说明」与 `doc/Day/Day19-工作日志.md` §六）；本文档"预期"仍以**代码推导**为准，**未在本仓库内重新执行**。
- 执行结果**只依据 Apifox 报告**（`doc/Test/coach-student.html`）记录；**未重新查询数据库**，故**不对测试后的数据库状态作断言**。
- 准备 SQL 是**草案**，需人工审阅后手动执行（**写步骤禁止 Execute All**；先跑独立只读预检查 `doc/testsql/coach-student-precheck.sql`）；本文档与集合均**不含**数据库连接信息、JWT 密钥、API Key 或其他配置敏感值。
