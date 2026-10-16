# 【Day 3 工作日志】

## 一、日期

2026-10-02

## 二、今日目标

搭建 Vue3 前端工程骨架：完成目录结构、请求封装、路由与三端布局占位，使前端能独立运行、并能与后端联调（代理 `/api`）。

## 三、完成的工作

**数据库设计：** 无。

**后端：** 无（本日聚焦前端；仅确保后端 `application.yml` 的端口与前端代理对齐，默认 8080）。

**前端：**

- 技术栈：**Vite 5 + Vue3 + TypeScript + Element Plus + Axios + Pinia + Vue Router**，项目位于 `D:\myself\Integrate\frontend`。
- 建立标准目录结构：`src/{api, assets, components, layout, router, store, utils, views}`。
- **请求封装 `utils/request.ts`**：
  - 请求拦截器：从 `localStorage` 读取 token，注入 `Authorization: Bearer <token>`。
  - 响应拦截器：判断 `code === 200` 则**解包返回业务数据**；`401` 清登录态并跳转登录页；`403` 与其它错误用 `ElMessage` 提示。
  - 导出统一的 `http.get/post/put/delete` 泛型方法。
- **登录态工具 `utils/auth.ts`**：集中管理 token / userInfo 的本地存取（`getToken/setToken/removeToken/clearAuth`）。
- **状态管理**：`store/index.ts`（pinia 实例）、`store/user.ts`（用户 store：token、userInfo、logout）。
- **路由 `router/index.ts`**：登录页 + `/student`、`/coach`、`/admin` 三端布局（懒加载）+ 全局守卫（无 token 跳登录）+ 各端未实现菜单兜底到 `ComingSoon` 占位页 + `NotFound` 404。
- **三端布局 `layout/{StudentLayout, CoachLayout, AdminLayout}.vue`**：顶栏 + 侧边菜单 + `router-view`，菜单项对齐各端功能树。
- **占位页面 `views/`**：`Login.vue`（占位登录）、`student/coach/admin/Dashboard.vue`、`common/ComingSoon.vue`、`NotFound.vue`。
- **工程配置**：`vite.config.ts`（`@` → `src` 别名、dev 代理 `/api` → `http://localhost:8080`）、`.env.development`（`VITE_API_BASE_URL=/api`）、`tsconfig`、`env.d.ts`。

**文档/设计：**

- 前端目录约定与各端菜单结构。
- 与后端接口契约的对应关系（请求前缀 `/api`、token 头格式、统一返回解包规则）。

## 四、关键技术决策

| 决策点 | 方案 |
|---|---|
| 构建工具 | Vite 5（`@vitejs/plugin-vue`） |
| 语言 | TypeScript 5.4 |
| UI 库 | Element Plus 2.7（全量引入，先跑通后优化） |
| 请求库 | Axios 1.7，统一封装 + 拦截器解包 |
| 路由 | Vue Router 4.4，history 模式，懒加载 + 全局守卫 |
| 状态 | Pinia 2.1 |
| 跨域 | 开发环境用 Vite proxy 代理 `/api` 到后端，规避 CORS |
| 目录约定 | api/assets/components/layout/router/store/utils/views |

## 五、遇到的问题及解决方案

**问题 1**

- 现象：`request.ts` 需要跳转登录页（`router.push('/login')`）而引入 router，同时 `router/index.ts` 又需要 token key 而引入 request，二者的 import 形成**循环依赖**。
- 原因：登录态常量放在 `request.ts`，导致路由与请求模块互相引用。
- 解决：抽出独立的 `utils/auth.ts` 存放 token/userInfo 的存取逻辑，`request.ts` 与 `router/index.ts` 都改为依赖它，**彻底打破循环**。

**问题 2**

- 现象：在工作目录（挂载盘）直接执行 `npm install` 反复失败，报 `ENOTEMPTY: directory not empty, rename .../node_modules/.dayjs-xxxx`。
- 原因：该挂载盘文件系统对 `rename` 操作支持受限，npm 的原子重命名在解包阶段失败（属环境问题，非代码问题）。
- 解决：在沙箱**本地磁盘**拷贝源码后安装依赖并完成校验，再把验证通过的 `package-lock.json` 带回到 `frontend/`，保证本机安装可复现。

**问题 3**

- 现象：`vite build` 提示 `Some chunks are larger than 500 kB after minification`（Element Plus 全量引入导致主包偏大，index chunk 约 1.2 MB）。
- 原因：采用 Element Plus 全量引入（`app.use(ElementPlus)` + 全量 CSS），未做按需加载。
- 解决：骨架阶段先接受该提示（功能正确、构建成功）；后续可改为 `unplugin-vue-components` + `unplugin-auto-import` 按需引入，或做 `manualChunks` 拆分优化。

## 六、产出物清单

- `frontend/` 工程 27 个文件：`index.html`、`package.json`、`package-lock.json`、`vite.config.ts`、`tsconfig*.json`、`env.d.ts`、`.env.development`
- `src/main.ts`、`src/App.vue`、`src/assets/main.css`
- `src/utils/request.ts`、`src/utils/auth.ts`
- `src/store/index.ts`、`src/store/user.ts`
- `src/router/index.ts`
- `src/layout/{StudentLayout, CoachLayout, AdminLayout}.vue`
- `src/views/{Login, NotFound}.vue`、`src/views/common/ComingSoon.vue`、`src/views/{student,coach,admin}/Dashboard.vue`
- `src/api/index.ts`（接口层占位）

## 七、遗留问题与明日计划

- 遗留：`Login.vue` 当前为"占位登录"（按所选角色写入演示登录态并跳转），尚未接真实后端；Element Plus 未做按需引入。
- 明日计划（Day 4）：生成用户模块后端代码——实体、Mapper、Service、Controller，打通登录闭环（JWT 签发与校验），并补上接口级角色鉴权。
