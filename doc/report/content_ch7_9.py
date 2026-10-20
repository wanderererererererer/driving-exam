from docx.shared import Cm


def build(ctx):
    # ---------------- Chapter 7 ----------------
    ctx.h('7  系统测试', 1)
    ctx.h('7.1  测试策略与证据分级', 2)
    ctx.body('本项目的测试证据分为五级。A 级是当前代码的自动化测试执行记录，有 Surefire 报告和具体批次；B 级是历史 Apifox 接口测试报告，能证明某个时间点发生过真实 HTTP 调用，但不能代表当前版本；C 级是前端类型检查和构建，只证明类型与打包通过；D 级是仅编写或仅存在于源码、尚未在当前环境执行的测试用例；E 级是需要 Docker、真实 MySQL、浏览器或外部 AI 服务的运行验收。报告在引用任何结果时都标注其级别，不把不同时间、不同环境的数字简单相加后称为“全部通过”。')
    ctx.body('测试方法包括：JUnit 5 + Mockito 的单元/服务测试；Apifox 集合的真实 HTTP 历史测试；`vue-tsc --noEmit` 类型检查；Vite 生产构建；以及需要独立环境的集成测试。单元测试主要验证业务规则、状态转换、锁顺序和错误码；接口测试验证登录、参数和响应；集成测试验证真实数据库、Qdrant 和外部模型，但当前多数集成测试因环境缺失未完成。')

    ctx.h('7.2  自动化测试清单', 2)
    ctx.body('源码中共有 45 个测试类、300 个 `@Test` 方法；其中 `AnswerGraderTest` 另有 5 个参数化方法。按源码声明方法计为 305 个，参数展开后 Surefire 执行 328 次。表 7-1 按包统计类数和 `@Test` 数。')
    ctx.table(['包/范围', '测试类数', '@Test 数', '主要内容'], [
        ['admin', '4', '18', '用户状态、题目校验、Excel 导入、试卷一致性'],
        ['ai', '3', '16', 'Prompt、上下文预算、AI 会话服务'],
        ['analysis', '1', '10', '掌握度、衰减、短板判定'],
        ['appointment', '1', '7', '锁顺序、冲突、状态流转、禁用教练'],
        ['chat', '3', '33', '会话、游标、未读、Controller 契约、DDL 映射'],
        ['coach', '6', '32', '学员列表、学习数据、错题、AI 建议/确认'],
        ['controller', '3', '8', '教练端 Controller 身份透传与参数'],
        ['exam', '3', '12', '开始/恢复、交卷、重复交卷、超时、结算'],
        ['grader', '1', '13', '单选/多选/判断、NFKC、脏输入；5 个参数化'],
        ['integration', '10', '47', '数据库守卫、并发、DeepSeek/Embedding/Qdrant Mock 与真实 IT'],
        ['interceptor', '1', '6', 'JWT 缺失/无效/禁用/角色变更/跨角色'],
        ['rag', '9', '98', '分块、装配、入库、检索、向量转换、开发集合守卫'],
        ['合计', '45', '300', '参数展开后 328 次执行'],
    ], widths=[3.4, 2.2, 2.2, 9.8], font_size=8.5, cap='表7-1  自动化测试类与 @Test 统计')

    ctx.h('7.3  执行批次与真实结果', 2)
    ctx.body('现有 Surefire 报告不是一次完整运行，而是 2026-10-19 至 2026-10-20 的多个批次。表 7-2 按报告文件时间汇总。把 8 个批次机械相加得到 328 项，其中通过 316、失败 1、错误 2、跳过 9；这个总数只表示“现有报告的合计”，不表示 45 个类在同一批次全部通过。')
    ctx.table(['批次时间', '类数', 'tests', 'failures', 'errors', 'skipped', '说明'], [
        ['2026-10-19 14:44', '11', '69', '1', '0', '6', '含部分 AI/集成测试'],
        ['2026-10-19 14:46', '3', '19', '0', '0', '3', '部分跳过'],
        ['2026-10-19 14:50', '1', '1', '0', '1', '0', '数据库守卫上下文加载失败'],
        ['2026-10-19 14:51', '1', '1', '0', '1', '0', '并发测试上下文加载失败'],
        ['2026-10-19 16:57', '3', '8', '0', '0', '0', 'Controller 等'],
        ['2026-10-20 14:01', '7', '81', '0', '0', '0', '定向/扩展测试'],
        ['2026-10-20 14:17', '1', '41', '0', '0', '0', 'AnswerGrader 参数化'],
        ['2026-10-20 14:54', '18', '108', '0', '0', '0', '与 Day34 日志一致'],
    ], widths=[3.4, 1.6, 1.8, 2.0, 1.8, 1.8, 4.5], font_size=8.8, cap='表7-2  Surefire 执行批次（非一次完整运行）')
    ctx.body('2026-10-20 的三个批次共 230 项全部通过，其中 108 项与 Day34 工作日志的“108 tests, 0 failures/errors/skips”一致。2026-10-19 的批次共 98 项，包含 1 个失败、2 个错误和 9 个跳过；失败和错误的性质见 7.7 节，不能简单归因于业务代码。报告不会把 2026-10-19 的失败隐藏，也不会把 2026-10-20 的部分批次说成“全量 328 项通过”。')

    ctx.h('7.4  代表性测试用例', 2)
    ctx.body('表 7-3 选取对业务一致性影响最大的用例。表中“实际结果”只填写现有证据能支持的内容；“当前版本复验”列说明发布前是否仍需回归。')
    ctx.table(['编号', '测试目标', '前置/输入', '预期结果', '实际结果与证据'], [
        ['TC-01', '缺失 token 访问受保护接口', '无 Authorization 头', 'HTTP 401 + code 401', '通过；JwtInterceptorTest（2026-10-20 批次）'],
        ['TC-02', '禁用账号的旧 token', '账号 status=0，token 未过期', 'HTTP 401 + code 1102', '通过；JwtInterceptorTest'],
        ['TC-03', '角色变更后的旧 token', 'token role 与库中 role 不同', 'HTTP 401 + code 1106', '通过；JwtInterceptorTest'],
        ['TC-04', '跨角色访问', 'STUDENT token 访问 /api/coach/**', 'HTTP 403 + code 403', '通过；JwtInterceptorTest'],
        ['TC-05', '已有未超时考试再次开始', '同一学员/科目存在 ONGOING', '返回原考试，不重复创建', '通过；ExamServiceImplTest'],
        ['TC-06', '重复交卷', '同一考试连续提交', '第二次返回既有报告，不重复结算', '通过；ExamFinalizerTest/ExamServiceImplTest'],
        ['TC-07', '超时考试结算', 'start_time+duration 已过期', '状态转 TIMEOUT，写入成绩/进度', '通过；ExamTimeoutSchedulerTest'],
        ['TC-08', '预约时段冲突', '同教练/日期/时段已有 PENDING', '返回 3002，不插入', '通过；AppointmentServiceImplTest'],
        ['TC-09', '预约创建锁顺序', '并发创建同一教练预约', '先锁教练行，再查冲突/插入', '通过单元测试；真实 MySQL 并发未验证'],
        ['TC-10', '预约非法状态', 'CANCELLED 再审批/完成', '返回 3004/3005', '通过；AppointmentServiceImplTest'],
        ['TC-11', '聊天游标非法', 'lastId 与 beforeId 同时传', '返回 1001', '通过；ChatServiceImplTest'],
        ['TC-12', '聊天发送与已读', '有效绑定；发送后读取', '未读+1；已读后本方 unread=0', '通过；ChatServiceImplTest'],
        ['TC-13', 'Excel 正常导入', '模板正确、行语义合法', '全部写入，success=total', '通过；AdminQuestionExcelServiceTest'],
        ['TC-14', 'Excel 行错误原子性', '某行答案/选项非法', 'success=0，整批不写入，返回 row/field', '通过；AdminQuestionExcelServiceTest'],
        ['TC-15', 'Excel 重复题干', '文件内或库内已存在', '拒绝整批，success=0', '通过；AdminQuestionExcelServiceTest'],
        ['TC-16', 'RAG 上下文预算', '超长检索片段', '上下文不超过 1200 字符，截断有标记', '通过；RetrievalContextBuilderTest'],
        ['TC-17', 'RAG 开发集合守卫', '集合名不等于 driving_exam_kb_dev', '拒绝入库', '通过；KbDevIngestGuardTest'],
        ['TC-18', '真实 Qdrant 集成', '需 Qdrant + SILICONFLOW_API_KEY', '检索返回真实结果', '失败：Key 未设置（2026-10-19）'],
    ], widths=[1.4, 3.4, 4.2, 4.0, 4.5], font_size=8.0, cap='表7-3  代表性测试用例与证据')
    ctx.body('TC-09 的“通过单元测试”只说明代码按预期调用了锁方法、冲突检查和插入顺序，不代表真实 MySQL 高并发下已经通过压力测试。TC-18 的失败是环境缺少 API Key，不是断言失败；报告把它列为“未验证”，而不是“功能失败”。')

    ctx.h('7.5  历史 Apifox 接口测试', 2)
    ctx.body('`doc/Test/` 下有 16 份 Apifox HTML 报告，日期为 2026-10-03 至 2026-10-19。它们能证明某些接口在报告生成时发生过真实 HTTP 调用，但早于 Day30–Day38 的认证、考试、预约、管理员和前端修改。表 7-4 给出各模块最新或代表性结果。')
    ctx.table(['模块', '报告时间', '请求/失败', '断言/失败', '当前判断'], [
        ['刷题', '2026-10-03；2026-10-08', '24/1；24/8', '100/0；100/9', '有历史证据；后续复跑有断言失败，需按当前代码重跑'],
        ['错题本', '2026-10-03', '22/1', '85/0', '有历史证据'],
        ['学情分析', '2026-10-07', '11/1', '54/0', '有历史证据'],
        ['模拟考试', '2026-10-08', '21/1', '83/0', '有历史证据；早于 Day31 报告/超时修改'],
        ['预约', '2026-10-09', '42/2', '118/0', '有历史证据；早于 Day32 锁修改'],
        ['教练学员', '2026-10-13', '49/3', '133/0', '有历史证据；不含 Day28 新增接口'],
        ['聊天', '2026-10-19', '47/7', '147/0', '有历史证据；请求失败性质待按 401/403 核对'],
        ['AI/管理员/Dashboard', '无', '—', '—', '未验证；不能引用其他报告代替'],
    ], widths=[3.0, 4.2, 3.4, 3.4, 5.3], font_size=8.4, cap='表7-4  历史 Apifox 报告摘要')
    ctx.body('历史报告中出现过两次异常低通过率（预约 21.43%、聊天 0%），随后同日的复跑恢复到 85%–90%；原因可能是凭证、环境或请求顺序，现有资料无法完全确认，报告标记为“待核实”。`doc/testjson/` 下的 8 份 Postman 集合合计 232 请求/779 断言，但它们是静态集合计数，不是执行结果，不能当作通过证据。')

    ctx.h('7.6  前端检查与构建', 2)
    ctx.body('前端没有自动化测试框架，`package.json` 没有 test 脚本，`src` 下没有 `*.test.*`/`*.spec.*`。当前验证手段是 `vue-tsc --noEmit` 类型检查和 `vite build` 生产构建。2026-10-20 的 Day38 工作日志记录两项均 exit 0，当前 `dist/index.html` 引用 `assets/index-CI9I_tjy.js`，与日志入口一致；构建只有 chunk 体积提示，没有类型或编译错误。')
    ctx.body('这组证据只能证明路由组件能被解析、类型检查通过、生产包能生成，不能证明浏览器页面、接口请求、响应式布局和交互全部正常。报告把它列为 C 级证据，并明确“不能把 type-check/build 写成浏览器验收通过”。')

    ctx.h('7.7  未执行、失败与跳过项分析', 2)
    ctx.table(['项目', '状态', '原因/影响'], [
        ['Docker 当前镜像验收', '未执行', '当前环境无 Docker CLI/socket；无法确认镜像包含最新代码'],
        ['真实 MySQL 并发预约/考试', '未验证', 'ExamSettlementConcurrencyTest 上下文加载失败；未见真实并发结果'],
        ['真实 MySQL Excel 导入/回滚', '未执行', '需要真实数据库和 multipart 环境；当前只有单元测试'],
        ['Qdrant/Embedding/DeepSeek 真实链路', '未验证', 'R24QdrantIntegrationIT 因 SILICONFLOW_API_KEY 未设置失败；DevKbIngestIT/R3/R4 跳过'],
        ['浏览器三角色验收', '未执行', '无浏览器环境；工作台、表格、弹窗、考试页未做像素和 Network 验收'],
        ['响应式与长文本', '未执行', '1366/1440/1920 宽度、长用户名、长题干未实测'],
    ], widths=[5.4, 2.6, 8.6], font_size=8.6, cap='表7-5  未执行与未验证项目')
    ctx.body('两个错误的性质需要单独说明。`DbConnectionGuardTest` 与 `ExamSettlementConcurrencyTest` 均在 Spring 上下文加载阶段失败，原因是 DeepSeek 配置属性绑定，而不是 MySQL 断言失败；因此不能用它们证明数据库并发有问题，也不能证明并发已经通过。`R24QdrantIntegrationIT` 的失败是缺少 `SILICONFLOW_API_KEY`；`SystemPromptBuilderTest`、`DeepSeekClientTest` 和三个 RAG IT 的跳过是类级 `@Disabled` 或 `RAG_DEV_INGEST` 未开启。')

    ctx.h('7.8  缺陷与风险分析', 2)
    ctx.body('现有测试暴露的主要风险包括：认证只覆盖单元/服务层，未做真实 HTTP 三角色全链路；考试和预约的并发测试未在真实 MySQL 完成；Excel 导入只有单元测试，未验证真实 multipart 和事务回滚；RAG 真实链路未打通；前端没有端到端测试。另有历史数据风险：如果题目已被逻辑删除或标准答案被改写，已有考试报告不会自动修复，Day31 的冻结规则只能防止新增修改，不能修复旧数据。')
    ctx.body('这些风险的影响不同。认证和业务规则已有较多单元测试，风险相对可控；真实数据库并发和外部 AI 链路风险最高，因为它们直接决定“设计是否在真实环境成立”。报告在第 8 章把这些风险与设计措施对应起来，不把单元测试通过当成生产可靠性证明。')

    ctx.h('7.9  测试章节小结', 2)
    ctx.body('本章的证据可以支持三个结论：第一，源码层面已有较完整的自动化测试，覆盖认证、考试、预约、聊天、教练、管理员、Excel 和 RAG 组件；第二，2026-10-20 的 108 项批次和 230 项当日批次有明确通过记录；第三，真实 Docker、MySQL 并发、浏览器和外部 AI 服务仍未验收。任何超出这三个结论的表述都缺乏证据。')

    # ---------------- Chapter 8 ----------------
    ctx.page_break()
    ctx.h('8  安全性、可靠性与可扩展性分析', 1)
    ctx.h('8.1  风险—措施—作用—剩余限制', 2)
    ctx.body('本章不逐项复述功能，而是按“风险—设计措施—作用—剩余限制”分析。表 8-1 汇总主要风险。')
    ctx.table(['风险', '设计措施', '作用', '剩余限制'], [
        ['未认证请求访问业务接口', 'JWT 拦截器校验签名/有效期', '阻止缺失或伪造 token', 'token 泄露后有效期内仍可用'],
        ['禁用账号/角色变更后旧 token', '按 userId 回读 sys_user；status/role 复查', '旧 token 立即失效', '每次请求多一次数据库查询'],
        ['前端篡改角色/路由', '后端路径角色 + 业务归属校验', '前端守卫不是安全边界', '业务归属需逐接口实现'],
        ['重复交卷/并发结算', '`ONGOING → 终态` 条件更新；单事务结算', '只产生一份成绩', '历史脏数据不自动修复'],
        ['客户端关闭导致考试不结束', '服务端定时扫描 + 同一结算器', '考试最终进入终态', '依赖调度器运行'],
        ['预约并发双写', '事务 + 教练行锁；状态流转预约行锁', '当前代码路径下串行化', '无数据库唯一约束兜底'],
        ['Excel 部分写入', '全量解析/语义校验后事务写入', '任一行错误不写任何数据', '模板固定、同步导入'],
        ['AI 检索故障被伪装成回答', '4005/4006；无命中与故障分离', '用户知道检索不可用', '真实链路未验收'],
        ['RAG 开发集合误用', 'KbDevIngestGuard 只允许 dev 集合', '防止误写生产集合', '无生产集合切换/旧点清理'],
    ], widths=[3.5, 5.7, 4.2, 4.0], font_size=8.2, cap='表8-1  风险与设计措施')

    ctx.h('8.2  身份认证与权限的安全边界', 2)
    ctx.body('JWT 的价值是让后端在不保存会话的情况下识别用户，但它不是“安全”的同义词。当前实现通过 HS256 签名、有效期、账号状态复查和角色复查，解决了“伪造 token、禁用账号、角色变更”三类问题；通过路径角色和业务归属校验，解决了“跨角色、跨用户”两类越权。它没有解决 token 主动撤销、刷新令牌、令牌泄露和 HTTPS 传输问题。前端路由守卫只能改善导航体验，localStorage 中的角色可以被修改，真正的访问控制必须在后端。')
    ctx.body('资源归属校验是路径权限的必要补充。假设学员 A 和学员 B 都通过 `/api/student/**`，路径规则无法区分两人；Service 必须校验预约的 student_id、聊天会话的参与者、考试报告的 user_id。教练端同理，必须校验 `coach_student.status=1`，否则教练可能通过猜测 ID 查看非本人学员。当前实现已经在这些主链路中做了归属校验，但新增接口时仍需逐项保持。')
    ctx.body('生产化还需要补充：服务端 token 撤销或短有效期 + 刷新令牌；HTTPS/TLS；登录失败限流；密码强度和审计；敏感配置的密钥管理。报告不会把当前系统描述成已达到生产安全等级。')

    ctx.h('8.3  可靠性与一致性分析', 2)
    ctx.body('可靠性设计集中在“状态变更”而不是“所有请求”。考试结算是典型的多表写入：作答记录、错题、成绩、科目进度必须在同一事务中完成；条件更新抢占终态解决并发重复；服务端定时任务解决客户端消失。预约创建是“检查后写入”，单靠检查会有竞态，因此用教练行锁把同一教练的并发创建串行化；状态流转用预约行锁避免并发覆盖。Excel 导入是“全量校验后写入”，避免部分成功。AI 调用不进入业务事务，外部服务故障不会回滚考试或预约。')
    ctx.body('这些措施提高了当前代码路径下的一致性，但没有经过真实高并发压力测试。行锁的吞吐、锁等待、死锁概率和数据库隔离级别的影响都没有实测数据；报告的结论应限定为“设计上降低了竞态风险，单元测试验证了锁调用和状态规则，真实并发仍待验证”。')
    ctx.body('从可靠性工程角度看，系统还缺少熔断、重试预算、集中日志和告警。AI 客户端有超时配置，但没有重试和熔断；定时任务单实例运行，没有分布式调度；数据库没有备份恢复演练。这些都属于生产化工作，不是课程设计必须完成的内容，但应在展望中列出。稳定性模式强调对失败进行隔离、限流和可观测处理，本系统目前只在 AI 故障语义和事务边界上做了基础工作[19]。')

    ctx.h('8.4  可扩展性分析', 2)
    ctx.body('当前架构的可扩展性主要来自模块边界。新增学员功能可以在 `student` 包内增加 Controller/Service，不需要改教练或管理员代码；新增角色需要扩展 `PathRoleRule`、前端 Layout 和路由 meta；新增 AI 能力可以替换 `EmbeddingClient`/`QdrantClient`，不影响考试和预约；新增部署副本需要把定时任务改为分布式锁或单实例部署。')
    ctx.body('主要扩展方向包括：生产 RAG 知识库治理（集合切换、旧点清理、引用展示、评估集）；数据库迁移框架（Flyway/Liquibase）；预约数据库唯一约束和排班容量；服务端 token 撤销；消息推送/WebSocket；Excel 异步导入和导出；前端端到端测试。优先级应按“影响真实验收 > 数据一致性 > 用户体验 > 新功能”排序。')

    ctx.h('8.5  安全性、可靠性与可扩展性小结', 2)
    ctx.body('当前系统在课程设计范围内完成了身份认证、角色隔离、资源归属、考试幂等、预约锁、Excel 原子性和 AI 故障语义等关键措施。它的安全等级和可靠性仍属于“课程设计可演示”而非“生产可用”。明确这一点，比夸大系统能力更符合软件工程报告的要求。')

    # ---------------- Chapter 9 ----------------
    ctx.page_break()
    ctx.h('9  总结与展望', 1)
    ctx.h('9.1  项目解决的问题', 2)
    ctx.body('本项目把驾考学习中被分散的练习、错题、模考、预约和教练沟通放回同一条业务链。学员不再需要在多个工具之间复制成绩或截图，教练可以在一个工作台看到名下学员的学情和预约，管理员可以统一维护题库、知识点和试卷。系统不是简单地把线下流程搬到网页，而是通过统一身份、统一数据口径和统一状态机，让学习数据可以被后续环节使用。')
    ctx.h('9.2  完成的核心模块与工程设计', 2)
    ctx.body('完成的核心模块包括认证与个人中心、学员练习/错题/学情、模拟考试、预约、聊天、AI/RAG、教练学员管理和管理员管理。代表性的工程设计有：JWT + 账号状态复查 + 路径角色 + 业务归属的四层权限；考试条件更新抢占终态和独立结算器；服务端超时扫描；预约教练行锁和预约行锁；聊天游标与事务未读；RAG 的 800 字符分块、topK=5、1200 字符上下文和 4005/4006 故障语义；Excel 全量校验后事务写入。')
    ctx.h('9.3  当前能够证明的成果', 2)
    ctx.body('可以证明的成果包括：源码中存在完整的三角色业务实现；45 个测试类、300 个 `@Test` 方法，现有 Surefire 报告合计 328 次执行；2026-10-20 的 108 项批次 0 失败/0 错误/0 跳过，当日 230 项批次全部通过；前端 type-check/build 在 2026-10-20 通过；历史 Apifox 报告覆盖练习、错题、学情、考试、预约、教练和聊天。不能证明的成果包括：Docker 当前镜像、真实 MySQL 并发、外部 Qdrant/DeepSeek 链路、完整浏览器验收和生产性能。')
    ctx.h('9.4  尚未完成的验收与改进优先级', 2)
    ctx.table(['优先级', '事项', '原因'], [
        ['P0', '真实 MySQL 并发预约/考试、Excel 导入回滚', '直接决定一致性设计是否在真实环境成立'],
        ['P0', 'Qdrant/Embedding/DeepSeek 真实入库、检索、4005/4006', 'AI 是项目特色，但当前只有代码和 Mock/单元测试'],
        ['P1', 'Docker 当前镜像与三角色浏览器验收', '决定演示版本是否与源码一致'],
        ['P1', '预约数据库唯一约束与数据库迁移框架', '应用层锁缺少数据库兜底，已有卷不会自动迁移'],
        ['P2', 'token 撤销/刷新、HTTPS、登录限流', '生产安全边界'],
        ['P2', 'RAG 生产集合、旧点清理、引用展示', '知识库治理和可解释性'],
        ['P3', '消息推送/WebSocket、Excel 异步导入、端到端测试', '体验和可维护性提升'],
    ], widths=[2.2, 8.4, 6.0], font_size=8.6, cap='表9-1  后续改进优先级')
    ctx.h('9.5  展望', 2)
    ctx.body('后续工作应按“先验收、再治理、后扩展”的顺序推进。先在没有破坏性的独立环境中完成真实 MySQL、Qdrant、Docker 和浏览器验收；再补充数据库迁移、预约唯一约束和 token 撤销；最后扩展生产 RAG、消息推送和端到端测试。课程设计报告的价值不在于声称系统已经完美，而在于把已完成、可验证的部分讲清楚，把未完成和有限制的部分如实记录，为下一阶段工作提供可执行的起点。')

    ctx.page_break()
    ctx.h('致    谢', 1)
    ctx.body('感谢指导教师在课程设计选题、需求分析、系统设计和报告撰写过程中的指导，感谢同学在需求讨论、测试和文档整理过程中提供的帮助。通过本次课程设计，笔者对前后端分离、身份认证、事务与并发控制、检索增强生成和工程化测试有了更完整的认识。文中参考的公开文档和论文已在参考文献中列出，项目中的不足也将作为后续学习和改进的方向。')

    ctx.page_break()
    ctx.h('参考文献', 1)
    refs = [
        '[1] Spring. Spring Boot Reference Documentation (3.2.5)[EB/OL]. https://docs.spring.io/spring-boot/docs/3.2.5/reference/html/, 2024.',
        '[2] Vue.js. Vue.js 3 Documentation[EB/OL]. https://vuejs.org/guide/, 2024.',
        '[3] Vite. Vite Documentation[EB/OL]. https://vitejs.dev/guide/, 2024.',
        '[4] Element Plus. Element Plus Documentation[EB/OL]. https://element-plus.org/, 2024.',
        '[5] Baomidou. MyBatis-Plus Documentation[EB/OL]. https://baomidou.com/, 2024.',
        '[6] Oracle. MySQL 8.0 Reference Manual[EB/OL]. https://dev.mysql.com/doc/refman/8.0/en/, 2024.',
        '[7] Jones M, Bradley J, Sakimura N. JSON Web Token (JWT): RFC 7519[S]. IETF, 2015. https://www.rfc-editor.org/rfc/rfc7519.',
        '[8] OWASP Foundation. OWASP Top 10[EB/OL]. https://owasp.org/www-project-top-ten/, 2021.',
        '[9] DeepSeek. DeepSeek API Documentation[EB/OL]. https://api-docs.deepseek.com/, 2024.',
        '[10] Chen J, Xiao S, Zhang P, et al. BGE M3-Embedding: Multi-Lingual, Multi-Functionality, Multi-Granularity Text Embeddings Through Self-Knowledge Distillation[EB/OL]. arXiv:2402.03216, 2024.',
        '[11] Lewis P, Perez E, Piktus A, et al. Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks[C]//Advances in Neural Information Processing Systems (NeurIPS), 2020. arXiv:2005.11401.',
        '[12] Qdrant. Qdrant Documentation[EB/OL]. https://qdrant.tech/documentation/, 2024.',
        '[13] Docker. Docker Documentation[EB/OL]. https://docs.docker.com/, 2024.',
        '[14] Apache Software Foundation. Apache POI Documentation[EB/OL]. https://poi.apache.org/, 2024.',
        '[15] Pressman R S, Maxim B R. Software Engineering: A Practitioner’s Approach[M]. 9th ed. New York: McGraw-Hill, 2020.',
        '[16] Fowler M. Patterns of Enterprise Application Architecture[M]. Boston: Addison-Wesley, 2002.',
        '[17] Kleppmann M. Designing Data-Intensive Applications[M]. Sebastopol: O’Reilly Media, 2017.',
        '[18] Fielding R T. Architectural Styles and the Design of Network-based Software Architectures[D]. Irvine: University of California, Irvine, 2000.',
        '[19] Nygard M T. Release It! Design and Deploy Production-Ready Software[M]. 2nd ed. Raleigh: Pragmatic Bookshelf, 2018.',
        '[20] Nginx. Nginx Documentation[EB/OL]. https://nginx.org/en/docs/, 2024.',
        '[21] 公安部. 2025 年全国机动车保有量达 4.69 亿辆 驾驶人达 5.59 亿人[EB/OL]. 中华人民共和国中央人民政府网, 2026-01-26. https://www.gov.cn/lianbo/202601/content_7056115.htm.',
        '[22] TSO (The Stationery Office). Official DVSA Theory Test Kit[EB/OL]. Google Play, 2025. https://play.google.com/store/apps/details?id=uk.co.tso.ctt.',
        '[23] Zutobi. Free Driving Theory Test Practice 2026[EB/OL]. Zutobi Drivers Ed, 2026. https://zutobi.com/uk/mock-theory-test.',
        '[24] 驾考宝典. 驾考宝典官方网站[EB/OL]. https://www.jiakaobaodian.com/, 2026.',
        '[25] 驾校一点通. 驾校一点通官方网站[EB/OL]. https://www.jxedt.com/, 2026.',
        '[26] Shi Y, Yu K, Dong Y, Chen F. Large language models in education: a systematic review of empirical applications, benefits, and challenges[J]. Computers and Education: Artificial Intelligence, 2026, 10: 100529. DOI: 10.1016/j.caeai.2025.100529.',
        '[27] Ali D, Fatemi Y, Boskabadi E, Nikfar M, Ugwuoke J, Ali H. ChatGPT in Teaching and Learning: A Systematic Review[J]. Education Sciences, 2024, 14(6): 643. DOI: 10.3390/educsci14060643.',
        '[28] 国家互联网信息办公室, 国家发展改革委, 教育部, 等. 生成式人工智能服务管理暂行办法: 令第 15 号[A/OL]. 2023-07-13 发布, 2023-08-15 施行. https://www.gov.cn/gongbao/2023/issue_10666/202308/content_6900864.html.',
        '[29] 全国人民代表大会常务委员会. 中华人民共和国个人信息保护法[A/OL]. 2021-08-20 通过, 2021-11-01 施行. http://www.npc.gov.cn/npc/c2/c30834/202108/t20210820_313088.html.',
    ]
    for ref in refs:
        p = ctx.doc.add_paragraph(style='ReportReference')
        p.paragraph_format.left_indent = Cm(0.75)
        p.paragraph_format.first_line_indent = Cm(-0.75)
        r = p.add_run(ref)
        ctx._font(r, '宋体', 10.0, False)

    # ---------------- Appendices ----------------
    ctx.page_break()
    ctx.h('附录A  主要接口前缀', 1)
    ctx.table(['前缀', '职责'], [
        ['/api/auth/**', '登录、当前用户、资料、密码'],
        ['/api/student/study/**', '知识点、顺序/章节/随机练习、作答'],
        ['/api/student/wrong-book/**', '错题列表、分类统计、重练、掌握'],
        ['/api/student/analysis/**', '学情总览、知识点、趋势'],
        ['/api/student/exam/**', '试卷、开始/恢复、暂存、判题、交卷、报告、历史'],
        ['/api/student/appointment/**', '教练列表、创建、列表、详情、取消'],
        ['/api/student/ai/**', '提问、AI 会话、消息、删除会话'],
        ['/api/student/chat/**', '会话、消息、发送、已读'],
        ['/api/coach/students/**', '学员列表、绑定、学情、学习数据、错题、AI 建议、确认'],
        ['/api/coach/appointments/**', '预约列表、通过、拒绝、完成'],
        ['/api/coach/chat/**', '教练端会话、消息、发送、已读'],
        ['/api/admin/**', 'Dashboard、AI 统计、用户、绑定、知识点、题库、试卷'],
        ['/api/admin/appointments/**', '全量预约只读'],
        ['/api/admin/rag/**', '管理员知识库入库'],
    ], widths=[5.4, 10.2], font_size=8.8, cap='表A-1  主要接口前缀')

    ctx.h('附录B  测试与验收证据摘要', 1)
    ctx.table(['证据', '内容', '状态'], [
        ['自动化测试源码', '45 个测试类、300 个 @Test；参数展开 328 次', '已存在'],
        ['2026-10-20 108 项批次', '0 failures/0 errors/0 skipped，与 Day34 日志一致', '已执行'],
        ['2026-10-20 81+41 项批次', '0 failures/0 errors/0 skipped', '已执行'],
        ['2026-10-19 批次', '98 项：1 失败、2 错误、9 跳过', '已执行，含环境问题'],
        ['历史 Apifox', '练习/错题/学情/考试/预约/教练/聊天', '历史证据，非当前复跑'],
        ['前端 type-check/build', '2026-10-20 exit 0；dist 入口 index-CI9I_tjy.js', '已执行'],
        ['Docker/浏览器/真实 MySQL/Qdrant', '未执行或未验证', '待完成'],
    ], widths=[4.6, 8.4, 3.4], font_size=8.6, cap='表B-1  测试证据分级')

    ctx.h('附录C  报告生成与待补充信息', 1)
    ctx.body('本报告以 `doc/detail/综合课设封面及评分表V1.doc` 转换后的封面为底座，在封面和成绩评定表之后追加正文；封面原有学院、专业、评分项目、比例和签名栏保留，未预填成绩或签名。正文由 Python 脚本生成，使用参考模板的页边距、宋体正文、黑体标题、图表题注和参考文献格式；目录页码按最终 PDF 版式反填。')
    ctx.body('封面和成绩评定表中的班级、学生姓名、学号、指导教师、职称、时间、成绩、评语和签名等信息未在项目资料中提供，当前保留空白或标注待填写。正文中的测试日期、批次和证据来源来自仓库中的 Surefire 报告、Day30–Day39 日志、Apifox 报告和前端构建记录；无法确认的内容已标注“待核实”或“未验证”。')
