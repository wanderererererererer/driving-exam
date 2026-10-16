# 【Day 19 工作日志】

## 一、日期

2026-10-13

**时间线说明（与计划不一致，如实记录）**：`doc/Day/Day18-工作日志.md` 的「明日计划（Day 19）」写的是**管理端**（数据概览 / 用户管理 / 题库管理含被推迟的 CRUD + Excel 导入 等）。实际本日做的是**教练端 · 学员管理**——与 `doc/coach-student-design.md` 标题《教练端 · 学员管理设计（Day 19 · 定稿）》的编号一致；管理端整体顺延。

本日**只做后端 + 测试资产 + 联调**，教练端学员管理的**前端页面未做**，记入第七节遗留。

## 二、今日目标

完成**教练端 · 学员管理**：5 个接口（我的学员列表 / 绑定或创建学员 / 改科目阶段与备注 / 解绑 / 学员四科状态）→ 接口级联调 → 生成测试集合与用例文档，并回归既有集合。

## 三、完成的工作

**数据库：** **无新表**——只用既有 `coach_student`、`student_subject_status`、`sys_user` 三表，本模块**只是在既有表上写业务**。**未改任何 DDL。**

**后端：** 新增 **7 个文件**、改动既有**代码**文件 **1 个**。

### 1. DTO / VO（4 个新增）

- `pojo/vo/StudentVO.java`（`bindId` / `studentId` / `username` / `realName` / `phone` / `bindTime`；**`bindId` 为后补字段**，见问题 1）。
- `pojo/vo/SubjectStatusVO.java`（`subjectCode` / `status` / `mockPassCount` / `lastMockScore`；**刻意不含 `studyProgress`**——该列当前无写入方、恒为 `0.00`）。
- `pojo/dto/StudentBindDTO.java`（`username` 必填 + `@Size(max=50)`；`realName` / `phone`(格式 `1[3-9]\d{9}`) / `password`(6~64) 创建模式必填）。
- `pojo/dto/StudentUpdateDTO.java`（`subjectCode` 必填 + `@Pattern` 限 `SUBJECT_1~4`；`phaseStatus` / `coachRemark` 可选）。

### 2. Service（`server/coach`）

- `CoachStudentService.java`（**5 方法，仅签名**；不继承 `IService`）。
- `coach/impl/CoachStudentServiceImpl.java`（归属校验 `loadOwnedActive`、防 N+1、**仅 POST 加 `@Transactional`**）。

### 3. Mapper（改动既有 1 个）

| 文件 | 改动 |
|---|---|
| `server/mapper/StudentSubjectStatusMapper.java` | 新增 `upsertCoachFields()`：`INSERT … ON DUPLICATE KEY UPDATE` + `COALESCE`（照 `MockExamAnswerMapper` / `WrongBookMapper` 的既有写法）。**本模块唯一改动的既有文件。** |

### 4. Controller

- `server/controller/CoachStudentController.java`（`/api/coach/students`，5 端点；`coachId` 走 `@RequestAttribute(JwtInterceptor.ATTR_USER_ID)`，不采信前端传参）。

**前端：** （本日不涉及）教练端学员管理页面未做。

**文档 / 测试：**

- `doc/coach-student-design.md`（Step 0 设计定稿，决策 Q1~Q15）。
- `doc/testjson/coach-student-api.postman_collection.json`（回归测试集合，**49 请求 / 133 断言**，组 0~6，含前置守卫 `00e` / `00f` / `00g` / `00h`）。
- `doc/test-cases-coach-student.md`（测试用例文档，`TC-CS-01`~`19` 及子编号，与集合编号一一对应）。
- `doc/testsql/coach-student-test-setup.sql`（**写库**准备脚本）。
- `doc/testsql/coach-student-precheck.sql`（**独立只读预检查**，**仅 SELECT**）。

## 四、关键技术决策

| 决策点 | 方案 | 理由 |
|---|---|---|
| 三个按 id 操作接口的 `{id}` | **`coach_student.id`**，列表 VO 用 `bindId` 暴露它 | `{id}` 要的是「绑定记录」主键；`studentId`（`sys_user.id`）不可混用。初版 `StudentVO` 缺 `bindId`，无法闭环（见问题 1） |
| 越权 / 不存在 / 已解绑 | **一律 `3102 BINDING_NOT_FOUND`** | 三者返回同一响应，不泄露「该绑定确实存在、只是不属于你」 |
| 解绑语义 | **`status = 0` 软解绑，保留行** | 不删行、不级联取消预约、不删任何历史；教练端后续操作因 `status=1` 过滤而不可见 |
| 解绑后重绑 | **复用原行**：置 `status=1` 并**刷新 `bind_time`** | 不可插新行——`uk_coach_student(coach_id, student_id)` 会拒绝 |
| 改学情落库 | 落到既有 `student_subject_status`（`upsertCoachFields`，**COALESCE 空值语义**） | `coach_student` 无对应列；不改 DDL |
| 事务边界 | **只有 POST 加 `@Transactional`** | 仅 POST 跨表写（`sys_user` + `coach_student`）；PUT 是单条原子 upsert、DELETE 是单表单条写，均不加 |
| 防 N+1 | 分页查绑定 → 收集去重 `studentId` → **一次 `IN` 查 `sys_user`** → 内存装配；空集合直接短路 | 绝不循环里逐条查库（`IN ()` 是 SQL 语法错误） |
| 错误码 | **未新增**，直接用既有的 `3101` / `3102` | 设计阶段已确认两码已定义、全项目零引用 |

## 五、遇到的问题及解决方案

**问题 1（`StudentVO` 缺 `bindId`，列表给不出后续接口所需的 id）**

- 现象：Step 5 测试准备时发现——初版 `StudentVO` 只有 `studentId`，而 `PUT /{id}`、`DELETE /{id}`、`GET /{id}/status` 三个接口的 `{id}` 要的是 `coach_student.id`；**列表给不出这个 id**，前端无法闭环，测试也只能靠 SQL 直查。
- 解决：采纳**方案 A**——`StudentVO` 增加 `bindId`（取自 `CoachStudent.id`），**三个接口的 `{id}` 语义不变**（仍为 `coach_student.id`）。改动 3 处：`StudentVO`、`CoachStudentServiceImpl.toStudentVO`、设计文档。

**问题 2（Controller 路径与项目约定冲突）**

- 现象：Step 4 时清单写 `server/coach/CoachStudentController.java`，但项目 **8/8 个 Controller 都在 `server/controller/`**、设计文档 §九 也与之不一致。
- 解决：采纳**方案 A**——放 `server/controller/CoachStudentController.java`，与既有约定统一。

**问题 3（测试准备 SQL 与集合的**安全审计**，共 3 轮只读审计 + 最小修复）**

- 现象：测试准备资产（准备 SQL + 集合 + 用例文档）存在若干**数据安全 / 假阳性**隐患。
- 修复（均**只读审计 + 最小修复**，不动业务、不自动改库）：
  1. `student02` 复位语句**默认禁用**（整体注释），避免自动停用既有绑定；
  2. 第 2 步绑定 INSERT **移除 `ON DUPLICATE KEY UPDATE`**，改为「**仅该组合尚无绑定行时插入**」（不复活 `status=0`、不改既有行）；
  3. 只读预检查**拆分为独立文件** `coach-student-precheck.sql`（**仅 SELECT**，可整段执行），主脚本顶部醒目提示**禁止 Execute All**；
  4. 集合中**提取失败即清空变量**（`00e` / `00f` / 四个登录 / `TC-CS-14a`），杜绝沿用上一次运行的旧值造成级联误报；
  5. 新增**弱守卫 `00h`**（`TC-CS-06` 前，`cs_new_01` 前置；并明确其**只能**排除「已绑定给 coach01」、**不能**证明全局不存在，须配合只读 SQL）；
  6. 绑定 INSERT 两侧账号均加 **`role` 正确 + `status=1` + `deleted=0`** 校验；
  7. 全部表名**统一 `driving_exam.` 前缀**（避免写入落到别的 schema）。

**问题 4（Apifox「默认断言」造成的假阳性，401 / 403）**

- 现象：最终报告里 `TC-CS-16a` / `16b` / `16c` 被界面按「非 200」计为失败。
- 原因：这三条是**鉴权用例**，`401 / 403` 正是**刻意返回的真实状态码**；红的是 Apifox 的「请求失败」，而我们自己写的断言**全部通过**。
- 处置：**不改鉴权逻辑**，只在集合描述与用例文档 §0.4 标注为**已知假阳性**。本现象本项目已多次出现（见 Day10 / Day15 / Day17 / Day18 记录）。

**问题 5（DB 快照变化：两组测试绑定在执行前已存在）**

- 现象：执行前只读检查发现 `coach01↔student_cs01`、`coach02↔student_cs02` **已存在**（`status=1`），已不同于更早的「4 账号 / 1 绑定」快照。
- 处置：第 2 步绑定 INSERT 是「**仅组合不存在时插入**」，重跑为 **no-op**；**未做任何清理 / 解绑 / 重建 / 复活**。写入步骤可视前置满足情况**跳过**。

## 六、产出物清单

**本日统计口径（分类计数，勿混）**

| 类别 | 数量 | 明细 |
|---|---|---|
| 新增 · 后端 | **7** | VO 2 · DTO 2 · Service 1 · ServiceImpl 1 · Controller 1 |
| 改动 · 后端代码 | **1** | `server/mapper/StudentSubjectStatusMapper.java`（新增 `upsertCoachFields`） |
| 新增 · 文档 / 测试 | **5** | 设计 1 · 测试集合 1 · 用例文档 1 · 准备 SQL 1 · 只读预检查 SQL 1 |

**后端（新增 7）**

```
pojo/vo/StudentVO.java · SubjectStatusVO.java
pojo/dto/StudentBindDTO.java · StudentUpdateDTO.java
server/coach/CoachStudentService.java · coach/impl/CoachStudentServiceImpl.java
server/controller/CoachStudentController.java
```

**后端（改动 1 个既有文件）**

```
server/mapper/StudentSubjectStatusMapper.java   加 upsertCoachFields()
```

**文档 / 测试（新增 5）**

```
doc/coach-student-design.md
doc/testjson/coach-student-api.postman_collection.json   （49 请求 / 133 断言）
doc/test-cases-coach-student.md
doc/testsql/coach-student-test-setup.sql
doc/testsql/coach-student-precheck.sql
```

**验证 —— 接口级联调（Apifox v2.8.40，本地执行）**

- 报告文件：`doc/Test/coach-student.html`；**运行时间 2026-10-13 16:42:40**。
- **HTTP 请求 49 个**，其中 **3 个为预期的鉴权用例**（`TC-CS-16a` 无 Token → **401**；`16b` 学员 Token 访问教练接口 → **403**；`16c` 学员 Token 访问 POST → **403**）。Apifox 按「非 200 即请求失败」统计，界面记为「**请求失败 3**」（请求通过率 93.88%）。**这 3 个不是业务失败**——三条用例的自定义断言**全部通过**，`401/403` 正是刻意返回的真实状态码。
- **断言总数 133，断言失败 0。**
- **结论：本次集合内的断言全部通过。** 口径提示：**「请求失败数 3」与「断言失败数 0」是不同层，不要混为一谈**；不得为把请求通过率做成 100% 而改鉴权逻辑。
- 前提：后端已由用户在本地启动（集合能跑通即表明 5 个接口可用）。

## 七、遗留问题与明日计划

- 遗留（按优先级）：
  1. **教练端学员管理前端未做**——学员列表 / 绑定与创建 / 改学情 / 解绑 / 学情展示等页面。
  2. **Step 5 的 `learning-data` / `wrong-book` 接口未做**（设计里登记为「当天有余力」项）。
  3. **管理端整体未做**——数据概览 / 用户管理 / **题库管理（含被推迟的 CRUD + Excel 导入）** / 试卷管理 / 预约管理 / AI 统计 / 个人中心。
  4. **`doc/testjson/all-api.postman_collection.json` 合并集合延后**——待剩余接口加入后再一次性合并。
  5. **测试后数据库状态未再核对**——本集合含创建账号 / 改学情 / 解绑重绑等写副作用；如需再次回归，应先跑 `doc/testsql/coach-student-precheck.sql` 确认前置。
- 明日计划（Day 20）：**管理端**（或按用户口径）——先出设计梳理方案，确认后再写代码。
