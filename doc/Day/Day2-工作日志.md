# 【Day 2 工作日志】

## 一、日期

2026-10-01

## 二、今日目标

在后端编码前把"契约"钉死：统一返回格式定稿、全量 REST API 接口清单定稿、并生成可运行的 Spring Boot 后端骨架。

## 三、完成的工作

**数据库设计：** 无（Day 1 已定稿；本日仅校对 15 张表与实体字段的映射关系）。

**后端：**

- **Step 2.1 统一返回格式定稿**
  - `ApiResponse<T>`：**仅 3 字段** `code` / `message` / `data`（明确去掉 `timestamp`），`data` 为空时保留 `null`、不使用 `@JsonInclude` 剔除。
  - `PageResult<T>`：`total` / `pages` / `pageNum` / `pageSize` / `records`，并提供 `of(IPage)` 与 `of(IPage, 转换函数)` 两个静态构造，与 MyBatis-Plus 分页桥接。
  - HTTP 状态码策略：**业务失败一律 HTTP 200 + body code**；**仅 401 / 403 返回真实 HTTP 状态码**，供前端 Axios 拦截器自动清 token 跳登录。
  - 错误码落地形式：`ErrorCode` 枚举 + `BusinessException` + 全局异常处理器；**通用段只保留 200 / 401 / 403 / 500**，其余全部归入业务段，两段不混用。
- **Step 2.2 API 接口清单定稿**：按角色分前缀 `/api/auth`、`/api/student`、`/api/coach`、`/api/admin`，覆盖认证、科目进度、刷题、错题本、模拟考试、学情分析、AI 问答、预约、聊天、教练端、管理员端全部接口，落盘为接口设计文档。
- **Step 2.3 Spring Boot 后端骨架生成**
  - 采用 **Maven 多模块聚合工程**：父工程 `driving-exam`（packaging=pom）+ 三个子模块：
    - `driving-exam-common`（`com.driving.exam.common`）：统一返回与异常体系。
    - `driving-exam-pojo`（`com.driving.exam.pojo`）：entity / dto / vo。
    - `driving-exam-server`（`com.driving.exam.server`）：config / controller / mapper / service / 启动类。
  - 父 pom 通过 `dependencyManagement` 统一管理 Spring Boot、MyBatis-Plus、JWT 等依赖版本；子模块引入时不写 version。
  - 生成启动类 `DrivingExamApplication`、统一返回体、异常体系、`application.yml`。

**前端：** 无。

**文档/设计：**

- `doc/api-design.md`：API 接口设计文档（含通用约定、统一返回结构、错误码规范、全量接口清单、关键业务边界）。
- 关键接口边界在文档中固化：题目 VO 不返回 `answer` / `analysis`；模拟考试支持断点续考；聊天用 `lastId` 增量、前端 3~5 秒轮询、无新消息返回空数组；教练创建学员入参 `username/realName/phone`、后端生成默认密码；取消预约仅 `PENDING` 可取消。

## 四、关键技术决策

| 决策点 | 方案 |
|---|---|
| 统一返回 | `ApiResponse<T>`：code / message / data（无 timestamp） |
| 分页返回 | `PageResult<T>`：total / pages / pageNum / pageSize / records |
| HTTP 状态 | 业务失败 HTTP 200 + body code；仅 401/403 用真实状态码 |
| 错误码 | 通用段 4 个（200/401/403/500）+ 业务段分段（1xxx 通用与用户、2xxx 题库考试、3xxx 预约关系、4xxx AI、5xxx 聊天） |
| 工程结构 | Maven 多模块：common / pojo / server |
| 依赖管理 | 父 pom `dependencyManagement` 统一版本 |
| 接口风格 | 按角色分前缀 `/api/{auth|student|coach|admin}` |
| 技术版本 | Spring Boot 3.2.5、MyBatis-Plus 3.5.7、MySQL 8.0、JDK 17（Language Level 17） |

## 五、遇到的问题及解决方案

**问题 1**

- 现象：多模块拆完后，若父 pom 里声明 `spring-boot-maven-plugin`，`common`、`pojo` 会被打成可执行 jar。
- 原因：父 pom 的 `<build><plugins>` 会被所有子模块继承，导致非启动模块也执行 `repackage`。
- 解决：**`spring-boot-maven-plugin` 只在 `driving-exam-server` 声明**，父工程不声明，common / pojo 只作为普通依赖 jar。

**问题 2**

- 现象：启动类迁到 `com.driving.exam.server` 后，`common` 模块里的全局异常处理器（`@RestControllerAdvice`）扫描不到。
- 原因：`@SpringBootApplication` 默认只扫描启动类所在包及其子包，即 `com.driving.exam.server.**`。
- 解决：启动类改为 `@SpringBootApplication(scanBasePackages = "com.driving.exam")`，覆盖 common / pojo / server；同时 `@MapperScan` 指向 `com.driving.exam.server.mapper`。

**问题 3**

- 现象：重构后实体包路径变化，`application.yml` 的 `type-aliases-package` 仍指向旧包，MyBatis 别名失效。
- 原因：`common` 拆出后实体归入 `pojo` 模块，包名从 `com.driving.exam.entity` 变为 `com.driving.exam.pojo.entity`。
- 解决：将 `type-aliases-package` 更新为 `com.driving.exam.pojo.entity`，并同步 `mapper-locations` 到 server 模块的 `classpath*:mapper/**/*.xml`。

**问题 4**

- 现象：重构需删除旧的单模块 `backend` 目录，删除时报 `Operation not permitted`。
- 原因：工作目录受沙箱权限限制，默认不允许删除文件。
- 解决：开启该工作目录的文件删除权限后完成清理；旧结构由新的 `driving-exam` 多模块工程完全取代。

## 六、产出物清单

- `doc/api-design.md`（API 接口设计文档，已定稿）
- 多模块工程 `driving-exam/`：父 `pom.xml` + 三个子模块 `pom.xml`
- `driving-exam-common`：`ApiResponse`、`PageResult`、`ErrorCode`、`BusinessException`、`GlobalExceptionHandler`
- `driving-exam-server`：`DrivingExamApplication`、`application.yml`
- 目录骨架：config / controller / entity / mapper / service(/impl) / dto / vo（后续填充）

## 七、遗留问题与明日计划

- 遗留：`application.yml` 的数据源为占位配置，待补真实连接信息（后已填 `localhost:3306/driving_exam`，root）。
- 明日计划（Day 3）：生成 Vue3 前端骨架（Vite + Vue3 + TypeScript + Element Plus + Axios + Pinia + Vue Router），封装请求工具与路由，产出登录页与三端布局占位。
