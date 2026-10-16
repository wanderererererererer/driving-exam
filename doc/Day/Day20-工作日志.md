# 【Day 20 工作日志】

## 一、日期

2026-10-14

**范围说明（如实记录）**：原计划 Day 20 = **全流程联调 / 修复 Bug / 全系统跑通**。但 Day 20 开始前的**只读审计**（本轮同一对话）查明：**管理端**（前后端皆 0）、**教练端前端**、**学员端考试/预约前端**、**聊天**均**未实现**，因此"全系统联调"的前提尚不具备。据此将 Day 20 **重定为"补功能 + 分链联调"**，并按**分阶段、一步一停**推进。

本日志记录 **Day 20 第一阶段**：**学员端 · 模拟考试 —— 试卷列表 + 开始考试入口**（纯前端）。

## 二、本阶段目标

让学员端能进入「模拟考试」页面、拉取并展示后端真实的**可用试卷列表**、点击按钮调用真实的**开始考试**接口并按真实响应处理——**不含**答题页 / 倒计时 / 交卷 / 成绩报告。

## 三、完成的工作（接口依据 + 文件变更）

### 1. 接口依据（均从当前仓库源码确认）

| 用途 | 方法 · 路径 | 关键入参 | 关键返回 | 源码位置 |
|---|---|---|---|---|
| 试卷列表 | `GET /api/student/exam/papers` | `subjectCode`（**必填**，如 `SUBJECT_1`） | `List<PaperVO>`：`id/title/subjectCode/questionCount/duration/totalScore/passScore/ongoingExamId` | `ExamController.papers()`（`/api/student/exam`） |
| 开始考试 | `POST /api/student/exam/start` | body `{ paperId }` | `ExamVO`：`examId/paperTitle/status/remainingSeconds/questions/answeredMap` | `ExamController.start()` |

- **开始考试的语义（关键）**：`ExamServiceImpl.start()` —— 若该学员在这份试卷上**已有进行中的考试**：**未超时 → 直接返回该场**（断点续考）；**已超时 → 先收尾旧的、再新建一场**。即**不是每次点击都新建**。
- **`PaperVO.ongoingExamId`（Long，可 null）**：有进行中考试则填其 examId，供前端显示「继续考试」。前端据此区分按钮文案。
- 试卷列表只返回该科目下**已启用的模拟卷**（`paper_type=MOCK`、`status=ENABLED`），按 `exam_paper.id` 升序。

### 2. 文件变更

| 文件 | 类型 | 用途 |
|---|---|---|
| `frontend/src/types/index.ts` | 修改 | 新增 `ExamStatus` 与 `ExamVO` 类型（与后端 `ExamVO` 字段一一对应；`PaperVO` 原已存在，未动） |
| `frontend/src/api/exam.ts` | **新增** | 封装 `getPapersApi(subjectCode)` / `startExamApi(paperId)` |
| `frontend/src/views/student/ExamView.vue` | **新增** | 试卷列表页 + 科目切换 + 开始/继续考试入口 + 加载/空/错误态 + 防重复提交 |
| `frontend/src/router/index.ts` | 修改 | 学员端新增路由 `/student/exam`（`StudentExam`） |
| `frontend/src/layout/StudentLayout.vue` | 修改 | 侧边菜单新增「模拟考试」入口（图标 `Document`） |

**未改动**：后端（Java）、数据库、SQL、`utils/request.ts`（全局请求封装）、登录/权限、其他业务模块。

## 四、关键技术决策

| 决策点 | 方案 | 理由 |
|---|---|---|
| 科目来源 | 页内 **科目切换（科目一 / 科目四）** | `GET /papers` 的 `subjectCode` 是**必填**参数；两者都是真实科目，**不虚构查询参数** |
| 开始 vs 继续 | 依据 `PaperVO.ongoingExamId` 决定按钮文案；**两种情况都调用真实的 `POST /start`** | 后端对"已有进行中考试"本就返回该场，调用 start 即真实语义 |
| 开考后 | **不跳转任何页面**，仅提示成功 + 保留 `examId` | 答题页尚未实现，**不伪造"已进入答题"状态、不跳不存在的路由** |
| 失败提示 | 依赖现有请求拦截器（`request.ts`）统一 `ElMessage`，页面**不重复弹** | 遵循项目既有错误处理规范 |
| 防重复提交 | `startingId` ref；请求期间所有开始按钮 `disabled`、当前按钮 `loading` | 与项目既有按钮 loading 风格一致 |

## 五、遇到的问题及解决方案

**问题 1（发现"计划 vs 实际"偏差，先纠正再动手）**

- 审计发现管理端等缺失（见 §一 范围说明），据此把 Day 20 重定为"补功能 + 分链联调"，并只做本阶段。**未虚构管理端/联调结果。**

**问题 2（`start` 可能返回"已有考试"而非新建）**

- 若按"每次点击都新建"的直觉写，会误判语义。已按 `ExamServiceImpl.start()` 的真实逻辑（返回已有 / 超时收尾后新建）处理：两种按钮都调真实 start，成功后重新拉列表使按钮变「继续考试」。

## 六、产出物清单

- 新增：`frontend/src/api/exam.ts`、`frontend/src/views/student/ExamView.vue`。
- 修改：`frontend/src/types/index.ts`、`frontend/src/router/index.ts`、`frontend/src/layout/StudentLayout.vue`。

**验证（静态；本环境无浏览器，未做页面实跑）**

- `npm run type-check`（`vue-tsc --noEmit`）→ **EXIT 0**。
- `npm run build`（`vite build`）→ **EXIT 0**（约 54s）；产物含 `dist/assets/ExamView-*.js`（≈2.88 kB），证明新页已正确进入懒加载路由。
- **未做**：浏览器实际点击验证（本环境无法运行浏览器）；未对真实后端发起请求。

## 七、遗留问题与下一阶段计划

- 本阶段**未做**（刻意）：答题页、题目渲染、答案选择、倒计时、答题卡、交卷、判分、成绩报告、考试历史。
- 待确认/风险：① 答题页尚未实现，开考后**只能提示、无法进入答题**；② 已有进行中考试的**继续**同样受限于答题页；③ 前端未做**角色路由拦截**（全项目现状：路由守卫只校验 token，权限由后端 `PathRoleRule` 兜底）——本阶段**未改全局权限机制**。
- 下一阶段建议（**仅建议，未实施**）：**学员端 · 模拟考试答题页（第二阶段）**——按 `ExamVO.questions` / `answeredMap` 渲染题目、倒计时用 `remainingSeconds` 初始化、`POST /{examId}/answer` 暂存；范围与接口需**以仓库实际重新审查后**再定。

---

## 八、Day 20 · 第二阶段：模拟考试入口调整 + 考试记录入口

**触发**：第一阶段把「模拟考试」做成了**学生端侧边栏一级菜单**。产品要求调整——模拟考试应**归属科目一/科目四页面内部**，并在其旁**新增「考试记录」入口**。本阶段仅为**布局调整 + 入口衔接 + 考试记录复用**，不含答题页。

### 8.1 只读检查结论

- 科目一 / 科目四**共用同一组件** `SubjectHomeView.vue`（路由 `/student/subject/:subjectCode`），非独立组件。
- **考试历史后端已存在且可复用**：`GET /api/student/exam/history?subjectCode=&pageNum=&pageSize=` → `PageResult<ExamRecordVO>`；`subjectCode` **必填**；**只含已结束**（SUBMITTED / TIMEOUT）考试、按交卷时间倒序；分页 pageNum(默认1) / pageSize(默认10，后端上限 20)。
- `ExamRecordVO` 真实字段：`examId / paperTitle / score / isPass / submitTime / duration`。
- **字段/能力缺口（如实记录，未伪造）**：① **无「考试状态」字段**（接口只返回已结束考试，不回传 SUBMITTED/TIMEOUT）→ 记录页**不展示**考试状态；② **无成绩详情页**（`GET /{examId}/report` 存在但前端无页面）→ **不提供「查看详情」入口**。

### 8.2 页面布局调整（任务 A）

- `SubjectHomeView.vue`：**保留**原 4 个练习卡片（顺序/随机/章节/我的错题，**业务逻辑与跳转未改**）；在其**下方**新增**两个并排功能卡片**——**模拟考试**（图标 `Document`）/ **考试记录**（图标 `Clock`），副标题分别为"模拟真实考试，检验学习成果"/"查看历史成绩与考试情况"。
- 布局：新增 `.sh__body`（flex column 居中）包裹「4 练习卡 `.sh__grid`」+「两功能卡 `.sh__more`」，整体水平垂直居中；两功能卡 `flex:0 0 240px`、`flex-wrap` 居中——**窄屏自动换行、不横向溢出、不重叠**；样式沿用既有卡片风格（圆角/阴影/浅色配色常量），**未引入新 UI 依赖**。科目一/科目四因共用组件而**自动同款**，各自传各自 `subjectCode`。

### 8.3 模拟考试入口（任务 B）

- **移除** `StudentLayout.vue` 侧边栏「模拟考试」一级菜单项（及随之无用的 `Document` 图标 import）；**保留** `/student/exam` 页面与路由（**未删页**）。
- 科目改由**路由 query** 传递：科目一 → `/student/exam?subjectCode=SUBJECT_1`，科目四 → `?subjectCode=SUBJECT_4`。`ExamView.vue` **移除页内科目切换**，改从 query 读取；**缺参 / 非法 → 提示「科目不存在」并跳回 dashboard**（**不静默请求错误科目**）。
- **开始考试原有逻辑保持不变**：依 `PaperVO.ongoingExamId` 显示「继续考试 / 开始考试」，两种情况均调用真实 `POST /start`；成功后**只提示、不跳转**，保留 `examId`（答题页仍未接入）。**未改后端开始考试逻辑。**

### 8.4 考试记录功能（任务 C）

- 复用既有后端接口：`api/exam.ts` 新增 `getExamHistoryApi(subjectCode, pageNum, pageSize)` —— **未新建后端接口**。
- 新增页面 `ExamRecordsView.vue`（路由 `/student/exam-records?subjectCode=`）：`el-table` 展示 **试卷名称 / 考试时间 / 得分 / 是否通过**（**均后端真实字段**；得分用 tag 显示通过/未通过）；`el-pagination` 分页；加载中 `el-skeleton`、空 `el-empty`、失败 `el-result` + **重试**；有**返回**对应科目首页的路径。科目一/科目四**各按 `subjectCode` 取各自记录，不混淆**。

### 8.5 文件变更（本阶段）

| 文件 | 类型 | 用途 |
|---|---|---|
| `frontend/src/views/student/SubjectHomeView.vue` | 修改 | 4 练习卡下方新增「模拟考试 / 考试记录」两个并排卡片 + 布局/样式 |
| `frontend/src/views/student/ExamView.vue` | 修改 | 科目改由路由 query 读取、移除页内切换、加校验与返回 |
| `frontend/src/views/student/ExamRecordsView.vue` | **新增** | 考试记录列表页（复用 history 接口） |
| `frontend/src/api/exam.ts` | 修改 | 新增 `getExamHistoryApi` |
| `frontend/src/types/index.ts` | 修改 | 新增 `ExamRecordVO` 类型 |
| `frontend/src/router/index.ts` | 修改 | 新增路由 `/student/exam-records` |
| `frontend/src/layout/StudentLayout.vue` | 修改 | **移除**「模拟考试」侧边菜单项 |

**未改动**：后端 Java、数据库、SQL、`utils/request.ts`、登录/权限、科目二/三、其他模块。

### 8.6 验证结果（静态；本环境无浏览器，未做页面实跑）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `npm run type-check`（`vue-tsc --noEmit`） | **0** | 类型检查通过 |
| `npm run build`（`vite build`） | **0** | 构建成功（≈29s）；产物含 `ExamRecordsView-*.js`（≈2.3 kB）、`ExamView-*.js`、`SubjectHomeView-*.js` |

- **未做**：**浏览器实际联调**（本环境无法运行浏览器）；**未对真实后端发起请求**。→ 结论**仅限"静态检查通过"**。

### 8.7 未完成事项与风险

- **答题页仍未实现**：模拟考试"开始/继续"成功后**只能提示、无法进入答题**（本阶段范围外）。
- **考试记录能力受后端所限**：**无「考试状态」字段、无成绩详情页** → 记录页只展示 4 个真实字段，**未伪造**状态/详情。若后续要展示状态或详情，需**先评估后端**（`ExamRecordVO` 是否补 status、是否为 report 建前端页），**本轮未改后端**。
- **前端仍无角色路由拦截**（全项目现状：路由守卫只校验 token，权限由后端 `PathRoleRule` 兜底）——本阶段**未改**全局权限机制。
- **下一阶段建议**（仅建议，未实施）：① **学员端 · 模拟考试答题页**（承接 `examId`/`ongoingExamId`）；② 若产品确需"考试状态/成绩详情"，**先出后端评估**再谈前端。范围均需**以仓库实际重新审查后**再定。

---

## 九、Day 20 · 第三阶段 A：模拟考试答题页（答题交互）

**触发**：第二阶段"开始/继续考试"只提示、不进答题。本阶段实现**答题页第一阶段**——恢复考试、渲染题目/选项、**本地**作答、翻题、答题卡、状态处理；**不含**倒计时 / 服务器暂存 / 交卷 / 判分 / 报告。

### 9.1 接口核查结论（均以仓库源码为准）

- **`ExamVO`**（`POST /start` 与 `GET /{examId}` 返回）：`examId / paperTitle / status(ONGOING|SUBMITTED|TIMEOUT) / remainingSeconds / questions / answeredMap`。
  - **`questions`** ＝ `List<QuestionVO>`：`{ id, subjectCode, questionType(SINGLE|MULTIPLE|JUDGE), content, difficulty, mediaUrl, options: List<{ optionKey, optionContent }> }`；**不含 `answer` / `analysis`**（防作弊）。
  - **`answeredMap`** ＝ `Map<questionId, userAnswer>`（`questionId → 字符串答案`）。
- **作答格式**（`ExamAnswerDTO`）：`{ questionId, userAnswer }`；单选 `'A'`、多选 `'AB'`（**字母升序、无分隔**）、判断 `'对' | '错'`。
- **恢复考试接口存在且可复用**：`GET /api/student/exam/{examId}`（`resume`）→ `ExamVO`；**名义 GET，但"已超时"会惰性收尾**（返回 `status=TIMEOUT`）；不存在/非本人 → `2104`。→ **刷新可恢复**。
- **后续闭环接口均已存在**（本阶段**未接入**，仅登记调用顺序）：`POST /{examId}/answer`（单题暂存；仅 `ONGOING` 可写，否则 2102；**不拦超时**）、`POST /{examId}/submit`（交卷，**幂等**）、`GET /{examId}/report`（报告，`ONGOING` → 2105）。
- **`remainingSeconds`** 由后端算 `duration*60 − (now − startTime)`，负数归 0；非进行中为 0。

### 9.2 页面实现

- **新增答题页** `ExamAnswerView.vue`，路由 `/student/exam/:examId`（name `StudentExamAnswer`）。
- **入口衔接**：试卷列表页「开始/继续」成功后 → `router.push('/student/exam/{examId}?subjectCode=...')`（`examId` **用后端真实返回值**；新建或已有进行中考试都走同一条）。**未在前端生成考试 ID、未改后端逻辑。**
- **题目展示**：按后端顺序渲染「序号/总题数、题型标签、题干、`mediaUrl` 图片（有值才显示）、选项」；**不显示正确答案与解析**。题型**仅**实现后端真实支持的 SINGLE / MULTIPLE / JUDGE。
- **作答交互（仅本地）**：单选点选、多选勾选（本地按字母升序拼接为 `'AB'`）、判断（`对`/`错`）；切换/返回题目**保留**已选（页面内存 `Map<questionId,userAnswer>`）。
- **导航**：上一题 / 下一题（**自由翻题**，允许留空）；**答题卡**显示 当前 / 已答 / 未答 并支持点击跳题；顶部显示"第 X/N 题 · 已答 M/N"。
- **本地 vs 服务器（明确区分）**：答案**仅暂存在本页内存，未保存到服务器**（页面顶部醒目的 warning alert 声明）；刷新会丢失本地选择，仅按后端 `answeredMap` 恢复"服务器已保存部分"。本阶段**未调用** `POST /{examId}/answer`。
- **交卷**：按钮为**占位（`disabled`，标注"下一阶段接入"）**，**未实现**提交/判分/报告。

### 9.3 状态处理

- 首次加载中 → `el-skeleton`；加载失败 → `el-result` + **重试** + 返回；**`examId` 非法/缺失** → 提示"无效的考试"并跳回列表；**题目为空** → `el-empty`；**`status != ONGOING`（已交卷/超时）** → "本场考试已结束"态、**不可作答**；**刷新/直接打开** → 按 `examId` 重新 `GET /{examId}` 恢复。

### 9.4 文件变更（本阶段）

| 文件 | 类型 | 用途 |
|---|---|---|
| `frontend/src/views/student/ExamAnswerView.vue` | **新增** | 答题页：恢复考试、题目/选项渲染、本地作答、翻题、答题卡、状态处理 |
| `frontend/src/api/exam.ts` | 修改 | 新增 `getExamApi(examId)`（`GET /{examId}` 恢复）；并**注释登记**暂存/交卷/报告接口（未接入） |
| `frontend/src/views/student/ExamView.vue` | 修改 | 开始/继续成功后**跳转答题页**（不再只提示） |
| `frontend/src/router/index.ts` | 修改 | 新增路由 `/student/exam/:examId` |

**未改动**：后端 Java、数据库、SQL、`utils/request.ts`、登录/权限、科目一/四入口与考试记录页、其他模块（已按 mtime 核对：`ExamController.java`/`ExamServiceImpl.java` 仍为 10-08）。

### 9.5 验证结果（静态；本环境无浏览器，未做页面实跑）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `npm run type-check`（`vue-tsc --noEmit`） | **0** | 类型检查通过 |
| `npm run build`（`vite build`） | **0** | 构建成功（≈22s）；产物含 `ExamAnswerView-*.js`（≈5.5 kB）与 `.css` |

- **未做**：浏览器实际联调；**未对真实后端发起请求**；**未调用任何会创建考试 / 暂存答案 / 交卷 / 改变考试状态的接口**。→ 结论**仅限"静态检查通过"**。

### 9.6 未完成事项与风险

- **无倒计时**（保留钩子：已取得 `exam.remainingSeconds`，未渲染/未计时）。
- **无服务器暂存 / 交卷 / 判分 / 成绩报告**（后续阶段）。
- **本地答案刷新会丢**（未接入暂存接口）——已在页面明确提示。
- **前端仍无角色路由拦截**（全项目现状）。

### 9.7 下一阶段建议（仅建议，未实施）

按此顺序接入闭环：**单题暂存（`POST /{examId}/answer`）→ 倒计时（用 `remainingSeconds` 初始化）→ 交卷（`POST /{examId}/submit`）→ 成绩报告页（`GET /{examId}/report`）**。范围与接口需**以仓库实际重新审查后**再定。

---

## 十、Day 20 · 第四阶段：服务器暂存与倒计时

**触发**：第三阶段答案只在页面内存、且无倒计时。本阶段接入**服务器暂存（含恢复）**与**倒计时**；**不含**交卷 / 判分 / 成绩报告。

### 10.1 核实到的真实接口契约（以源码为准，未凭空新增）

- **单题暂存**：`POST /api/student/exam/{examId}/answer`，body `ExamAnswerDTO { questionId, userAnswer }` → `ApiResponse<Void>`（data=null）。
  - **允许状态**：仅 `status=ONGOING` 可写（否则 `EXAM_SUBMITTED(2102)`）；**不拦超时**。
  - **归属**：`loadOwnedExam` 校验 `userId`，不存在/非本人 → `EXAM_NOT_FOUND(2104)`。
  - **落库**：`MockExamAnswerMapper.upsertAnswer` = `INSERT … ON DUPLICATE KEY UPDATE user_answer=…`（**无版本号**）。
- **答案格式**（与前端一致）：单选 `'A'`；多选 `'AB'`（**字母升序、无分隔**）；判断 `'对'|'错'`。`userAnswer` 为 `@NotBlank`（**空串会 1001**，且无"清除答案"接口）。
- **恢复**：`GET /api/student/exam/{examId}` → `ExamVO`；`answeredMap = Map<questionId, userAnswer>`（**含服务器已保存答案**）；`remainingSeconds` ＝ `duration*60 − (now − startTime)`（负数归 0；非进行中为 0）。**超时的进行中考试会被惰性收尾**，返回 `status=TIMEOUT`。
- **非法题目 ID**：暂存接口不校验题目是否属于本卷（多余 id 由交卷时的判分遍历忽略）——**本阶段前端只保存当前卷内题目**。
- **前端错误处理**：`utils/request.ts` 拦截器统一 `ElMessage`（401 清态跳登录 / 403 / 业务失败）；**页面不重复弹**，仅落地错误态。

### 10.2 答案保存与恢复逻辑

- **保存**：选项变化后调用 `saveExamAnswerApi(examId, questionId, userAnswer)`。
  - **只存变化的题**：单选/判断选中项相同 → 不触发；多选按集合变化触发。
  - **空答案不保存**（后端 `@NotBlank`、无清除接口）——本地可取消勾选，但服务器保留旧值（**已知局限**）。
  - **同题串行化（防旧覆盖新）**：`inFlight` 哨兵 + **收敛循环** `ensureSaved()`——同一题同时只跑一条保存链，链内循环"直到当前答案 == 已保存值"；飞行途中再改答案只以最新值收尾。不同题可并行（不同行）。
  - **失败不静默**：失败由拦截器弹提示 + 落地 `error` 态（题目处「保存失败」+「重试保存」按钮、答题卡标红）。
- **恢复**：进入/刷新按 `examId` 调 `getExamApi`；用 `answeredMap` 同时回填 `answers` 与 `savedAnswers`（**回填的即视为"已保存"**）；题目选项、顶部已答数、答题卡状态随之重建。**失败保留错误态 + 重试，不用空答案覆盖**；`status != ONGOING` 如实展示"已结束"，不强行恢复为进行中。
- **保存状态模型**（题目处 chip + 答题卡 + 顶部汇总）：`none`（未答）/ `saving`（保存中…）/ `dirty`（待保存…）/ `saved`（已保存）/ `error`（保存失败）。

### 10.3 倒计时

- 以 `GET /{examId}` 返回的 `remainingSeconds` **初始化**；**刷新/重进都重新向后端取**，不依赖前端旧计时。
- 页面级**单实例**计时器：`startTimer()` 先 `stopTimer()` 再建；`onUnmounted` 清理——避免重复/并行计时。
- 显示 `MM:SS`，≥1 小时自动变 `HH:MM:SS`；≤60 秒变红。
- **归零行为**：停止计时 + 置 `timeUp` → 顶部**红色警示**"考试时间已到…等待交卷流程接入"，并**禁止继续作答**（UI 层）。**不自动交卷、不改后端状态**。

### 10.4 文件变更

| 文件 | 类型 | 用途 |
|---|---|---|
| `frontend/src/api/exam.ts` | 修改 | 新增 `saveExamAnswerApi(examId, questionId, userAnswer)`（`POST /{examId}/answer`）；同步注释（暂存已接入，交卷/报告仍登记未接入） |
| `frontend/src/views/student/ExamAnswerView.vue` | 修改 | 接入服务器暂存（串行化保存链 + 保存状态模型）、`answeredMap` 恢复、倒计时与归零处理 |

**未改动**：后端 Java、数据库、SQL、`ExamView.vue`、`ExamRecordsView.vue`、`SubjectHomeView.vue`、`router`、`utils/request.ts`、登录/权限（已按 mtime 核对）。

### 10.5 验证结果（静态；本环境无浏览器/未连真实后端）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `npm run type-check`（`vue-tsc --noEmit`） | **0** | 类型检查通过 |
| `npm run build`（`vite build`） | **0** | 构建成功（≈21s）；产物含 `ExamAnswerView-*.js`（≈8.0 kB）+ `.css` |

- **未做**：浏览器实际联调；**未对真实后端发起请求**（含暂存接口）。→ 仅"静态检查通过"，**非真实联调通过**。

### 10.6 未验证事项与第五阶段风险

- **未真实验证**：暂存成功/失败、串行化在真实并发下的表现、`answeredMap` 恢复、倒计时归零与 UI 锁定——**均只经静态检查**。
- **空答案不可清除**：后端无清除接口 + `@NotBlank` → 取消勾选后服务器保留旧值（刷新会"复活"旧答案）。**若需支持请先评估后端**。
- **超时交卷未处理（第五阶段核心）**：倒计时归零只做前端提示与锁定，**后端此时仍是 `ONGOING`**（服务端惰性超时只在 `resume`/`submit` 触发）。第五阶段需处理：**超时后调 `submit`（后端按 `isTimeout` 判分、状态记 `TIMEOUT`）+ 报告页**；并确认前端时钟与后端时钟的边界（以服务端为准）。
- **保存失败若因 `2102`（已交卷）**：本阶段只标"保存失败"，**未**自动刷新为"已结束"——第五阶段可结合 `submit`/`report` 完善。
- 前端仍无角色路由拦截（全项目现状）。

### 10.7 第五阶段建议（仅建议，未实施）

**超时/主动交卷 + 成绩报告**：`POST /{examId}/submit`（幂等；超时由后端判定）→ `GET /{examId}/report` 报告页；倒计时归零后触发交卷流程并跳报告。范围与接口需**以仓库实际重新审查后**再定。

---

## 十一、Day 20 · 第五阶段 A：答题规则与考试过程整改

**背景**：用户在浏览器验证后提出 6 项整改。本阶段**先只读核查**每项根因，再只做**前端可独立完成**的部分；涉及后端状态机/接口的按"报告缺口 + 停止"处理。

### 11.1 六项需求的根因与处理结论

| 需求 | 根因（证据） | 分类 | 处理 |
|---|---|---|---|
| **A 判断题选项过大** | `.ea__judge` 用 `flex`+`flex:1`、`padding:18px`、`font-size:18px` → 两个按钮占满整行 | **前端可独立** | ✅ 已改（两列 grid + 收窄 + 缩小） |
| **B 单选需互斥** | 核查 `ExamAnswerView`：SINGLE 走 `pickSingle()`（**整串覆盖为一个 key**），`isPicked()` 用 `answer.split('').includes(key)` → **单选本就互斥**；MULTIPLE 才 `toggleMultiple` | **前端（已满足）** | ⚠️ 代码核查**未发现单选可多选**；见 11.4 说明 |
| **C 科目一即时判题** | 练习页 `QuestionCard` 点击即 `store.submit()` → `POST /student/study/answer` 返回 `AnswerResultVO`（isCorrect/正确答案/解析）→ **练习已即时判题+锁定**；模拟考试 `POST /exam/{examId}/answer` 返回 **Void**，判分只在交卷 | **练习=已实现；考试=后端缺口** | ⛔ 考试不实现（无判题接口 + 中途给答案违考试语义）；见 11.4 |
| **D 考试隐藏侧边栏** | 答题页此前是 `StudentLayout` 子路由，侧边栏与顶栏常驻 | **前端可独立** | ✅ 已改（`meta.fullscreen` + 布局条件渲染 + 页内唯一「退出考试」） |
| **E 退出暂停计时** | `MockExam` 仅 `startTime`；`remainingSeconds = duration*60 − (now − startTime)`；`ExamStatus` 只有 ONGOING/SUBMITTED/TIMEOUT；**全后端无 pause/暂停** | **后端缺口** | ⛔ 不实现（不伪造暂停）；见 11.5 |
| **F 提交后锁定** | 练习：`submitted`（results 有判分结果）后禁止再点 → **已锁定**；考试：`POST /answer` 只是保存、返回 Void、**无"已提交/锁定"字段** | **练习=已实现；考试=后端缺口** | ⛔ 考试不实现；见 11.4 |

### 11.2 练习 vs 模拟考试的规则区别（核查结论）

- **普通练习**（`/student/study`，`StudyView`+`QuestionCard`+`store/practice`）：**即时判题 + 即时揭晓答案（含正确答案/解析）+ 提交后锁定**（单选/判断点击即判，多选勾选后点「提交」）。判分接口 `POST /api/student/study/answer`。
- **模拟考试**（`/student/exam/:examId`，`ExamAnswerView`）：**只保存、不判分**（`ExamAnswerDTO→Void`）；判分仅在交卷（`ExamFinalizer`）。**考试中不揭晓答案**（`QuestionVO` 不含 answer/analysis）。二者规则不同，**不得混淆**。

### 11.3 本次实际完成的修改（仅前端）

- **A**：`.ea__judge` 改 `display:grid; grid-template-columns:1fr 1fr; gap:12px; max-width:320px`；`.ea__judge-btn` 改 `padding:11px 0; font-size:15px`（去 `flex:1`）→ 两列等宽、收窄、占位减小；窄屏随容器收缩、无溢出。
- **D**：`router` 给答题路由加 `meta.fullscreen`；`StudentLayout` 命中全屏时**只渲染 `<router-view/>`（不渲染侧边栏/顶栏）**；答题页头部按钮由「← 返回」改为**「← 退出考试」**，点击弹**二次确认**（明确"退出后考试仍在进行、剩余时间继续减少、可稍后「继续考试」恢复"），确认后**仅导航离开**（**不交卷、不改状态**）。

### 11.4 B / C / F 说明（练习 vs 考试）

- **B（单选互斥）**：核查**当前代码单选已互斥**（SINGLE 只写到单一 key；MULTIPLE 才多选）——`README` 无变更。若用户仍见"多选"，更可能是（a）该题本就是**多选题**，或（b）浏览器缓存旧包。**请以最新构建复核**；如仍有问题请提供题目类型与截图。
- **C（即时判题）**：若用户指**普通练习**——**已具备**；若指**模拟考试**——**后端缺口**：无单题判题接口，且考试中途揭晓答案**违背考试语义**；本阶段**未**在考试页硬编码答案或直接比对题库（禁用做法）。
- **F（提交后锁定）**：普通练习**已锁定**；模拟考试**无"单题提交"动作**（只有自动保存），后端也**无提交/锁定字段** → 无法持久锁定，**未实现**。

### 11.5 E 说明（暂停计时 = 后端缺口，未实现）

- **证据**：`MockExam` 无暂停字段；`ExamStatus` 无 PAUSED；`computeRemainingSeconds/isTimeout` 均按 `now − startTime` 实算 → **退出后时间照常流逝**，前端 `clearInterval` 不能暂停它。
- **不做**：不伪造暂停、不改 `startTime`/剩余时间、不用前端变量掩盖。
- **最小后端方案（仅建议，未实施）**：`mock_exam` 增 `paused_at`(datetime, 可空) + `paused_total_seconds`(int, 默认0)；新增"暂停/恢复"接口：暂停置 `paused_at=now`；恢复时 `paused_total_seconds += now − paused_at`、清空 `paused_at`；`computeRemainingSeconds`/`isTimeout` 改为 `elapsed = (now − startTime) − pausedTotal(− 当前暂停中未计入部分)` 并**冻结**；超时判定与 `submit` 不变。**涉及后端表结构/状态机，须先确认**。

### 11.6 文件变更

| 文件 | 类型 | 用途 |
|---|---|---|
| `frontend/src/views/student/ExamAnswerView.vue` | 改 | 判断题两列收窄（A）；头部「退出考试」+ 二次确认（D） |
| `frontend/src/layout/StudentLayout.vue` | 改 | 支持 `meta.fullscreen`：全屏路由不渲染侧边栏/顶栏（D） |
| `frontend/src/router/index.ts` | 改 | 答题路由加 `meta.fullscreen: true`（D） |

**未改动**：后端 Java、数据库、SQL、`ExamView.vue`、`ExamRecordsView.vue`、`SubjectHomeView.vue`、`api/exam.ts`、练习模块（`store/practice.ts`、`QuestionCard.vue`）等（已按 mtime 核对：近 20 分钟 `.java`/`.sql` **0 处改动**）。

### 11.7 验证结果（静态；本环境无浏览器/未连后端）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `npm run type-check`（`vue-tsc --noEmit`） | **0** | 类型检查通过 |
| `npm run build`（`vite build`） | **0** | 构建成功（≈22s）；产物含 `ExamAnswerView-*.js`（≈8.2 kB）、`StudentLayout-*.js` |

- **未做**：**浏览器实际联调**（判断题两列、退出确认、隐藏侧边栏、退出后计时仍走等场景**均未在浏览器实测**）。→ 仅"静态检查通过"，**非真实联调通过**。

### 11.8 未完成 / 风险 / 下一阶段建议

- **未完成（后端缺口，均未实施）**：**C 考试即时判题**（无接口且语义不当）、**E 退出暂停计时**（无暂停机制）、**F 考试单题锁定**（无提交/锁定字段）。
- **待用户确认**：**B** 若在最新构建下仍见单选可多选，请提供题目类型 + 截图（当前代码核查为互斥）。
- **核实边界**：A/D 仅经静态检查，**未在浏览器验证**；E 的"退出后计时继续"由后端算法决定，**未实测**。
- **下一阶段建议**：① 若确认要以"考试中途暂停"为需求 → 先评审 11.5 的后端最小方案；② 若"即时判题/锁定"实为**练习**诉求 → 已满足，无需改动考试；③ 正常闭环仍为**交卷 + 成绩报告**（第五阶段 A 之外）。

---

## 十二、Day 20 · 第五阶段 B：修复退出模拟考试后无法恢复原场

### 12.1 只读追踪的完整恢复链路

- **退出**：答题页「退出考试」→ 二次确认 → `stopTimer()` + `router.push('/student/exam?subjectCode=…')`（`backTo()`）。**只导航，不调任何接口、不改考试状态、不清答案**（核 `ExamAnswerView.exitExam`）。
- **重进列表**：`ExamView` 挂载 → `watch(subjectCode, {immediate})` → `GET /api/student/exam/papers?subjectCode=SUBJECT_1`；`subjectCode` 由 `SubjectHomeView` 的「模拟考试」卡片透传。
- **`ongoingExamId` 来源**：后端 `ExamServiceImpl.papers()` 用 `mock_exam` 中 `user_id=当前用户 AND status='ONGOING'` 的行，按 `paperId→examId`（`putIfAbsent`）回填到对应 `PaperVO.ongoingExamId`（`Long`，可空）。字段名/序列化与前端 `PaperVO.ongoingExamId` **一致**（已核 `PaperVO.java`、`types/index.ts`）。
- **原「继续考试」按钮**：`ExamView.startExam()` → `POST /api/student/exam/start {paperId}` → 用返回的 `examId` 进入答题页。
- **`start` 语义（关键）**：`ExamServiceImpl.start()` → `findOngoing(userId,paperId)`（status='ONGOING'）→ **若存在且 `!isTimeout` 则返回该场**；**否则（不存在，或已超时）→ 先 `finalize` 旧场、再 `createExam` 新建一场**。
- **答题页恢复**：`GET /api/student/exam/{examId}`（`resume`）→ 返回题目 + `answeredMap` + `remainingSeconds`；若该场已超时则**惰性收尾并返回 `status=TIMEOUT`**。

### 12.2 根因（代码证据）

**前端恢复链路本身正确**（退出只导航；列表按 `ongoingExamId` 显示「继续考试」；答题页按 examId 恢复）。**真正的缺陷在「继续考试」按钮走 `POST /start`**：

- `start` 在"进行中考试**已超时**"时会 **`finalize` 旧场 + `createExam` 新建一场**（`ExamServiceImpl.start` 第 39–52 行）。于是用户点「继续考试」后进入的是一支**全新考试**（examId 不同、答案为空）——表现即"**无法恢复原来那场**"。
- 触发条件：该进行中考试已过 `duration`（演示卷 20 分钟）→ `isTimeout = now − startTime > duration*60` 为真。
- 该缺陷**已由代码证实**：`start` 明确存在"收尾+新建"分支；前端却把「继续考试」建立在 `start` 之上，无法保证进入的是原 `examId`。

> 备选原因（未能由代码排除，需运行数据区分）：① 浏览器缓存旧包；② `ongoingExamId` 未被后端回填（需看 `GET /papers` 实际响应）。已在 §12.6 列出需补充的证据。

### 12.3 最小修复（仅前端）

- `ExamView.vue`：把「开始/继续」按钮逻辑改为 **`onPaperAction(paper)`**：
  - **`paper.ongoingExamId != null` → 直接 `router.push('/student/exam/{ongoingExamId}')`**，**不再调用 `POST /start`** → **必进入原 examId、绝不新建**；超时则如实由答题页 `resume` 显示"该场已结束"，而非被新考试掩盖。
  - `ongoingExamId == null` → `POST /start` 新建，用返回的 `examId` 进入。
- 未改后端、未改数据库、未改其他页面。

### 12.4 文件变更

| 文件 | 类型 | 用途 |
|---|---|---|
| `frontend/src/views/student/ExamView.vue` | 改 | 「继续考试」改为**直接进入 `ongoingExamId` 原场**（不再经 `POST /start`）；「开始考试」仍走 `start` |

**未改动**：后端 Java、数据库、SQL、`ExamAnswerView`、`ExamRecordsView`、`SubjectHomeView`、`router`、`api/exam.ts`（已按 mtime 核对：近 20 分钟 `.java`/`.sql` **0 处改动**）。

### 12.5 验证结果

| 命令 | 退出码 | 结果 |
|---|---|---|
| `npm run type-check`（`vue-tsc --noEmit`） | **0** | 类型检查通过 |
| `npm run build`（`vite build`） | **0** | 构建成功；产物含 `ExamView-*.js` |

- **未做**：**浏览器实际联调**；**未调用真实后端**。→ 仅"静态检查通过"，**非真实联调通过**。

### 12.6 仍未验证 / 需补充的证据

- **需你补一份运行证据**以坐实触发条件（若复测仍失败）：退出后重新进入「科目一 · 模拟考试」时，**`GET /api/student/exam/papers?subjectCode=SUBJECT_1` 的响应**（看 `ongoingExamId` 是否为原来那支的 id，还是 `null`）。
  - 若 `ongoingExamId` 为**原 id** → 修复后「继续考试」**必进原场**；若该场已超时，会显示"已结束"（此时"无法继续"是**后端超时规则**所致，需产品/后端决定是否要暂停，见 11.5）。
  - 若 `ongoingExamId` 为 **null** → 说明原场已被收尾；需进一步查是何调用触发（`start`/`resume`/`submit`）。
- **退出后计时继续**（后端无暂停）不变，本阶段按 11.5 未处理。

### 12.7 浏览器复测步骤（人工）

1. 科目一 → 模拟考试 → **开始考试**（进入答题页）；记下 URL 里的 `examId`。
2. **至少选择并保存一道题**（题目处显示「已保存」）。
3. 点「**退出考试**」并确认 → 回到试卷列表，该试卷应显示「**进行中**」+「**继续考试**」。
4. **刷新浏览器**（F5）或重新从 科目一 → 模拟考试 进入（以验证不依赖组件内存）。
5. 点「**继续考试**」。
6. 核对：URL 的 `examId` **与第 1 步相同**、**第 2 步的答案已回显**、**剩余时间**按后端计时（相对第 1 步已减少）。
   - ⚠️ 若第 1 步到现在**已超过 20 分钟**，该场会被判超时：修复后会**显示"本场考试已结束"**（这是后端规则，不是"新考试"）。

### 12.8 已完成 / 未完成

- **已完成**：退出→重进的**恢复链路核查**；「继续考试」**改为直进原 examId**（消除"新建考试掩盖原场"）。
- **未完成（范围外）**：暂停计时、单题锁定、交卷、成绩报告。
- **待你复测**：§12.7 六步 + §12.6 的 `GET /papers` 响应（若仍异常）。

---

## 十三、Day 20 · 第五阶段 C：答题卡实时显示单题正误

### 13.1 原答题卡不显示正误的根因

- 答题卡状态此前**只依据"是否作答/保存状态"**（`saveStateOf`：未答/保存中/已保存/失败），**没有"对错"维度**。
- **模拟考试没有单题判题能力**：`POST /exam/{examId}/answer` 只保存（返回 `Void`）；判分只在**交卷**（`ExamFinalizer`）。考试中 `QuestionVO` **不含答案/解析**（防作弊），故前端**无法**自行判定。→ 需求需**后端新增单题判题**。

### 13.2 判题接口与评分规则（后端，最小实现）

- **新接口**：`POST /api/student/exam/{examId}/judge`，body `{questionId}` → `ExamJudgeVO { questionId, answered, isCorrect }`（**只回正误，不回标准答案/解析**）。
- **校验**（`ExamServiceImpl.judge`）：`loadOwnedExam`（不存在/非本人 → `2104`）→ `status=ONGOING`（否则 `2102`）→ 题目须属于本卷（否则 `1001`）。
- **判题答案 = 服务器已保存答案**：从 `mock_exam_answer`（`uk_exam_question`，每卷每题至多一行）取该题**已保存**的 `user_answer`，**不采信前端传入**；无保存答案 → `answered=false`。
- **规则复用** `AnswerGrader.isCorrect(questionType, userAnswer, correctAnswer)` —— 与**交卷判分完全同一套**（单选/多选归一化、多选去重排序、判断同义映射；全对才算对）。
- **不泄露答案**：接口只回布尔；`ExamVO` 新增的 `correctMap` 亦只回布尔，**不含标准答案/解析**。

### 13.3 判题结果如何持久化 / 刷新恢复

- **不新增表/字段**：判题结果**从可信数据重算**——"已保存答案（`mock_exam_answer`，服务端持久化）+ 题目标准答案"，由服务端用 `AnswerGrader` 计算，**稳定可复现**。
- `ExamVO` 新增 `correctMap: Map<questionId, isCorrect>`（仅含已作答题目），在 `buildExamVO`（`start`/`resume` 都经它）中填充 → 前端**加载即恢复**答题卡正误。
- 前端 `correctMap` 状态：加载时由 `ExamVO.correctMap` 播种；「下一题」判题时更新。

### 13.4 前端交互（ExamAnswerView）

- **触发时机**：点「下一题」→ ①`await ensureSaved()` 等该题保存**收敛**；②若 `saveError` → 提示"保存失败，请重试"、**不推进**；③`judgeExamAnswerApi` 判题 → 写 `correctMap`；④**再推进**。判题失败 → 提示、**不推进**（可重试）。连点由 `advancing` 兜底。
- **答题卡**（颜色约定按要求）：**蓝=答对、红=答错**；**中性灰=已答未判**；**橙=待保存/保存失败**；**当前题用蓝环**（不填充，保证正误底色可见）。每题同时显示 **✓/✗ 图标**（不只靠颜色）。
- 未作答保持白底。判题过的题切题/返回**颜色保留**；刷新/重进由 `correctMap` 恢复。
- 未改动：题目导航、单选互斥、多选逻辑、答案保存、倒计时、退出恢复、普通练习页。

### 13.5 文件变更

| 文件 | 类型 | 用途 |
|---|---|---|
| `driving-exam-pojo/.../vo/ExamJudgeVO.java` | **新增** | 判题结果 VO（questionId/answered/isCorrect；不含答案） |
| `driving-exam-pojo/.../dto/ExamJudgeDTO.java` | **新增** | 判题入参（questionId） |
| `driving-exam-pojo/.../vo/ExamVO.java` | 改 | 新增 `correctMap`（刷新恢复正误） |
| `driving-exam-server/.../exam/ExamService.java` | 改 | 新增 `judge(...)` 声明 |
| `driving-exam-server/.../exam/impl/ExamServiceImpl.java` | 改 | 注入 `AnswerGrader`；`judge` 实现；`buildExamVO` 填 `correctMap`（+`buildCorrectMap`） |
| `driving-exam-server/.../controller/ExamController.java` | 改 | 新增 `POST /{examId}/judge` |
| `frontend/src/api/exam.ts` | 改 | 新增 `judgeExamAnswerApi` |
| `frontend/src/types/index.ts` | 改 | `ExamVO.correctMap`；新增 `ExamJudgeVO` |
| `frontend/src/views/student/ExamAnswerView.vue` | 改 | 「下一题」保存→判题→推进；`correctMap` 状态；答题卡正误样式/图标 |

**未改动**：数据库/表结构/SQL、课程文案、练习模块、其他考试页、登录权限。

### 13.6 验证结果

| 命令 | 退出码 | 结果 |
|---|---|---|
| `npm run type-check`（`vue-tsc --noEmit`） | **0** | 通过 |
| `npm run build`（`vite build`） | **0** | 成功；产物含 `ExamAnswerView-*.js`（≈9.1 kB） |
| Maven 构建（后端） | **未执行** | **本环境无 `mvn`、无 `mvnw`** → 后端仅做**静态检查**（大括号/括号配平、关键点存在性）；**未编译** |

- **未做**：浏览器真实联调；对真实后端的接口调用。→ 前端仅"静态检查通过"；后端**未编译、未运行**。

### 13.7 未验证事项与风险

- **后端未编译**：需你在本机 `mvn install`（多模块）确认；新增注入 `AnswerGrader` 与构造器，Spring 装配应无问题，但**未经编译验证**。
- **未真实联调**：判题返回、答题卡变色、刷新恢复、失败重试均**未实测**。
- **边界**：最后一题无「下一题」→ 本阶段不判题（待交卷阶段覆盖）；保存失败时**不推进**（按需求）。
- **需求 8/9/10 回归**（超时/已结束不可答题、不泄露答案、恢复/倒计时/导航无回归）**未实测**。

### 13.8 浏览器验收步骤（人工）

1. 开始一场模拟考试（科一）；在**单选**题选一个（先记录哪个选项是对/错）。
2. 点「**下一题**」→ 答题卡该题**立即**：答对=**蓝**、答错=**红**（并显示 ✓/✗）。
3. 再答一题（可**多选/判断**），再「下一题」→ 对应正误色出现。
4. 点答题卡回**上一题** → 选项回显、答题卡正误色**一致**。
5. **刷新页面 / 退出后重进原考试** → 已保存答案与答题卡正误色**正确恢复**。
6. （可选）断网后点「下一题」→ 提示保存/判题失败、**不显示错误正误**、可重试。
7. 确认 UI 中**不出现标准答案/解析**。

---

## 十四、Day 20 · 第五阶段 D：回归修复——退出模拟考试后无法恢复原场

### 14.1 恢复链路只读追踪（代码证据）

- **无 Git 历史**（工作目录非 Git 仓库，全树无 `.git`）→ 改为对照**当前代码 + 本日志**。
- **退出**（`ExamAnswerView.exitExam`）：二次确认 → `stopTimer()` + `router.push(backTo())`；`backTo()` 用 `route.query.subjectCode` 拼 `/student/exam?subjectCode=…`。**不调任何后端接口、不改考试状态、不清答案/判题结果**。
- **列表**（`ExamView`）：`GET /papers`；「继续考试」判定 `paper.ongoingExamId != null`，且**直接 `router.push('/student/exam/{ongoingExamId}')`，不调用 `POST /start`**（第五阶段 B 的修复**仍在**）。
- **`ongoingExamId` 来源**：后端 `papers()` 取 `mock_exam` 中 `user_id=当前 AND status='ONGOING'` 的行按 `paperId→examId` 回填（**未被第五阶段 C 改动**）。
- **答题页加载**（`ExamAnswerView.load`）：`GET /exam/{examId}`（`resume`）→ 回填 `answers`/`savedAnswers`/`correctMap`；失败则 `loadError=true` → 显示"考试加载失败 + 重试/返回"（**不会**静默跳列表、**不会**新建考试）。
- **路由/布局**：`exam/:examId` 在 `StudentLayout` 子路由中、位置在 catch-all 之前，`meta.fullscreen` 隐藏侧边栏——**均未变**。

### 14.2 失败位置（第五阶段 C 的关联）

**第五阶段 C 在恢复路径上的唯一改动 = `buildExamVO` 新增 `buildCorrectMap`（逐题判分）。** 原来的实现**没有防护**：`AnswerGrader.isCorrect(...)` → `normalizeType(...)` 对**未知 / 空题型**会 `throw IllegalArgumentException`；该异常经 `buildExamVO` → `resume` → 控制器**冒泡为 500** → `GET /exam/{examId}` 整个失败 → 答题页显示"考试加载失败" → 用户看到"**无法回到考试**"。

→ **失败位置 = 进入原 `examId` 后、`GET /exam/{examId}` 因 `correctMap` 计算抛错而 500**（对应核查项 10 的第 3 种情况）。

> **诚实边界**：种子 36 题题型均为 `SINGLE/MULTIPLE/JUDGE`（已核 `seed-question.sql` + `seed-question-extra.sql`），**故此路径对"纯种子数据"不一定抛错**；本环境**无浏览器、无法查库**，**未能确证**用户命中的具体触发（是否库中存在脏题型，或其实是 20 分钟超时收尾）。两种可能见 §14.4。

### 14.3 最小修复（仅后端 1 文件）

`ExamServiceImpl.buildCorrectMap`：**逐题判分加 `try/catch(RuntimeException)`，异常时"跳过该题、不写入正误"**——**单题判分异常不得拖垮整个考试详情接口**。修复后即使个别题数据异常，`GET /exam/{examId}` 仍正常返回，考试可打开/答题/恢复；该题在答题卡上仅显示"已答未判"。
- 判分口径**未变**（仍复用 `AnswerGrader`）；未改状态机、未改 `start`/`submit`/`answer`；未新增接口或字段。

### 14.4 两种可能原因（需你运行数据区分）

| 可能 | 现象 | 证据取法 | 是否本次已修 |
|---|---|---|---|
| **A 判分异常致详情 500** | 点"继续考试"→ 页面"考试加载失败" | 浏览器 Network：`GET /api/student/exam/{id}` 状态码=**500**（响应含异常） | ✅ 已修（`buildCorrectMap` 加防护） |
| **B 考试已超时收尾** | 点"继续考试"→ 页面"本场考试已结束"；列表此前显示"进行中" | Network：`GET /exam/{id}` 返回 200 且 `data.status='TIMEOUT'` | ❌ 按现有规则（**暂停未实现**，超出本阶段范围） |

### 14.5 文件变更 / 后端与数据库

| 文件 | 类型 | 改动 |
|---|---|---|
| `driving-exam-server/.../exam/impl/ExamServiceImpl.java` | 改 | `buildCorrectMap` 逐题 `try/catch`：单题判分异常跳过、不阻断整场加载 |

**未改动**：前端（本次为纯后端修复）、数据库、SQL、表结构、`start`/`submit`/`answer`、练习模块。

### 14.6 验证结果

| 命令 | 退出码 | 结果 |
|---|---|---|
| `npm run type-check`（`vue-tsc --noEmit`） | **0** | 通过（前端未改，回归确认） |
| `npm run build`（`vite build`） | **0** | 成功 |
| Maven 构建（后端） | **未执行** | **本环境无 `mvn`、无 `mvnw`** → 仅**静态检查**（大括号/括号配平=0、`catch` 存在）；**未编译、未运行** |

- **未做**：后端编译、真实接口测试、浏览器联调。→ **不声称后端编译通过**。

### 14.7 未验证事项与风险

- **后端未编译**：请在本机 `mvn install` 确认（本改动仅一个方法体，风险低）。
- **触发未确证**：无法在本环境区分 A / B；若修复后**仍**无法恢复，请按 §14.4 取一次 Network 证据。
- **超时（B）不属本次范围**：暂停计时未实现（见 §11.5）；20 分钟内应为可恢复。

### 14.8 浏览器复测步骤（人工）

1. 开始一场**新**模拟考试；**记录 URL 中的 `examId`**。
2. 作答**至少一道题**，等显示"已保存"、答题卡出现正误色。
3. 点「**退出考试**」并确认。
4. 试卷列表应显示「**进行中**」+「**继续考试**」。
5. 点「**继续考试**」→ 核对 URL 的 `examId` **与第 1 步一致**。
6. 确认**已保存答案**与**答题卡正误色**均恢复。
7. **刷新页面**后再次确认恢复正常。
   - 若第 5 步显示"**考试加载失败**"→ 请抓 `GET /exam/{id}` 的**状态码与响应**（对应可能 A）。
   - 若显示"**本场考试已结束**"→ 是**超时**（对应可能 B）。

---

## 十五、Day 20 · 修复「模拟考试提交按钮无响应」

### 15.1 根因（代码证据）

- `frontend/src/views/student/ExamAnswerView.vue` 的答题区底部，交卷按钮是 **硬编码占位**：
  ```html
  <!-- 交卷：下一阶段接入（占位，不实现提交） -->
  <el-button disabled>交卷（下一阶段接入）</el-button>
  ```
  —— 它**没有 `@click`**、**永久 `disabled`**、**不是任何函数** → **点击无任何反应**。
- 前端**根本没有交卷封装**：`frontend/src/api/exam.ts` 只有 papers/start/history/{examId}/answer/judge，交卷仅在注释里登记为"未接入"。
- 后端交卷接口**已存在且可用**：`POST /api/student/exam/{examId}/submit`（`ExamController.submit` → `ExamServiceImpl.submit` → `ExamFinalizer.finalize`），`ExamSubmitDTO.answers` 可空（空则读 `mock_exam_answer` 草稿），**幂等**，**无「必须全部作答」前置**。

→ 结论：**"提交按钮无响应"= 该按钮是未接线的禁用占位，与后端/考试状态无关**（非超时、非状态非法）。

### 15.2 点击后实际执行到哪一步

**执行到第 0 步即止**：原生 `disabled` 的 `el-button` 不触发 `click`，事件根本没派发 → 无确认弹窗、无请求、无任何状态变化。

### 15.3 修改文件与改动

| 文件 | 类型 | 改动 |
|---|---|---|
| `frontend/src/api/exam.ts` | 改 | 新增 `submitExamApi(examId)`（`POST /{examId}/submit`，body 空 `{}` → 后端读草稿）；注释同步（交卷已接入） |
| `frontend/src/types/index.ts` | 改 | 新增 `ExamReportVO` + `ExamReportItemVO`（与后端 VO 字段一致） |
| `frontend/src/views/student/ExamAnswerView.vue` | 改 | 交卷按钮**接线**：`:loading="submitting"`、`:disabled="submitting || advancing"`、`@click="submitExam"`；新增 `submitting` 与 `submitExam()` |

`submitExam()`：二次确认（`ElMessageBox.confirm`）→ 置 `submitting`（防重复）→ `submitExamApi(examId)` → 成功：**停止计时 + 提示得分/是否及格 + 切入页面既有的「已结束」状态**（`exam.status='SUBMITTED'`，复用现成 UI，不新建报告页）→ 失败：拦截器提示、`submitting=false` 恢复可点。未改评分规则、状态定义、接口契约。

### 15.4 是否改后端 / 数据库 / SQL

**均未改。** 纯前端 3 个文件（已核 mtime：近 20 分钟无 `.java`/`.sql` 改动）。

### 15.5 构建与测试结果

| 命令 | 退出码 | 结果 |
|---|---|---|
| `npm run type-check`（`vue-tsc --noEmit`） | **0** | 通过 |
| `npm run build`（`vite build`） | **0** | 成功；产物含 `ExamAnswerView-*.js`（≈9.5 kB） |
| 后端 Maven | **未执行** | 本环境无 `mvn`/`mvnw`；**本轮未改后端，无需编译** |

- **未做**：浏览器联调；真实接口调用。

### 15.6 待你在浏览器验证 / 复测步骤

1. 打开 `http://localhost:5173/student/exam/8?subjectCode=SUBJECT_1`（后端已启动、`examId=8` 为进行中）。
2. 点「**交卷**」→ 应**弹出确认框**（此前无反应）。
3. 确认后：提示"已交卷：得分 X/Y，及格/未及格"，页面切换为"**本场考试已结束/考试已提交**"态。
4. 再次进入同一 `examId`（或刷新）→ 因已交卷，显示"已结束"（幂等，不报错）。
5. （可选）在**已结束**的考试页上，交卷按钮不应出现；点「返回」可回列表，列表该场不再显示"继续考试"。
6. 若第 2 步仍无弹窗 → 抓浏览器控制台报错与 Network 是否有 `POST /api/student/exam/8/submit`。

---

## 十六、Day 20 · 交卷后展示总成绩（成绩结算区）

### 16.1 后端已提供的成绩字段（复用，未重算）

`ExamReportVO`（`POST /{examId}/submit` 与 `GET /{examId}/report` 均返回）：
`examId` · `score` · `totalScore` · `passScore` · `isPass` · `correctCount` · `wrongCount` · `unansweredCount` · `items[]`。
得分由 `ExamFinalizer` 按**试卷每题分值**累加（`answer_result`/`score`）——**不是题数、也不是固定每题 1 分**；及格判定 `score >= passScore`（`passScore` 开考时从试卷复制）。**展示所需字段后端已全，故本轮不改后端。**

### 16.2 本轮改动

- **`ExamAnswerView.vue`**：把"已结束（`!ongoing`）"的占位 `el-result` 替换为**成绩结算区**（页面主体持续显示）——
  - 大字 **`{{score}} / {{totalScore}} 分`**（超大得分 + 分母满分）；
  - **及格/未及格** `el-tag`（依 `isPass`）；
  - **正确 / 错误 / 未答 / 及格线**（`correctCount`/`wrongCount`/`unansweredCount`/`passScore`）；
  - **返回考试列表**按钮；
  - 三态：`reportLoading`（`el-skeleton`）/ `reportError`（错误提示 + **重新加载** + 返回）/ 正常。
- **交卷成功后**：直接用**交卷响应**作为成绩（`report.value = rpt`）+ 切 `exam.status='SUBMITTED'` → 结算区立即显示（不再只靠 `ElMessage`）。
- **刷新 / 直接打开已交卷考试**：`load()` 发现 `status !== 'ONGOING'` → 调 **`GET /{examId}/report`** 重新取成绩（`loadReport`）→ 结算区显示；**不新建考试、不回到答题、不把已交卷当进行中**。
- **`api/exam.ts`**：新增 `getExamReportApi(examId)`（`GET /{examId}/report`）。**`types/index.ts` 未改**（`ExamReportVO` 上一阶段已加）。

### 16.3 文件变更 / 后端与数据库

| 文件 | 类型 | 改动 |
|---|---|---|
| `frontend/src/api/exam.ts` | 改 | 新增 `getExamReportApi(examId)`（刷新成绩页用） |
| `frontend/src/views/student/ExamAnswerView.vue` | 改 | 成绩结算区；交卷成功写入成绩；`load()` 对已结束考试 `loadReport()` |

**未改**：后端 Java、数据库、SQL、表结构、评分规则、其他模块。

### 16.4 构建与测试结果

| 命令 | 退出码 | 结果 |
|---|---|---|
| `npm run type-check`（`vue-tsc --noEmit`） | **0** | 通过 |
| `npm run build`（`vite build`） | **0** | 成功；产物含 `ExamAnswerView-*.js`（≈11.2 kB） |
| 后端 Maven | **未执行** | 本环境无 `mvn`/`mvnw`；**本轮未改后端** |

- **未做**：浏览器联调 / 真实接口调用。

### 16.5 待你在浏览器验证

1. 进行中考试 → 点「交卷」→ 确认 → 页面**主体**出现成绩结算：**大字 `得分 / 满分`** + **及格/未及格** + 正确/错误/未答/及格线。
2. **刷新**该成绩页（`/student/exam/{id}`）→ 成绩**仍在**（经 `GET /{id}/report` 重新取，非丢失、非回到答题）。
3. 点「返回考试列表」→ 回到试卷列表；该场不再显示"继续考试"。
4. （异常）若把后端报告接口临时打挂 → 结算区显示"成绩加载失败 + 重新加载"，**不会**变成"进行中"或新建考试。
