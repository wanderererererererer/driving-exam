# 【Day 26 工作日志】

## 一、日期

2026-10-19（项目 Day26 阶段；自然日按当前会话记录）

## 二、本阶段目标

1. 在 Day25 学员只读页基础上完成教练学员写操作（绑定/创建、改学情/备注、解绑）。
2. 启动管理员端第一阶段：个人中心、预约查看。
3. 只读核查 Excel 导入与 Docker 部署前置条件。
4. 不修改数据库结构、不写入业务数据、不创建 Docker 文件。

## 三、教练学员写操作

### 3.1 文件与接口

| 文件 | 类型 | 内容 |
|---|---|---|
| `frontend/src/api/coachStudent.ts` | 修改（56 行） | 增加 `bindOrCreateStudentApi`、`updateStudentSubjectApi`、`unbindStudentApi` |
| `frontend/src/views/coach/StudentsView.vue` | 修改（672 行） | 绑定/创建弹窗、修改学情/备注、解绑、刷新与防重复提交 |
| `frontend/src/types/index.ts` | 修改 | 增加 `StudentBindParams`、`StudentUpdateParams` |
| `frontend/src/router/index.ts` | 保持已有 `/coach/students` | 无新增路由 |

真实接口：

- `POST /api/coach/students`（同一入口：用户名已存在→绑定；不存在→创建并绑定）
- `PUT /api/coach/students/{bindId}`（`bindId = coach_student.id`）
- `DELETE /api/coach/students/{bindId}`（软解绑，不删除历史、不取消预约）

### 3.2 前端行为

- 绑定/创建有两种模式；创建模式提交用户名、姓名、手机号、初始密码，字段校验按后端 DTO；
- 修改学情必须选择科目，阶段/备注至少一项；空的可选字段不发送，避免空串触发 `@Pattern`；
- 当前教练备注没有读取接口，页面明确提示“留空表示不修改”，未伪造原值；
- 解绑二次确认并说明软解绑的实际影响；
- 写操作成功刷新列表/学情，提交期间禁用重复提交。

## 四、管理员端第一阶段

| 文件 | 内容 |
|---|---|
| `frontend/src/views/admin/ProfileView.vue` | 个人中心：复用 `/api/auth/me`、`/profile`、`/password` |
| `frontend/src/views/admin/AppointmentsView.vue` | 管理员预约只读列表、状态筛选、分页 |
| `frontend/src/api/admin.ts` | 管理员预约只读 API（本日版本） |
| `driving-exam-server/.../controller/AdminAppointmentController.java` | `GET /api/admin/appointments` 只读接口 |
| `AppointmentService/Impl` | 新增 `listAllAppointments` 全量只读查询 |
| `frontend/src/router/index.ts` | `/admin/appointments`、`/admin/profile` |

其余 7 个管理员菜单本日仍为 ComingSoon；`/api/admin` 其余业务接口尚未实现。

## 五、Excel 与 Docker 前置核查

- Excel：当前 JDK 17 项目配置、Maven 多模块；**无 Excel 解析依赖**；题目/选项/知识点字段已核实；模板/校验/事务方案已提出，**尚未实现**。
- Docker：当前无 Dockerfile、docker-compose、Nginx、README、初始化脚本；MySQL dump schema-only、无账号种子；本日只做架构/风险核查，**未创建文件、未部署**。

## 六、验证结果

| 检查 | 结果 |
|---|---|
| 教练写操作前端 `vue-tsc --noEmit` | **exit=0**（合并前阶段检查） |
| `npm run build` | **Day26 阶段未单独执行**；Day27 合并管理员页面后的构建结果见 Day27 日志 |
| `git diff --check`（变更文件） | **exit=0** |
| 后端编译 | **未执行**：环境无 Java 17/Maven |
| 后端自动化测试 | **未执行** |
| 真实接口联调 | **未执行** |
| 浏览器页面验证 | **未执行** |
| Docker 验收 | **未执行** |

## 七、未完成 / 风险

- 后端新增 `AdminAppointmentController` 与 `listAllAppointments` 未编译、未重启、未联调。
- 教练写操作未真实联调；未使用隔离测试账号，未写数据库。
- 管理员其余 7 个菜单未实现。
- Excel 导入未实现；Docker 未实现。
- 下一步：Day27 继续管理员九菜单、Excel、Docker 和统一前端构建/静态检查。

## 八、工作区与约束

- 未修改数据库、SQL、报告；未创建 Docker 文件。
- 未执行 Git add/commit/reset/clean；未启动/停止服务。
