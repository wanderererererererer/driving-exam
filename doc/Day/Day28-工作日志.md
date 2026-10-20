# 【Day 28 工作日志】

## 一、日期

2026-10-20

## 二、本阶段目标

在聊天模块基本收尾后，补齐一个影响学员、教练日常使用的实际业务闭环：
学员端和教练端菜单中的“个人中心”入口此前没有对应路由，点击后落入 ComingSoon 占位页。
本阶段将现有管理员个人中心提取为公共页面，并接入学员端、教练端和管理员端三个角色。

## 三、完成的工作

1. 将 `frontend/src/views/admin/ProfileView.vue` 移动到
   `frontend/src/views/common/ProfileView.vue`，改为学员/教练/管理员共用组件。
2. 在 `frontend/src/router/index.ts` 新增：
   - `/student/profile` → `StudentProfile`
   - `/coach/profile` → `CoachProfile`
   - `/admin/profile` 改指向公共页面
3. 三个角色均复用现有认证接口：
   - `GET /api/auth/me`
   - `PUT /api/auth/profile`
   - `PUT /api/auth/password`
4. 保留页面已有行为：
   - 读取当前用户资料；
   - 修改姓名/手机号并重新读取确认；
   - 修改密码并校验两次输入一致；
   - 加载失败/重试、保存中状态、成功提示；
   - 更新本地用户 store，保持布局顶栏信息一致。
5. 未修改后端、数据库、接口契约、Docker 或 Nginx。

## 四、关键决策

- 不新增后端个人中心接口：`/api/auth/**` 已由 `AuthController` 提供通用资料和密码接口，三个角色均可使用。
- 不复制三份个人中心页面：移动为公共组件，避免重复维护。
- 不改变管理员个人中心现有业务行为，仅调整组件路径和路由引用。
- 未修改前端角色守卫；`profile` 路由继承所属端父路由的 `meta.role`。

## 五、问题与解决

问题：`StudentLayout.vue` 和 `CoachLayout.vue` 的菜单已有 `/student/profile`、`/coach/profile`，但路由未注册，点击后落入对应端的 ComingSoon 占位页。

解决：将现有 `admin/ProfileView.vue` 提取为 `common/ProfileView.vue`，在学员端和教练端路由中注册 `profile`，同时把管理员路由改为引用公共组件。

## 六、验证结果

实际执行：

```bash
cd frontend
npm run type-check
npm run build
```

结果：

- `npm run type-check`：exit 0，无 TypeScript 错误。
- `npm run build`：exit 0，生产构建成功。
- 构建产物包含新的 `ProfileView-*.js` 和新的 `index-*.js`。
- 未执行浏览器页面验收；未重建 Docker 前端容器。

## 七、遗留问题

1. 需要在浏览器中验证三个角色分别进入 `/student/profile`、`/coach/profile`、`/admin/profile` 后的页面展示、资料保存和密码修改。
2. 前端构建产物已更新，但 Docker 前端容器尚未重建，运行中的页面仍是旧资源。
3. 管理员端其余只读接口和 Excel 导入仍缺少完整运行验收。
4. 学员端、教练端 Dashboard 仍是占位页，后续可作为下一项核心可用性改进。
5. 课程设计报告后续章节和答辩素材仍未完成。
