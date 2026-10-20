# 【Day 32 工作日志】

## 一、日期

2026-10-20

## 二、本阶段目标

按 `doc/系统剩余功能排查报告.md` 的 P1 项，修复：

- 禁用账号、角色变更后旧 JWT 仍能访问受保护接口的问题；
- 学生绑定、教练绑定、聊天、预约等链路对目标账号状态的绕过；
- 预约创建“先查冲突、后插入”的并发竞态，以及预约状态流转的并发覆盖风险。

保留 Day30 AI/RAG、Day31 模拟考试的全部修改，不修改数据库结构，不重置或删除数据。

## 三、原问题与实际代码证据

### 1. JWT 只信任 token 内角色

修复前 `JwtInterceptor.preHandle`：

- 从 token 解析 `userId`、`username`、`role`；
- 直接按 token 中的 `role` 做 `PathRoleRule` 判断；
- 从未按 `userId` 查询 `sys_user`。

因此：

- 管理员禁用账号后，已签发 token 在有效期内仍可调用原角色接口；
- 管理员修改角色后，旧 token 仍按旧角色授权；
- JWT 默认有效期约 24 小时，风险窗口较大。

`AuthController.login` 只在登录时检查账号状态，无法覆盖登录后的状态变化。

### 2. 绑定/聊天/预约目标账号状态

- `CoachStudentServiceImpl.bindOrCreateStudent` 只检查已有账号角色，未检查目标学员 `status`；
- `ChatServiceImpl.requirePeer` 只检查角色和绑定，未检查对方账号状态；
- `AdminService.createBinding` 只检查双方角色，未检查状态；
- `AppointmentServiceImpl.listMyCoaches` 只跳过已删除教练，未跳过禁用教练。

### 3. 预约创建竞态

修复前 `AppointmentServiceImpl.createAppointment`：

- 先 `selectCount` 查询 PENDING/CONFIRMED 冲突；
- 再单独 `insert`；
- 方法没有 `@Transactional`；
- 代码注释明确承认“先查后插”的并发竞态；
- 数据库 `appointment` 没有教练/日期/时段唯一约束。

两个并发请求都可能先看到“无冲突”，然后各自插入一场预约。

预约状态定义保持原规则：

- 占用时段：`PENDING`、`CONFIRMED`；
- 释放时段：`CANCELLED`、`REJECTED`、`COMPLETED`。

### 4. 预约状态流转竞态

`cancel/approve/reject/complete` 也是“先查状态、后 update”，两个并发请求可能同时读到旧状态，后提交者覆盖先提交者（例如同时通过和拒绝）。

## 四、修复方案与修改文件

### 1. JWT 统一账号状态复查

- `driving-exam-server/.../interceptor/JwtInterceptor.java`
  - 注入 `UserMapper`；
  - token 校验后按 `userId` 查询 `sys_user`；
  - 账号不存在/逻辑删除 → 401 `UNAUTHORIZED`；
  - `status != 1` → 401 `ACCOUNT_DISABLED(1102)`；
  - token 中角色与数据库当前角色不一致 → 401 `ROLE_MISMATCH(1106)`；
  - 使用数据库当前角色进行 `PathRoleRule` 判断；
  - request 属性写入数据库当前用户名和角色。
- `frontend/src/utils/request.ts`
  - HTTP 401 时优先显示后端 body 的 `message`（如“账号已被禁用”），仍然清 token 并跳登录页，保持原有处理路径。
- `driving-exam-server/src/test/.../interceptor/JwtInterceptorTest.java`
  - 正常账号、禁用账号旧 token、角色变更旧 token、跨角色路径、缺失/无效 token、不存在用户。

### 2. 绑定/聊天/预约目标账号状态

- `CoachStudentServiceImpl.bindOrCreateStudent`：已有学员 `status != 1` → 1102，不允许绑定/复活。
- `ChatServiceImpl.requirePeer`：对方账号被禁用 → 1102，不创建/进入可发送会话；历史会话列表仍可查看。
- `AdminService.createBinding`：教练或学员被禁用 → 1102。
- `AppointmentServiceImpl.listMyCoaches`：跳过禁用教练。
- `AppointmentServiceImpl.createAppointment`：锁定教练行后检查 `status == 1`，否则 1102。

### 3. 预约创建并发保护

- `UserMapper.selectByIdForUpdate`：`SELECT ... FROM sys_user WHERE id=? AND deleted=0 FOR UPDATE`。
- `AppointmentServiceImpl.createAppointment`：
  - 增加 `@Transactional(rollbackFor = Exception.class)`；
  - **事务第一次读取就锁定教练账号行**，之后才查绑定和冲突；
  - 同一教练的并发创建串行化；不同教练互不阻塞；
  - 冲突查询仍只统计 PENDING/CONFIRMED；
  - 插入后事务提交才释放教练行锁。
- 这样第二个事务在获得锁后才建立一致性读视图，能看到前一个事务已提交的预约，避免 REPEATABLE READ 下旧快照漏掉冲突。

### 4. 预约状态流转并发保护

- `AppointmentMapper.selectStudentOwnedForUpdate`、`selectCoachOwnedForUpdate`；
- `cancelMyAppointment`、`approveAppointment`、`rejectAppointment`、`completeAppointment` 增加 `@Transactional` 并使用预约行锁；
- 两个并发审批/取消/完成只会有一个读到合法旧状态并成功，另一个看到新状态后返回 3004/3005；
- 取消、拒绝、完成后依然保持原状态定义和时段释放规则。

### 5. 测试文件

- `JwtInterceptorTest`（新增）。
- `AppointmentServiceImplTest`（新增）：
  - 锁顺序、正常创建、重复时段 3002、不同时段允许、未绑定 3003、禁用教练 1102、取消 PENDING、审批 PENDING。
- `ChatServiceImplTest`：补充禁用对方账号 1102 测试，并给测试用户补 `status=1`。
- `CoachStudentServiceImplBindStatusTest`（新增）：禁用学员不可绑定。
- 既有 Exam/Coach/Chat 测试继续运行，确认 Day30/Day31 未回滚。

## 五、账号禁用、角色变更、预约并发如何受到保护

### 账号禁用

- 每个 `/api/**` 受保护请求都会在 `JwtInterceptor` 中按 `userId` 查询 `sys_user`；
- `status != 1` 直接 401 + 1102，业务 Controller 不会执行；
- 目标账号在绑定、聊天、预约创建中也有状态检查。

### 角色变更

- token 中角色必须与数据库当前角色一致；
- 不一致返回 401 + 1106，要求重新登录；
- 路径权限使用数据库当前角色，不信任 token 中的旧角色；
- 路径本身角色不匹配仍返回 403，与“旧 token 失效”区分。

### 预约并发

- 创建：教练行锁 + 事务，冲突查询和插入在同一事务内；
- 状态流转：预约行锁 + 事务，状态检查与更新串行化；
- 占用状态保持 PENDING/CONFIRMED，终态保持 CANCELLED/REJECTED/COMPLETED；
- 没有增加唯一约束，避免对现有数据和“同教练不同时段”规则做未经分析的收紧。

## 六、测试与构建结果

### 已实际执行并通过

```text
mvn -pl driving-exam-server -am test \
  -Dtest=JwtInterceptorTest,AppointmentServiceImplTest,ChatServiceImplTest,ChatControllerContractTest,ChatEntityMappingTest,CoachStudentServiceImplBindStatusTest,CoachStudentServiceImplListTest,CoachStudentServiceImplLearningDataTest,CoachStudentServiceImplWrongBookTest,CoachStudentServiceImplAiSuggestTest,CoachStudentServiceImplSavedAiSuggestTest,ExamServiceImplTest,AdminServiceExamConsistencyTest,ExamFinalizerTest,ExamTimeoutSchedulerTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：

- **Tests run: 95, Failures: 0, Errors: 0, Skipped: 0**
- `BUILD SUCCESS`

覆盖：

- 禁用账号旧 token、角色变更旧 token、正常账号、跨角色、缺失/无效 token；
- 目标账号禁用后的聊天、教练绑定、预约创建；
- 预约锁顺序、重复时段、不同时段、终态取消；
- Day31 考试/试卷一致性测试仍通过；
- 既有聊天、教练学员、AI 建议等单元测试仍通过。

### 已实际执行并通过的构建

```text
mvn -pl driving-exam-server -am -DskipTests package
```

结果：`BUILD SUCCESS`。

前端：

```text
cd frontend
npm run type-check
npm run build
```

结果：

- type-check exit 0；
- build exit 0；
- 构建产物包含更新后的 `request-*.js`，仍只有既有 chunk 体积提示。

### 已执行但失败

本阶段定向测试和构建没有失败。

### 因环境限制未执行

- 未在真实 MySQL 上执行两个并发预约请求；
- 未执行真实登录后禁用账号/改角色的 HTTP 验收；
- 未执行 Docker/浏览器验收；
- 未执行 `ExamSettlementConcurrencyTest` 或新增数据库并发测试（当前环境没有隔离的 `TEST_DB_*`）；
- 未执行完整 Maven 测试集合，避免依赖数据库/外部服务的测试产生噪声。

### 仅静态检查

- 检查了 `sys_user`、`appointment` 的现有列和索引；
- `UserMapper` / `AppointmentMapper` 新增 SQL 只使用现有字段，不改变表结构；
- 通过编译和单元测试确认拦截器构造器、Mapper 方法和 Service 调用关系；
- 前端 `vue-tsc` 和生产构建确认 `request.ts` 修改兼容现有类型。

## 七、数据库结构变更与影响

本阶段**没有修改数据库表结构**，没有新增/删除列、迁移脚本或唯一约束。

新增的 `FOR UPDATE` 查询只使用现有表和字段：

- `sys_user.id`、`deleted`、`status`、`role`；
- `appointment.id`、`student_id`、`coach_id`、`deleted`。

对既有数据：

- 不删除、不清空、不重置任何数据；
- 已有预约不自动合并/删除；历史上若已存在同一教练同一天同一时段的重复数据，本阶段不会自动清理；
- 历史 token 不会被主动批量作废，但账号被禁用/角色变更后下一次请求会被拦截；
- 预约行锁可能短暂增加同一教练/同一预约上的等待，不改变正常业务结果。

## 八、需要在 Docker/MySQL/浏览器环境验证的事项

1. 正常登录、管理员禁用学员后，学员旧 token 请求受保护接口应返回 401 + 1102，前端清登录态；
2. 管理员修改角色后，旧 token 请求应返回 401 + 1106，重新登录后按新角色进入；
3. 学生绑定、教练绑定、聊天、预约创建对禁用目标账号的反馈；
4. 两个并发请求抢同一教练同一日期时段，数据库最终只能有一条 PENDING/CONFIRMED；
5. 不同日期/时段仍可正常创建；
6. 两个并发审批/取消/完成同一预约，最终只有一个合法状态变更；
7. 取消/拒绝/完成后同一时段可再次预约；
8. 以上需要在隔离 MySQL 测试库或验收环境验证，不可对已有业务数据做重置。

## 九、未解决/需继续关注

- 没有服务器端 logout/黑名单：用户主动退出目前仍然只清本地 token，若账号未被禁用，旧 token 在有效期内仍可能被重放；本阶段解决的是“禁用/角色变更后立即失效”，不是完整 token 撤销体系。
- 未增加预约时段唯一约束；当前依赖事务 + 教练行锁，适用于现有单实例/小规模部署。若未来多实例或高并发，需要进一步评估数据库约束/分布式锁。
- 真实 MySQL 并发行为和 REPEATABLE READ 快照效果未在当前环境执行验证，必须按第八节在隔离环境确认。
- Day30/Day31 修改已保留，本阶段没有改动 AI/RAG、Dashboard 或模拟考试业务逻辑。
