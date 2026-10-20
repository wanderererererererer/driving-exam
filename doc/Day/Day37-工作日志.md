# 【Day 37 工作日志】

## 一、日期与目标

- 日期：2026-10-20
- 目标：在不改变业务功能、接口、权限和真实数据口径的前提下，完成全前端浅色视觉系统升级，并完善三端工作台。

## 二、实施前检查

已检查：

- `frontend/package.json`、`main.ts`、`assets/main.css`；
- 三端布局 `StudentLayout.vue`、`CoachLayout.vue`、`AdminLayout.vue`；
- `Login.vue`、三端 Dashboard、全部实际 `views` 路由和公共组件；
- `api` 封装、路由守卫、角色地址；
- Git 工作区未提交修改。

确认：

- 使用 Vue 3、TypeScript、Vite、Element Plus、Pinia，无新增依赖；
- 现有路由、API、业务状态和角色边界保持不变；
- 运行环境无 Docker/browser 访问能力，不能做容器或像素级页面验收；
- 截图中的旧占位页面与当前源码不一致，Docker 前端镜像需要重建。

## 三、设计令牌与公共框架

### 1. 全局样式

修改：`frontend/src/assets/main.css`

建立：

- 页面背景/卡片/边框/主文字/次级文字/品牌蓝/语义色；
- 字体层级、圆角、阴影、卡片内边距；
- Element Plus 主题变量桥接；
- 公共布局类：
  - `app-layout` / `app-header` / `app-brand` / `app-aside` / `app-menu` / `app-main`；
  - `app-page-title` / `app-page-desc` / `app-section-title` / `app-muted`；
- 按钮、输入框、表格、分页、弹窗、标签、Tabs、Tree、Upload、Alert、Empty、Loading 统一规范；
- 响应式：1200px、900px、720px 三档布局收紧。

设计方向：白色/浅灰底、低饱和蓝 `#7597D5`、浅蓝选中背景 `#E8EFFB`、柔和边框和轻阴影；不使用大面积渐变、炫光或过度动画。

### 2. 公共布局

修改：

- `frontend/src/layout/StudentLayout.vue`
- `frontend/src/layout/CoachLayout.vue`
- `frontend/src/layout/AdminLayout.vue`

统一：

- 64px 顶部栏、224px 侧边栏、24px 内容边距；
- 品牌图标、系统名、角色副标题；
- 当前用户首字母头像与姓名；
- 菜单圆角选中态；
- 学员/教练/管理端角色强调色：
  - 学员：蓝；
  - 教练：低饱和青蓝；
  - 管理员：低饱和靛蓝。

保留原有菜单、路由、退出登录和全屏考试逻辑。

### 3. 登录页

修改：`frontend/src/views/Login.vue`

- 左侧品牌/产品说明，右侧登录卡片；
- 用户、密码带图标输入；
- 三个角色改为易读的选项卡片；
- 保留原有登录逻辑、表单校验、角色选择和 redirect；
- 窄屏隐藏左侧说明，只保留登录卡片。

### 4. 全站颜色统一

对 `frontend/src` 下 34 个 `.vue`/`.css` 文件做了受控色值迁移：

- 旧 Element 蓝 `#409eff` → 品牌蓝 `#7597D5`；
- 旧深灰/次级灰、边框、浅蓝底、状态色统一到新令牌；
- 未改动业务逻辑、接口、路由和组件结构。

公共页：

- `views/common/ComingSoon.vue` 增加统一空状态卡片；
- `views/NotFound.vue` 使用统一背景。

## 四、三端工作台

### 学员端

文件：`frontend/src/views/student/Dashboard.vue`

- 标题改为“学员工作台”；
- 保留真实数据：
  - 科目一/四进度、正确率、薄弱知识点数；
  - 待攻克错题；
  - 待确认预约；
  - 未读消息；
  - 最近预约；
- 快捷入口增加“科目一模考”“考试记录”；
- 全部现有学习入口保留；
- 接口失败仍显示“加载失败”，不会伪装为 0。

### 教练端

文件：`frontend/src/views/coach/Dashboard.vue`

检查后现有实现已覆盖本阶段要求，未做无依据的功能改写：

- 绑定学员、待处理预约、已确认预约、未读消息；
- 待处理预约列表；
- 名下学员概况；
- 学员管理、预约管理、学员消息、个人中心入口；
- 分区加载/失败/空状态；
- 没有伪造“今日预约”。

只通过全局设计令牌和色值迁移统一视觉。

### 管理端

文件：`frontend/src/views/admin/DashboardView.vue`

- 标题改为“管理端工作台”；
- 保留四张统计卡；
- 新增用户构成（学员/教练/管理员）；
- 新增待处理预约列表和总数；
- 新增最近提交预约列表；
- 新增分组快捷入口；
- 统计、用户构成、待处理预约、最近预约分区块加载；
- 失败显示告警/“加载失败”，不显示 0；
- AI 问答卡明确提示“历史记录数，不代表当前 RAG 可用性”。

数据来源：

- `GET /api/admin/dashboard`
- `GET /api/admin/users?role=...&pageSize=1`
- `GET /api/admin/appointments?status=PENDING&pageSize=5`
- `GET /api/admin/appointments?pageSize=5`

没有新增 API 或数据库结构。

## 五、页面覆盖

构建已验证所有懒加载路由组件可解析。实际纳入视觉规范的页面：

- 登录、404、ComingSoon；
- 学员：工作台、科目首页、刷题、错题本、智能问答、学情分析、聊天、个人中心、考试列表、答题、考试报告、考试记录、预约；
- 教练：工作台、学员管理、预约管理、聊天、个人中心；
- 管理：工作台、用户、绑定、知识点、题库、试卷、预约查看、AI 统计、个人中心。

业务页面保留原有组件结构、API、分页、筛选、表单、上传、状态机和权限逻辑；本轮主要统一 Element Plus 控件、颜色、间距和布局。

## 六、验证结果

### 已实际执行并通过

```text
cd frontend
npm run type-check
npm run build
```

结果：

- type-check exit 0；
- build exit 0；
- 构建入口为 `dist/assets/index-DpRjiHvv.js`；
- 只有既有 chunk 体积提示，无编译错误；
- 路由组件全部被 Vite 成功解析。

### 未执行

- 真实浏览器截图与像素级验收；
- Docker 前端镜像重建/更新；
- 真实接口的浏览器 Network 验收；
- 不同分辨率下的横向溢出和遮挡检查；
- 键盘焦点、长文本、窄屏弹窗等真实交互检查。

不能把 type-check/build 结果写成浏览器验收通过。

## 七、Docker 版本

当前 Claude 环境无 Docker CLI/socket，未更新容器，也无法确认用户 Docker Desktop 中运行的版本。

用户本机最小更新方式（只更新前端，不影响 MySQL/后端数据卷）：

```powershell
cd D:\myself\Integrate
docker compose --env-file docker/.env -p driving-exam-accept build frontend
docker compose --env-file docker/.env -p driving-exam-accept up -d --no-deps --force-recreate frontend
docker compose --env-file docker/.env -p driving-exam-accept ps frontend
```

如果还要让 Day30–Day34 后端修改进入容器，再定向重建 backend；不要执行 `down -v`、prune 或数据库重置。

## 八、剩余风险与缺口

1. 运行中的前端镜像可能仍是旧版本，必须重建并硬刷新验证；
2. 无浏览器，无法确认实际视觉、响应式和交互；
3. `SUBJECT_4` 种子题库不足，学员端科目四仍可能显示真实空数据；
4. 管理端 AI 问答数量不是 RAG 可用性检测，页面已明确口径；
5. `admin/Dashboard.vue` 是未挂路由的旧占位文件，真正路由使用 `DashboardView.vue`；
6. 部分业务页面仍使用各自的历史 class 命名，视觉统一主要依赖 Element Plus 令牌和公共变量，后续可继续按页面细化；
7. 本轮未修改后端，也未运行后端测试；Day34 的 108 项测试是既有证据，不是本轮重新执行结果。

## 九、答辩用简短说明

- 全站采用统一浅色设计令牌，三端共享颜色、字体、间距、圆角和控件规范；
- 学员端强调学习进度和练习入口，教练端强调待办/预约/学员，管理端强调统计/待办/业务记录；
- 三端工作台数据均来自现有真实接口，不使用静态占位数字；
- 角色权限仍由路由守卫和后端 JWT/路径规则共同保证；
- 接口失败以告警/失败文案呈现，不伪装为 0；
- 设计说明与页面覆盖详见 `doc/前端UI视觉设计说明.md`。

## 十、修改文件清单

公共：

- `frontend/src/assets/main.css`
- `frontend/src/layout/StudentLayout.vue`
- `frontend/src/layout/CoachLayout.vue`
- `frontend/src/layout/AdminLayout.vue`
- `frontend/src/views/Login.vue`
- `frontend/src/views/NotFound.vue`
- `frontend/src/views/common/ComingSoon.vue`

工作台：

- `frontend/src/views/student/Dashboard.vue`
- `frontend/src/views/coach/Dashboard.vue`（视觉随全局令牌统一）
- `frontend/src/views/admin/DashboardView.vue`

全站色值/控件统一：

- `frontend/src` 下 34 个 `.vue`/`.css` 文件的既有色值迁移；
- 未替换任何 API、路由、业务状态或数据数组。

文档：

- `doc/Day/Day37-工作日志.md`（本文件）
- `doc/前端UI视觉设计说明.md`
