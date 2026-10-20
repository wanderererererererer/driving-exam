# 【Day 36 工作日志】

## 一、日期与目标

- 日期：2026-10-20
- 目标：完善学员端、教练端、管理端三套工作台；优先使用真实接口，处理加载/空/失败状态，补充快捷入口，并核查 Docker 运行版本。

## 二、现状与版本核查

实际检查了：

- `frontend/src/router/index.ts`
- `frontend/src/views/student/Dashboard.vue`
- `frontend/src/views/coach/Dashboard.vue`
- `frontend/src/views/admin/DashboardView.vue`
- `frontend/src/api/{auth,analysis,wrongBook,appointment,chat,coachStudent,admin}.ts`
- `frontend/src/layout/{StudentLayout,CoachLayout,AdminLayout}.vue`
- 后端相关 Controller/Service 和 `docker-compose.yml`
- 现有测试、`frontend/dist` 和 Git 工作区

运行环境事实：

- 当前 Claude 环境仍无 Docker CLI/socket，无法直接查看或重建用户 Docker Desktop 中的容器；
- 当前 Claude 环境无法访问用户浏览器的本地 `18083`；
- 因此 **不能确认 Docker 中实际运行的镜像版本**，也没有把构建证据写成浏览器验收。

版本判断证据：

- 学员/教练源码中已经没有“学员端首页（占位）”“教练工作台（占位）”“Day 4 起接入”这些字符串；
- 当前工作区的 Dashboard 已包含真实接口调用、指标和列表；
- 截图仍显示旧占位内容，说明容器前端大概率仍是旧构建，需要重建 frontend 镜像；
- 无法从当前环境读取 Docker 镜像 ID 或容器内静态资源来最终确认。

## 三、修改内容

### 1. 学员端工作台

文件：`frontend/src/views/student/Dashboard.vue`

修改：

- 标题由“学员首页”调整为“学员工作台”；
- 快捷入口新增“科目一模考”和“考试记录”入口；
- 保留已有的真实数据指标：
  - 科目一/四学情进度；
  - 待攻克错题；
  - 待确认预约；
  - 未读消息；
  - 最近预约列表。
- 现有独立请求和 `Promise.allSettled` 分区错误状态保留，接口失败不会被显示为 0。

数据来源：

| 指标 | 接口 |
| --- | --- |
| 学员基本信息 | `GET /api/auth/me` |
| 科目一/四练习进度、正确率、薄弱知识点数 | `GET /api/student/analysis/overview?subjectCode=SUBJECT_1/SUBJECT_4` |
| 待攻克错题、错题总数、已掌握 | `GET /api/student/wrong-book/summary` |
| 待确认预约数量 | `GET /api/student/appointment?status=PENDING&pageNum=1&pageSize=1` |
| 最近预约 | `GET /api/student/appointment?pageNum=1&pageSize=5` |
| 未读消息 | `GET /api/student/chat/sessions` |

### 2. 教练端工作台

文件：`frontend/src/views/coach/Dashboard.vue`

核查后，现有实现已经覆盖本阶段要求，未做无依据的改写：

- 指标：绑定学员、待处理预约、已确认预约、未读消息；
- 主工作区：待处理预约列表、名下学员概况、快捷操作；
- 数据来源：
  - `GET /api/coach/students`
  - `GET /api/coach/appointments?status=PENDING/CONFIRMED`
  - `GET /api/coach/chat/sessions`
  - `GET /api/auth/me`
- 分区错误、加载状态、空状态已存在；
- 没有展示“今日预约”，因为现有接口没有日期筛选参数，不能用列表前 5 条冒充今日数据；
- 只展示当前 JWT 教练名下数据；预约操作仍进入已有预约管理页，不在 Dashboard 伪造状态变化。

### 3. 管理端工作台

文件：`frontend/src/views/admin/DashboardView.vue`

本次将原来只有四张统计卡的页面扩展为管理工作台：

- 顶部正式标题“管理端工作台”和口径说明；
- 统计卡：用户总数、启用题目数、预约总数、AI 问答总数；
- 用户构成：学员、教练、管理员数量，使用现有用户分页接口的 `total`；
- 待处理预约：最近 5 条 PENDING 预约和总数；
- 最近提交的预约：按后端 `create_time DESC` 返回的最近 5 条，显示状态；
- 快捷入口：用户、绑定、知识点、题库、试卷、预约查看、AI 问答统计、个人中心；
- 分区 `Promise.allSettled`：统计、角色数量、待处理预约、最近预约任一失败只影响对应区块；
- 失败显示“加载失败”或告警，不把失败显示为 0；
- AI 问答卡明确提示“历史问答记录数，不代表当前 RAG 可用性”，没有伪造 RAG 健康状态。

数据来源：

| 指标/区域 | 接口 | 口径 |
| --- | --- | --- |
| 用户总数、启用题目数、预约总数、AI 问答总数 | `GET /api/admin/dashboard` | 后端聚合：用户全部、题目 status=1、预约全部、AI 记录全部 |
| 学员/教练/管理员数量 | `GET /api/admin/users?role=...&pageSize=1` | `PageResult.total` |
| 待处理预约数量/列表 | `GET /api/admin/appointments?status=PENDING&pageSize=5` | `total` 和最近 5 条 |
| 最近提交的预约 | `GET /api/admin/appointments?pageSize=5` | 后端按 create_time DESC、id DESC 的最近 5 条 |
| 快捷入口 | 前端路由 | 对应已有管理员页面 |

没有新增后端接口或数据库结构。

## 四、权限与数据边界

- 学员/教练 Dashboard 只调用各自角色路径，身份由 JWT 识别；
- 教练端没有传入教练 ID，不能读取其他教练的学员/预约；
- 管理端所有数据继续调用 `/api/admin/**`，由现有 `PathRoleRule` 限制为 ADMIN；
- 三个工作台均不使用静态演示数字；
- 失败接口不显示为 0 或空列表；
- 没有新增“今日预约”等无可靠数据源的指标。

## 五、测试与构建结果

### 已实际执行并通过

```text
cd frontend
npm run type-check
npm run build
```

结果：

- `npm run type-check`：exit 0；
- `npm run build`：exit 0；
- 当前构建入口：`dist/assets/index-DoKUurt3.js`；
- 只有既有 chunk 体积提示，无编译错误。

后端未修改，因此本阶段没有重新运行后端测试；Day34 已执行的相关后端测试为 108 项通过（0 failures/0 errors/0 skipped），可作为后端既有证据，但不是本阶段重新执行结果。

### 未执行

- 真实 Docker 构建/重启/健康检查；
- 浏览器登录后的三个工作台视觉与交互验收；
- 真实接口在浏览器中的 Network 验证；
- 真实空数据、失败、部分失败场景的页面验收。

## 六、Docker 运行版本

当前容器**未由本阶段更新**。原因：

- Claude 环境没有 Docker CLI/socket，无法访问用户 Docker Desktop；
- 本地 `frontend/dist` 不会进入 Docker 镜像，Dockerfile 在构建时重新执行 `npm ci`/`npm run build`；
- 截图中的旧占位文案与当前源码不一致，说明运行中的 frontend 镜像不是当前工作区构建。

用户在本机可执行的最小更新方式（只影响 frontend，不删除卷、不重建数据库）：

```powershell
cd D:\myself\Integrate
docker compose --env-file docker/.env -p driving-exam-accept build frontend
docker compose --env-file docker/.env -p driving-exam-accept up -d --no-deps --force-recreate frontend
docker compose --env-file docker/.env -p driving-exam-accept ps frontend
```

如果还要让 Day30–Day34 的后端修改进入容器，需要同样定向重建 backend；本阶段只改了前端工作台，不应重建 MySQL 或执行 `down -v`。

部署后验证：

1. 浏览器加载的 `index-*.js` 是否为新构建；
2. 学员/教练页面是否不再出现“占位/Day 4”；
3. 管理端是否出现用户构成、待处理预约、最近预约和快捷入口；
4. Network 中的接口与上方数据来源一致；
5. 接口失败时页面是否显示“加载失败”而不是 0。

## 七、遗留问题与限制

1. 当前 Docker 运行版本未确认、未更新；
2. 没有浏览器截图或真实接口验收；
3. `SUBJECT_4` 种子题库仍不足，学员工作台科目四可能显示无题目；
4. 管理端 AI/RAG 没有可靠健康检查接口，因此没有展示 RAG 状态卡；
5. 管理端“最近预约”是按下单时间倒序，不是按预约日期范围；
6. 管理员用户构成使用三次分页 total 请求，数据量小，当前可接受；
7. 三个工作台的窄屏换行和真实视觉仍需浏览器验收。

## 八、答辩用工作台设计说明

### 学员工作台

- 目标：让学员首屏看到学习进度、错题、待确认预约和消息，并能直接进入刷题、模考、预约、AI、聊天。
- 数据：学情 overview、错题 summary、预约分页、聊天会话。
- 交互：卡片/进度条展示，快捷入口直接路由；无数据的科目明确显示“暂无题目”，接口失败显示“加载失败”。
- 权限：仅调用 `/api/student/**` 和 `/api/auth/me`，JWT 识别当前学员。

### 教练工作台

- 目标：让教练优先处理待确认预约，同时看到名下学员和未读消息。
- 数据：学员分页、预约按状态分页、聊天会话。
- 交互：待处理预约表提供“去处理”进入预约管理；学员概况进入学员管理；不伪造今日预约。
- 权限：只读取当前教练名下数据，预约审批/状态变更仍在预约管理页面复用后端状态机。

### 管理端工作台

- 目标：提供全局业务概览、用户构成、待处理预约与快捷管理入口。
- 数据：管理员 dashboard 聚合、用户分页 total、预约分页 total 和最近列表。
- 交互：统计卡、用户构成、待处理/最近预约表、快捷入口。
- 权限：全部接口位于 `/api/admin/**`，仅 ADMIN 可访问；AI 问答数是历史记录统计，不宣称 RAG 在线状态。

## 九、结论

三套工作台的源码已统一为真实数据驱动；管理端从四张统计卡补齐了用户构成、待处理预约、最近预约和快捷入口；学员端补充了模考/考试记录入口。当前未完成的是 Docker 前端版本更新和浏览器验收，不能把 type-check/build 结果写成页面已验收。
