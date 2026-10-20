# 【Day 29 工作日志】

## 一、日期

2026-10-20

## 二、本阶段目标

将学员端、教练端 Dashboard 从静态占位页改为真实数据工作台，优先复用现有只读接口，
不新增表、不改数据库结构、不伪造演示数字，并保留上一轮个人中心的已有修改。

## 三、完成的工作

### 1. 学员端 Dashboard

文件：`frontend/src/views/student/Dashboard.vue`

新增的真实指标与数据来源：

| 指标 | 接口 | 计算口径 |
| --- | --- | --- |
| 科目一练习进度 | `GET /api/student/analysis/overview?subjectCode=SUBJECT_1` | `progress` 百分比；提示已答题数/总题数、正确率、薄弱知识点数 |
| 科目四练习进度 | `GET /api/student/analysis/overview?subjectCode=SUBJECT_4` | 同上 |
| 待攻克错题 | `GET /api/student/wrong-book/summary`（不传科目） | `pending`；提示全部错题数与已掌握数 |
| 待确认预约 | `GET /api/student/appointment?status=PENDING&pageNum=1&pageSize=1` | 使用 `PageResult.total`，不是当前页记录数 |
| 未读消息 | `GET /api/student/chat/sessions` | 汇总各会话 `unreadCount` |

同时展示：

- 账号基本信息：`GET /api/auth/me`（用户名、真实姓名、手机号、角色）；
- 最近 5 条预约：`GET /api/student/appointment?pageNum=1&pageSize=5`，按后端返回顺序展示，不自行重排；
- 快捷入口：科目一/四刷题、错题本、智能问答、科目二/三预约、教练聊天、个人中心。

### 2. 教练端 Dashboard

文件：`frontend/src/views/coach/Dashboard.vue`

新增的真实指标与数据来源：

| 指标 | 接口 | 计算口径 |
| --- | --- | --- |
| 绑定学员 | `GET /api/coach/students?pageNum=1&pageSize=5` | 使用 `PageResult.total`；列表仅展示前 5 名学员 |
| 待处理预约 | `GET /api/coach/appointments?status=PENDING&pageNum=1&pageSize=5` | 使用 `total`；列表展示最近 5 条待确认预约 |
| 已确认预约 | `GET /api/coach/appointments?status=CONFIRMED&pageNum=1&pageSize=1` | 使用 `total`，仅表示已通过且未完成的预约 |
| 未读消息 | `GET /api/coach/chat/sessions` | 汇总 `unreadCount`，并统计有未读消息的会话数 |

同时展示：

- 账号基本信息：`GET /api/auth/me`；
- 名下学员概况：真实姓名/用户名、当前科目、当前阶段、教练确认状态；
- 快捷操作：学员管理、预约管理、学员消息、个人中心。

### 3. 状态处理

两个页面均实现：

- 全页加载状态；
- 每个数据块使用 `Promise.allSettled` 独立加载，单个接口失败不阻塞其它区块；
- 失败区块显示明确告警，不把失败显示成 0；
- 无数据时显示 `暂无数据` / 空状态；
- 提供“刷新数据”按钮重新加载。

## 四、接口与权限核对

实际核对后端 Controller 与前端类型定义后使用：

- 学员端只调用 `/api/student/**` 与 `/api/auth/me`；
- 教练端只调用 `/api/coach/**` 与 `/api/auth/me`；
- 学员身份由 JWT 识别，未传入任何学员 ID，不能读取其他学员数据；
- 教练端接口只返回当前教练名下数据；Dashboard 未新增任何跨教练查询；
- `PageResult` 的总数使用 `total` 字段，未用 `records.length` 冒充总数；
- 预约状态统计分别按 `status=PENDING` / `status=CONFIRMED` 请求，不混淆不同状态。

未新增后端接口、数据库表、数据库字段或初始化脚本。

## 五、验证结果

实际执行：

```bash
cd frontend
npm run type-check
npm run build
```

结果：

- `npm run type-check`：exit 0，无 TypeScript 错误。
- `npm run build`：exit 0，生产构建成功。
- 最终修改后又执行了一次 type-check 和 build，均通过。
- 构建输出包含学员端和教练端两个 `Dashboard-*.js` chunk。
- 未执行浏览器页面验收。
- 未执行真实登录后的接口联调。

## 六、Docker 部署核查

- 本轮只修改前端页面，未修改后端、数据库、SQL、Nginx 或 Compose 配置。
- `docker-compose.yml` 中与本轮改动相关的服务只有 `frontend`，理论上只需重建前端服务。
- 当前 Claude 运行环境没有 Docker CLI / Docker socket，无法执行镜像构建或容器更新；未对 MySQL 数据卷做任何操作。
- 因此 Docker 中的前端仍是旧资源，Dashboard 的新页面需要在前端服务重建后才能在浏览器中看到。

## 七、遗留问题

1. 需要在浏览器中实际登录学员账号、教练账号，验证 Dashboard 的真实数据、失败提示和入口跳转。
2. 需要在具备 Docker 的环境重建前端服务；本轮未执行。
3. 学员端 Dashboard 的“科目二/三”只用预约记录体现，未展示阶段进度，因为当前没有学员端读取四科学情阶段的接口。
4. 教练端未实现“今日预约”统计：现有预约列表接口没有日期筛选参数，使用当前页记录推断今日数量会不准确，因此没有伪造该指标。
5. 管理员端其余只读接口和 Excel 导入仍缺少完整运行验收。
6. 课程设计报告后续章节和答辩素材仍未完成。

## 八、验收记录（Day29 追加）

2026-10-20 对学员端、教练端 Dashboard 做了只读复核：

- 当前源码中的接口调用、统计口径和 Day29 日志记录一致：
  - 学员端使用 `/api/auth/me`、`/api/student/analysis/overview`、`/api/student/wrong-book/summary`、`/api/student/appointment`、`/api/student/chat/sessions`；
  - 教练端使用 `/api/auth/me`、`/api/coach/students`、`/api/coach/appointments`、`/api/coach/chat/sessions`。
- 已确认后端预约接口的总数来自 `PageResult.total`，列表按 `create_time DESC, id DESC` 排序；错题汇总接口在不传科目时按全部科目统计。
- 当前环境仍没有 Docker CLI 和 Docker socket，未执行镜像构建、容器更新或浏览器验收。
- 本地 `frontend/dist` 已包含本轮构建产物；Dockerfile 会在镜像构建时重新执行 `npm ci` 和 `npm run build`，因此仅更新本地 `dist` 不会改变容器内容。

