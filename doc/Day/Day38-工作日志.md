# 【Day 38 工作日志】

## 一、日期与目标

- 日期：2026-10-20
- 目标：在 Day37 浅色令牌基础上，进行第二阶段布局与排版重构，解决页面过窄、信息层级不足、卡片同质和文字偏小问题。

## 二、审查结论

修改前实际检查了：

- `frontend/src/assets/main.css`
- 三个布局组件
- `Login.vue`、三端 Dashboard
- `router/index.ts`、全部实际路由页面
- Element Plus 全局覆盖和各页面 scoped 样式

发现的主要问题：

1. `app-main > *` 最大宽度只有 1440px，且页面普遍有额外内边距，宽屏下内容显得窄而集中。
2. 页面标题大多 20px、表格 13px、辅助说明 12px，信息层级不够清楚。
3. 三端 Dashboard 大量使用同尺寸白卡，缺少主次。
4. 管理端工作台是四张同规格统计卡 + 两列普通卡片，容易像默认后台模板。
5. 侧边栏、顶部栏和内容区的基础样式虽统一，但页面局部仍有旧模板式间距和配色。
6. 部分页面只有全局色值统一，没有针对页面布局做局部调整。

## 三、全局布局与排版

修改：`frontend/src/assets/main.css`

- 内容区最大宽度从 1440px 调整为 **1680px**，并设置 `width: 100%`，宽屏下铺开。
- 主内容区改为上下 20px、左右 24px；1200px 以下收紧为 20px。
- 页面标题令牌 24px，区域标题 16px，辅助说明 13px。
- 表格正文 14px、表头 13px，提升可读性。
- 增加 Tabs、Tree、Descriptions、Upload 的统一样式。
- 保留 Element Plus 主题变量桥接和公共布局类。

## 四、公共布局与登录页

修改：

- `frontend/src/layout/StudentLayout.vue`
- `frontend/src/layout/CoachLayout.vue`
- `frontend/src/layout/AdminLayout.vue`

统一：

- 64px 顶部栏、224px 侧边栏、角色色品牌标记、用户首字母头像；
- 菜单圆角选中态和角色色；
- 主内容区统一使用 `app-main`；
- 保留原菜单、路由、退出登录和全屏考试逻辑。

修改：`frontend/src/views/Login.vue`

- 左侧品牌说明 + 右侧登录卡片；
- 用户/密码图标输入；
- 三角色选择卡片；
- 窄屏收敛为单栏；
- 登录逻辑、校验、角色选择和 redirect 不变。

修改：`frontend/src/views/NotFound.vue`、`views/common/ComingSoon.vue`

- 统一浅色背景、卡片和空状态视觉。

## 五、三端工作台重构

### 学员端

文件：`frontend/src/views/student/Dashboard.vue`

- 页面标题改为 24px 的“学员工作台”；
- 保留真实科目进度、错题、预约、消息、最近预约；
- 快捷入口增加“科目一模考”“考试记录”；
- 保留分区加载和失败提示，不把失败显示成 0。

### 教练端

文件：`frontend/src/views/coach/Dashboard.vue`

- 检查后现有业务信息已经满足要求，没有重写业务逻辑；
- 通过 Day37 全局令牌和本轮统一排版调整视觉；
- 待处理预约保持优先，已确认/绑定学员/消息保持清晰；
- 不新增“今日预约”等无可靠数据源的指标。

### 管理端

文件：`frontend/src/views/admin/DashboardView.vue`

重构为：

1. 页面头部：26px 标题、说明、刷新；
2. 单行概览面板：用户总数（突出）、启用题目、预约总数、AI 问答历史；不再使用四张相同白卡；
3. 不对称主工作区：
   - 左侧：待确认预约（顶部 3px 状态色强调）、最近提交预约；
   - 右侧：用户构成（真实数量比例进度条）、分组快捷入口（图标型操作块）；
4. 所有区块独立加载、失败告警、真实空状态；
5. AI 问答继续标注为历史记录数，不代表 RAG 在线。

用户构成百分比基于后端返回的总数与角色总数计算；没有数据时显示 0 进度，不伪造数字。

## 六、全站页面统一

- 对全站现有页面进行 Day37 色值迁移的基础上，本轮统一标题字号、表格字号、内容宽度和控件规范。
- 19 个页面标题由 20px 调整为 24px（含三端业务页、个人中心、考试报告等）。
- 公共 Element Plus 控件统一：
  - 按钮、输入框、表格、分页、弹窗、标签、Tabs、Tree、Upload、Alert、Empty；
  - 表格表头、行高、悬停态；
  - 表单标签、间距；
  - 卡片圆角、边框和阴影。
- 保留所有页面原有业务结构、API、表单校验、分页、筛选、上传、考试/预约/聊天/AI 功能。

## 七、验证结果

实际执行：

```text
cd frontend
npm run type-check
npm run build
```

结果：

- type-check：exit 0；
- build：exit 0；
- 当前构建入口：`dist/assets/index-CI9I_tjy.js`；
- 只有既有 chunk 体积提示，无编译错误；
- 全部懒加载路由组件被 Vite 成功解析。

### 未执行

- 真实浏览器像素级验收；
- Docker 前端镜像更新；
- 不同分辨率、长文本、窄屏弹窗和横向溢出的实际检查；
- 真实接口的浏览器 Network 验收。

不能把 type-check/build 写成浏览器验收通过。

## 八、Docker 版本

当前 Claude 环境没有 Docker CLI/socket，未更新容器，也无法确认用户 Docker Desktop 中的运行版本。

用户本机最小更新方式（只影响 frontend）：

```powershell
cd D:\myself\Integrate
docker compose --env-file docker/.env -p driving-exam-accept build frontend
docker compose --env-file docker/.env -p driving-exam-accept up -d --no-deps --force-recreate frontend
docker compose --env-file docker/.env -p driving-exam-accept ps frontend
```

不要执行 `down`、`down -v`、prune，不删除 MySQL 数据卷。若还要让 Day30–Day34 后端修改进入容器，再定向重建 backend。

## 九、剩余风险与优先浏览器检查

剩余风险：

- 运行中的前端镜像可能仍是旧版本；
- 管理端概览面板在 1366px 以下会变为两列/单列，需真实浏览器确认；
- 快捷入口两列在窄侧栏下可能换行；
- 表格在较窄窗口可能出现横向滚动；
- 长用户名、长题目和窄屏弹窗未实测；
- `SUBJECT_4` 种子题库仍可能不足；
- 部分旧页面仍保留各自历史 class 命名，视觉统一依赖全局令牌。

建议浏览器优先检查：

1. `/admin/dashboard`：概览面板、待确认预约、用户构成、快捷入口的宽度和层级；
2. `/student/dashboard`：首屏学习进度与快捷入口；
3. `/coach/dashboard`：待处理预约和学员概况；
4. `/login`、一个管理表格页、一个弹窗/表单页、`/student/exam/:examId` 答题页；
5. 1366×768、1440×900、1920×1080 三档宽度。

## 十、修改文件清单

- `frontend/src/assets/main.css`
- `frontend/src/layout/StudentLayout.vue`
- `frontend/src/layout/CoachLayout.vue`
- `frontend/src/layout/AdminLayout.vue`
- `frontend/src/views/Login.vue`
- `frontend/src/views/NotFound.vue`
- `frontend/src/views/common/ComingSoon.vue`
- `frontend/src/views/student/Dashboard.vue`
- `frontend/src/views/coach/Dashboard.vue`（视觉随全局规范）
- `frontend/src/views/admin/DashboardView.vue`
- 全站 34 个 `.vue`/`.css` 文件的色值与标题字号统一
- `doc/Day/Day38-工作日志.md`（本文件）
- `doc/前端UI视觉设计说明.md`（同步布局与排版规范）

未修改后端、数据库、路由契约或 API 调用。
