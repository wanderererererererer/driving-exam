def build(ctx):
    # ---------------- Chapter 4 ----------------
    ctx.h('4  总体设计', 1)
    ctx.h('4.1  设计原则', 2)
    ctx.body('总体设计遵循四条原则。第一，按角色和业务边界划分模块，学员、教练和管理员各自拥有独立的页面入口和 API 前缀，避免把权限判断散落在页面条件中。第二，关键状态由后端统一维护，前端只负责展示和触发；考试状态、预约状态、未读数和题目冻结规则都不能由前端自行推断。第三，接口契约先于页面实现，统一响应、分页结构、错误码和 DTO/VO 让三个角色可以并行开发。第四，外部服务可降级，AI 检索失败必须返回明确错误，而不是让页面显示一个看似正常的回答。上述分层和契约思想与分层架构模式一致[16]。')
    ctx.body('这些原则对应到代码结构，就是“Controller 负责协议、Service 负责业务、Mapper 负责 SQL、前端 API 层负责契约”。第 5 章解释业务规则，第 6 章解释代码如何落地；本章先说明系统由哪些部分组成、各部分如何协作。')

    ctx.h('4.2  系统架构', 2)
    ctx.body('系统采用浏览器—Nginx 前端—Spring Boot 后端—MySQL 的主链路，并接入 DeepSeek、SiliconFlow Embedding 和 Qdrant 三个 AI 相关组件。浏览器通过同源 `/api` 访问后端；Nginx 托管 Vue 构建产物，并把 `/api` 反向代理到后端；后端通过 MyBatis-Plus 访问 MySQL，通过 HTTP 客户端访问 DeepSeek 和 Embedding 服务，通过 Qdrant REST 接口完成向量写入和检索。')
    ctx.figure('figures/architecture.png', 13.0, '图4-1  系统总体架构')
    ctx.body('图 4-1 中，前端不直接访问数据库或 AI 服务，所有业务请求都经过后端。这样做的原因是：数据库凭据、JWT 密钥和外部 AI Key 不应出现在浏览器；角色权限、资源归属和业务状态必须由后端统一判断；AI 调用需要记录用户、会话和引用，不能由前端直接拼接。Nginx 只承担静态资源和反向代理，不承担业务逻辑。')
    ctx.body('AI 相关组件被放在后端之外，是为了让它们可以独立替换。Embedding 服务把文本转换为 1024 维向量，Qdrant 保存向量并执行相似度检索，DeepSeek 根据上下文生成自然语言回答。三者通过后端编排，任何一个组件故障都不会改变考试、预约等确定性业务的状态。')

    ctx.h('4.3  模块划分与调用关系', 2)
    ctx.body('系统按业务能力划分为认证与个人中心、学员学习、模拟考试、预约、聊天、AI/RAG、教练管理和管理员管理八个模块。表 4-1 给出模块职责、主要入口和依赖关系。模块划分的依据是“同一模块共享同一组实体和业务规则”，而不是按页面数量拆分。图 4-2 从功能视角给出六个一级模块及其二级功能，可与表 4-1 对照阅读。')
    ctx.table(['模块', '主要职责', '主要入口', '依赖'], [
        ['认证与个人中心', '登录、当前用户、资料、密码、JWT 状态复查', 'AuthController、JwtInterceptor', 'sys_user'],
        ['学员学习', '知识点、顺序/章节/随机练习、作答、错题、学情', 'StudentStudyController、StudentWrongBookController、AnalysisController', 'question、answer_record、wrong_book、student_subject_status'],
        ['模拟考试', '试卷、开始/恢复、暂存、判题、交卷、报告、历史', 'ExamController、ExamFinalizer、ExamTimeoutScheduler', 'exam_paper、mock_exam、mock_exam_answer、answer_record'],
        ['预约', '学员创建/取消、教练审批/拒绝/完成、管理员只读', 'StudentAppointmentController、CoachAppointmentController、AdminAppointmentController', 'appointment、coach_student、sys_user'],
        ['聊天', '会话、消息、未读、已读、会话摘要', 'StudentChatController、CoachChatController、ChatServiceImpl', 'chat_session、chat_message、coach_student'],
        ['AI/RAG', '知识入库、检索、上下文、回答、引用记录', 'AiChatController、AdminRagController、KnowledgeIngestService', 'ai_chat_record、question、knowledge_point、Qdrant、DeepSeek'],
        ['教练管理', '学员列表、学习数据、错题、AI 建议/确认', 'CoachStudentController', 'coach_student、student_subject_status、wrong_book'],
        ['管理员管理', '用户、绑定、知识点、题库/Excel、试卷、统计', 'AdminController、AdminRagController', '全量主数据表'],
    ], widths=[3.0, 6.0, 5.2, 4.3], font_size=8.2, cap='表4-1  模块职责与依赖')
    ctx.body('模块之间存在明确的调用方向：Controller 接收请求并做参数校验，Service 组合多个 Mapper 完成业务，Mapper 执行 SQL；跨模块调用只通过 Service 接口，不直接操作对方的 Mapper。例如考试结算需要写作答记录、错题和科目进度，`ExamFinalizer` 组合 `AnswerRecordMapper`、`WrongBookMapper`、`MockExamMapper` 和 `StudentSubjectStatusMapper`，而不是让 Controller 逐个调用。这样事务边界清晰，测试也容易替换依赖。')
    ctx.figure('系统功能结构图.png', 14.0, '图4-2  系统功能结构图')
    ctx.body('图 4-2 中，系统根节点下分为用户认证、学员学习、智能问答、预约管理、教练管理和管理员管理六个一级模块，二级功能共 26 项；学员学习再分为练习题库、管理错题、参加模考、分析学情，预约管理分为查询教练、申请预约、取消预约、处理预约，教练管理分为管理学员、查看学情、生成建议、收发消息，管理员管理分为查看数据、管理用户、管理绑定、管理知识点、管理题库、管理试卷、查看预约、统计问答。该图是功能设计视图，不是开发状态图。')
    ctx.body('需要区分“图中存在”和“已通过验收”。当前源码中六个一级模块均有对应 Controller、Service 或前端路由；但“用户认证—退出登录”目前只是前端清理本地 token，没有服务端 token 撤销；管理员预约是只读查看，不提供审批；真实浏览器、Docker、MySQL 并发和外部 AI/RAG 的运行验收仍未完成。因此，图 4-2 只能说明功能范围，不能作为所有功能均已通过验收的证据。')

    ctx.h('4.4  接口契约与统一响应', 2)
    ctx.body('前后端分离后，接口契约是双方共同遵守的边界。后端统一返回 `ApiResponse<T>`，包含 code、message、data；分页接口返回 `PageResult<T>`，包含 total、pages、pageNum、pageSize、records。业务失败通常使用 HTTP 200 + 业务 code，只有 401/403 使用真实 HTTP 状态码，便于前端统一处理登录失效和无权限。接口以资源路径和 HTTP 方法组织，符合 REST 风格的基本约束[18]。')
    ctx.body('这种约定的好处是：前端请求拦截器只在一个地方判断 `code === 200`、401、403 和其他业务错误；Controller 不需要为每个接口重复写异常捕获；测试可以直接断言业务 code 和数据结构。缺点是 HTTP 状态码不能完整表达业务失败，监控系统需要解析响应体；对于课程设计，这种取舍可以接受。')
    ctx.table(['错误段', '范围', '含义举例'], [
        ['认证/通用', '401、403、1001、1102、1103、1106', '未登录、无权限、参数错误、账号禁用、令牌无效、角色不一致'],
        ['题库/学习', '2101–2106', '试卷不存在/停用、考试状态非法、题目不属于试卷、题型不可判分'],
        ['预约/绑定', '3001–3005、3101–3102', '预约不存在/非本人、时段冲突、未绑定、状态非法、重复绑定、学员不存在'],
        ['AI', '4001–4006', '模型调用失败、超时、限流、会话不存在、检索故障、入库失败'],
        ['聊天', '5001', '会话不存在或非参与者'],
    ], widths=[2.8, 5.8, 8.7], font_size=8.8, cap='表4-2  主要错误码分段')
    ctx.body('错误码不是装饰，而是前端和测试的共同语言。例如 AI 检索故障返回 4005，前端可以提示“知识检索暂不可用”，而不是把模型基于空上下文的回答当成正常答案；预约冲突返回 3002，前端可以提示用户换一个时段；考试状态非法返回 2102，前端可以回到考试详情而不是继续提交。')

    ctx.h('4.5  权限设计的三层结构', 2)
    ctx.body('权限设计分为三层。第一层是 JWT 身份认证：拦截器校验签名、有效期和令牌格式，解析出 userId、username、role。第二层是路径角色规则：`PathRoleRule` 按 `/api/student/**`、`/api/coach/**`、`/api/admin/**` 判断角色，`/api/auth/**` 和 `/api/common/**` 允许任意已登录用户。第三层是业务归属校验：Service 在查询和更新时验证资源是否属于当前用户，例如预约的 student_id/coach_id、聊天会话的参与者、考试报告的 user_id、教练学员的有效绑定。')
    ctx.table(['层级', '检查内容', '失败结果', '主要位置'], [
        ['JWT 认证', '签名、有效期、token 格式、userId 解析', 'HTTP 401；code 401/1103', 'JwtInterceptor、JwtUtil'],
        ['账号状态', 'sys_user 是否存在、status=1、role 与 token 一致', 'HTTP 401；code 401/1102/1106', 'JwtInterceptor、UserMapper'],
        ['路径角色', 'STUDENT/COACH/ADMIN 路径前缀', 'HTTP 403；code 403', 'PathRoleRule、WebMvcConfig'],
        ['业务归属', '预约 student_id/coach_id、聊天参与者、报告 user_id、有效绑定', '业务错误码 2105/3001/3102/5001', '各 Service'],
    ], widths=[2.6, 7.2, 4.4, 3.8], font_size=8.6, cap='表4-3  权限三层与失败语义')
    ctx.body('前端路由守卫只承担导航体验：无 token 时跳转登录页，角色不匹配时跳转到对应角色首页；它不能阻止用户修改 localStorage 或直接构造 HTTP 请求。表 4-3 中的后三层都在后端执行，前端守卫不参与安全判断。')
    ctx.figure('figures/auth_flow.png', 10.8, '图4-3  请求认证与权限判断流程')
    ctx.body('图 4-3 说明了一个请求从进入 `/api/**` 到业务层的完整路径。签名或有效期失败、账号不存在、账号禁用、token 角色与当前角色不一致都会返回 401；路径角色不匹配返回 403；只有通过三层检查，Controller 才会执行。前端路由守卫只负责页面导航，不参与这层判断。即使有人在浏览器中修改本地角色或直接构造请求，后端仍会按数据库中的角色和资源归属拒绝。')
    ctx.body('这种设计的代价是每个受保护请求都要回读一次 `sys_user`，比纯无状态 JWT 多一次数据库查询；但它换来了账号禁用和角色变更的即时生效。对于课程设计规模，这个代价可以接受；如果未来访问量增大，可以引入短期缓存或令牌版本号，但需要重新评估失效一致性。')

    ctx.h('4.6  部署设计', 2)
    ctx.body('系统使用 Docker Compose 编排四个服务。表 4-3 给出服务、端口、数据卷和职责。端口以 `docker-compose.yml` 中的默认配置为准，实际部署可通过环境变量覆盖。')
    ctx.table(['服务', '默认端口', '持久化/配置', '职责'], [
        ['mysql', '3306（容器内）', 'MySQL 命名卷；初始化 SQL 仅在空卷首次执行', '业务数据持久化'],
        ['backend', '8080（容器内）', '环境变量读取数据库、JWT、AI 配置', 'Spring Boot API 与业务逻辑'],
        ['frontend', '80（容器内，可映射到宿主端口）', 'Nginx 静态资源；`/api` 反向代理到 backend', 'Vue 页面与同源代理'],
        ['qdrant', '6333（容器内）', 'Qdrant 命名卷', '知识向量存储与检索'],
    ], widths=[2.4, 4.0, 5.7, 5.2], font_size=8.8, cap='表4-4  Docker Compose 服务设计')
    ctx.body('前端 Nginx 使用 `try_files $uri $uri/ /index.html` 支持 Vue Router 的 history 模式[20]；`/api/` 反向代理到 `http://backend:8080`，并保留 Host、X-Real-IP 和 X-Forwarded-* 头。后端通过环境变量读取数据库连接、JWT 密钥和 AI 配置，避免把敏感信息写入镜像。MySQL 和 Qdrant 的命名卷保证容器重建后数据仍在，但也带来一个限制：已有卷不会自动执行新的初始化 SQL，数据库结构升级需要单独设计迁移流程。')
    ctx.body('部署设计没有声称已经完成当前版本验收。当前工作环境没有 Docker CLI/socket，无法确认运行中的镜像是否包含 Day30–Day39 的代码；前端本地 `dist` 也不会自动进入镜像，更新页面后必须重建 frontend 镜像。第 7 章会把这一点列为未执行项。')

    ctx.h('4.7  数据库概念模型', 2)
    ctx.body('数据库围绕用户、题库、考试、预约和聊天五条主线组织。用户与作答、错题、考试、学情、预约、AI 记录和聊天会话构成 1:N 关系；知识点通过 parent_id 构成树；题目属于知识点并拥有多个选项；试卷与题目通过组卷关联；考试与试卷、考试与暂存答案、聊天会话与消息分别构成 1:N 关系。数据库以应用层关系为主，未建立物理外键。')
    ctx.figure('figures/er.png', 13.2, '图4-4  核心实体关系（E-R）')
    ctx.body('图 4-4 只表达核心实体和主要关系，不试图画出所有字段。需要强调的是，图中的 1:N 关系是逻辑关系，不是数据库外键；删除或停用主数据时，应用层必须检查引用。例如题目被考试引用后不能直接删除，试卷被考试引用后不能修改，聊天会话通过唯一键保证一个师生对只有一个会话。完整表结构见 5.1 节。')

    ctx.h('4.8  总体设计小结', 2)
    ctx.body('本章把系统拆分为八个模块，给出了架构、接口、权限和部署设计。总体设计的核心不是“画一张架构图”，而是明确每个部分的职责和边界：前端只做展示与交互，后端统一维护业务状态，数据库保存事实，AI 组件提供辅助能力但不参与确定性判断。第 5 章将逐项展开这些边界内的业务规则。')
    ctx.body('从模块边界看，认证和权限贯穿所有模块，但只在 JWT 拦截器、路径角色规则和 Service 归属校验中实现，避免每个 Controller 重复写权限代码；考试、预约和聊天的状态由各自 Service 维护，其他模块不直接修改对方的状态表；AI/RAG 作为旁路能力，不进入考试和预约事务。从接口契约看，统一响应和错误码让前端可以在一个请求拦截器中处理 401/403 和业务错误；从部署看，Compose 只负责服务编排，不负责数据库迁移，因此版本升级需要单独记录迁移步骤。')

    # ---------------- Chapter 5 ----------------
    ctx.page_break()
    ctx.h('5  详细设计', 1)
    ctx.h('5.1  数据库设计', 2)
    ctx.body('数据库共 16 张基础表，按职责可分为用户与关系、题库与知识点、考试、学习记录、预约、聊天和 AI 记录七组。表 5-1 给出表名、职责和关键约束。字段类型和默认值以 `sql/_localhost-2026_10_08_17_26_16-dump.sql` 为准；该 dump 只包含结构，不包含业务数据。')
    ctx.table(['表名', '职责', '关键字段与约束'], [
        ['sys_user', '用户与登录', 'username(UK)、password、role、status、deleted；idx_role、idx_phone'],
        ['coach_student', '师生绑定', 'coach_id、student_id、status、bind_time；UK(coach_id, student_id)'],
        ['student_subject_status', '学员科目进度', 'student_id、subject_code、status、mock_pass_count、last_mock_score；UK(student_id, subject_code)'],
        ['appointment', '科二/科三预约', 'student_id、coach_id、subject_code、appointment_date、time_slot、status、deleted；无时段唯一约束'],
        ['question', '题库', 'subject_code、knowledge_point_id、question_type、content、answer、difficulty、status、deleted'],
        ['question_option', '题目选项', 'question_id、option_key、option_content、is_correct；UK(question_id, option_key)'],
        ['knowledge_point', '知识点树', 'subject_code、parent_id、name、level、description、deleted'],
        ['exam_paper', '试卷', 'subject_code、title、paper_type、total_score、pass_score、duration、status、deleted'],
        ['exam_paper_question', '组卷关联', 'paper_id、question_id、score、sort_order；UK(paper_id, question_id)'],
        ['mock_exam', '考试场次', 'user_id、subject_code、paper_id、start_time、submit_time、score、status'],
        ['mock_exam_answer', '考试暂存', 'exam_id、question_id、user_answer；UK(exam_id, question_id)'],
        ['answer_record', '作答流水', 'user_id、subject_code、question_id、is_correct、source、exam_id、answer_time'],
        ['wrong_book', '错题本', 'user_id、question_id、wrong_count、mastered；UK(user_id, question_id)'],
        ['chat_session', '聊天会话', 'student_id、coach_id、last_message、student_unread、coach_unread；UK(student_id, coach_id)'],
        ['chat_message', '聊天消息', 'session_id、sender_id、sender_role、content、is_read'],
        ['ai_chat_record', 'AI 问答记录', 'user_id、session_id、question、answer、references_json、model'],
    ], widths=[4.3, 3.0, 8.5], font_size=7.8, cap='表5-1  数据库表概览')
    ctx.body('核心实体之间的关系需要从业务上理解，而不是只看字段。`sys_user` 是身份中心，学员和教练都保存在同一张表，通过 role 区分；`coach_student` 表达有效指导关系，status=1 才允许聊天、查看学情和创建预约；`student_subject_status` 以 `(student_id, subject_code)` 唯一键记录四科进度；`question` 属于知识点，`question_option` 保存选项，`exam_paper_question` 把题目组进试卷；`mock_exam` 记录一次考试，`mock_exam_answer` 保存考试中的暂存答案，`answer_record` 保存最终作答流水；`wrong_book` 以用户和题目为唯一键；`chat_session` 以师生对为唯一键，`chat_message` 保存消息；`ai_chat_record` 保存 AI 问答和引用 JSON。')
    ctx.body('逻辑删除只用于 `sys_user`、`knowledge_point`、`question`、`exam_paper` 和 `appointment` 五张主数据表，流水表和关系表不使用。这样设计的原因是：主数据需要保留历史引用，直接物理删除会破坏报告和成绩的可追溯性；流水数据本身是事实记录，不需要再叠加删除标记。`coach_student` 的解除绑定使用 `status=0`，不是逻辑删除；重新绑定会复用原行并刷新 `bind_time`。')
    ctx.body('数据库没有物理外键。这样做的好处是批量导入、删除和测试数据准备不受外键顺序限制，应用层可以根据业务规则决定是否允许删除；代价是引用完整性完全由应用层保证，任何绕过 Service 的直接 SQL 都可能产生孤儿数据。项目通过唯一约束、状态检查和冻结规则降低风险，但没有数据库级兜底。')
    ctx.body('预约表是一个典型例子：`appointment` 只有普通索引 `idx_coach_id` 和 `idx_subject_date`，没有 `(coach_id, appointment_date, time_slot)` 唯一约束。冲突检测由应用层在事务中完成：先锁定教练账号行，再统计同一教练、同一日期、同一时段且状态为 PENDING/CONFIRMED 的记录，最后插入。这样可以在当前代码路径下避免并发双预约，但无法防止绕过 Service 的直接插入，也不能替代数据库唯一约束。报告不会把这一方案描述成已经通过真实高并发压力测试。')

    ctx.h('5.2  身份认证与权限', 2)
    ctx.body('登录链路从 `AuthController.login` 开始：根据用户名查询 `sys_user`，校验 BCrypt 密码、角色和 status；成功后由 `JwtUtil` 签发 HS256 token，subject 为 userId，claims 包含 username 和 role，默认有效期 86400 秒。前端把 token 保存到 localStorage，并在后续请求中通过 Axios 拦截器添加 `Authorization: Bearer <token>`。')
    ctx.body('请求进入 `/api/**` 时，`JwtInterceptor` 先放行 OPTIONS 预检；然后要求 Authorization 头以 `Bearer ` 开头，截取 token 并校验签名和有效期。解析失败返回 HTTP 401 + code 401；userId 无法转换为 Long 返回 401 + 1103。随后拦截器按 userId 查询 `sys_user`：用户不存在返回 401 + 401，status 非 1 返回 401 + 1102，token 中的 role 与数据库当前 role 不一致返回 401 + 1106。最后按 `PathRoleRule` 判断路径角色，不匹配返回 403 + 403。通过后把 userId、username、role 写入 request attribute，Controller 不再解析 token。')
    ctx.body('这套链路解决了三个问题。第一，禁用账号或角色变更后，旧 token 不能继续访问；第二，路径角色规则把学员、教练、管理员接口隔离；第三，业务层继续校验资源归属，防止同一角色内部越权。它没有解决 token 主动撤销、刷新令牌和令牌泄露后的即时失效，这些属于后续改进。')
    ctx.body('前端路由守卫按父路由的 `meta.role` 做导航限制，并在无 token 时跳转登录页。但前端代码运行在用户浏览器中，localStorage 中的角色可以被修改，因此路由守卫只能改善体验，不能作为安全边界。真正的访问控制在后端 JWT、路径规则和 Service 归属校验。')

    ctx.h('5.3  练习、错题与学情分析', 2)
    ctx.body('练习模块提供顺序、章节和随机三种模式。顺序模式按题目 ID 或排序顺序加载；章节模式按知识点过滤；随机模式在指定科目内随机取题。题目 VO 只返回题干、选项、题型、难度和知识点，不返回标准答案和解析。这样设计是为了防止用户在答题前通过浏览器网络面板或前端状态看到答案；答案只在提交后由后端判分并返回。')
    ctx.figure('figures/practice_flow.png', 10.4, '图5-1  练习—判题—错题—学情数据流')
    ctx.body('图 5-1 展示了练习的数据流。提交答案后，`AnswerGrader` 统一做 NFKC 归一化、trim 和大写转换：单选题保留 A–Z；多选题去重并排序；判断题把“对/正确/是/T/TRUE/Y/YES/√/✓”等映射为正确，把“错/错误/否/F/FALSE/N/NO/×/✗/X”等映射为错误。归一化结果为空时回退到 trim 原文。判分结果写入 `answer_record`，source 由接口决定为 PRACTICE、RETRY 或 EXAM；答错时更新 `wrong_book`，`wrong_count` 加一，`mastered` 置 0；重练答对时标记 `mastered=1`。')
    ctx.body('错题分类是互斥的，优先级为 MASTERED（mastered=1）> HIGH_FREQUENCY（mastered=0 且 wrong_count≥3）> PENDING。`wrong_count` 不会清零，掌握也不是终态：再次答错会把 `mastered` 重置为 0。这样既保留历史错误次数，又允许学员通过重练重新掌握。')
    ctx.body('学情分析以作答记录和错题为输入，按知识点聚合。掌握度使用 `clamp(A×D×B, 0, 1)`：A 是按难度加权的正确率，D 是时间衰减因子 `0.5^(Δ/14)`（半衰期 14 天），B 是错题修正因子 `1+0.15m−0.25h`，并限制在 `[0.75,1.15]`；没有错题时 B=1。短板判定使用原始掌握度：`mastery<0.6 且答题数≥3` 为 LOW_MASTERY；低难度题答题数≥2 且低难度错误率高于高难度错误率时为 BASE_WEAK；同时命中时优先 LOW_MASTERY。趋势默认 14 天、上限 90 天，不补空白日。')
    ctx.body('这些公式是设计文档中的口径，不是凭空估计的性能指标。它们的作用是让“学情分析”有一个可解释的计算过程，而不是只展示正确率。需要说明的是，`mockExamCount/AvgScore/PassRate` 仍是预留字段，`student_subject_status.study_progress` 没有写入方，页面刻意不展示未实现指标。')

    ctx.h('5.4  模拟考试流程与一致性', 2)
    ctx.body('考试状态为 ONGOING、SUBMITTED、TIMEOUT。试卷的 duration 和 pass_score 在开考时复制到 `mock_exam`，因此即使管理员之后停用试卷，已有考试仍能按原参数交卷和报告。演示卷 `SUBJECT_1` 为 36 题、20 分钟、总分 36、及格 32；这是种子数据，不是所有试卷的固定值。')
    ctx.figure('figures/states.png', 12.8, '图5-2  模拟考试与预约状态流转')
    ctx.body('开始考试时，`ExamServiceImpl.start` 在事务中执行 `SELECT ... FROM exam_paper WHERE id=? AND deleted=0 FOR UPDATE`，锁定试卷行，避免同一试卷的并发开考产生重复场次。若学员已有 ONGOING 考试且未超时，直接返回原考试；若已超时，先调用结算器收尾再创建新考试；新卷会先校验所有题型可判分，否则返回 2106。')
    ctx.body('答题时，`answer` 要求考试状态为 ONGOING、答案非空、题目属于当前试卷；若已超时，先结算再返回 2102。暂存使用 `INSERT ... ON DUPLICATE KEY UPDATE`，依赖 `uk_exam_question(exam_id, question_id)`，因此同一题重复暂存只保留一条记录。')
    ctx.body("交卷由 `ExamFinalizer` 在一个事务中完成。它先校验全卷题型，再执行 `UPDATE mock_exam SET status=#{status} WHERE id=#{examId} AND status='ONGOING'`；只有影响 1 行的请求继续写入作答记录、错题、成绩和科目进度，影响 0 行说明已被其他请求结算，直接返回既有报告。这就是“条件更新抢占终态”：数据库的行级条件保证同一场考试只有一个请求能完成结算。重复交卷因此是幂等的，不会生成多份成绩。条件更新和幂等写入是数据密集型系统中处理并发状态变更的常用手段[17]。")
    ctx.body('服务端超时任务不能只依赖前端倒计时。学员可能关闭浏览器、断网或刷新页面，前端计时器不再运行；`ExamTimeoutScheduler` 每 60 秒扫描 `status=ONGOING` 且 `DATE_ADD(start_time, INTERVAL duration MINUTE) <= NOW()` 的考试，每批最多 50 条，调用同一个 `ExamFinalizer` 结算。这样即使客户端消失，考试也会进入终态。')
    ctx.body('历史一致性通过冻结规则保护：题目一旦被任何考试引用，管理员不能修改、停用或删除；已有答题记录的题目不能改标准答案；试卷被任何 `mock_exam` 引用后不能修改，停用时有 ONGOING 考试会被拒绝。试卷停用后已有考试仍可恢复、交卷和查看报告；但题目被逻辑删除时，`loadPaperItems` 会跳过该题，这是已知局限，报告不会把它说成完全修复。')
    ctx.table(['阶段', '关键动作', '一致性措施', '失败/异常'], [
        ['开始/恢复', '锁试卷行；查 ONGOING；必要时先结算', '事务 + `FOR UPDATE`；题型可判分校验', '2101 试卷不可用；2106 题型不可判分'],
        ['暂存', '校验状态/归属/超时；upsert 答案', '`uk_exam_question`；超时先结算', '2102 状态非法；1001 题目不属于试卷'],
        ['交卷', '条件更新抢占终态；写作答/错题/成绩/学情', '`ONGOING → 终态` 条件更新；单事务', '影响 0 行返回既有报告；未完成结算 2105'],
        ['超时', '每分钟扫描到期 ONGOING', '数据库时间 `DATE_ADD`；同一结算器', '单条失败不影响其他考试'],
        ['报告', '按试卷题目左连接作答记录', '未答题也展示；只允许本人查看', '进行中 2105'],
    ], widths=[2.2, 6.0, 5.4, 4.4], font_size=8.5, cap='表5-2  模拟考试关键阶段')

    ctx.h('5.5  预约与并发控制', 2)
    ctx.body('预约状态为 PENDING、CONFIRMED、CANCELLED、REJECTED、COMPLETED。PENDING 表示学员已提交、等待教练处理；CONFIRMED 表示教练已通过；CANCELLED 表示学员取消；REJECTED 表示教练拒绝；COMPLETED 表示训练完成。只有 PENDING 和 CONFIRMED 占用时段，三个终态释放时段。学员只能取消 PENDING，教练只能审批/拒绝 PENDING、完成 CONFIRMED。')
    ctx.table(['当前状态', '允许动作', '操作者', '目标状态', '说明'], [
        ['PENDING', '通过', '教练', 'CONFIRMED', '占用时段；仅本人预约'],
        ['PENDING', '拒绝', '教练', 'REJECTED', '释放时段；仅本人预约'],
        ['PENDING', '取消', '学员', 'CANCELLED', '释放时段；仅本人预约'],
        ['CONFIRMED', '完成', '教练', 'COMPLETED', '释放时段；仅本人预约'],
        ['CANCELLED/REJECTED/COMPLETED', '无', '—', '终态', '不可再流转'],
    ], widths=[4.0, 3.2, 2.8, 3.4, 5.0], font_size=8.8, cap='表5-3  预约状态流转')
    ctx.body('时段固定为 08:00-10:00、10:00-12:00、14:00-16:00、16:00-18:00 四个值，DTO 用 `@Pattern` 校验；数据库中的 time_slot 是自由文本，没有字典表。科目限定为 SUBJECT_2/3，日期使用 `@FutureOrPresent`。这些约束在接口层先拦截明显非法输入，Service 再做业务校验。')
    ctx.body('创建预约的校验顺序是：事务开始；`SELECT ... FROM sys_user WHERE id=? AND deleted=0 FOR UPDATE` 锁定教练行；教练不存在返回 3003，禁用返回 1102；检查 `coach_student` 有效绑定，未绑定返回 3003；统计同一教练、同一日期、同一时段、状态为 PENDING/CONFIRMED 的预约，存在冲突返回 3002；最后插入 PENDING。锁教练行的目的是把同一教练的并发创建串行化，避免两个请求同时看到“无冲突”后都插入。')
    ctx.body('状态流转使用预约行锁：`SELECT ... FROM appointment WHERE id=? AND student_id/coach_id=? AND deleted=0 FOR UPDATE`，查不到统一返回 3001，状态不允许返回 3004/3005。这样并发审批和取消不会互相覆盖。当前方案仍有三个限制：没有数据库唯一约束兜底；没有排班表和教练容量概念；学员不能取消已确认预约。报告不会把应用层锁描述成已经通过真实数据库高并发压力测试。')

    ctx.h('5.6  聊天与消息', 2)
    ctx.body('聊天会话以 `(student_id, coach_id)` 唯一键保证一个师生对只有一个会话。创建会话前校验对端角色和 status、当前双方存在有效绑定；重复创建使用 `INSERT IGNORE` 后重查，避免并发重复插入。会话不存在或当前用户不是参与者统一返回 5001，不泄露会话是否存在。')
    ctx.body('消息分页使用游标：`lastId` 大于 0 时取该 ID 之后的消息并按 ID 正序返回，用于轮询新消息；`beforeId` 大于 0 时取该 ID 之前的消息，先按倒序查询再反转为正序，用于加载更早历史；两者都为空时取最新一页。`lastId` 与 `beforeId` 互斥，非法参数返回 1001；pageSize 默认 20、上限 50。返回顺序统一为正序，前端不需要再次排序。')
    ctx.body('发送消息在事务中锁定会话行，校验参与者和有效绑定，内容 trim 后限制 1–1000 码点，插入 `chat_message(is_read=0)`，更新 `chat_session.last_message`（截 500 码点）、`last_message_time` 和接收方 unread 计数。已读接口锁定会话，只更新对方发来的未读消息，并把自己的 unread 清零。这样未读数与消息状态在同一事务中变化，不会出现“消息已读但计数没清”的中间状态。')
    ctx.table(['操作', '关键条件', '数据变化', '异常'], [
        ['创建会话', '角色/状态/有效绑定', 'INSERT IGNORE + 重查', '1102/1106/3102'],
        ['拉取消息', '参与者；lastId/beforeId 互斥', '按游标返回正序消息', '1001/5001'],
        ['发送消息', '参与者；有效绑定；1–1000 码点', '插入消息；更新摘要和对方未读', '3102/5001'],
        ['标记已读', '参与者', '对方消息 is_read=1；本方 unread=0', '5001'],
    ], widths=[3.0, 5.6, 6.8, 3.2], font_size=8.8, cap='表5-4  聊天关键操作')

    ctx.h('5.7  AI/RAG 详细设计', 2)
    ctx.body('AI/RAG 有两条流程：知识入库和用户问答。知识入库由管理员接口 `POST /api/admin/rag/ingest` 触发，装配器只读 `question.analysis`（status=1、SUBJECT_1/4、解析非空）和 `knowledge_point.description`（非空）；分块器把文本按 800 字符上限切分，优先在句号、问号、分号或换行处断开，超长无边界时硬切，不重叠、不丢字符；EmbeddingClient 调用 BGE-M3 得到 1024 维向量；QdrantClient 使用确定性 point ID 做幂等 upsert。')
    ctx.figure('figures/rag_ingest.png', 6.2, '图5-3  RAG 知识入库流程')
    ctx.body('用户问答流程是：用户问题编码后检索 topK=5 的片段；RetrievalContextBuilder 在 1200 字符预算内构建上下文，预算包含声明和来源标签，放不下时先截断再停止；DeepSeek 根据上下文生成回答；实际注入的引用写入 `ai_chat_record.references_json`。检索成功但没有命中时，系统正常回答并保持引用为空；检索服务、配置或 Qdrant 故障时返回 4005，不返回伪造回答；入库任一步失败返回 4006。')
    ctx.figure('figures/rag_query.png', 5.4, '图5-4  RAG 问答检索流程')
    ctx.body('为什么需要 RAG，而不是直接让大语言模型回答？因为题库解析和知识点描述是本系统可控的领域知识，直接回答无法保证与本地题库一致，也无法记录引用。RAG 把回答限制在检索到的片段内，并留下可追溯的记录。RAG 不能解决什么问题？它不能保证检索一定命中，不能保证模型一定正确使用上下文，也不能替代判分和业务状态；因此系统仍保留确定性判分和错误码。')
    ctx.body('引用记录的用途是审计和后续展示：管理员可以知道某次回答注入了哪些题目解析或知识点；如果回答与题库不一致，可以追溯到具体片段。但当前 `AiRecordVO` 没有返回引用字段，前端也没有引用展示，这是明确未完成项。开发集合守卫只允许 `driving_exam_kb_dev`，没有生产集合切换和旧向量清理；真实 Qdrant、Embedding 和 DeepSeek 链路未在当前环境完成运行验收。')
    ctx.table(['场景', '行为', '用户可见结果', '证据状态'], [
        ['检索命中', '注入片段并生成回答', '正常回答，引用写入记录', '代码与单元测试；真实链路未验收'],
        ['检索无命中', '不注入片段，正常回答', '回答可读，引用为空', '代码路径；真实链路未验收'],
        ['检索基础设施故障', '返回 4005', '提示检索暂不可用，不伪装回答', '代码路径；真实故障未验收'],
        ['入库失败', '返回 4006', '提示入库失败，不部分写入', '代码路径；真实 Qdrant 未验收'],
    ], widths=[3.4, 5.0, 5.8, 4.4], font_size=8.8, cap='表5-5  AI/RAG 场景与失败语义')

    ctx.h('5.8  管理员功能与 Excel 导入', 2)
    ctx.body('管理员端承担平台基础数据维护：用户列表/详情/创建/更新/状态；师生绑定；知识点树和描述；题库 CRUD、状态和 Excel 导入；试卷 CRUD/状态；全量预约只读；AI 问答统计。用户状态只接受 0/1；知识点树返回 description，编辑可回显并清空；题目进入考试后冻结；试卷被考试引用后不能修改。Excel 文件解析使用 Apache POI[14]。')
    ctx.body('Excel 导入是管理员端最复杂的写操作。模板固定 14 列：科目编码、知识点 ID、知识点名称、题型、题干、选项 A–D、正确答案、解析、难度、媒体 URL、状态；表头必须逐列同名同序。文件只接受 `.xlsx/.xls`，不超过 10MB，有效行不超过 2000；超过限制直接拒绝。')
    ctx.figure('figures/excel_import.png', 12.0, '图5-5  Excel 题目导入流程')
    ctx.body('图 5-5 展示了导入的完整校验链。后端逐行解析必填字段：科目、知识点 ID、题型、题干、答案；知识点 ID 必须是数字；难度默认 1、状态默认 1；判断题不读选项，其余题型收集非空 A–D；然后调用与单条编辑相同的 `AdminService.validateQuestion`，检查科目合法、题型为 SINGLE/MULTIPLE/JUDGE、知识点存在且科目匹配、难度 1–5、状态 0/1、判断题答案合法、选择题至少两个选项且键 A–D 唯一、单选答案单键、多选至少两个且有效不重复。')
    ctx.body('语义校验通过后，系统检查文件内重复键 `subjectCode + 题干` 和题库已有同科目同题干；任何重复都拒绝。所有行通过后才在 `@Transactional` 中逐条创建题目；任一行解析或校验失败，`success=0`，不写入任何数据，并返回 `total/success/failed/errors[]`，每条错误包含 Excel 行号、字段和原因。这样避免了“前几行成功、后几行失败”的部分写入。')
    ctx.table(['错误场景', '检测点', '返回/处理'], [
        ['扩展名不支持', '文件名后缀', '拒绝上传，提示仅支持 xlsx/xls'],
        ['文件超过 10MB', 'multipart 大小', '参数错误，提示大小限制'],
        ['表头缺失/顺序错误', '第 1 行逐列比对', 'row=1、field=表头、原因'],
        ['科目/知识点缺失', '逐行必填与存在性', '对应 row/field/message'],
        ['题型/选项/答案不合法', 'validateQuestion', '对应 row/field/message'],
        ['文件内或库内重复', 'subjectCode + 题干', '拒绝整批，success=0'],
        ['任意行错误', '全量预校验', '不写任何题目，事务不开始或回滚'],
    ], widths=[4.2, 5.8, 7.0], font_size=8.6, cap='表5-6  Excel 导入典型错误')

    ctx.h('5.9  详细设计小结', 2)
    ctx.body('本章的核心设计决策可以归纳为表 5-7。每一项决策都对应一个具体风险，而不是为了增加技术复杂度。')
    ctx.table(['设计决策', '解决的风险', '实现手段', '剩余限制'], [
        ['JWT + 账号状态复查', '禁用账号/角色变更后旧 token 继续访问', '拦截器回读 sys_user', '无服务端撤销/刷新令牌'],
        ['考试条件更新 + 单事务结算', '重复交卷、部分写入', '`ONGOING → 终态`；ExamFinalizer', '历史脏数据不自动修复'],
        ['服务端超时任务', '客户端关闭后考试不结束', '定时扫描 + 同一结算器', '依赖调度器运行'],
        ['预约教练行锁 + 预约行锁', '并发双预约/状态覆盖', '事务 + `FOR UPDATE`', '无数据库唯一约束'],
        ['聊天游标 + 事务未读', '消息重复/未读不一致', 'lastId/beforeId；锁会话行', '轮询而非推送'],
        ['RAG 故障码', '把检索故障伪装成正常回答', '4005/4006；无命中与故障分离', '真实链路未验收'],
        ['Excel 全量校验后写入', '部分导入/脏数据', '逐行语义校验 + 事务', '模板固定；无异步导入'],
    ], widths=[4.3, 5.0, 5.3, 4.2], font_size=8.4, cap='表5-7  详细设计决策与限制')

    # ---------------- Chapter 6 ----------------
    ctx.page_break()
    ctx.h('6  系统实现', 1)
    ctx.h('6.1  开发环境与工程结构', 2)
    ctx.table(['项目', '版本/说明'], [
        ['后端', 'Java 17、Spring Boot 3.2.5、Maven 多模块'],
        ['数据访问', 'MyBatis-Plus 3.5.7、MySQL 8、HikariCP'],
        ['前端', 'Vue 3.4、TypeScript、Vite 5、Element Plus 2.7、Pinia、Vue Router 4'],
        ['身份认证', 'JWT（HS256，jjwt）、BCrypt'],
        ['AI/RAG', 'DeepSeek、SiliconFlow BGE-M3、Qdrant 1.19.2'],
        ['部署', 'Docker Compose、MySQL 8、Nginx 1.27、Qdrant 数据卷'],
    ], widths=[3.4, 12.2], font_size=8.8, cap='表6-1  系统环境')
    ctx.body('后端分为 common、pojo、server 三个模块。common 放统一响应、错误码、业务异常和全局异常处理；pojo 放实体、DTO 和 VO；server 放 Controller、Service、Mapper、拦截器、定时任务和集成客户端。前端按 `layout`、`views`、`components`、`api`、`store`、`types`、`utils` 分层。三套角色 Layout 和页面目录让学员、教练、管理员的路由和菜单互不干扰。')

    ctx.h('6.2  后端实现', 2)
    ctx.body('后端共有 14 个 Controller、88 个方法级映射。Controller 只做三件事：接收并校验请求参数、从 request attribute 取当前用户、调用 Service 并包装统一响应。业务规则放在 Service，SQL 放在 Mapper。DTO 用于接收请求，VO 用于返回前端；实体不直接暴露给 Controller，避免把数据库字段和前端契约耦合。')
    ctx.table(['Controller', '基础路径', '主要职责'], [
        ['AuthController', '/api/auth', '登录、当前用户、资料、密码'],
        ['StudentStudyController', '/api/student/study', '知识点、顺序/章节/随机题、作答'],
        ['StudentWrongBookController', '/api/student/wrong-book', '错题列表、汇总、重练、掌握'],
        ['AnalysisController', '/api/student/analysis', '学情总览、知识点、趋势'],
        ['ExamController', '/api/student/exam', '试卷、开始/恢复、暂存、判题、交卷、报告、历史'],
        ['StudentAppointmentController', '/api/student/appointment', '教练列表、创建、列表、详情、取消'],
        ['StudentChatController', '/api/student/chat', '会话、消息、发送、已读'],
        ['AiChatController', '/api/student/ai', '提问、会话、消息、删除'],
        ['CoachStudentController', '/api/coach/students', '学员、绑定、学情、学习数据、错题、AI 建议/确认'],
        ['CoachAppointmentController', '/api/coach/appointments', '预约列表、通过、拒绝、完成'],
        ['CoachChatController', '/api/coach/chat', '教练侧会话、消息、发送、已读'],
        ['AdminController', '/api/admin', 'Dashboard、AI 统计、用户、绑定、知识点、题库/Excel、试卷'],
        ['AdminAppointmentController', '/api/admin/appointments', '全量预约只读'],
        ['AdminRagController', '/api/admin/rag', '知识库入库'],
    ], widths=[4.8, 5.4, 7.0], font_size=8.2, cap='表6-2  后端 Controller 与职责')
    ctx.body('统一响应和异常处理是后端实现的基础设施。`ApiResponse` 包含 code、message、data；`PageResult` 包含 total、pages、pageNum、pageSize、records。业务异常由 `GlobalExceptionHandler` 转成统一 JSON；参数校验失败返回 1001；401/403 使用真实 HTTP 状态；上传超限会提示 10MB。这样前端不需要为每个接口写不同的错误处理。')
    ctx.body('DTO/VO 的使用让接口契约更稳定。例如创建预约使用 `AppointmentCreateDTO`，返回列表使用 `AppointmentVO`，VO 会补充学员/教练姓名而不暴露实体；考试报告使用专门的报告 VO，包含逐题正确答案和解析；AI 记录 VO 只返回前端需要的字段。代价是 DTO/VO 数量增加，需要在实体、DTO、VO 之间转换，但换来的是接口字段可控和测试可读。')

    ctx.h('6.3  前端实现', 2)
    ctx.body('前端共有 27 条角色内页面路由，对应 24 个不同的角色页面组件。学员端 13 个路由，覆盖工作台、科目首页、刷题、错题、AI、学情、聊天、个人中心、考试列表/答题/报告/记录、预约；教练端 5 个，覆盖工作台、预约、学员、聊天、个人中心；管理员端 9 个，覆盖工作台、用户、绑定、知识点、题库、试卷、预约、AI 统计、个人中心。另有登录、404 和少量兜底页面。')
    ctx.table(['层级', '路径示例', '组件/职责'], [
        ['学员', '/student/dashboard、/student/study、/student/exam/:examId', '工作台、刷题、答题/报告/记录、预约、聊天、AI'],
        ['教练', '/coach/dashboard、/coach/students、/coach/appointments', '工作台、学员管理、预约处理、聊天'],
        ['管理员', '/admin/dashboard、/admin/questions、/admin/papers', '工作台、用户/绑定/知识点/题库/试卷/预约/统计'],
        ['公共', '/login、/404、/student/chat、/coach/chat', '登录、错误页、复用 ChatView 的聊天页'],
    ], widths=[2.2, 7.6, 7.0], font_size=8.8, cap='表6-3  前端主要路由')
    ctx.body('前端 API 层按业务模块封装：`auth`、`study`、`wrongBook`、`exam`、`appointment`、`chat`、`ai`、`analysis`、`coachStudent`、`admin`。`src/utils/request.ts` 统一设置 baseURL、15 秒超时、Authorization 头和响应解包；`code===200` 返回 data，401 清理本地登录态并跳转登录页，403 提示无权限，其他错误显示后端 message。模板下载绕过普通拦截器，使用原生 axios + blob + Bearer，以保留二进制响应。')
    ctx.body('Pinia 的 `user` store 保存 token 和用户信息；`practice` store 保存题目、当前索引、判分结果、模式、分页、加载/提交状态，并负责预加载下一题；`aiChat` store 保存消息、会话列表和当前 sessionId。AI 会话 ID 单独持久化在 localStorage 中，登录 token 和用户信息也使用独立 key，避免刷新后丢失会话。')
    ctx.body('页面状态处理是前端实现的重点。学员工作台用 `Promise.allSettled` 并发请求多个接口，单个区块失败只显示该区块的“加载失败”，不把失败当成 0；教练工作台区分待处理/已确认预约和未读消息；管理员工作台分别加载概览、角色分布、待确认预约和最近预约。考试列表有 loading、失败重试、空列表和重复开始保护；考试报告有逐题空态和答对/答错/未答标记；预约页面有请求序号防旧响应覆盖；聊天用 AbortController 和会话序号隔离，轮询连续失败 3 次后停止并提供重试。')
    ctx.body('学员、教练和管理员主要页面的实际展示位置见 6.6 节；当前为空白占位，待补真实截图，不能把占位框当作页面验收结果。')

    ctx.h('6.4  关键业务的设计—实现对应', 2)
    ctx.body('第 5 章解释设计，本节说明设计如何落到代码。表 6-4 给出对应关系，避免把设计内容原样复制。')
    ctx.table(['设计点', '实现位置', '实现效果'], [
        ['JWT 状态复查', 'JwtInterceptor + JwtUtil', '每次请求回读 sys_user；禁用/角色变更返回 401'],
        ['路径角色规则', 'PathRoleRule + WebMvcConfig', '学员/教练/管理员 API 前缀隔离'],
        ['考试开始/结算', 'ExamServiceImpl.start；ExamFinalizer.finalize', '锁试卷行；条件更新抢占终态；单事务写四表'],
        ['考试超时', 'ExamTimeoutScheduler + MockExamMapper', '每 60 秒扫描到期 ONGOING，调用同一结算器'],
        ['预约并发', 'AppointmentServiceImpl.createAppointment；UserMapper.selectByIdForUpdate', '锁教练行；冲突检查和插入在同一事务'],
        ['预约状态流转', 'AppointmentServiceImpl + AppointmentMapper', '预约行锁；非法状态返回 3004/3005'],
        ['聊天游标/未读', 'ChatServiceImpl + ChatSessionMapper', 'lastId/beforeId；锁会话；未读与消息同事务'],
        ['RAG 入库/检索', 'KnowledgeIngestService、KnowledgeRetriever、QdrantClient', '800 字符分块；topK=5；1200 字符上下文；4005/4006'],
        ['Excel 原子导入', 'AdminQuestionExcelService + AdminService.validateQuestion', '全量校验后事务写入；错误行号/字段/原因'],
    ], widths=[4.0, 7.2, 5.6], font_size=8.4, cap='表6-4  设计点到实现位置')
    ctx.body("以考试结算为例，设计中的“条件更新抢占终态”不是抽象描述，而是 `ExamFinalizer` 中一条带 `status='ONGOING'` 条件的 UPDATE；结算器是独立 Bean，确保 `@Transactional` 通过代理生效。以预约为例如，设计中的“教练行锁”落在 `UserMapper.selectByIdForUpdate`，而不是在 Service 中做一次普通查询后再判断。以聊天为例，未读数不是前端计算，而是发送事务中更新 `chat_session` 的计数器。")
    ctx.body('Excel 导入的实现也对应设计中的原子性：Controller 接收 multipart 文件，Service 先检查扩展名、大小和表头，再逐行解析并调用与单条编辑相同的校验；所有行通过后才进入事务。错误返回结构是 `{total, success, failed, errors:[{row, field, message}]}`，前端根据 `failed` 提示“本次未写入任何数据”。')

    ctx.h('6.5  部署实现', 2)
    ctx.body('前端 Dockerfile 使用 node:20-alpine 执行 `npm ci` 和 `npm run build`，产物复制到 nginx:1.27-alpine；Nginx 配置 history 回退和 `/api/` 反向代理[20]。后端镜像使用 Java 17 运行 Spring Boot jar，通过环境变量读取数据库、JWT 和 AI 配置。Compose 中 backend 依赖 MySQL healthy，frontend 依赖 backend healthy；MySQL 和 Qdrant 使用命名卷。')
    ctx.body('部署实现有两个容易忽略的细节。第一，前端本地 `dist` 不会自动进入镜像，更新页面后必须重建 frontend 镜像，否则浏览器仍加载旧资源；第二，MySQL 初始化 SQL 只在空数据卷首次启动时执行，已有卷不会自动执行新的建表或种子脚本。报告中把这些列为部署限制，而不是让读者误以为 `docker compose up` 会自动完成所有迁移。')

    ctx.h('6.6  系统页面展示（待补真实截图）', 2)
    ctx.body('本节为实际系统页面预留展示位置。以下 16 个区域均为空白占位框，不是系统截图，也不代表浏览器验收已经完成；后续插入真实截图时，请删除框内提示文字，保留图题。截图只用于展示页面形态，不能仅凭页面显示正常推断后端权限、并发或外部 AI 链路已经通过测试。')
    ctx.body('截图建议使用 1600×900 或同比例的桌面浏览器窗口，保持同一浏览器、同一缩放比例；页面中的账号、姓名、手机号等敏感信息应替换为演示数据。每张截图插入后应与对应功能说明、图题放在同一页，避免图题与图片分离。')
    ctx.table(['图号', '页面', '角色', '对应路由/说明'], [
        ['图6-1', '登录页面', '公共', '/login；三角色选择'],
        ['图6-2', '学员工作台', '学员', '/student/dashboard'],
        ['图6-3', '学员刷题页面', '学员', '/student/study'],
        ['图6-4', '学员错题本', '学员', '/student/wrong-book'],
        ['图6-5', '学员模拟考试（答题页）', '学员', '/student/exam/:examId'],
        ['图6-6', '学员预约页面', '学员', '/student/appointment'],
        ['图6-7', '学员 AI 问答页面', '学员', '/student/ai'],
        ['图6-8', '教练工作台', '教练', '/coach/dashboard'],
        ['图6-9', '教练学员管理', '教练', '/coach/students'],
        ['图6-10', '教练预约管理', '教练', '/coach/appointments'],
        ['图6-11', '聊天/消息页面', '学员/教练', '/student/chat 或 /coach/chat'],
        ['图6-12', '管理员工作台', '管理员', '/admin/dashboard'],
        ['图6-13', '管理员用户管理', '管理员', '/admin/users'],
        ['图6-14', '管理员题库管理', '管理员', '/admin/questions'],
        ['图6-15', '管理员试卷管理', '管理员', '/admin/papers'],
        ['图6-16', '管理员预约查看', '管理员', '/admin/appointments'],
    ], widths=[2.0, 4.6, 2.4, 7.0], font_size=8.0, cap='表6-5  真实页面截图预留清单')

    def shot(title, desc, fig_no, group=None):
        ctx.page_break()
        if group:
            ctx.h(group, 3)
        ctx.screenshot_placeholder(title, desc, fig_no, height_cm=8.8)

    shot('登录页面', '对应路由 `/login`。展示三角色选择、账号密码输入和登录按钮；截图时使用演示账号，不出现真实密码。', '图6-1', '6.6.1  登录与学员端')
    shot('学员工作台', '对应路由 `/student/dashboard`。展示科目一/四进度、错题、待确认预约、未读消息和快捷入口。', '图6-2')
    shot('学员刷题页面', '对应路由 `/student/study`。展示题干、选项、答题卡和提交按钮；注意不要截出标准答案或解析。', '图6-3')
    shot('学员错题本', '对应路由 `/student/wrong-book`。展示待攻克/高频错/已掌握分类、错题列表和重练入口。', '图6-4')
    shot('学员模拟考试（答题页）', '对应路由 `/student/exam/:examId`。展示倒计时、题目、答题卡和交卷入口；如更希望展示结果，可替换为考试报告页。', '图6-5')
    shot('学员预约页面', '对应路由 `/student/appointment`。展示教练、日期、固定时段、预约状态和取消入口。', '图6-6')
    shot('学员 AI 问答页面', '对应路由 `/student/ai`。展示提问输入、回答内容、历史会话入口；不要伪造模型回答。', '图6-7')

    shot('教练工作台', '对应路由 `/coach/dashboard`。展示绑定学员、待处理/已确认预约、未读消息和快捷操作。', '图6-8', '6.6.2  教练端')
    shot('教练学员管理', '对应路由 `/coach/students`。展示学员列表、当前科目、学习数据和 AI 建议/确认入口。', '图6-9')
    shot('教练预约管理', '对应路由 `/coach/appointments`。展示待确认预约、通过/拒绝/完成操作和状态筛选。', '图6-10')
    shot('聊天/消息页面', '对应路由 `/student/chat` 或 `/coach/chat`。展示会话列表、消息气泡、未读数和输入框；学员端与教练端可二选一。', '图6-11')

    shot('管理员工作台', '对应路由 `/admin/dashboard`。展示用户总数、启用题目、预约总数、AI 问答历史、待确认预约和快捷入口。', '图6-12', '6.6.3  管理员端')
    shot('管理员用户管理', '对应路由 `/admin/users`。展示用户列表、角色/状态筛选、编辑和状态切换入口。', '图6-13')
    shot('管理员题库管理', '对应路由 `/admin/questions`。展示题目列表、科目/知识点/题型筛选、Excel 导入入口；如需要也可替换为知识点管理页。', '图6-14')
    shot('管理员试卷管理', '对应路由 `/admin/papers`。展示试卷列表、题目数量、总分/及格分/时长、状态和组卷入口。', '图6-15')
    shot('管理员预约查看', '对应路由 `/admin/appointments`。展示全量预约、学员/教练、日期时段和状态；该页面只读。', '图6-16')

    ctx.page_break()
    ctx.h('6.7  实现中的工程取舍', 2)
    ctx.body('实现过程中做了几项取舍。第一，没有引入 Spring Security，而是用轻量 JWT 拦截器，换取路径规则和错误码的直观性，代价是安全能力需要自行实现。第二，没有使用 JPA，而是用 MyBatis-Plus 显式 SQL，换取事务和锁的可审查性，代价是部分 CRUD 需要手写。第三，没有把 AI 调用放进考试/预约事务，避免外部服务超时拖长数据库事务；AI 失败不改变业务状态。第四，没有为预约增加数据库唯一约束，而是先完成应用层锁和测试，数据库约束作为后续迁移项。第五，前端没有引入端到端测试，先保证类型检查和构建，浏览器验收留到具备环境时执行。')
    ctx.body('这些取舍不是“最优方案”，而是课程设计阶段在时间、环境和可验证性之间的选择。报告在第 7 章说明哪些取舍已有测试证据，第 8 章说明它们带来的剩余风险。')
