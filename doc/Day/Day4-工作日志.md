# 【Day 4 工作日志】

## 一、日期

2026-10-02

## 二、今日目标

完成后端用户模块的完整闭环：实体 → Mapper → Service → Controller，打通登录（JWT 签发/校验）、个人中心，并补上接口级角色鉴权与统一异常处理。

## 三、完成的工作

**数据库设计：** 无（沿用 Day 1 的 `sys_user` 表结构）。

**后端：**

- **实体层（pojo 模块）**
  - `entity/User`：严格对齐 `sys_user`（11 字段），`@TableName("sys_user")`、`@TableId(type = IdType.AUTO)`、`@TableLogic`（`deleted`）、`@TableField(fill = ...)`（`createTime` INSERT、`updateTime` INSERT_UPDATE）。
  - `dto/LoginDTO`（username/password/role，均 `@NotBlank`）、`dto/ProfileUpdateDTO`（realName/phone/avatar）、`dto/PasswordUpdateDTO`（oldPwd/newPwd）。
  - `vo/UserVO`（`id/username/realName/phone/avatar/role/status`，**不含 password**）、`vo/LoginVO`（token/userId/username/realName/role）。
- **配置层（server 模块）**
  - `config/MyMetaObjectHandler`：实现 `MetaObjectHandler`，`insertFill` 填 `createTime`+`updateTime`，`updateFill` 填 `updateTime`，使用 `strictInsertFill` / `strictUpdateFill`（仅字段为 null 且声明了 fill 时才填）。
  - `config/SecurityConfig`：提供 `BCryptPasswordEncoder` Bean（仅引入 `spring-security-crypto`，**未引入完整 Spring Security**）。
  - `config/PathRoleRule`：路径 → 角色规则表（AntPathMatcher）。
- **持久层**
  - `mapper/UserMapper extends BaseMapper<User>`（`@Mapper`）。
  - `service/UserService extends IService<User>`；`service/impl/UserServiceImpl extends ServiceImpl<UserMapper, User>`（`@Service`）。
- **工具**
  - `utils/JwtUtil`：jjwt 0.12.5，`generateToken(userId, username, role)` / `parseToken(token)` / `validateToken(token)`，密钥与有效期读 `application.yml`。
- **Web 层**
  - `interceptor/JwtInterceptor`：解析 `Authorization: Bearer <token>` → `validateToken` → 按 `PathRoleRule` 校验角色 → 写入 `userId/username/role` 属性；失败返回 401 / 403 + `ApiResponse` JSON；放行 OPTIONS。
  - `config/WebMvcConfig`：注册拦截器，拦 `/api/**`，放行 `/api/auth/login`、`/error`。（本系统采用**邀请制**，不做自助注册：学员由教练在"学员管理"创建、教练由管理员在"用户管理"创建、管理员由系统预置；学员首次登录后可用 `PUT /api/auth/password` 自助改密。）
  - `controller/AuthController`（`/api/auth`）：`POST /login`、`GET /me`、`PUT /profile`、`PUT /password`。
- **接口级鉴权**：规则表 `/api/auth/**`、`/api/common/**` 全员放行；`/api/student/**`→STUDENT、`/api/coach/**`→COACH、`/api/admin/**`→ADMIN；角色不匹配返回 403。
- **统一 404**：全局异常处理器新增 `NoResourceFoundException` 分支 → HTTP 404 + `ApiResponse` 格式。

**前端：** 无（本日聚焦后端）。

**文档/设计：**

- 登录闭环数据流：`LoginDTO → 校验(密码/角色/状态) → JwtUtil.generateToken → LoginVO`。
- 鉴权链路：`JwtInterceptor`（登录）→ `PathRoleRule`（角色）→ Controller（业务）。

## 四、关键技术决策

| 决策点 | 方案 |
|---|---|
| 实体映射 | MyBatis-Plus 注解；`@TableLogic` 只加主数据表 |
| 自动填充 | `MetaObjectHandler` + `strictXxxFill`，仅 null 时填充 |
| 密码存储 | BCrypt（`spring-security-crypto`，非完整 Spring Security） |
| 认证 | JWT 无状态，`Authorization: Bearer <token>` |
| token 载荷 | `subject=userId`，自定义 `username`、`role` |
| 登录校验顺序 | 密码(1101) → 角色(1106) → 禁用(1102)（账号枚举防护） |
| 接口鉴权 | 路径前缀规则表（可扩展），403 拦截 |
| 404 处理 | 统一为 `ApiResponse` + HTTP 404 |
| 技术版本 | Spring Boot 3.2.5、MyBatis-Plus 3.5.7、Spring Security Crypto 6.2.4、jjwt 0.12.5、JDK 17 |

## 五、遇到的问题及解决方案

**问题 1**

- 现象：`AuthController` 用到 `BCryptPasswordEncoder` 时无法编译，类不存在。
- 原因：工程缺少 `spring-security-crypto` 依赖——`BCryptPasswordEncoder` 并不在 Spring Boot 默认依赖里。
- 解决：在 `driving-exam-server` 的 pom 中引入 `spring-security-crypto`（版本由父工程管理，6.2.4）；**刻意不引入 `spring-boot-starter-security`**，否则会自动拦截全部接口。

**问题 2**

- 现象：登录接口始终返回「用户名或密码错误」，即便账号密码正确。
- 原因：库中 `sys_user.password` 存的是明文/非 BCrypt 密文，而 `passwordEncoder.matches(明文, 库中值)` 要求库中为 BCrypt 密文。
- 解决：将种子/测试账号密码替换为 BCrypt 密文（示例：`<明文示例>` → `<BCrypt 密文，省略>`），并明确"新增用户必须走 `passwordEncoder.encode()`"的规范。

**问题 3**

- 现象：用"学员账号 + ADMIN 角色"登录返回 1101，无法判断究竟是"角色不对"还是"密码错/账号不存在"。
- 原因：登录时把三种失败原因统一压成同一个 1101（账号枚举防护），导致**测试存在盲区**，演示时也无法直观证明越权被拦截。
- 解决：新增错误码 `ROLE_MISMATCH(1106, "该账号与所选角色不匹配")`，角色不匹配单独返回 1106；对照测试（同账号密码、仅换角色 → 1106 vs 200）即可证明角色校验生效。

**问题 4**

- 现象：未实现的接口路径（如 `/api/student/subjects`）被兜底异常分支捕获，返回 `code=500`「系统异常」并打印 ERROR 堆栈。
- 原因：Spring Boot 3.2 静态资源未命中时抛出 `NoResourceFoundException`，被全局处理器的 `Exception` 兜底分支接住。
- 解决：在全局异常处理器新增 `NoResourceFoundException` 专属分支，返回 **HTTP 404 + `ApiResponse`（1002「请求的资源不存在」）**，日志降为 WARN、不打堆栈。

**问题 5**

- 现象：学员 token 可访问 `/api/coach/**`、`/api/admin/**` 等非本端接口。
- 原因：拦截器此前只校验"是否登录"，不校验角色，属于真实的越权风险。
- 解决：新增 `PathRoleRule` 路径→角色规则表，拦截器中按路径前缀校验角色，不匹配返回 403 + `ApiResponse`。验证：学员 token 访问 `/api/coach/students` → 403。

**问题 6**

- 现象：`@TableLogic` 未在 `application.yml` 全局开启，担心逻辑删除不生效。
- 原因：逻辑删除可全局配置，也可由实体注解声明。
- 解决：采用实体 `@TableLogic` 显式声明（只加 5 张主数据表），依赖 MyBatis-Plus 默认约定（删除值 1、未删值 0），与 `sys_user.deleted` 语义一致，无需额外配置。

**问题 7**

- 现象：在 PowerShell 执行 `mvn clean install` 报错「无效的标记: --release」。
- 原因：系统 `JAVA_HOME` 指向 **JDK 8**，而项目要求 **JDK 17**；JDK 8 的 `javac` 不支持 `--release` 参数。
- 解决：改用 IDEA 的 Maven 面板执行（IDEA 使用项目配置的 **JDK 22**，Language Level 17），或临时切换 `JAVA_HOME`。

## 六、产出物清单

- pojo：`User`、`LoginDTO`、`ProfileUpdateDTO`、`PasswordUpdateDTO`、`UserVO`、`LoginVO`
- server：`MyMetaObjectHandler`、`SecurityConfig`、`PathRoleRule`、`UserMapper`、`UserService`、`UserServiceImpl`、`JwtUtil`、`JwtInterceptor`、`WebMvcConfig`、`AuthController`
- common：`ErrorCode` 新增 `ROLE_MISMATCH(1106)`；`GlobalExceptionHandler` 新增 `NoResourceFoundException` 分支
- server pom：新增 `spring-security-crypto` 依赖
- Postman/Apifox 验证记录（登录 200、角色不匹配 1106、无 token 401、越权 403、未实现接口 404）

## 七、遗留问题与明日计划

- 遗留：前端登录页仍为占位；管理员端用户管理 CRUD 尚未实现；系统尚无 `/api/student`、`/api/coach`、`/api/admin` 真实业务接口。
- 明日计划（Day 5）：前端登录对接——`Login.vue` 改调真实 `POST /api/auth/login`，`request.ts` 存真实 token，路由守卫按真实登录态判断。
