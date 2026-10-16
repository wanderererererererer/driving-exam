# 教练端 · 学员管理设计（Day 19 · 定稿）

> 项目：基于大语言模型的驾考智能辅导系统设计与实现
> 状态：**Step 0 设计定稿**（决策已拍板，可据此进入 Step 1 编码）
> 口径：**凡标「已核实」的均为仓库实测**（附文件路径/行号或 DDL）；标「设计决定」的为本次拍板结果；标「待确认」的尚未决定。
> 归档：`doc/coach-student-design.md`

---

## 〇、本次拍板的决策（原两处冲突已裁决）

| # | 原冲突 | 裁决结果 |
|---|---|---|
| **冲突 1** | 清单写 `PUT/DELETE /api/coach/students`（无 `{id}`），`api-design.md` §4.10 写 `/{id}` | **以 `api-design.md` 为准**：`PUT /api/coach/students/{id}`、`DELETE /api/coach/students/{id}`；**`{id}` 明确表示 `coach_student.id`** |
| **冲突 2** | `PUT` 要改的"备注/科目阶段"在 `coach_student` 表**无对应列** | **采纳选项 A**：落到现有 `student_subject_status` 表（其 `coach_remark`、`status` 两列已存在），入参增加 `subjectCode`；**不改 DDL** |
| **冲突 3**（Step 5 测试准备时发现） | 初版 `StudentVO` **没有 `bindId`**，而 `PUT /{id}`、`DELETE /{id}`、`GET /{id}/status` 都需要 `coach_student.id` —— **列表给不出后续接口所需的 id**，前端与测试都无法闭环 | **采纳选项 A（已实现）**：`StudentVO` 增加 `bindId`（取自 `CoachStudent.id`）；**三个接口的 `{id}` 语义不变**（仍为 `coach_student.id`）。改动 3 个文件：`StudentVO`、`CoachStudentServiceImpl.toStudentVO`、本文档 |

---

## 一、当前仓库现状（已核实）

### 1.1 已就绪（可直接复用）

| 项 | 依据（实测） |
|---|---|
| `CoachStudent` 实体 | `pojo/entity/CoachStudent.java`；`@TableName("coach_student")`；字段 `id / coachId / studentId / bindTime / status / createTime / updateTime`；**无 `@TableLogic`**（该表无 `deleted` 列） |
| `CoachStudentMapper` | `server/mapper/CoachStudentMapper.java`：`extends BaseMapper<CoachStudent>`，无自定义方法 |
| `StudentSubjectStatus` 实体 + Mapper | `pojo/entity/StudentSubjectStatus.java`（16 字段）、`server/mapper/StudentSubjectStatusMapper.java`（仅 BaseMapper） |
| `User` 实体 + `UserService` | `pojo/entity/User.java`；`UserServiceImpl` **仅 `extends ServiceImpl<UserMapper,User>`**，`save()` 可直接建号 |
| `PasswordEncoder`（BCrypt） | `server/config/SecurityConfig.java` L18-19 `@Bean`；`AuthController` 已注入 |
| 错误码 `3101` / `3102` | `ErrorCode.java` L50-51：`STUDENT_ALREADY_BOUND(3101,"该学员已绑定")`、`BINDING_NOT_FOUND(3102,"绑定关系不存在")`；**已定义、全项目零引用**，可直接启用 |
| 鉴权与取当前用户 | `PathRoleRule`（`/api/coach/**` → COACH）；`@RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long coachId`（见 `CoachAppointmentController`） |
| 唯一键 | `coach_student`：`uk_coach_student(coach_id, student_id)`；`sys_user`：`uk_username(username)`；`student_subject_status`：`uk_student_subject(student_id, subject_code)` |

### 1.2 未实现（本模块要补）

`controller/` 无 `CoachStudentController`；全仓库无 `CoachStudentService`；`pojo/vo/` 现有 23 个 VO 中**无** `StudentVO` / `SubjectStatusVO`。

### 1.3 既有 Service 的可复用性（**读过实现，非仅看签名**）

| Service | 实测结论 |
|---|---|
| `AnalysisService.overview/knowledge/trend(Long userId, …)` | 内部只按 `userId` 过滤（如 `AnswerRecord::getUserId`），**无当前登录用户依赖、无权限校验** → 可复用 |
| `WrongBookService.list/summary/retry(…)` | `list` 为 `wrapper.eq(WrongBook::getUserId, userId)`，**无权限校验** → 可复用 |
| `ExamService.history(Long userId, …)` | `wrapper.eq(MockExam::getUserId, userId)`，**无权限校验** → 可复用 |

> ⚠️ **设计决定（安全）**：这些方法**自身不校验"该 userId 是否归当前教练管"**。因此**教练端必须在调用前完成归属校验**，且**绝不接受前端直接传 `studentId` 去查数据**——`studentId` 只能由归属校验通过的绑定记录**反查**得到。

### 1.4 `student_subject_status` 的约束与写入现状（已核实）

**约束**（源：`sql/_localhost-2026_10_08_17_26_16-dump.sql`）：

```
UNIQUE KEY `uk_student_subject` (`student_id`,`subject_code`)
`status`              varchar(30)  NOT NULL DEFAULT 'NOT_STARTED'
`mock_pass_count`     int          NOT NULL DEFAULT '0'
`coach_remark`        varchar(500) NULL
`study_progress`      decimal(5,2) DEFAULT '0.00'
`create_time`         datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
`update_time`         datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
```

**写入现状**：

| 列 | 现有写入方 | 结论 |
|---|---|---|
| `mock_pass_count`、`last_mock_score` | ✅ `ExamFinalizer.applySubjectStatus()`（模考交卷**upsert**：不存在则 `insert`，存在则只改这两列） | 有写入 |
| `status` | 插入时**交给 DB 默认值** `NOT_STARTED` | ❌ 从未被更新 |
| `coach_remark` | 无（代码里 2 处 `setCoachRemark` 属 **Appointment** 实体，不是本表） | ❌ 恒为 NULL |
| `study_progress` | 无 | ❌ **从未被写过 → 恒为 `0.00`** |
| `exam_ready_suggest` / `ai_suggest` / `ai_suggest_time` / `coach_confirm_status` / `exam_result` / `exam_date` | 无 | ❌ 恒为 NULL |

**并发写入的既有先例（已核实）**：项目**已有两处** `INSERT ... ON DUPLICATE KEY UPDATE` 的成熟写法——
`MockExamAnswerMapper.upsertAnswer()` 与 `WrongBookMapper`，注释均写明"**单条 SQL 原子，避免并发下重复插入**"。

### 1.5 新建学员账号必须满足的登录条件（已核实）

`AuthController.login()`（L44-72）依次校验：

```
① username 命中（userService.lambdaQuery().eq(User::getUsername, …)）
② passwordEncoder.matches(入参密码, user.password)
③ user.role 必须等于登录时传的 role      → 否则 1106
④ user.status 必须 = 1                    → 否则 1102
```

**设计决定**：新建 `sys_user` 必须写全 `username`（唯一）、`password`（BCrypt 密文）、`role='STUDENT'`、`status=1`。

**另已核实**：`application.yml` **未配置** `insert-strategy`（= MyBatis-Plus 默认 `NOT_NULL`），故实体中为 `null` 的字段**不参与 INSERT**，`deleted` 由 DB 默认 `0` 落库；`create_time` / `update_time` 由 `MyMetaObjectHandler`（`strictInsertFill` / `strictUpdateFill`）自动填充。**建号时无需手动设置这三项。**

---

## 二、五个接口的最终定义

> 通用：`coachId` 一律取自 `@RequestAttribute(JwtInterceptor.ATTR_USER_ID)`，**不采信前端传参**；
> 返回统一 `ApiResponse<T>`；分页 `pageNum` 默认 1、`pageSize` 默认 10、**后端强制上限 20**（同预约模块）。

### 2.1 `GET /api/coach/students` 我的学员列表

| 项 | 定义 |
|---|---|
| 入参 | `pageNum?=1`、`pageSize?=10`（**不加关键字/科目筛选**，已拍板） |
| 返回 | `PageResult<StudentVO>`（**每个元素都带 `bindId`**，供后续三个按 id 操作的接口直接使用） |
| 取数 | `WHERE coach_id = ? AND status = 1`（只返回有效绑定） |
| 排序 | `bind_time DESC, id DESC` |
| 防 N+1 | 分页查绑定 → 收集去重 `studentId` → **一次 `IN` 查 `sys_user`** → 内存映射；**空集合直接返回空分页结果**（`IN ()` 是 MySQL 语法错误） |
| 空结果 | `total=0` 的 `PageResult`，**不是错误** |
| 错误场景 | 仅 401 / 403 |

### 2.2 `POST /api/coach/students` 绑定或创建学员

| 项 | 定义 |
|---|---|
| 入参 | `StudentBindDTO` |
| 返回 | `Void` |
| 语义 | **同一入口两模式**：`username` 已存在 → 仅绑定；不存在 → **建号 + 绑定** |
| 校验顺序（**不可颠倒**） | ① `@Valid`（`username` 必填、`realName`/`phone`/`password` 必填——**创建模式必填，绑定模式不要求**，见 §四.1）<br>② 查 `sys_user` by `username`：<br>　· **已存在**：`role != STUDENT` → **1106**；已存在有效绑定 → **3101**；否则③<br>　· **不存在**：创建 `sys_user`（`role=STUDENT`、`status=1`、密码 BCrypt）→③<br>③ 写 `coach_student`：**若存在同 pair 的历史记录（`status=0`）则复用该行并置 `status=1` 且刷新 `bind_time`**（否则会被 `uk_coach_student` 拒绝，见 §四.3） |
| 事务 | **需要 `@Transactional`**（跨表写 `sys_user` + `coach_student`） |
| 错误场景 | `1001`、`1106`、`3101`；并发撞 `uk_username` 时可能 `1105` |

### 2.3 `PUT /api/coach/students/{id}` 改科目阶段 / 备注

| 项 | 定义 |
|---|---|
| `{id}` | **`coach_student.id`** |
| 入参 | `StudentUpdateDTO{ subjectCode 必填, phaseStatus?, coachRemark? }`，**两个可选字段至少传一个** |
| 返回 | `Void` |
| 校验顺序 | ① `@Valid` → ② **归属校验**：`WHERE id=? AND coach_id=? AND status=1`，0 行 → **3102** → ③ 以该记录的 `studentId` + 入参 `subjectCode` 写 `student_subject_status` |
| 事务 | **不需要**（见下"并发方案"：单条原子 SQL；且归属校验为只读） |
| 错误场景 | `1001`（`subjectCode` 非法 / 两个可选字段都没传）、`3102` |

**并发与"不存在则创建"的方案（已核实可行，设计决定采用）**

- 现状：`student_subject_status` 有唯一键 `uk_student_subject(student_id, subject_code)`；项目**已有** upsert 先例（`MockExamAnswerMapper.upsertAnswer`）。
- **不采用**"先 SELECT 再决定 INSERT/UPDATE"（并发下两个请求可能都判定为"不存在"，第二个 INSERT 撞唯一键）。
- **采用**：在 `StudentSubjectStatusMapper` 新增自定义方法（**照 `MockExamAnswerMapper` 的写法**）：

  ```sql
  INSERT INTO student_subject_status (student_id, subject_code, status, coach_remark, create_time, update_time)
  VALUES (#{studentId}, #{subjectCode}, COALESCE(#{status}, 'NOT_STARTED'), #{coachRemark}, NOW(), NOW())
  ON DUPLICATE KEY UPDATE
      status       = COALESCE(#{status}, status),
      coach_remark = COALESCE(#{coachRemark}, coach_remark),
      update_time  = NOW()
  ```
  - **单条 SQL 原子**，并发下由唯一键兜底，**不会产生重复行**；
  - `COALESCE` 保证"**没传的字段不改**"；插入分支里 `status` 缺省落 `NOT_STARTED`（与 DB 默认一致）；
  - **已知限制**：无法把 `coach_remark` **清空为 NULL**（要清空需另设语义）——记入 **Q12**；
  - 因此 **`PUT` 不需要 `@Transactional`**（单条 SQL 自身原子），与预约模块的判据一致。

### 2.4 `DELETE /api/coach/students/{id}` 解绑

| 项 | 定义 |
|---|---|
| `{id}` | **`coach_student.id`** |
| 返回 | `Void` |
| 实现 | `SELECT ... WHERE id=? AND coach_id=? AND status=1` → 0 行 → **3102**；否则 `status=0` + `updateById` |
| 事务 | 不需要（单表单条写） |
| 错误场景 | `3102`（不存在 / 不属本教练 / 已解绑——**三者同一响应**，不泄露存在性） |
| **不联动** | **不取消预约、不删除任何历史数据**（已拍板，见 §四.4） |

### 2.5 `GET /api/coach/students/{id}/status` 学员状态详情

| 项 | 定义 |
|---|---|
| `{id}` | **`coach_student.id`**；先归属校验（`id + coach_id + status=1`），0 行 → **3102** |
| 返回 | `List<SubjectStatusVO>`，**固定补齐 4 科**（`SUBJECT_1~4`） |
| 取数 | 用反查出的 `studentId` 查 `student_subject_status`；**缺失科目按 `NOT_STARTED` 内存补齐**（`mockPassCount=0`、`lastMockScore=null`） |
| ⚠️ **只读约束** | **查询绝不写库**——不得为了"补齐展示数据"而插入行（已拍板） |
| 错误场景 | `3102` |

---

## 三、DTO / VO 字段定义

> 风格对齐既有类：`@Data` + `implements Serializable` + `serialVersionUID`；校验用 Hibernate Validator；`@Pattern` 为**全串匹配**（不写 `^...$`）；`isXxx` 用包装类型 `Boolean`。

### 3.1 新增 VO

**`StudentVO`**（学员列表元素）

| 字段 | 类型 | 来源 | 说明 |
|---|---|---|---|
| **`bindId`** | `Long` | `coach_student.id` | **绑定记录 ID** —— 教练端三个按 id 操作的接口（`PUT /{id}`、`DELETE /{id}`、`GET /{id}/status`）用的就是它 |
| `studentId` | `Long` | `coach_student.student_id` | 学员**用户** ID（`sys_user.id`） |
| `username` | `String` | `sys_user.username` | 登录名 |
| `realName` | `String` | `sys_user.real_name` | 真实姓名 |
| `phone` | `String` | `sys_user.phone` | 手机号 |
| `bindTime` | `LocalDateTime` | `coach_student.bind_time` | 绑定时间（列表排序依据） |

> **★ `bindId` 是后补的（设计阶段遗漏，已修正）**：初版 `StudentVO` 只给了 `studentId`，
> 而 §2.3 / §2.4 / §2.5 三个接口的 `{id}` 要求的是 `coach_student.id` —— 列表**给不出**这个 id，
> 前端无法完成"列表 → 点某学员 → 改备注 / 解绑 / 看状态"的闭环，测试也只能靠 SQL 直查。
> 现已在 `StudentVO` 增加 `bindId`（取自 `CoachStudent.id`），**三个接口的 `{id}` 语义不变**
> （仍为 `coach_student.id`，未改成 `studentId`）。
> **`bindId` ≠ `studentId`**：前者是"这对师生关系"的主键，后者是学员账号的主键；二者不可混用。

- **不返回** `password`（绝不外泄）、`deleted`（内部字段）、`avatar`（已拍板不加）、绑定状态 `status`（列表只含有效绑定，无意义）。
- **容忍 null**：`sys_user` 带 `@TableLogic`，被逻辑删除的账号查不出来 → 与预约模块一致，**保留记录、姓名留 `null`**（"历史记录要完整"口径）。

**`SubjectStatusVO`**（学员某科进度）

| 字段 | 类型 | 来源 |
|---|---|---|
| `subjectCode` | `String` | `student_subject_status.subject_code` |
| `status` | `String` | 同名列（缺失时为 `NOT_STARTED`） |
| `mockPassCount` | `Integer` | 同名列（缺失时为 `0`） |
| `lastMockScore` | `Integer` | 同名列（缺失时为 `null`） |

> **`studyProgress` 已移除**（已拍板）：该列**无任何写入方、恒为 `0.00`**（见 §1.4），保留会误导前端。
> `coachRemark` / `coachConfirmStatus` / `aiSuggest` 等列本日**不返回**（属后续接口范畴）。

**为什么新建而非复用 `CoachVO`**：`CoachVO` 是"教练名片"（`coachId/realName/phone`，`sys_user.id = coach_id`），与学员列表结构不同，**不属重复创建**。

### 3.2 新增 DTO

**`StudentBindDTO`**（POST 入参）

| 字段 | 校验 | 说明 |
|---|---|---|
| `username` | `@NotBlank` + `@Size(max=50)`（对齐 `varchar(50)`） | 必填；已存在则绑定，不存在则创建 |
| `realName` | **创建模式必填** + `@Size(max=50)` | 见 §四.1 |
| `phone` | **创建模式必填** + 格式校验 + `@Size(max=20)` | 见 §四.1 |
| `password` | **创建模式必填** + `@Size(min=6,max=64)` 之类 | 初始密码，**仅创建时使用**；见 §四.2 |

> 「创建模式必填」的落地方式（**设计决定**）：用 **Bean Validation 分组**（`@GroupSequence` / `groups`）在 Controller 上按模式触发；若实现复杂度过高，退化为 **Service 内手写分支判空并抛 `1001`**。二者取其一，Step 1 定稿时二选一，**不为此新增错误码**。

**`StudentUpdateDTO`**（PUT 入参）

| 字段 | 校验 | 说明 |
|---|---|---|
| `subjectCode` | `@NotBlank` + `@Pattern("SUBJECT_1|SUBJECT_2|SUBJECT_3|SUBJECT_4")` | 必填 |
| `phaseStatus` | 可选 + `@Pattern`（5 个枚举名） | 学习阶段 |
| `coachRemark` | 可选 + `@Size(max=500)`（对齐列长） | 教练备注 |

---

## 四、业务规则

### 4.1 绑定已有 vs 创建新学员

- **同一入口**（`POST`），由"`username` 是否已在 `sys_user` 存在"分流。
- **字段要求（已拍板）**：
  - **创建新学员**：`username`、`realName`、`phone`、初始密码 **四项必填**；
  - **绑定已有学员**：**不要求**重新填写姓名、电话和初始密码（这些已在库中）。
- **创建时强制 `role=STUDENT`**；若 `username` 命中的是教练/管理员 → `1106`（不得把他人账号"绑成"学员）。
- **创建新账号属现有需求**：`api-design.md` §5 第 1 条原文「教练创建学员：入参含 `username`、`realName`、`phone`；默认密码由后端生成」；`WebMvcConfig` 的「不做自助注册」只针对**学员自助注册**。

### 4.2 初始密码策略（已拍板）

- **由教练在创建时指定初始密码**（`StudentBindDTO.password`），**复用项目现有加密机制**：注入 `SecurityConfig` 的 `PasswordEncoder`（BCrypt），存密文。
- **不得返回明文密码**（`POST` 返回仍为 `Void`，与 `api-design` 一致）；**不得写入日志**（项目既有约定：AI 调用日志亦不记录敏感信息）。
- 已核实：新建账号满足 §1.5 的四个登录条件即可正常登录。

### 4.3 重复绑定 / 解绑后再绑

- **当前有效绑定已存在** → `3101`。
- **历史解绑过（`status=0`）再绑同一人** → **复用原记录**：置 `status=1` 并**刷新 `bind_time` 为当前时间**（已拍板；语义为"本次绑定时间"）。
  - 不可插入新行——`uk_coach_student(coach_id, student_id)` 会拒绝。

### 4.4 解绑与历史数据（已拍板）

- 解绑 = **`coach_student.status = 0`**（`api-design` §4.10 原文；`data-conventions.md` §7 通用状态位 "1 有效 / 0 无效"）。
- **不联动取消预约、不删除任何历史**（`appointment`、`answer_record`、`wrong_book`、`mock_exam`、`student_subject_status`、AI 记录全部保留）。
- **解绑后教练端不可再访问该学员**：列表、PUT、DELETE、status **一律带 `status = 1`** → 对同一 id 的后续操作返回 `3102`。历史数据仍在库中，只是教练端不可见。
- **解绑不影响学员自身**：学员仍可登录、刷题、看自己的错题与学情（这些接口不查 `coach_student`）。

---

## 五、权限校验与事务边界

### 5.1 统一的越权原则（已拍板）

```
WHERE coach_student.id = {id} AND coach_student.coach_id = {coachId} AND coach_student.status = 1
```

- 查不到（不存在 / 不是我的 / 已解绑）→ **一律 `3102 BINDING_NOT_FOUND`**，不区分、不泄露"该学员真实存在"（与 `3001` / `2003` / `4004` 既有口径一致）。
- **`studentId` 只能由该绑定记录反查**，绝不接受前端传入 —— 这是防止"跨教练越权读取学情/错题"的关键。
- **查询不写库**：`GET {id}/status` 的 4 科补齐**只在内存中完成**。

### 5.2 事务边界

| 接口 | `@Transactional` | 理由 |
|---|---|---|
| `POST`（创建+绑定） | **是** | 跨表写 `sys_user` + `coach_student`；避免"账号建了但绑定失败"产生孤儿账号 |
| `POST`（仅绑定） | 同上（同一方法，注解覆盖） | 单条插入，无害 |
| `PUT` | **否** | 单条 `INSERT ... ON DUPLICATE KEY UPDATE`，自身原子（见 §2.3） |
| `DELETE` | 否 | 单表单条写 |
| `GET ×2` | 否 | 只读 |

> **实现要点**：注解加在 `CoachStudentServiceImpl` 的 **public 方法**上（Controller 跨 Bean 调用，代理生效）；**不得**拆成私有方法在同方法内 `this.xxx()` 调用（自调用不走代理、事务静默失效——项目已有此教训，见 `ExamFinalizer`）。

---

## 六、与现有 Service 的复用方案

| 本日需要的能力 | 复用对象 | 结论 |
|---|---|---|
| 学员各科学习阶段/进度 | — | **直接查 `student_subject_status`**（本日新写，逻辑极简） |
| 学员模考历史 / 正确率 / 错题数 / 薄弱点 | `ExamService.history` / `AnalysisService.*` / `WrongBookService.*` | **Step 5 才用**；调用前**必须先做归属校验**，再把 `studentId` 传入 |
| 建号 | `UserService.save()`（`ServiceImpl` 自带） | 直接可用；密码用注入的 `PasswordEncoder` 加密 |
| 查用户信息 | `UserMapper.selectBatchIds` | 列表装配用一次 `IN` |

> **既有实现类保持不动**（`AnalysisServiceImpl` 等只被调用，不被修改）。
> **唯一需要改的既有文件**：`StudentSubjectStatusMapper.java` **新增一个 upsert 方法**（照 `MockExamAnswerMapper` 写法）。

---

## 七、测试用例与验收标准

集合：`doc/testjson/coach-student-api.postman_collection.json`（**唯一真源**，改后必须"先删集合再重新导入"）；用例文档：`doc/test-cases-coach-student.md`（`TC-CS-01…`，与集合一一对应）。
前置（写进集合描述）：清库 SQL + 造数（1 教练、2 学员、1 条已解绑记录）；**组 0 含前置守卫**（照 appointment 集合 `00d`）。

| 编号 | 场景 | 预期 |
|---|---|---|
| TC-CS-01 | 学员列表（有绑定） | `code=200`，含姓名/手机/绑定时间 |
| TC-CS-02 | 学员列表（无绑定） | `total=0` 的 `PageResult`，**非报错** |
| TC-CS-03 | 已解绑学员**不出现**在列表 | 不含该学员 |
| TC-CS-04 | 绑定**已有**学员账号（只传 `username`） | `code=200` |
| TC-CS-05 | **重复绑定** | `3101` |
| TC-CS-06 | 创建并绑定新学员（四项齐全） | `code=200`；`sys_user` 新增，`role=STUDENT`、`status=1`、密码为 BCrypt 密文；**响应体不含明文密码** |
| TC-CS-07 | 创建模式缺 `realName`/`phone`/密码 | `1001` |
| TC-CS-08 | 绑定目标 `username` 实为教练/管理员 | `1106` |
| TC-CS-09 | `PUT` 改科目阶段+备注（记录**不存在**） | `code=200`，且 `student_subject_status` **新增一行** |
| TC-CS-10 | `PUT` 只传 `coachRemark`（记录**已存在**） | `code=200`，`status` **保持原值不变**（COALESCE 语义） |
| TC-CS-11 | `PUT` 两个可选字段都不传 | `1001` |
| TC-CS-12 | `DELETE` 解绑 | `code=200`；`status=0` |
| TC-CS-13 | 重复解绑 / 解绑已解绑 id | `3102` |
| TC-CS-14 | **越权**：对别的教练名下学员执行 PUT/DELETE/status | 一律 `3102`，**与"不存在"同一响应** |
| TC-CS-15 | `GET {id}/status` | 返回**固定 4 科**；缺失科目为 `NOT_STARTED`；**且不产生 INSERT**（查库核对行数不变） |
| TC-CS-16 | **无 token** 访问 | 真实 **HTTP 401** |
| TC-CS-17 | **学员 token** 访问 `/api/coach/**` | 真实 **HTTP 403** |
| TC-CS-18 | 参数校验：`subjectCode` 非法/小写 | `1001` |
| TC-CS-19 | **回归**：重跑 `appointment-api` | 断言失败 0（`coach_student` 是预约绑定校验依据） |

**验收标准**

1. 5 个接口可用，`mvn install` **BUILD SUCCESS**。
2. 集合全绿；**业务断言失败 0**；Apifox 界面若仍显示 401/403 用例"失败"，按既有口径说明为**工具默认断言假阳性**（与业务断言分开表述）。
3. 越权、重复绑定、已解绑三类异常均有对应用例与指定错误码。
4. **未新增错误码**（若确需新增，先单独报批）。

---

## 八、尚存问题（需你拍板）

| # | 问题 | 选项 / 风险 | 我的推荐 |
|---|---|---|---|
| **Q12** | `PUT` 无法把 `coach_remark` **清空为 NULL**（COALESCE 语义所致） | 接受（保持简单）↔ 改为"传空串即清空"↔ 改"先查后写+事务"（并发下靠唯一键兜底） | **先接受**，并在接口文档写明；确有需求再单独设计 |
| **Q13** | `StudentBindDTO` 的"创建模式必填"落地方式 | Bean Validation **分组**（`@GroupSequence`）↔ Service 内**手写分支判空**抛 `1001` | 两者皆可；若 Step 1 发现分组校验会使 Controller 变复杂，**改用 Service 手写判空**（更直观、可读） |
| **Q14** | 初始密码的最小长度/复杂度 | 无项目约定 | 建议 `@Size(min=6,max=64)`，**不引入复杂度正则**（避免过度设计） |
| **Q15** | 包位置 | 新建 `server/coach/` ↔ 放进既有 `server/service/` | **新建 `server/coach/`**（与 `exam`/`appointment`/`analysis`/`ai` 同风格） |

---

## 九、Step 1 起的实施顺序（**本步不做，仅登记**）

```
Step 1：StudentVO + SubjectStatusVO + StudentBindDTO + StudentUpdateDTO            （4 文件）
Step 2：CoachStudentService 接口（5 方法，只签名）                                  （1 文件）
Step 3：StudentSubjectStatusMapper 增加 upsert 方法（改既有文件）＋
        CoachStudentServiceImpl（归属校验、防 N+1、仅 POST 加 @Transactional）      （2 文件）
Step 4：CoachStudentController（5 接口）                                            （1 文件）
Step 5：（当天有余力）learning-data / wrong-book —— 复用既有 Service，调用前加归属校验
Step 6：测试集合 + 用例文档 + 联调 + 回归 appointment-api
```

**Step 1 起将触碰的文件（共 8 个：7 新增 + 1 修改）**
新增：`pojo/vo/StudentVO.java`、`pojo/vo/SubjectStatusVO.java`、`pojo/dto/StudentBindDTO.java`、`pojo/dto/StudentUpdateDTO.java`、`server/coach/CoachStudentService.java`、`server/coach/impl/CoachStudentServiceImpl.java`、`server/controller/CoachStudentController.java`
修改：`server/mapper/StudentSubjectStatusMapper.java`（新增 upsert 方法）
**不改**：`ErrorCode.java`（3101/3102 已存在）、任何 SQL、任何既有 Service 实现。

---

## 附：本文件的核实边界

- 标「已核实」的结论均来自本次对仓库文件的实际读取（附路径/行号或 DDL 片段）；
- 未能确认的内容（如密码最小长度的项目约定）**未写入为事实**，而是列入 §八 待你拍板；
- 本步**未修改**任何 Java / SQL / 测试集合 / 其他文档。
