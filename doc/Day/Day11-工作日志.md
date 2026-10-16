# 【Day 11 工作日志】

## 一、日期

2026-10-04

## 二、今日目标

完成**刷题前端全套**：科目首页分流、章节选择、刷题页（四种练习模式共用一套 UI）、错题本页；把后端 15 个接口全部接上。

## 三、完成的工作

**数据库设计：** 无。

**后端：** 无（仅 Day 10 的种子数据扩充服务于前端验证）。

**前端：** 新增 13 个文件、改动 5 个。

### 1. 基础设施

- `src/types/index.ts`（新）：共享领域类型（`SubjectCode` / `QuestionType` / `WrongBookCategory` / `PracticeMode` / `PageResult<T>` / `QuestionVO` / `KPTreeNodeVO` / `AnswerResultVO` / `WrongBookItemVO` / `WrongBookSummaryVO` 等）。
  - **为什么单建 `types/`**：`QuestionVO` 被 `api/study.ts` 与 `api/wrongBook.ts` **同时使用**（错题重练返回的就是 `QuestionVO`），放任一 api 文件都会造成交叉 import。
  - **类型刻意"少字段"**：`QuestionOptionVO` 没有 `isCorrect`、`QuestionVO` 没有 `answer`/`analysis` —— 与"防作弊"一致，避免诱导别人去读永远 undefined 的字段。
- `src/api/study.ts`（新）：刷题 6 接口 + `STUDY_PAGE_SIZE = 20` 常量。
- `src/api/wrongBook.ts`（新）：错题本 5 接口。

### 2. 练习会话 store（`store/practice.ts`，组合式写法）

- 四种模式**共用一套状态**：`SEQUENCE` / `CHAPTER`（分页，支持预加载）、`RANDOM` / `RETRY`（一次性取完）。
- 关键状态：`questions` / `currentIndex` / `selections`（用户选了什么）/ `results`（判分结果）/ `totalQuestions` / `hasMore` / `loading` / `preloading`。
- **`submit` 按 mode 自动分发**：`RETRY` → 重练接口，其余 → 练习接口。UI 不需要关心。
- **预加载**：滑到**倒数第 5 题**时静默拉下一页并**按 `question.id` 去重**追加；仅对分页模式生效。
- **`selections` 与 `results` 的语义区分**（关键约定）：
  - 判断"**是否已提交**" → 用 `results.has(id)`；
  - 判断"**是否已选选项**"（多选提交按钮的启用条件）→ 用 `selections.has(id)`。
  - **不能用 `selections` 判提交**，否则答完的题会被误判。

### 3. 组件

| 组件 | 说明 |
|---|---|
| `PracticeToolbar.vue` | 「← 返回」+ 进度；**进度仅在 `SEQUENCE` 模式显示**（章节/随机/重练没有"全库进度"概念） |
| `QuestionCard.vue` | 题干 + `mediaUrl` + 三题型分支渲染 + 判分反馈 + 解析 + 「AI 帮助记忆」占位按钮 |
| `QuestionFooter.vue` | 上一题 / 下一题；**下一题要求本题已提交** |
| `ChapterPickerDialog.vue` | 章节选择弹窗（el-tree + 题数角标 + 失败重试 + 请求取消） |

### 4. 页面与路由

| 页面 | 路由 | 说明 |
|---|---|---|
| `SubjectHomeView.vue` | `/student/subject/:subjectCode` | 4 个入口卡片（顺序 / 随机 / 章节 / 我的错题），无统计 |
| `StudyView.vue` | `/student/study?subjectCode&mode&knowledgePointId` | 刷题页容器 |
| `WrongBookView.vue` | `/student/wrong-book?subjectCode` | 错题本（三 tab + 列表） |

- **`StudyView`**：解析路由 query → 调 store；键盘 `←/→` 翻页（挂 window，排除输入框）；触摸左右滑（**绑题目容器**，非 window）；`<QuestionCard :key="store.currentQuestion?.id">`；空状态处理。
- **错题本页**：`WrongBookTabs`（三 tab + 角标，角标来自 `summary`）+ `WrongBookTable`（列表 + 分页 + 操作）。
- **`StudentLayout` 菜单修正**："科目一/四"指向 `/student/subject/SUBJECT_1|4`；**高亮逻辑改为**：刷题页/错题本页的科目在 query 上，需映射回对应菜单项，否则进刷题页后菜单会"失去高亮"。

**文档/设计：**

- `doc/study-frontend-structure.md`（组件结构 + 数据流 + 文件清单 + 三条契约）
- `photo/design/study-frontend.png`（结构图）
- 同步更新 `test-cases-practice.md` / `practice-api.postman_collection.json` 中因种子扩充而变化的**硬数字**（集合 24 处、文档 6 处）

## 四、关键技术决策

| 决策点 | 方案 |
|---|---|
| **重练 = 第 4 种练习模式** | 刷题页只有一套 UI，"取题/提交走哪个接口"由模式决定 → **避免为重练再写一套界面** |
| 答题卡 | **取消**。答题卡是**考试**场景的产物（为了回头检查）；刷题答完就走、错题自动进错题本，不需要回头 |
| 预加载 | 距末尾 5 题时**静默**拉下一页；用独立 `preloading` 标志位（不复用 `loading`，否则答题时会闪遮罩） |
| 交互 | 单选/判断**点击即判分**；多选勾选后点「提交」；答对 **600ms 自动跳**，答错**停留**看解析 |
| 翻页 | 按钮 / 键盘 `←→` / 触摸左右滑 **三种都支持**；PC 鼠标拖动不触发 |
| 状态划分 | 跨组件共享的作答状态放 **Pinia**；知识点树、错题本列表等单页状态放**组件本地** |
| 持久化 | 练习会话**不持久化**（刷新即重来）；仅登录态持久化 |

## 五、遇到的问题及解决方案

**问题 1（最容易踩的坑）**

- 现象：答对后 600ms 自动跳下一题；但若在这 600ms 内**用户自己点了「下一题」**，会发生**跳两题**。
- 原因：挂起的定时器没有被取消，用户手动翻页后又触发了一次自动跳转。
- 解决：三重防护——
  1. **主保险**：`<QuestionCard :key="currentQuestion.id">`，换题即重建组件，`onUnmounted` 自动清计时器；
  2. **兜底**：`watch(() => store.currentIndex, clearAutoNext)` —— 只要当前题变了就清挂起的定时器；
  3. **只在 `doSubmit` 里排定时器**（不靠 watch 判断"是否刚提交"），从根上保证只有真的点了提交才可能触发。
  用等价脚本验证了 4 个场景（正常跳、手动翻页不双跳、切科目不误跳、连续两题各跳一次）。

**问题 2**

- 现象：关闭章节弹窗时，会莫名弹出 **"canceled"** 的错误提示。
- 原因：组件卸载/关闭时会 `abort` 掉未完成的请求，axios 抛 `CanceledError`；而响应拦截器对**任何错误**都 `ElMessage.error`，该错误没有 `response`，走了 else 分支，把 `"canceled"` 弹了出来。
- 解决：拦截器加 2 行——`if (axios.isCancel(error)) return Promise.reject(error)`，**主动取消的请求静默忽略**。这是"卸载时取消请求"的必要配套。

**问题 3**

- 现象：在刷题页点「← 返回」时，会弹一个 **"科目不存在"** 的红字提示，并再次跳转。
- 原因：离开页面时路由 query 被清空 → `watch(route.query)` 触发 → `subjectCode` 变成空串 → 被判为非法参数。
- 解决：watch 里加一行守卫 `if (!parsed.subjectCode) return`（**query 被清空 = 正在离开本页**）。手动改成非法科目（非空）仍会正常提示并跳回。

**问题 4**

- 现象：错题本**每行**都有一个「重练」按钮，但点任意一行效果**完全相同**——都是进入重练模式抽一批题。
- 原因：后端重练接口按"优先高频错、不够补待攻克"**抽一批**，**无法指定单题**；每行放按钮会让用户以为"只练这一题"。
- 解决：**删掉每行的按钮**，改为在 tab 栏右侧放一个主色调的「**错题重练**」按钮——语义准确（练一批），也不再误导。

**问题 5**

- 现象：`AbortController` 取消旧请求时，旧请求的 `finally` 会把 `loading` 置 false，**覆盖掉新请求刚设的 `loading = true`**，导致 loading 状态闪一下消失。
- 原因：两次请求的 `finally` 都会执行，无法区分"我是不是当前那次"。
- 解决：捕获本次的 controller，`finally` 里判断 `if (controller === mine)` 才清 loading —— **只有最后一次发出的请求有资格清 loading**。

**问题 6**

- 现象：种子数据只有 18 题，`pageSize = 20` 时 `pages = 1`、`hasMore = false`，**预加载永远不触发**，无法验证。
- 原因：题量小于一页。
- 解决：扩充种子数据（新增 `sql/seed-question-extra.sql`，+18 题，合计 **36 题** → `pages = 2`），并**同步更新已定稿的测试文档与集合里的硬数字**（否则下次跑测试会一片红）。

## 六、产出物清单

**前端（新增 13 个）**

```
src/types/index.ts
src/api/study.ts · src/api/wrongBook.ts
src/store/practice.ts
src/views/student/SubjectHomeView.vue · StudyView.vue · WrongBookView.vue
src/components/study/PracticeToolbar.vue · QuestionCard.vue · QuestionFooter.vue · ChapterPickerDialog.vue
src/components/wrongbook/WrongBookTabs.vue · WrongBookTable.vue
```

**前端（改动 5 个）**

```
src/router/index.ts       加 3 条路由
src/layout/StudentLayout.vue  菜单指向科目首页 + 高亮映射
src/utils/request.ts      静默忽略取消的请求
src/api/study.ts          知识点树接口加 AbortSignal 参数
doc/…                     （见下）
```

**文档 / 数据**

- `doc/study-frontend-structure.md` + `photo/design/study-frontend.png`
- `sql/seed-question-extra.sql`（+18 题）
- 更新 `doc/test-cases-practice.md`（6 处）、`doc/testjson/practice-api.postman_collection.json`（24 处）、`doc/test-cases-wrong-book.md`（前置数据）

**验证**

- `vue-tsc --noEmit` **0 错误**（每一步都跑）
- `vite build` **构建成功**（证明路由里的动态 `import` 文件都真实存在）

## 七、遗留问题与明日计划

- 遗留：
  - **题库管理模块（Day 8 推迟到 Day 19）**仍未实现；
  - 「AI 帮助记忆」按钮目前是**占位**（点击弹提示），真实调用待 Day 13；
  - 科目二/科目三的独立首页未建（预约类，待 Day 18），菜单暂落到占位页；
  - `store.progressText` 因进度移到顶部而**成为死代码**，按纪律留到第 4 周收尾统一清理。
- 明日计划（Day 12）：接入 **DeepSeek V4 Flash** —— 生成大模型调用工具类（HTTP 封装 + 降级逻辑），这是本项目最大的技术亮点。
