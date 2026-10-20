# 【Day 25 工作日志】

## 一、日期

2026-10-19（项目 Day25 阶段；自然日按当前会话记录，若实际跨日以用户记录为准）

**日志说明**：`doc/Day/Day23-工作日志.md` **不存在**；Day23 的学员端预约前端工作已在 Day24 日志 §4.1 以“补记”形式记录，本日志不把它冒充为 Day23 原始日志。

## 二、本阶段目标

1. 在教练端实现学员管理**只读页面**：列表、分页、四科学情查看。
2. 只读核查管理员端 9 个菜单的现状与依赖。
3. 只读核查 Docker 部署条件、依赖和风险。
4. 不实现写操作、不修改后端/数据库、不创建 Docker 文件。

## 三、完成的工作

### 3.1 教练学员只读页面

| 文件 | 类型 | 内容 |
|---|---|---|
| `frontend/src/api/coachStudent.ts` | 新增 | 封装 `GET /api/coach/students`、`GET /api/coach/students/{bindId}/status` |
| `frontend/src/views/coach/StudentsView.vue` | 新增 | 学员列表、分页、四科学情弹窗、加载/空/错误/重试 |
| `frontend/src/types/index.ts` | 修改 | 增加 `StudentVO`、`SubjectPhaseStatus`、`SubjectStatusVO` |
| `frontend/src/router/index.ts` | 修改 | 教练端新增 `/coach/students`（`CoachStudents`） |

- 列表只使用后端支持的 `pageNum/pageSize`，固定 `pageSize=10`；后端不支持关键字/科目筛选。
- 学情弹窗按 `bindId`（`coach_student.id`）调用状态接口，固定展示四科；`lastMockScore=null` 显示 `—`。
- 教练端菜单 `/coach/students` 原本已存在，本阶段只补路由和页面。
- 本阶段未实现绑定/创建、改学情、解绑等写操作。

### 3.2 管理员端只读审计

- `AdminLayout.vue` 有 9 个菜单：数据概览、用户管理、绑定管理、知识点管理、题库管理、试卷管理、预约查看、AI 问答统计、个人中心。
- 当时只有 `/admin/dashboard` 有真实路由但页面是 `"--"` 占位；其余 8 个菜单落 `AdminComingSoon`。
- 后端无 `/api/admin` Controller；`PathRoleRule` 已预留 `/api/admin/** → ADMIN`。
- 可复用实体/Mapper：`sys_user`、`coach_student`、`knowledge_point`、`question`、`question_option`、`exam_paper`、`exam_paper_question`、`appointment`、`ai_chat_record`。
- 结论：9 个菜单全部属于最终目标，但**均未实现真实业务页面/接口**；本阶段只做拆解，不开发。

### 3.3 Docker 只读核查

- 后端：Spring Boot 3.2.5 / Java 17 / Maven 多模块，端口 8080；当前无现成 jar。
- 前端：Vite 构建；`.env.development` 使用 `/api`；Vite proxy 仅开发用。
- MySQL：dump 为 schema-only 16 表，无 `CREATE DATABASE`/账号种子；seed 顺序需手工组合。
- 当前无 Dockerfile、docker-compose、Nginx、README、启动脚本。
- 结论：Docker 首版应覆盖 MySQL + 后端 + Nginx 前端；Qdrant/RAG 作为第二阶段；密钥不得进入镜像/仓库。

## 四、验证结果

| 检查 | 结果 |
|---|---|
| `./node_modules/.bin/vue-tsc --noEmit` | **exit=0，无输出** |
| `npm run build` | **exit=0**；产物含 `StudentsView`；仅有既有大 chunk 警告 |
| 真实后端接口联调 | **未执行**：执行环境无法访问用户本机后端 |
| 浏览器页面验证 | **未执行** |
| 后端编译/测试 | **未执行**：环境无 Java 17/Maven |
| Docker 部署 | **未执行**：无 Docker/Nginx 文件 |

## 五、未完成与下一步

- 未实现教练学员写操作（绑定/创建、改学情/备注、解绑）。
- 管理员 9 菜单均未实现真实功能。
- 未创建 Docker/README。
- 下一步：Day26 完成教练写操作 + 管理员第一阶段（个人中心/预约查看），并继续 Excel/Docker 前置核查。

## 六、工作区与约束

- 本日只读审计未修改后端、数据库、SQL、报告；前端只读页面代码为本日实现。
- 未执行 Git add/commit/reset/clean。
