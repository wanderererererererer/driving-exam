# 【Day 5 工作日志】

## 一、日期

2026-10-02

## 二、今日目标

把前端登录页从"占位"接通到真实后端，建立 **token 端到端链路**（登录 → 本地存储 → 请求携带 → 过期处理）；同时确定账号体系的根本形态。

## 三、完成的工作

**数据库设计：** 无。

**后端：** 无（用户模块已在 Day 4 完成，本日仅按前端需要核对接口契约）。

**前端：**

- **新建 `api/auth.ts`**：封装 `/api/auth` 四个接口（`login` / `getMe` / `updateProfile` / `updatePassword`），并定义与后端一一对应的类型：`LoginParams`(↔LoginDTO)、`LoginResult`(↔LoginVO)、`UserInfoVO`(↔UserVO)、`ProfileUpdateParams`、`PasswordUpdateParams`。
  - `UserRole` 类型定义于此，`store/user.ts` 从它引入并 re-export（避免重复定义与 api ↔ store 循环引用）。
- **改造 `store/user.ts`**：
  - 新增 `login` action：`await loginApi(...)` → 写入 token 与用户信息（同时落 localStorage）。
  - 新增 `restore()` action：从 localStorage 同步回 store。
  - getter 由 `role` 改为 `currentRole`，并保留 `isLoggedIn`。
- **改造 `views/Login.vue`**：占位登录 → 真实调用 `store.login`；按角色跳转（STUDENT→`/student`、COACH→`/coach`、ADMIN→`/admin`），有 `redirect` 参数则优先；按钮加 `loading` 防重复提交；`onMounted` 调 `restore()`。
- **核对 `utils/request.ts`**：确认 token 来源单一（见"关键技术决策"），401 清登录态并跳登录页，其它错误统一提示。
- 测试：`vue-tsc --noEmit` 类型检查 0 错误。

**文档/设计：**

- **确定账号体系为「邀请制」**——本系统**不做自助注册**：
  - 学员账号由**教练**在"学员管理"中创建（后端生成默认密码）；
  - 教练账号由**管理员**在"用户管理"中创建；
  - 管理员账号由**系统预置**；
  - 学员首次登录后可通过 `PUT /api/auth/password` 自助改密。
- 连带处理：同步修订计划文档（"登录注册完成" → "登录完成"）；后端 `WebMvcConfig` 白名单**移除 `/api/auth/register`**（避免放行一个不存在的接口造成误导）。

## 四、关键技术决策

| 决策点 | 方案 |
|---|---|
| **token 单一来源** | `utils/auth.ts`（localStorage）为唯一事实来源；Pinia store 是镜像；`request.ts` 从 localStorage 读。**request 刻意不依赖 store**——拦截器运行在 Vue 上下文之外，且 store→api→request 已是单向依赖，反向依赖会成环 |
| **错误提示归属** | 统一由 `request.ts` 响应拦截器弹 `ElMessage`；页面 `catch` **只用来停 loading，不重复弹**（否则一次错误弹两次） |
| 登录态持久化 | 仅登录态持久化（localStorage）；**练习会话不持久化**（后续 Day 11 沿用） |
| 账号体系 | 邀请制，不做自助注册 |
| 角色来源 | 登录后存于 store；菜单/跳转按 `currentRole` 决定 |

## 五、遇到的问题及解决方案

**问题 1**

- 现象：`request.ts` 需要跳登录页（`router.push('/login')`）而 import router，而 `router/index.ts` 需要 token 而 import request，二者形成**循环依赖**。
- 原因：登录态常量与读写逻辑放在 `request.ts`，导致路由与请求模块互相引用。
- 解决：抽出独立的 `utils/auth.ts` 存放 token / userInfo 的存取（`getToken/setToken/removeToken/clearAuth`），`request.ts` 与 `router/index.ts` 都改为依赖它，**彻底打破循环**。

**问题 2**

- 现象：登录失败（如 1101）时，预期只弹一次错误提示，实际可能弹两次。
- 原因：响应拦截器已统一弹 `ElMessage`，而 `Login.vue` 在 catch 中若再弹一次，就重复了。
- 解决：确立"**拦截器统一弹、页面不弹**"的约定；`Login.vue` 的 catch 只负责停止 loading。

**问题 3**

- 现象：401 时拦截器清掉了 localStorage，但 Pinia 内存里的 `token` / `userInfo` 仍是旧值（**内存与存储不一致**）。
- 原因：清理逻辑写在拦截器里，Pinia 无从感知。
- 解决：`Login.vue` 的 `onMounted` 调用 `store.restore()`——**一旦落到登录页就与 localStorage 重新对齐**。

## 六、产出物清单

- `frontend/src/api/auth.ts`（新增）
- `frontend/src/store/user.ts`（改造：login / restore / currentRole）
- `frontend/src/views/Login.vue`（改造：真实登录）
- `frontend/src/utils/request.ts`（核对确认，未改）
- 计划文档修订：`计划-修订.docx`（"登录注册"→"登录"）
- 后端连带改动：`WebMvcConfig` 白名单移除 `/api/auth/register`

## 七、遗留问题与明日计划

- 遗留：`/student`、`/coach`、`/admin` 三端首页仍是占位骨架；登录页尚未做"记住我"、验证码等增强。
- 明日计划（Day 6）：前后端联调，正式跑通登录流程；验证角色越权的错误码；处理联调中发现的问题。
