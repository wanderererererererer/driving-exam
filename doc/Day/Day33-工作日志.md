# 【Day 33 工作日志】

## 一、日期

2026-10-20

## 二、本阶段目标

按 `doc/系统剩余功能排查报告.md` 的管理员缺口，完成：

- 管理员用户详情、状态参数校验；
- 绑定管理中的禁用账号/重复绑定检查覆盖；
- 知识点描述的树接口回显与清空；
- Excel 题目导入的语义校验、文件限制、错误行明细和事务一致性；
- 前端导入成功/失败提示与错误原因展示。

保留 Day30 AI/RAG、Day31 模拟考试、Day32 JWT/预约并发修改，不修改数据库结构和无关业务模块。

## 三、确认存在的缺口与证据

### 1. 用户管理缺少详情接口

`AdminController` 原只有用户列表、创建、更新、状态接口，没有 `GET /api/admin/users/{id}`；
前端编辑直接使用列表行数据，无法重新读取最新资料。

### 2. 用户状态参数未统一校验

- `AdminService.createUser` 直接使用 `dto.getStatus()`；
- `AdminService.updateUser` 直接使用 `dto.getStatus()`；
- 只有独立的 `setUserStatus` 校验 0/1。
因此创建/更新可以写入 `status=2` 等非法值。

### 3. 知识点描述无法回显和清空

- `KPTreeNodeVO` 没有 `description` 字段；
- `KnowledgePointServiceImpl` 建树时没有设置描述；
- `KnowledgePointsView.openEdit` 固定把描述设为空；
- 保存时 `description: form.description.trim() || undefined` 无法清空已有描述。

### 4. Excel 导入的语义错误没有行级明细

`AdminQuestionExcelService` 原来只解析基础字段，然后把题目交给 `AdminService.createQuestion`；
非法科目、知识点不存在、选项/答案错误等语义问题在写入阶段才抛异常，导致：

- 无法返回具体失败行；
- 整个导入事务回滚；
- 前端只能看到通用失败，不能查看失败原因。

### 5. Excel 上传限制与 Spring multipart 不一致

服务层 `MAX_FILE_BYTES = 10MB`，但 `application.yml` 没有配置
`spring.servlet.multipart.max-file-size`；Spring Boot 默认约 1MB。
超过 1MB 的文件会先被 Spring 拒绝，且没有专门的 multipart 异常处理。

### 6. 前端导入文件类型/大小未预检

`QuestionsView` 只依赖后端；选择 `.txt` 或超大文件后才会发请求。导入结果弹窗虽有错误表，
但没有明确“失败时整批未写入”的文案提示。

## 四、修改文件与修复内容

### 用户管理

- `driving-exam-server/.../controller/AdminController.java`
  - 新增 `GET /api/admin/users/{id}`。
- `driving-exam-server/.../admin/AdminService.java`
  - 新增 `getUser(Long id)`；
  - 创建/更新用户校验 status 只能为 0/1，并给出明确参数错误；
  - `validateQuestion` 改为公共方法，供 Excel 导入复用；
  - 题目语义错误改为带具体原因的业务消息。
- `frontend/src/api/admin.ts`
  - 新增 `getAdminUserApi(id)`。
- `frontend/src/views/admin/UsersView.vue`
  - 编辑前调用详情接口读取最新资料。

### 知识点管理

- `driving-exam-pojo/.../KPTreeNodeVO.java`
  - 增加 `description` 字段。
- `driving-exam-server/.../KnowledgePointServiceImpl.java`
  - 建树时回填 `description`。
- `frontend/src/types/index.ts`
  - `KPTreeNodeVO` 增加 `description?: string | null`。
- `frontend/src/views/admin/KnowledgePointsView.vue`
  - 编辑时回显描述；
  - 保存时发送空字符串以允许清空描述（后端仅对 null 保留旧值）。

### Excel 导入

- `driving-exam-server/.../admin/AdminQuestionExcelService.java`
  - 校验文件非空、扩展名 `.xlsx/.xls`、大小 ≤10MB；
  - 每行解析后调用 `adminService.validateQuestion`，把语义错误变成行级错误；
  - 全部行通过后才进入写入阶段；
  - 保留同科目 + 题干重复（文件内/数据库内）检查；
  - 保持 `@Transactional`，异常时整批回滚。
- `driving-exam-server/src/main/resources/application.yml` 和 `application.yml.example`
  - 增加 `spring.servlet.multipart.max-file-size: 10MB`、`max-request-size: 12MB`。
- `driving-exam-common/.../GlobalExceptionHandler.java`
  - 增加 `MaxUploadSizeExceededException`、`MissingServletRequestPartException`、`MultipartException` 处理，统一返回参数错误，不再落入通用 500。
- `frontend/src/views/admin/QuestionsView.vue`
  - 选择文件前检查扩展名和 10MB 大小；
  - 导入结果弹窗明确“失败时本次未写入任何数据”；
  - 错误表继续展示行号、字段、原因。

### 测试

- `AdminServiceUserStatusTest`（新增）：
  - 用户详情不存在/存在；
  - 创建/更新非法状态；
  - `setUserStatus` 0/1 与管理员自禁用保护；
  - 禁用账号绑定 1102、重复绑定 3101、解除绑定置 0。
- `AdminServiceQuestionValidationTest`（新增）：
  - 正常题目；
  - 非法科目、知识点不存在、选项不足、答案越界、非法状态。
- `AdminQuestionExcelServiceTest`（新增）：
  - 模板表头；
  - 正常导入；
  - 语义错误行级返回；
  - 文件内重复、数据库重复；
  - 一行合法一行失败时整批不写入；
  - 非 Excel 扩展名拒绝。
- `AdminServiceExamConsistencyTest` 继续通过，确认 Day31 题目/试卷冻结规则没有被绕过。

## 五、Excel 导入的数据一致性保证

1. **先解析、后写入**：所有非空行先解析、调用题目语义校验、检查文件内重复和题库重复；
2. **有任何错误不写入**：只要 `errors` 非空，立即返回 `total/success=0/failed/errors`，不调用 `createQuestion`；
3. **写入阶段事务**：全部行合法后才在同一事务中逐条调用 `createQuestion`；写入中异常会回滚整批；
4. **不覆盖已有题目**：只插入新题，不更新、不删除已有题目；
5. **重复口径**：同科目 + 完全相同的题干，文件内重复或题库已存在都拒绝；
6. **知识点校验**：通过 `AdminService.validateQuestion` 校验知识点存在且与科目一致；
7. **题目冻结规则**：Excel 只创建新题目，不修改已有题目；Day31 的“已进入考试题目不可修改/停用/删除”仍由 `AdminService` 保护。

失败结果示例：

```json
{
  "total": 2,
  "success": 0,
  "failed": 1,
  "errors": [
    { "row": 3, "field": "行", "message": "知识点不存在或与科目不匹配" }
  ]
}
```

## 六、测试与构建结果

### 已实际执行并通过

管理员/Excel 定向测试：

```text
mvn -pl driving-exam-server -am test \
  -Dtest=AdminQuestionExcelServiceTest,AdminServiceQuestionValidationTest,AdminServiceUserStatusTest,AdminServiceExamConsistencyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：

- **Tests run: 18, Failures: 0, Errors: 0, Skipped: 0**
- `BUILD SUCCESS`

包含管理员及回归关联测试的扩展运行：

```text
mvn -pl driving-exam-server -am test \
  -Dtest=AdminQuestionExcelServiceTest,AdminServiceQuestionValidationTest,AdminServiceUserStatusTest,AdminServiceExamConsistencyTest,JwtInterceptorTest,AppointmentServiceImplTest,ChatServiceImplTest,ChatControllerContractTest,ChatEntityMappingTest,CoachStudentServiceImplBindStatusTest,CoachStudentServiceImplListTest,CoachStudentServiceImplLearningDataTest,CoachStudentServiceImplWrongBookTest,CoachStudentServiceImplAiSuggestTest,CoachStudentServiceImplSavedAiSuggestTest,ExamServiceImplTest,ExamFinalizerTest,ExamTimeoutSchedulerTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：

- **Tests run: 108, Failures: 0, Errors: 0, Skipped: 0**
- `BUILD SUCCESS`

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
- 构建产物包含更新后的管理员页面 bundle；
- 只有既有的 chunk 体积提示，没有编译错误。

### 已执行但失败

本阶段定向测试和构建没有失败项。

### 因环境限制未执行

- 未在真实 MySQL 上执行 Excel 导入；
- 未使用真实浏览器上传文件、下载模板、查看错误行；
- 未执行 Docker 容器更新；
- 未执行管理员接口的真实 HTTP 验收；
- 未执行完整 Maven 测试集合，避免依赖数据库/外部服务的测试产生噪声。

### 仅静态检查

- 检查了题目 DTO、`KPTreeNodeVO`、Excel 表头与 `QuestionDTO` 的字段对应；
- 通过编译确认 multipart 异常处理类可用；
- 前端通过 `vue-tsc` 和生产构建确认类型/模板一致；
- 未修改数据库表结构。

## 七、数据库结构变更与影响

本阶段**没有修改数据库表结构**，没有新增/删除列、迁移脚本或数据重置。

影响：

- `GET /api/admin/users/{id}` 只是读取现有 `sys_user`；
- 知识点描述回显使用已有 `knowledge_point.description`；
- Excel 导入仍只插入新题，不覆盖、不删除已有题目；
- 失败文件不会写入任何题目；
- 现有题库/试卷/考试数据不受影响。

## 八、尚未完成的运行时验收

1. 管理员登录后打开用户详情/编辑，确认详情接口和状态显示；
2. 创建/更新用户时提交非法状态，确认后端返回明确错误；
3. 知识点编辑时确认描述回显、修改、清空；
4. 上传正常 Excel，确认成功数量并刷新题库列表；
5. 上传含非法科目/不存在知识点/非法选项/错误答案的 Excel，确认错误行和原因；
6. 上传文件内重复/题库重复文件，确认整批不写入；
7. 上传超过 10MB 或非 xlsx/xls 文件，确认前端/后端限制；
8. 以上需在隔离 MySQL 或验收环境执行，不能对现有业务数据做重置。
