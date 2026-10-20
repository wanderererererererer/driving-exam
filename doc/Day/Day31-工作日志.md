# 【Day 31 工作日志】

## 一、日期

2026-10-20

## 二、本阶段目标

按 `doc/系统剩余功能排查报告.md` 的模拟考试缺口，修复考试业务闭环与数据一致性：

- 逐题成绩报告与学生历史考试入口；
- 服务端超时收尾，不依赖前端倒计时；
- 答案必须属于当前试卷，防止构造请求写入试卷外题目；
- 重复题目、考试时长/及格分边界校验；
- 考试开始后题库/试卷变更的一致性保护；
- 重复交卷、手动交卷与超时交卷的幂等边界。

保留 Day30 的 AI/RAG 修改，不修改数据库表结构、不删除或重置任何数据。

## 三、发现的实际问题与修复

### 1. 前端缺少逐题成绩报告

修复前：

- `frontend/src/views/student/ExamAnswerView.vue` 的已结束状态只展示总分、正确/错误/未答数量和及格线；
- `ExamReportVO.items`、`ExamReportItemVO` 已在类型中定义，后端也已返回逐题字段，但页面没有渲染；
- `frontend/src/views/student/ExamRecordsView.vue` 没有查看报告的操作；
- 路由中没有 `/student/exam/:examId/report`。

修复：

- 新增 `frontend/src/views/student/ExamReportView.vue`：
  - 调用 `GET /api/student/exam/{examId}/report`；
  - 展示总分、及格线、答对/答错/未答；
  - 逐题展示题型、题干、选项、你的答案、正确答案、是否答对和解析；
  - 服务端归属校验继续生效，页面不传用户 ID。
- `frontend/src/views/student/ExamRecordsView.vue` 增加“查看报告”操作。
- `frontend/src/views/student/ExamAnswerView.vue` 的成绩区增加“查看逐题报告”按钮。
- `frontend/src/router/index.ts` 增加路由 `StudentExamReport`。

### 2. 超时只依赖前端倒计时

修复前：

- 前端 `markTimeUp()` 只停止计时并显示提示，不调用交卷；
- 页面仍保留“交卷流程将在下一阶段接入”的过期文案；
- 客户端关闭或网络请求丢失时，考试可能长期停留在 `ONGOING`。

修复：

- `ExamServiceImpl.answer()` 在服务端时间已超时时先按超时结算，再拒绝延迟到达的答案；
- 新增 `MockExamMapper.selectExpiredOngoingIds`，按 `start_time + exam_paper.duration` 查找已过期的进行中考试；
- 新增 `ExamTimeoutScheduler`，默认每 60 秒扫描一次，调用 `ExamService.finalizeExpiredExams(50)`；
- `DrivingExamApplication` 启用 `@EnableScheduling`；
- `application-test.yml` 设置 `exam.timeout-scan-enabled: false`，避免集成测试产生额外扫描；
- `ExamAnswerView` 倒计时归零时锁定作答并自动提交；失败时显示明确提示并保留手动交卷重试入口；
- 删除过期文案，改为真实的自动交卷状态提示。

超时与手动交卷同时发生时，仍由 `ExamFinalizer` 的 `ONGOING → 终态` 条件抢占保证只有一方结算；定时任务也调用同一个 Finalizer，不新增第二套判分逻辑。

### 3. 答案归属校验不完整

修复前：

- `answer()` 只校验考试归属和状态，不校验题目是否属于试卷；
- `submit()` 请求体中的试卷外题目会被静默忽略；
- `judge()` 已有归属校验。

修复：

- `answer()` 通过 `exam_paper_question` 计数校验题目属于当前考试试卷，否则返回 `PARAM_ERROR(1001)`；
- `submit()` 对请求体中的每个题目检查是否属于试卷，试卷外题目直接返回 1001；
- 历史草稿中的试卷外题 ID 在交卷时过滤，不进入报告；
- 非本人考试仍通过 `loadOwnedExam` 统一返回 2104，不泄露存在性。

### 4. 试卷停用后已开始考试无法交卷/报告

修复前 `submit`、`report`、`resume`、`judge` 都使用“试卷必须启用”的 `loadPaper`，管理员停用试卷后，已有考试可能无法交卷或查看报告。

修复：

- 新增 `loadPaperForExistingExam`：只要求试卷存在，不要求 `status=1`；
- `start` 仍使用启用校验和试卷行锁；
- 已开始考试在试卷停用后仍可恢复、交卷、判题和查看历史报告；
- 管理员端新增规则：存在进行中考试时禁止停用试卷；只有已完成历史记录的试卷可以停用，且历史报告仍可读取。

### 5. 试卷/题库变更影响历史成绩

修复：

- `AdminService.updatePaper` 原有“有任意 `mock_exam` 引用即禁止改题目组成”保留；
- 题目一旦进入任何考试的试卷，`updateQuestion`、`setQuestionStatus`、`deleteQuestion` 全部拒绝，避免题干、选项、答案、解析和状态变化影响历史/进行中考试；
- `AdminService.validatePaper` 增加重复题目校验；
- 增加考试时长 `1–240` 分钟边界；
- 增加及格分 `0–总分` 边界；
- 增加试卷状态 0/1 校验；
- `setPaperStatus` 在存在进行中考试时拒绝停用。

### 6. 开始考试并发

- `ExamServiceImpl.start` 增加 `@Transactional`；
- `ExamPaperMapper.selectByIdForUpdate` 使用 `SELECT ... FOR UPDATE` 锁定试卷行；
- 同一试卷的并发开始请求串行化，避免两次请求同时查不到进行中考试后各建一场。

## 四、修改文件

### 后端

- `driving-exam-server/.../exam/ExamService.java`
  - 新增 `finalizeExpiredExams(int limit)`；更新 `answer` 的归属/超时契约说明。
- `driving-exam-server/.../exam/impl/ExamServiceImpl.java`
  - 超时收尾、答案归属校验、试卷停用后已有考试读取、开始考试事务和行锁。
- `driving-exam-server/.../exam/ExamTimeoutScheduler.java`
  - 新增定时扫描任务。
- `driving-exam-server/.../DrivingExamApplication.java`
  - 启用 `@EnableScheduling`。
- `driving-exam-server/.../mapper/MockExamMapper.java`
  - 增加 `selectExpiredOngoingIds`。
- `driving-exam-server/.../mapper/ExamPaperMapper.java`
  - 增加 `selectByIdForUpdate`。
- `driving-exam-server/.../mapper/ExamPaperQuestionMapper.java`
  - 增加 `countExamUsageByQuestionId`。
- `driving-exam-server/.../admin/AdminService.java`
  - 重复题目、时长/及格分/状态边界、题目被考试引用后的冻结、进行中考试禁止停用试卷。
- `driving-exam-server/src/main/resources/application.yml.example`
  - 增加 `exam.timeout-scan-*` 配置说明。
- `driving-exam-server/src/test/resources/application-test.yml`
  - 测试 profile 关闭超时扫描。

### 前端

- `frontend/src/views/student/ExamReportView.vue`（新增）
  - 逐题成绩报告。
- `frontend/src/views/student/ExamAnswerView.vue`
  - 自动交卷、超时失败重试、逐题报告入口、真实超时文案。
- `frontend/src/views/student/ExamRecordsView.vue`
  - 历史记录“查看报告”入口。
- `frontend/src/views/student/ExamView.vue`
  - 更新范围注释。
- `frontend/src/router/index.ts`
  - 增加 `StudentExamReport` 路由。

### 测试

- `ExamServiceImplTest`（新增）：
  - 正常开始考试（锁试卷并创建）、非本人 2104、试卷外暂存/交卷答案 1001、超时自动收尾、已结算重复交卷、停用试卷仍可交卷、定时扫描。
- `AdminServiceExamConsistencyTest`（新增）：
  - 重复题目、时长为 0、及格分超总分、题目被考试引用后修改/停用/删除拒绝、进行中考试禁止停用试卷。
- `ExamTimeoutSchedulerTest`（新增）：
  - 调度器调用 Service；异常不会向外抛出。
- 既有 `ExamFinalizerTest`、`AnswerGraderTest` 继续通过。

## 五、考试状态、答案归属与成绩一致性如何保证

### 考试状态

- 交卷/超时统一调用 `ExamFinalizer.finalize`；
- `MockExamMapper.claimForSettle` 使用单条 `UPDATE ... WHERE status='ONGOING'` 抢占；
- 抢到者继续结算，未抢到者返回 0，调用方重新读取已有报告；
- 定时超时任务也走同一抢占逻辑；
- 删除/停用题目和试卷的相关保护避免结算输入在考试期间变化。

### 答案归属

- `answer`：考试归属 → 状态 → 试卷存在 → 题目属于试卷 → 超时判定 → 写入；
- `submit`：考试归属 → 试卷存在 → 请求体逐题归属校验 → 合并草稿 → 结算；
- `judge`：原有考试归属和题目归属校验保留；
- 非本人考试统一 2104。

### 成绩一致性

- 报告由 `ExamFinalizer.buildReport` 从 `answer_record` 和试卷题目统一组装；
- 期末修复不改变成绩算法、`AnswerGrader` 或既有报告结构；
- 试卷题目在考试创建后被冻结，避免历史报告读取到被改写后的题目内容；
- 没有修改数据库表结构，也没有修改既有历史成绩数据。

## 六、测试与构建结果

### 已实际执行并通过

后端定向测试：

```text
mvn -pl driving-exam-server -am test \
  -Dtest=ExamServiceImplTest,AdminServiceExamConsistencyTest,ExamFinalizerTest,ExamTimeoutSchedulerTest,AnswerGraderTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：

- **Tests run: 58, Failures: 0, Errors: 0, Skipped: 0**
- `BUILD SUCCESS`

覆盖：

- 正常开始考试（试卷行锁 + 创建）；
- 试卷外暂存答案被拒绝；
- 试卷外交卷答案被拒绝；
- 非本人考试 2104；
- 超时暂存先结算再拒绝；
- 已结算重复交卷返回既有报告；
- 停用试卷后已有考试仍可交卷；
- 定时超时扫描；
- 重复题目、非法时长/及格分；
- 题目被考试引用后修改/停用/删除拒绝；
- 既有判分和结算测试。

后端构建：

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
- 构建产物包含 `ExamReportView-*.js`、更新后的 `ExamAnswerView-*.js` 和 `ExamRecordsView-*.js`；
- 只有既有的 chunk 体积提示，没有编译错误。

### 已实际执行但失败

本阶段定向测试和构建没有失败。

### 因环境限制未执行

- 未执行真实 MySQL 集成测试（`ExamSettlementConcurrencyTest` 需要 `TEST_DB_*`）；
- 未执行 Docker/浏览器验收；
- 未执行真实 HTTP 的考试开始/答题/交卷/报告；
- 未验证定时任务在真实 MySQL 上按 `DATE_ADD` 扫描的行为；
- 未执行完整 Maven 测试集合（避免依赖环境/数据库的测试产生噪声）。

### 仅静态检查

- 检查了 `mock_exam`、`exam_paper`、`exam_paper_question`、`mock_exam_answer` 的现有字段和唯一键；
- 新增 Mapper SQL 只读取/锁定现有表字段，不需要数据库结构变更；
- 通过编译和测试源码确认 Controller/DTO/VO/Service 的调用关系；
- 前端通过 `vue-tsc` 和生产构建确认路由、组件和类型一致。

## 七、数据库结构变更与影响

本阶段**没有修改数据库表结构**，没有新增/删除列，没有执行迁移 SQL。

新增内容只使用现有字段/表：

- `exam_paper.duration`、`start_time` 用于超时扫描；
- `exam_paper_question` 用于题目归属查询和考试引用计数；
- `mock_exam.status` 继续使用 `ONGOING` / `SUBMITTED` / `TIMEOUT`；
- `mock_exam_answer` 继续作为暂存和交卷答案来源。

对既有数据的影响：

- 不删除、不清空、不重置任何数据；
- 已开始的考试现在可在试卷停用后继续交卷/查看报告；
- 已进入任何考试的题目将被管理员端冻结（之后不能通过管理接口修改/禁用/删除）；
- 既有历史数据若在本次修复前已经出现“试卷题目缺失”或“题目被改写”，本阶段没有自动修复，需在数据库环境中单独审计。

## 八、Docker/浏览器环境恢复后的验收步骤

1. 仅重建后端服务（前端也有改动，若需查看报告页需同时重建 frontend）：

```text
docker compose --env-file docker/.env -p driving-exam-accept up -d --build backend frontend
```

2. 以学员账号开始一场考试，答几题、刷新页面确认答案恢复；
3. 等待或模拟超时，确认：
   - 前端自动请求交卷；
   - 后端状态变为 `TIMEOUT`；
   - 客户端关闭后定时任务能在扫描周期内收尾；
4. 交卷后打开 `/student/exam/{examId}/report`，确认逐题题目、用户答案、正确答案、判分和解析；
5. 从考试记录页点击“查看报告”再次打开；
6. 以非本人账号尝试打开同一报告，确认返回 2104；
7. 在管理员端尝试修改已被考试引用的题目/试卷，确认返回 `OPERATION_NOT_ALLOWED`；
8. 尝试创建重复题目的试卷，确认返回 1001；
9. 若需要验证并发，只使用独立测试库，不操作当前验收库：
   - 配置 `TEST_DB_*`；
   - 执行 `ExamSettlementConcurrencyTest`；
   - 不执行会写入大量业务数据的完整集合。

## 九、未解决/需运行时确认

1. 当前环境没有 Docker/MySQL/浏览器，不能声称页面和超时定时任务已实际通过；
2. 定时任务默认 60 秒扫描，客户端关闭后最坏延迟约一个扫描周期，属于可接受但需在验收中确认；
3. 旧历史数据中若有题目被删除/改写，报告仍可能缺少该题；本阶段只防止新增变更，不自动修复旧数据；
4. 管理员业务规则现在会冻结被考试引用的题目，若需要修正题目内容，必须通过受控的数据维护流程，不能直接使用普通编辑接口；
5. 本阶段未重新运行 Day30 的 AI/RAG 验收，也未修改 Day30 文件。
