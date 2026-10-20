# 【Day 34 工作日志】

## 一、实际验收环境

- 日期：2026-10-20
- 项目根目录：`D:\myself\Integrate`
- 当前环境检查结果：
  - `docker`：NOT FOUND；
  - `/var/run/docker.sock`：NOT PRESENT；
  - `mysql` 客户端：NOT FOUND；
  - `127.0.0.1:18083` 前端端口：Connection refused；
  - `127.0.0.1:18080` 后端端口：Connection refused；
  - 无浏览器自动化工具。

因此本阶段**没有执行真实 Docker 容器、真实 HTTP、浏览器或 MySQL 数据一致性验收**。
下文严格区分“单元测试通过”“构建通过”“静态检查”和“未执行的运行时验收”。

## 二、Day33 修改是否在当前工作区

已确认当前工作区包含 Day33 的修改：

- `AdminController.java` 存在 `GET /api/admin/users/{id}`（第 75-77 行）；
- `AdminService.java` 存在 `getUser`、用户状态 0/1 校验和公共 `validateQuestion`；
- `KPTreeNodeVO.java` 存在 `description` 字段；
- `KnowledgePointServiceImpl.java` 建树时调用 `vo.setDescription(...)`；
- `KnowledgePointsView.vue` 编辑回显描述并允许发送空字符串清空；
- `AdminQuestionExcelService.java` 有扩展名/大小/语义校验；
- `application.yml` 与 `application.yml.example` 有 `spring.servlet.multipart` 10MB/12MB 配置；
- `GlobalExceptionHandler.java` 有 multipart 异常处理；
- 前端 `QuestionsView.vue` 有文件类型/大小预检和失败整批提示。

未发现 Day33 修改被回滚。

## 三、验收项目与结果

### A. 用户管理

| 验收项 | 操作/证据 | 结果 |
| --- | --- | --- |
| 用户详情接口 | 静态核对 `AdminController.java:75-77` 的 `GET /users/{id}`；`UsersView.vue` 编辑前调用 `getAdminUserApi` | 代码存在，**未做真实 HTTP** |
| 详情读取最新数据 | `AdminService.getUser` 按 id 读取 `sys_user`；前端编辑不再只用列表行数据 | 代码级通过，**未做浏览器** |
| status 仅 0/1 | `AdminService.createUser/updateUser` 状态校验；`AdminServiceUserStatusTest` | **单元测试通过** |
| 非法状态不写库 | 测试验证返回 `PARAM_ERROR`，未调用 `insert/updateById` | **单元测试通过**，未用真实数据库验证 |
| 列表/更新/切换不退化 | 既有用户管理代码未改写；前端 type-check/build 通过 | **构建通过**，真实页面未验收 |

### B. 知识点描述

| 验收项 | 操作/证据 | 结果 |
| --- | --- | --- |
| 树接口返回 description | `KPTreeNodeVO.description` + `KnowledgePointServiceImpl` 填充 | 代码级通过 |
| 编辑回显 | `KnowledgePointsView.openEdit` 使用 `node.description ?? ''` | 代码级通过，**未做浏览器** |
| 修改后重新读取一致 | 后端更新已有 `description` 字段；无额外转换 | 代码级通过，**未做真实数据库往返** |
| 清空描述 | 前端发送空字符串；后端 `dto.getDescription() != null` 时覆盖 | 代码级通过，**未做真实数据库往返** |
| 不误改其他描述/父子关系 | `updateKnowledgePoint` 只更新目标 id；原有父子校验保留 | 代码级检查通过，**未做数据库验收** |

### C. Excel 题目导入

| 验收项 | 操作/证据 | 结果 |
| --- | --- | --- |
| 有效 Excel 导入 | `AdminQuestionExcelServiceTest.validImportWrites` | **单元测试通过**（mock 写入） |
| 非法科目/知识点/选项/答案 | `AdminServiceQuestionValidationTest` + Excel 语义错误测试 | **单元测试通过** |
| 文件内重复/题库重复 | `AdminQuestionExcelServiceTest` 两个重复测试 | **单元测试通过** |
| 一行合法一行失败 | `AdminQuestionExcelServiceTest.anyErrorPreventsAllWrites` 验证 `createQuestion` 从未调用 | **单元测试通过**，未用真实数据库前后计数 |
| 写入阶段异常回滚 | `@Transactional(rollbackFor=Exception.class)` 静态检查；未执行真实事务异常 | **未执行数据库级回滚验证** |
| 空文件/缺少文件/非 xlsx/超 10MB | 服务扩展名、大小检查；GlobalExceptionHandler multipart 处理 | 代码级通过；**未做真实 multipart 请求** |
| 无效 Excel 不出现通用 500 | `WorkbookFactory` 异常统一转 1001；multipart 异常单独处理 | 代码级检查；**未做真实请求** |
| 前端显示总数/成功/失败/行号/原因 | `QuestionsView` 结果弹窗错误表；失败时提示整批未写入 | 构建通过；**未做浏览器上传** |
| 模板下载与刷新 | `downloadQuestionTemplateApi` 与导入成功后 `load()` 仍存在 | 代码级检查；**未做浏览器** |
| 不绕过 Day31 冻结规则 | Excel 只调用 `createQuestion`，不修改已有题目；Day31 测试仍在 108 项回归中通过 | **单元测试通过** |

## 四、实际修复的文件及原因

本阶段**没有发现需要额外修改代码的新缺陷**，因此没有新增业务代码修改。

Day33 的修复在当前工作区完整存在，且本阶段通过其定向测试、构建和静态检查；运行环境不足导致无法进行真实运行时验收，不能用单元测试替代。

本阶段只新增了 `doc/Day/Day34-工作日志.md`。

## 五、测试与构建结果

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

包含管理员、认证、预约、聊天、教练、考试回归的扩展测试：

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
- 只有既有的 chunk 体积提示，无编译错误。

### 已执行但失败

本阶段实际执行的定向测试、扩展测试和构建均没有失败项。

### 未执行

- 真实 Docker 构建/启动/健康检查；
- 真实 HTTP 登录、管理员用户详情、状态接口；
- 浏览器用户管理、知识点管理、Excel 上传和模板下载；
- 真实 MySQL 的导入前后记录计数和事务回滚；
- 真实 multipart 10MB/文件类型验收；
- 隔离测试库上的 `ExamSettlementConcurrencyTest` 或其他集成测试；
- 完整 Maven 测试集合。

### Docker 版本

当前环境没有 Docker CLI/socket，无法查询或更新容器，也无法确认 Docker 中运行版本是否包含 Day33 代码。
因此本阶段**不能声称 Docker 已更新**，也不能声称旧容器已经提供 Day33 管理员页面/接口。

## 六、数据库是否变化

本阶段没有连接 MySQL，没有执行 SQL，没有修改数据库结构或业务数据。

Day33 也没有数据库结构变更：

- 用户详情只读现有 `sys_user`；
- 知识点描述使用既有 `knowledge_point.description`；
- Excel 导入只插入新题，不覆盖/删除已有题目。

因此无法宣称“真实数据库未被误改”的运行时证据；只能确认本阶段未执行任何数据库写入或重置操作。

## 七、尚未完成的运行时验收与阻塞

阻塞原因：

1. 当前执行环境没有 Docker CLI 和 Docker socket；
2. 没有可访问的 Docker 前端/后端端口；
3. 没有 MySQL 客户端或隔离测试数据库；
4. 没有浏览器自动化工具；
5. 无法安全读取或使用 `.env` 中的凭据，也不应索取 Token/密码。

因此以下必须等环境恢复：

- 在隔离 MySQL 或验收环境准备少量专用测试数据；
- 管理员登录后读取用户详情、提交非法状态；
- 知识点描述修改/清空并重新读取；
- 上传有效/非法 Excel，记录导入前后题库计数；
- 验证 `success/failed/errors` 和整批未写入；
- 验证模板下载和前端刷新；
- 检查 Docker 前端/后端是否已包含 Day33 修改。

## 八、答辩可用简短材料

管理员功能验收可以按三条链路说明：

1. **用户管理**：列表/详情/创建/编辑/启停用；详情从数据库重新读取，状态只接受 0/1；非法状态由业务错误拒绝。
2. **知识点管理**：树接口返回名称、父节点、层级、排序、题目数和描述；编辑回显描述，保存空字符串即为清空。
3. **Excel 导入**：模板列与题目 DTO 对齐；全部行先解析和语义校验，任何错误返回行号与原因且不写入；全部通过后在同一事务中插入；重复题目、非法科目/知识点/选项/答案、文件类型/大小和 multipart 异常均有明确处理。

数据一致性要点：导入是“先全量校验、后事务写入”，不是边解析边写；失败行不会造成部分写入，成功后才刷新题库列表。该结论目前有单元测试和构建证据，真实数据库计数/事务回滚仍需在隔离环境复核。
