# 预约模块设计（Day 18）

> 目标：学员端 / 教练端**练车预约闭环** —— 学员发起预约 → 教练审批（通过 / 拒绝）→ 完成
> 范围：**只服务科二 / 科三**；**本期只做后端**，前端二期
> 起点：`api-design.md` §4.8 / §4.10 已定接口清单；`appointment` / `coach_student` 两表已在库
> 归档：`doc/appointment-design.md`

---

## 〇、先摊开的关键取舍

这五条是**语义 / 取值层面的取舍**：不定下来，接口与状态机就写不下去。

1. **教练"拒绝"用独立状态 `REJECTED`，而不是复用 `CANCELLED` + `coach_remark` 推导。**
   `appointment.status` 是 `varchar(20)`，新增取值**不需要动表结构**，只需同步数据口径文档与列注释。
   这样做的理由是**展示层需要区分**："我自己取消的"与"被教练拒绝的"是两种不同结果；
   若都落 `CANCELLED`，前端只能靠"备注是否为空"去猜，脆弱且语义含糊。

2. **`time_slot` 在库里是自由文本、不建字典表，但由 DTO 的 `@Pattern` 固定为四个时段。**
   固定取值：`08:00-10:00` / `10:00-12:00` / `14:00-16:00` / `16:00-18:00`。
   若不约束，`8:00-10:00` 与 `08:00-10:00` 会被判成两个不同时段，
   **`3002 该时段已被预约` 形同虚设**——冲突检测完全失效。

3. **时段"占用"只算 `{PENDING, CONFIRMED}`，三个终态释放时段。**
   否则"取消 / 拒绝 / 完成过的时段会**永久占位**"，学员再也约不上。
   这是本模块**最容易写错、也最难自查**的一条。

4. **`appointment` 没有 `(coach_id, appointment_date, time_slot)` 唯一键 → 并发双写不处理。**
   冲突检测是"先查后插"，两个并发请求可能都通过检查。
   加唯一键会让**终态记录继续占位**（取消后仍冲突），代价更大；
   课设为单用户场景，**接受，记录在案**，与 AI 每日限额的竞态同一处理方式。

5. **教练侧只有"拒绝"（reject），没有"取消"（cancel）。**
   `cancel` 是**发起方（学员）**的动作——撤回"我发起的"；
   教练侧的动作是**响应**：`approve` / `reject` / `complete`。
   两者语义不同；教练侧若也能 `cancel`，会与 `reject` 重叠，
   还会产生"学员取消与教练拒绝谁先谁后"的竞态。故教练侧**不提供取消入口**。

---

## 一、功能范围

**业务主链路**：学员向**自己已绑定且绑定有效**（`coach_student.status = 1`）的教练发起练车预约，
教练审批（通过 / 拒绝），完成后标记。**只服务科二 / 科三**（`subject_code ∈ {SUBJECT_2, SUBJECT_3}`）。

### 1.1 角色边界

| 角色 | 能做 |
|---|---|
| STUDENT | 查我的教练 → 创建预约 → 查我的预约（含详情）→ 取消（仅 `PENDING`） |
| COACH | 查名下预约 → approve / reject / complete（**无取消**） |
| ADMIN | 本模块不参与（绑定关系属教练端「学员管理」） |

### 1.2 接口一览（9 个）

| 端 | 方法 | 路径 | 说明 |
|---|---|---|---|
| 学员 | GET | `/api/student/appointment/coaches` | 我的教练列表 |
| 学员 | POST | `/api/student/appointment` | 创建预约（返回 `Void`） |
| 学员 | GET | `/api/student/appointment` | 我的预约列表（分页） |
| 学员 | GET | `/api/student/appointment/{id}` | 预约详情 |
| 学员 | PUT | `/api/student/appointment/{id}/cancel` | 取消（仅 `PENDING`） |
| 教练 | GET | `/api/coach/appointments` | 名下预约列表（可按状态筛） |
| 教练 | PUT | `/api/coach/appointments/{id}/approve` | 审批通过 |
| 教练 | PUT | `/api/coach/appointments/{id}/reject` | 拒绝 |
| 教练 | PUT | `/api/coach/appointments/{id}/complete` | 标记完成 |

### 1.3 状态机

```
                        学员创建
                           │
                           ▼
                     [ PENDING ] ──学员取消──► [ CANCELLED ]（终态）
                      │        │
            教练 approve│        │教练 reject
                      ▼        ▼
               [ CONFIRMED ]  [ REJECTED ]（终态）
                      │
            教练 complete│
                      ▼
               [ COMPLETED ]（终态）
```

- **终态**：`CANCELLED` / `REJECTED` / `COMPLETED` —— 不可再流转。
- **`CONFIRMED` 之后双方都不能取消**（"确认即承诺"；需要变更只能线下联系教练）。
- **时段占用** = `status ∈ {PENDING, CONFIRMED}`；**终态释放时段**。

### 1.4 与其他模块的关系

与刷题 / 考试**完全解耦**；但**复用两张既有表**：
`sys_user`（教练 / 学员的姓名与电话）与 `coach_student`（"我的教练"与绑定校验）。

---

## 二、接口设计（9 个）

> 通用约定：`userId` 一律取自 JWT 解析后写入的 request 属性（`@RequestAttribute`），**不采信前端传参**；
> 角色鉴权在拦截器层按路径前缀完成（`/api/student/**` → STUDENT、`/api/coach/**` → COACH）。
> 分页统一 `pageNum` 默认 1、`pageSize` 默认 10，**后端强制上限 20**。

### 2.1 `GET /api/student/appointment/coaches` 我的教练

取该学员**有效绑定**（`coach_student.status = 1`）的教练，关联 `sys_user` 补姓名与电话。
**未绑定任何教练时返回空数组**——这不是错误，不抛 `3003`（`3003` 用于"创建时指定的教练不是我的"）。
只读接口，不加事务。

### 2.2 `POST /api/student/appointment` 创建预约（返回 `Void`）

**逻辑顺序（顺序不可颠倒）**：

1. 该教练必须是**本人有效绑定**的教练 → 否则 `3003`；
2. 该教练该日该时段不能已有**占用中**的预约（`status ∈ {PENDING, CONFIRMED}`）→ 否则 `3002`；
3. 落库，初始状态 `PENDING`。

**返回 `Void`**（不是新建 id）：前端创建成功后需**重新拉列表**，不靠 id 追加。
单表单条写，**不加事务**（"先查后插"的竞态见 §〇.4）。

### 2.3 `GET /api/student/appointment` 我的预约列表（分页）

只返回**本人**的预约（`student_id = 当前学员`）；可按 `status`、`subjectCode` 过滤。
`status` 取值为枚举名，**大小写敏感**（见 §2.6 说明）。

**排序：`create_time DESC, id DESC`。** 按"操作时间"倒序，"最新发生的在最前"——
学员提交 / 取消后立刻能在列表顶部看到结果。
**刻意不用 `appointment_date`**：预约列表**混合过去与未来**，
`DESC`（最远未来在前）与 `ASC`（最久过去在前）都没有自然正确的方向；
"按预约日期看"应由**前端分 tab / 筛选**解决，而不是换排序方向。
`id` 作**次级键**：`create_time` 是秒精度，同秒记录靠它保证全序稳定，避免分页漂移。

### 2.4 `GET /api/student/appointment/{id}` 预约详情

越权（查别人的）与不存在**返回同一个 `3001`**，不泄露存在性（见 §3.4）。

### 2.5 `PUT /api/student/appointment/{id}/cancel` 取消预约

**仅 `PENDING` 允许取消**（`CONFIRMED` 之后需联系教练，见 `api-design.md` §5 第 2 条）；
状态不符抛 `3004`。取消落 `CANCELLED`，该时段**随之释放**。单表单条写，不加事务。

### 2.6 `GET /api/coach/appointments` 教练名下的预约列表（分页）

只返回**本人名下**的预约（`coach_id = 当前教练`）；可按 `status` 过滤。
**不提供科目筛选**——与 `api-design.md` §4.10 一致（那里只写"可按状态筛"）。
排序与 §2.3 相同。

> **`status` 是枚举 `AppointmentStatus`**：由 Spring 走 `Enum.valueOf()` 转换
> （`StringToEnumConverterFactory`，只 `trim`、**不改大小写**）。
> 因此前端必须传 `PENDING / CONFIRMED / CANCELLED / REJECTED / COMPLETED` 的**全大写**枚举名；
> 传小写（或 `" pending "`）会触发 `MethodArgumentTypeMismatchException` → 全局处理器 → `1001`。
> 这是**刻意的严格校验**：传错值会立刻报错，而不是"静默返回全部"。

### 2.7 `PUT /api/coach/appointments/{id}/approve` 审批通过

**仅 `PENDING` 可审批**，否则 `3005`。通过后置 `CONFIRMED`。

### 2.8 `PUT /api/coach/appointments/{id}/reject` 拒绝

**仅 `PENDING` 可拒绝**，否则 `3005`。拒绝后置 **`REJECTED`**（与学员取消的 `CANCELLED` 区分），
该时段**释放**；拒绝原因写入 `coach_remark`。
入参 `AppointmentRejectDTO` 只有"拒绝原因"一个字段，**可为空**：
前端传 `{}` 即不填原因；**完全不传 body 会返回 `1001`**（缺请求体属参数错误）。

### 2.9 `PUT /api/coach/appointments/{id}/complete` 标记完成

**仅 `CONFIRMED` 可完成**，否则 `3005`。完成后置 `COMPLETED`（终态）。

---

## 三、DTO / VO 清单（`pojo` 模块）

| 类 | 位置 | 字段 |
|---|---|---|
| `AppointmentCreateDTO` | `pojo/dto` | `coachId`(`@NotNull`) / `subjectCode`(`@NotBlank` + `@Pattern` 限科二科三) / `appointmentDate`(`@NotNull` + `@FutureOrPresent`) / `timeSlot`(`@NotBlank` + `@Pattern` 限四时段) / `remark`(`@Size(max=255)`) |
| `AppointmentRejectDTO` | `pojo/dto` | `coachRemark`(`@Size(max=255)`，可空) |
| `CoachVO` | `pojo/vo` | `coachId` / `realName` / `phone` |
| `AppointmentVO` | `pojo/vo` | `id` / `studentId` / `studentName` / `studentPhone` / `coachId` / `coachName` / `coachPhone` / `subjectCode` / `appointmentDate` / `timeSlot` / `status` / `remark` / `coachRemark` / `createTime` / `updateTime` |

### 3.1 分层：实体用 `String`、VO 用枚举

`status` 在**实体层是 `String`**（只映射数据库列，与 `MockExam.status` / `StudentSubjectStatus.status` 一致）；
在 **VO 层是枚举 `AppointmentStatus`**（Jackson 按 `name()` 输出 `"PENDING"` 等，
与 `WrongBookItemVO.category` 一致）。**不给实体引入 `@EnumValue` / TypeHandler 配置**，保持项目现状。

### 3.2 `AppointmentStatus` 放 `pojo/enums`

与 `WrongBookCategory` / `WeakReason` 同层。理由：它会被**实体、Service、VO、前端**共同引用，
是跨模块的共享枚举，不属于 `server` 层。

### 3.3 `AppointmentVO` 的两处设计

- **两侧字段都带**（学员端要教练姓名电话、教练端要学员姓名电话）：一个 VO 服务两端，
  前后端各只维护一份结构。
- **不返回 `deleted`**：内部逻辑删除字段，对外无意义。

### 3.4 错误码

| 码 | 常量 | 含义 |
|---|---|---|
| `3001` | `APPOINTMENT_NOT_FOUND` | 预约不存在、**或不属于当前用户** |
| `3002` | `APPOINTMENT_TIME_CONFLICT` | 该时段已被预约 |
| `3003` | `STUDENT_NOT_BOUND` | 该教练不是本人有效绑定的教练 |
| `3004` | `APPOINTMENT_CANCEL_NOT_ALLOWED` | 仅可取消待确认的预约 |
| `3005`（**新增**） | `APPOINTMENT_STATUS_ILLEGAL` | 当前预约状态不允许此操作（approve / reject / complete 复用） |

> **越权与"不存在"返回同一个 `3001`**，不单独给越权码——若"别人的记录"返 403、"不存在"返 404，
> 攻击者就能据此**探测出哪些 id 真实存在**（存在性泄露）。这与 AI 记录（4004）、错题（2003）、
> 考试（2104）同一处理方式。

---

## 四、SQL 思路

**本模块无自定义 SQL**（两个 Mapper 都只用 `BaseMapper` 通用方法）。

1. **不用 join**：列表要展示的"姓名 / 电话"靠"**分页查 `appointment` → 收集去重后的
   `studentId` + `coachId` → 一次 `IN` 查 `sys_user` → 内存映射**"完成。绝不循环里逐条查库（防 N+1）。
2. **空集合先返回**：`selectBatchIds(空集合)` 会拼出 `WHERE id IN ()`，在 MySQL 是**语法错误**，
   故 `loadUserMap` 开头先判空返回空 Map（与 `QuestionOptionAssembler.loadByQuestionIds` 同样的防御）。
3. **冲突检测**：`selectCount` + `status IN (PENDING, CONFIRMED)`（终态剔除，见 §〇.3）。
4. **归属校验**：`WHERE id = ? AND student_id / coach_id = ?`，0 行即抛 `3001`。
   学员版与教练版**拆成两个方法**（`loadStudentOwned` / `loadCoachOwned`）——参数名即身份，
   调用处一眼能看出按哪个身份收敛，也避免把角色判断散进 SQL 条件。
5. **逻辑删除的差异**：
   - `appointment` **有** `deleted` → 实体加 `@TableLogic`，查询 / 更新**自动带** `deleted = 0`；
   - `coach_student` **没有** `deleted` → 实体**不加** `@TableLogic`（误加会拼出 `deleted = 0`，
     直接报 `Unknown column 'deleted'`）。有效性用 `status = 1` 表达，**解绑 = `status` 置 0**（不是删行）。
6. **姓名可能为 null**：`User` 实体带 `@TableLogic`，被逻辑删除的账号查不出来 →
   调用方必须**容忍 `Map` 里没有对应 key**，且**绝不 NPE**（见 §7.3 的"刻意差异"）。

---

## 五、前端（**本期不做**）

本模块本期**只做后端**；前端二期再开。二期范围与建议路由如下，届时可直接照着做。

| 端 | 页面 | 建议路由 | 要点 |
|---|---|---|---|
| 学员 | 我的预约 | `/student/appointment?subjectCode=` | 列表 + 「新建预约」按钮 + 行内「取消」（仅 `PENDING` 可点） |
| 学员 | 新建预约弹窗 | （弹窗，不占路由） | 教练下拉（`GET /coaches`）→ 科目 → 日期 → **时段下拉（固定四值）** → 备注 |
| 教练 | 预约处理 | `/coach/appointments` | 列表（默认筛 `status=PENDING`）+ 行内 approve / reject / complete |

前端需自行处理的映射：
`status` → 中文（`PENDING` 待确认 / `CONFIRMED` 已确认 / `CANCELLED` 已取消 / `REJECTED` 已拒绝 / `COMPLETED` 已完成）；
时段 → 固定四值下拉（**不要自由输入**，否则后端 `@Pattern` 直接 1001）。
**不做**排班 / 时段管理（库里没有时段表）。

---

## 六、实现顺序

按"**一次一个文件级任务、写完停下、`mvn install` 通过再继续**"的节奏推进，分五步：
实体 → 枚举与 DTO/VO → Mapper 与 Service 接口 → Service 实现 → Controller。
另有一处**对既有文件的改动**：`ErrorCode` 新增 `APPOINTMENT_STATUS_ILLEGAL(3005)`。

---

## 七、关键决策汇总

### 7.1 设计层

| 决策 | 结论 | 要点 |
|---|---|---|
| 教练拒绝的状态 | **独立 `REJECTED`** | 与学员取消的 `CANCELLED` 区分；`varchar(20)` 够用，不改结构 |
| 非法状态操作的错误码 | **新增 `3005`**，三动作复用 | `3004` 只覆盖"取消"，approve/reject/complete 各写一个码不经济 |
| `AppointmentStatus` 位置 | `pojo/enums` | 跨模块共享，与 `WrongBookCategory` 同层 |
| Service 包位置 | `server/appointment`（自成一体） | 与 `exam` / `ai` / `analysis` 一致 |
| 教练端是否要"预约详情"接口 | **不要** | 列表带全字段够用，避免多余接口 |
| `LocalDate` 的 Jackson 配置 | **不加显式配置** | Boot 默认输出 ISO `yyyy-MM-dd`，够用 |
| 本期范围 | **只做后端** | 前端二期开 |

### 7.2 实现层（六条裁定）

1. **先归属、后状态**：所有按 id 的操作先做归属校验（0 行 → `3001`），
   **确认存在且属于本人之后**才判状态（→ `3004` / `3005`）。顺序反了会向非本人泄露"这条存在"。
2. **占用排除终态**：冲突检测只看 `{PENDING, CONFIRMED}`。
3. **防 N+1**：一次 `IN` 查 `sys_user` 后内存映射。
4. **`WHERE` 带身份**：学员端一律 `student_id = ?`、教练端一律 `coach_id = ?`。
5. **五个写方法不加 `@Transactional`**：单表单条写，SQL 自身原子，
   与刷题作答、AI 落库一致；不拆独立 Bean（本模块无"同类自调用事务方法"）。
6. **`reject` 落 `REJECTED`**、原因写入 `coach_remark`（可为 null）。

### 7.3 一处刻意的差异：两条列表对"查不到的用户"处理相反

`User` 带 `@TableLogic`，被逻辑删除的账号查不出来。由此：

| 方法 | 查不到对方用户时 | 为什么 |
|---|---|---|
| `listMyCoaches` | **跳过该条**，不返回 `CoachVO` | "**选择对象要可用**"——姓名 `null` 的教练卡片会让学员误点、也约不成 |
| `listMyAppointments` / `listCoachAppointments` | **保留记录**，姓名电话留 `null` | "**历史记录要完整**"——历史不该因对方账号被删而消失 |

两者处理相反，是**有意为之**，不是疏漏。
