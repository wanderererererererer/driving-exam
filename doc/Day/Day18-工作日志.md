# 【Day 18 工作日志】

## 一、日期

2026-10-09

**时间线说明（与计划不一致，如实记录）**：`doc/Day/Day17-工作日志.md` 的「明日计划（Day 18）」写的是**教练端**（学员管理 / 学情查看 / 错题查看 / AI 建议 / 预约确认）。实际本日做的是**预约模块（科二 / 科三）后端**——理由是教练端的"预约确认"本身就依赖预约模块，先把它做出来更顺。因此：

- 本日**没有**实现任何教练端学生管理功能（列表 / 绑定 / 解绑 / 学情 / 错题 / AI 建议均未做），教练端整体顺延，已记入第七节遗留；
- 本日**只做后端**，预约模块前端（学员「我的预约」、教练「预约处理」）**未做**，也记入遗留。

## 二、今日目标

完成**预约模块**后端：学员端 5 个接口（我的教练 / 创建 / 列表 / 详情 / 取消）+ 教练端 4 个接口（列表 / 通过 / 拒绝 / 完成），共 **9 个接口**；随后做接口级联调、生成测试集合与测试用例文档。

## 三、完成的工作

**数据库：** **无新表**——`appointment`、`coach_student` 两表早已在库，本模块**只是在既有表上写业务**。另产出 **1 个注释同步脚本**。

- `sql/alter-appointment-status-comment.sql`：把 `appointment.status` 的**列注释**从四值更新为五值（`PENDING/CONFIRMED/CANCELLED/REJECTED/COMPLETED`）。**只改注释，不动结构**，可重复执行。

**后端：** 新增 **13 个文件**、改动既有**代码**文件 **1 个**。

> **本日改动面口径（避免误解，分类统计）**：新增文件 **17 个**（后端 13 + SQL 1 + 文档/测试 3），另有**交接材料 1 个**（非模块产出）；改动**既有**文件 **3 个**——其中**代码 1 个**（`ErrorCode.java`）、**文档 2 个**（见 §三.6）。**"代码只改了 1 个文件"不等于"整个仓库只动了 1 个既有文件"。**

### 1. 实体 + 枚举 + Mapper

- **实体 2 个**：`Appointment`（主数据表，有 `deleted` → 带 `@TableLogic`）、`CoachStudent`（**该表没有 `deleted` 列 → 刻意不加 `@TableLogic`**，有效性用 `status` 表达：1 有效 / 0 解绑；`bindTime` 交给数据库默认值）。
- **枚举 1 个**：`AppointmentStatus`（`PENDING / CONFIRMED / CANCELLED / REJECTED / COMPLETED`，javadoc 里带状态机）。
- **Mapper 2 个**：`AppointmentMapper` / `CoachStudentMapper`，**均仅用 `BaseMapper`，无自定义 SQL**。

### 2. DTO / VO

- **DTO 2 个**：`AppointmentCreateDTO`（`coachId` `@NotNull`；`subjectCode` `@Pattern` 限科二 / 科三；`appointmentDate` `@NotNull` + `@FutureOrPresent`；`timeSlot` `@Pattern` 限**固定四时段**；`remark` `@Size(max=255)`）、`AppointmentRejectDTO`（只有 `coachRemark` 一个字段，**可为空**）。
- **VO 2 个**：`CoachVO`（`coachId / realName / phone`）、`AppointmentVO`（15 字段，**学员端与教练端共用一个**：两侧的姓名与电话都带，`status` 用枚举，`LocalDate` 走 Boot 默认 `yyyy-MM-dd`）。

### 3. Service（`server/appointment`）

- `AppointmentService`（**9 方法，不继承 `IService`**——这是业务流程 Service，与刷题 / 考试同类）+ `AppointmentServiceImpl`。
- **7 个私有工具（共 8 个方法，`userIdsOf` 有两处重载）**：`loadStudentOwned` / `loadCoachOwned`（**按身份拆成两个方法**）、`loadUserMap`、`userIdsOf(Appointment)` / `userIdsOf(List)`、`toPageResult` / `toVO` / `toCoachVO`。

### 4. Controller

- `StudentAppointmentController`（`/api/student/appointment`，5 接口；`/coaches` 写在 `/{id}` **之前**，避免路径被吃掉）。
- `CoachAppointmentController`（`/api/coach/appointments`，4 接口）。
- 两端都从 `@RequestAttribute(JwtInterceptor.ATTR_USER_ID)` 取身份，**不采信前端传参**，也不再重复做角色判断（`PathRoleRule` 已在拦截器层按路径前缀完成）。

### 5. 改动既有代码（1 个）

| 文件 | 改动 |
|---|---|
| `common/exception/ErrorCode.java` | 新增 `APPOINTMENT_STATUS_ILLEGAL(3005, "当前预约状态不允许此操作")`（30xx 段，与 3001~3004 同段） |

### 6. 同步既有文档（2 个既有文件）

- `doc/api-design.md` §4.8 / §4.10：预约列表的 `status?` 注明**五值**（补上 `REJECTED` 这一新增取值）。
- `doc/data-conventions.md` §6：`appointment.status` 由四值扩为五值，并补齐**流转 / 三个终态 / 取消与拒绝规则 / 时段占用**口径。

> 说明：以上只是把**已经写进代码的口径**回填到既有文档，不改任何接口设计。

**前端：** （本日不涉及）——预约模块本期**刻意只做后端**，前端二期再开。

**文档 / 测试：**

- `doc/appointment-design.md`（预约模块设计文档）：9 接口、状态机、5 条关键取舍、DTO/VO 清单、SQL 思路、实现顺序、关键决策汇总。
- `doc/testjson/appointment-api.postman_collection.json`（回归测试集合）。
- `doc/test-cases-appointment.md`（测试用例文档，TC-AP-01~33，与集合**编号一一对应**）。

## 四、关键技术决策

| 决策点 | 方案 | 理由 |
|---|---|---|
| 教练"拒绝"的状态 | **独立 `REJECTED`**，不复用 `CANCELLED` + 备注推导 | 展示层要区分"我自己取消的"与"被教练拒绝的"；若都落 `CANCELLED`，前端只能靠"备注是否为空"猜，脆弱 |
| 时段取值 | 库里是自由文本，**由 DTO 的 `@Pattern` 固定四时段** | 不约束则 `8:00-10:00` 与 `08:00-10:00` 会被判成两个时段，**`3002` 时段冲突检测形同虚设** |
| 时段"占用"口径 | **只算 `{PENDING, CONFIRMED}`**，三个终态**释放**时段 | 否则"取消 / 拒绝 / 完成过的时段永久占位"，学员再也约不上——**本模块最容易写错的一条** |
| 并发双写 | `appointment` **无唯一键 → 不处理**并发 | 加唯一键会让**终态记录继续占位**（取消后仍冲突），代价更大；课设单用户场景，**接受并记录在案** |
| 教练侧动作集 | 只有 `approve / reject / complete`，**无 `cancel`** | `cancel` 是**发起方（学员）**的动作；教练是**响应方**。教练侧若能取消，会与 `reject` 重叠，还引入"谁先谁后"的竞态 |
| 越权与"不存在" | **返回同一个 `3001`** | 若"别人的"返 403、"不存在"返 404，攻击者能据此**探测 id 是否存在**（与 AI 记录 / 错题 / 考试同口径） |
| 校验顺序 | **先归属、后状态** | 顺序反了会向非本人**泄露"这条记录存在"** |
| 防 N+1 | 分页查出预约 → 收集去重用户 ID → **一次 `IN` 查 `sys_user`** → 内存映射 | 绝不循环里逐条查库；空集合先返回（`WHERE id IN ()` 是 MySQL 语法错误） |
| 事务边界 | 五个写方法（create / cancel / approve / reject / complete）**都不加 `@Transactional`** | **已逐个核对方法体**：五个写方法的**写操作各只有 1 条**（`create` → `insert`，其余四个 → `updateById`），且**全部落在同一张表 `appointment`**，**无跨表写**；写之前的 `selectCount` / `selectOne` 是**读**，不产生"写一半"的风险。故无一致性边界需要事务——与刷题作答、AI 落库口径一致，也不必抽独立 Bean。**注意措辞**："写只有一条"指的是**写操作**，方法体内仍有读操作（`create` 有 2 次读：绑定的 `coach_student` + 冲突的 `appointment`） |
| `status` 分层 | **实体用 `String`、VO 用枚举 `AppointmentStatus`** | 与 `MockExam.status` / `StudentSubjectStatus.status`、`WrongBookItemVO.category` 同一做法 |
| 查不到用户时的处理 | `listMyCoaches`**跳过**；两条预约列表**保留记录**、姓名留 `null` | "**选择对象要可用**"（姓名空的教练卡片会让学员误点）vs "**历史记录要完整**"（历史不该因对方账号被删而消失）——**两者相反是有意为之** |

## 五、遇到的问题及解决方案

**问题 1（改动既有文件时漏改 `ErrorCode`，编译直接挂）**

- 现象：`mvn install` 报 **3 个错误，全部指向 `ErrorCode.APPOINTMENT_STATUS_ILLEGAL`**。
- 原因：设计阶段已确认"新增 `3005`"，但**落地实现时只写了 Service，忘了同步 `ErrorCode` 枚举**。
- 解决：补上常量；并顺带全项目扫描所有 `ErrorCode.X` 引用确认无其它悬空引用（结果：无）。
- **值得警惕的模式**：本项目中**新建文件很少漏，改动既有文件容易漏**。此后凡涉及"改动既有文件"的任务，一律**先列清单、逐条核对**。

**问题 2（时段不约束则冲突检测失效）**

- 现象（设计阶段发现）：`time_slot` 在库里是 `varchar` 自由文本、**没有字典表**。若放任前端传值，`8:00-10:00` 与 `08:00-10:00` 会被当成不同字符串，冲突检测（`3002`）**永远查不出来**。
- 解决：**不建字典表**，改用 DTO 的 `@Pattern` 把取值锁死为四个固定时段；顺带解决"前端自由输入"的问题。

**问题 3（测试"错更多了"：一次未清库引发组 4 三连锁）**

- 现象：同一份测试集合，第二次跑时失败条数**从 3 涨到 5**，且新增的 3 条集中在组 4（`3002` / 状态断言 / `3005`）。
- 原因：**跑前没有重新执行集合描述里的「准备 SQL ① 清库」**。上一跑留下的那条 `06-03/14:00-16:00` 已被 approve 成 `CONFIRMED`（**占用态**），于是本跑"建夹具"直接撞 `3002` → 夹具没建成 → 后面两步拿到的是**旧的 `REJECTED` 记录** → 连锁报错。**三条不是三个 bug，是一个原因的因果传播。**
- 解决：① 明确"**失败重跑前必须先再执行一次准备 SQL**"；② 在组 0 **新增一条前置守卫** `00d`：用教练端列表断言 `total == 0`，**库没清就在第 4 个请求当场暴露**，而不是拖成组 4 的连锁红。选教练端列表而不是学员端，是因为冲突是按 `coach + 日期 + 时段` 判的，教练端为空才真正覆盖"时段没被占"这个前置。

**问题 4（Apifox 集合里学员 token 从某一步起全部 401）**

- 现象：某次跑，`00` 登录正常、`TC-AP-01/03` 用 `{{token}}` 正常，但**从 `TC-AP-04` 起所有学员端请求全部 401**；而同一跑里 `coachToken`、`token02` 全程有效。
- 定位（三步排除）：① **集合文件没问题**——`AP-01/03/04` 引用的是同一个 `{{token}}`，全集合只有 `00` 写它，且无任何前置 / 组级脚本；② **后端排除**——`JwtInterceptor` 是无状态的，401 只在头部缺失 / 非 `Bearer` / 签名或有效期不合法时产生，`jwt.expire-seconds` 为 24 小时，且**同一次运行内**另外两个 token 全程有效；③ **判据**——`TC-AP-31b`（学员 token 访问教练端，预期 `403`）返回的是 **`401`**，说明发出去的 `Authorization` 里 **token 被置空**（若 token 是教练或 student02 的，都会是 403）。
- 处置：集合变量由通用名 `token` **改名为 `studentToken`**，登录脚本写入、28 个请求的请求头同步替换（带 `}}` 精确匹配，未触碰 `{{token02}}`）。改后**一次通过**，`AP-31b` 回到正确的 `403`。
- **诚实标注**：写入方在 **Apifox 侧**（环境/全局同名变量、或 UI 上配置的"提取变量"），**未直接读取到，根因未最终确认**——`.json` 里看不到 UI 配置。改名切断了这条链路，属**规避**而非确认根因。**待核实**。

**问题 5（Apifox「默认断言」造成的假阳性）**

- 现象：最终报告里 `TC-AP-31a` / `31b` 报红，提示"HTTP 状态码错误：应当是 200 实际却是 401 / 403"。
- 原因：这两条是**鉴权用例**，`401 / 403` 正是我们**刻意返回的真实状态码**；红的是 **Apifox 项目级「默认断言：响应状态码 = 200」**，我们自己写的两条断言**全部通过**。
- 处置：**不改集合**，只在集合描述里加一段「已知假阳性」说明，避免后来者当 bug 去查。
- **口径提示**（供后续日志与答辩统一）：`doc/Day/Day10`、`Day15`、`Day17` 都记录过同一现象；其中 `Day15` 记为"第 5 次"（并列出错题本 2 次 / AI 1 次 / 本次 1 次），`Day17` 未记次数。**本节不给出新的次数结论，以免与既有记录冲突**——如需统一次数，应回头逐篇核对后再定。

## 六、产出物清单

**本日统计口径（分类计数，勿混）**

| 类别 | 数量 | 明细 |
|---|---|---|
| 新增 · 后端 | **13** | 实体 2 · 枚举 1 · DTO 2 · VO 2 · Mapper 2 · Service 2 · Controller 2 |
| 改动 · 后端代码 | **1** | `common/exception/ErrorCode.java`（新增 `APPOINTMENT_STATUS_ILLEGAL(3005)`） |
| 新增 · SQL | **1** | `sql/alter-appointment-status-comment.sql` |
| 新增 · 文档 / 测试 | **3** | `doc/appointment-design.md` · `doc/test-cases-appointment.md` · `doc/testjson/appointment-api.postman_collection.json` |
| 改动 · 既有文档 | **2** | `doc/api-design.md`（§4.8 / §4.10） · `doc/data-conventions.md`（§6） |
| 新增 · 交接材料 | 1 | `doc/GPT军师交接简报.md`（**非模块产出**） |

> **合计**：新增 **17** 个模块文件 + **1** 个交接材料；改动**既有**文件 **3** 个（**代码 1** + **文档 2**）。

**后端（新增 13）**

```
pojo/entity/Appointment.java · CoachStudent.java
pojo/enums/AppointmentStatus.java
pojo/dto/AppointmentCreateDTO.java · AppointmentRejectDTO.java
pojo/vo/AppointmentVO.java · CoachVO.java
server/mapper/AppointmentMapper.java · CoachStudentMapper.java
server/appointment/AppointmentService.java · impl/AppointmentServiceImpl.java
server/controller/StudentAppointmentController.java · CoachAppointmentController.java
```

**后端（改动 1 个既有文件）**

```
common/exception/ErrorCode.java   加 APPOINTMENT_STATUS_ILLEGAL(3005)
```

**SQL（新增 1）**

```
sql/alter-appointment-status-comment.sql   仅更新 appointment.status 列注释为五值
```

**文档 / 测试**

- `doc/appointment-design.md`（预约模块设计文档）
- `doc/testjson/appointment-api.postman_collection.json`（**42 请求 / 118 断言**，含前置守卫 `00d`）
- `doc/test-cases-appointment.md`（33 条用例，与集合编号一一对应）
- 同步既有文档：`doc/api-design.md`（§4.8 / §4.10）、`doc/data-conventions.md`（§6）

**交接材料（非本模块产出，单独归类）**

- `doc/GPT军师交接简报.md` —— 当日为**更换外部顾问**整理的交接材料，**不属于预约模块交付物**。

**验证**

- 后端 `mvn install` **BUILD SUCCESS**。
- **接口级联调（Apifox）**：最终报告 `doc/Test/appoint.html` 记录 **42 请求 / 118 断言 / 断言失败 0**。
  报告中 **Apifox 界面显示的 2 条失败 = `TC-AP-31a` / `31b`**，成因即问题 5 的**默认断言冲突**（两条用例的自定义断言均通过）——**不是系统缺陷**。**"断言失败 0"与"界面失败 2"这两个数说的是不同层，不要混为一谈。**
- **5 个状态的落库证据**：`PENDING` / `CONFIRMED` / `CANCELLED` / `REJECTED` / `COMPLETED` 各有用例 + SQL 核对（`doc/test-cases-appointment.md` 验收清单 5.2）。
- **两条边界验证通过**：① **终态释放时段**——`CANCELLED` 后、`REJECTED` 后同一时段均可重新预约；② **未绑定学员**的「我的教练」返回**空数组**（不抛 `3003`）。

## 七、遗留问题与明日计划

- 遗留（按优先级）：
  1. **预约模块前端未做**——学员「我的预约」+ 新建预约弹窗、教练「预约处理」；本期刻意只做后端，已记入二期。
  2. **教练端学生管理未做**——列表 / 绑定与创建 / 解绑 / 学员状态详情 / 学习数据 / 错题 / AI 建议 / 教练确认；**原计划在 Day 18，被预约模块顺延**。
  3. **Day 19 管理端待做**——含**被推迟的题库管理**（CRUD + Excel 导入），以及用户管理、数据概览、AI 统计。
  4. **考试前端页面未做**——试卷列表 / 考试进行 / 成绩报告 / 考试历史四页，及 `api/exam.ts`、路由与菜单。
  5. **`mockScore` / `mockExam*` 未回填进学情分析**——`AnalysisServiceImpl` 里这几个字段**仍恒为 null**。
  6. **`all-api.postman_collection.json` 合并集合延后**——等剩余接口也加入后再一次性合并。
  7. **第 4 周待做**：测试 / 部署 / 课程设计报告。
  8. **`doc/api-design.md` §3 错误码示例行缺 `3005`**——代码里已有 `APPOINTMENT_STATUS_ILLEGAL(3005)`，而该文档的段位示例只列到 `3004`（**文档与源码不一致**）。**本轮只在日志中登记，未修改该文档**，待后续单独补一行。
- 明日计划（Day 19）：**管理端** —— 数据概览、用户管理、知识点管理、**题库管理（含被推迟的 CRUD + Excel 导入）**、试卷管理；先出设计梳理方案，确认后再写代码。
