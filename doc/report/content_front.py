def build(ctx):
    doc = ctx.doc
    p = doc.add_paragraph()
    p.alignment = 1  # center
    p.paragraph_format.space_before = 36
    p.paragraph_format.space_after = 18
    r = p.add_run('基于大语言模型的驾考智能辅导系统设计与实现')
    ctx._font(r, '黑体', 22, True)

    p = doc.add_paragraph()
    p.alignment = 1
    p.paragraph_format.space_after = 24
    r = p.add_run('综合课程设计报告（整合完善版）')
    ctx._font(r, '宋体', 14, False)

    ctx.h('摘    要', 1)
    ctx.abstract_body('机动车驾驶考试既包含道路交通安全法律、法规和文明驾驶常识等理论学习，也包含科目二、科目三的场地与道路训练。对学员而言，刷题、错题复习、模拟考试、练车预约和教练沟通并不是彼此独立的操作，而是围绕“发现薄弱点—针对性练习—验证学习效果—安排实车训练”形成的连续过程；对教练而言，只有同时掌握学员的答题情况、错题分布、模考成绩和预约安排，才能给出有针对性的指导。传统驾培场景中，这些环节往往分散在纸质资料、通用题库软件、聊天工具和线下排班表中，数据难以复用，过程也难以追踪。')
    ctx.abstract_body('本文以这一业务链条为出发点，设计并实现了一个面向学员、教练和管理员三类角色的驾考智能辅导系统。系统采用前后端分离架构：后端基于 Java 17、Spring Boot 3.2.5、MyBatis-Plus 3.5.7 和 MySQL 8，使用 JWT 完成身份认证，并通过路径角色规则、账号状态复查和业务归属校验共同构成访问控制；前端基于 Vue 3、TypeScript、Vite 和 Element Plus，为三类角色提供独立的工作台、路由和操作界面。系统实现了认证与个人中心、科目一/四练习、错题本、学情分析、模拟考试、预约、学员—教练聊天、AI 问答，以及教练学员管理和管理员用户、绑定、知识点、题库、试卷、预约查看、AI 统计等功能。')
    ctx.abstract_body('在关键业务上，系统没有停留在页面和接口的简单拼接。模拟考试使用“开始事务 + 试卷行锁”控制同卷并发，使用 `ONGOING → SUBMITTED/TIMEOUT` 的条件更新完成交卷抢占，并通过独立结算器写入作答记录、错题、成绩和科目进度；重复交卷返回既有报告，服务端定时任务负责处理客户端关闭后的超时考试。预约创建在事务中先锁定教练账号行，再检查有效绑定和时段冲突；预约状态流转使用预约行锁，避免并发审批互相覆盖。题库 Excel 导入采用“全量解析与语义校验后再事务写入”的方式，任何一行错误都不会产生部分写入。AI 问答通过 BGE-M3 生成向量、Qdrant 检索知识片段、DeepSeek 生成回答，并将检索失败与“无命中”区分为不同语义。')
    ctx.abstract_body('本文的主要工作包括：完成三类角色的需求分析和权限边界划分；设计 16 张业务表及其唯一约束；实现学习、考试、预约、聊天、AI 和管理员管理闭环；针对考试结算、预约并发、JWT 状态复查、Excel 导入和 RAG 故障处理补充一致性措施；建立单元/服务测试、历史接口测试和前端构建证据；并对尚未完成的 Docker、真实 MySQL 并发、外部 AI 服务和浏览器验收进行明确说明。本文严格区分“源码已实现”“自动化测试已执行”“历史接口报告”“仅设计未执行”和“运行验收未完成”，不以构建通过代替功能验收，也不虚构性能指标或用户规模。')
    ctx.kv('关键词：', '驾考辅导；大语言模型；检索增强生成；Spring Boot；Vue 3；JWT；数据一致性；软件工程')

    ctx.page_break()
    ctx.h('Abstract', 1)
    ctx.body('Driving-exam learning is not a set of isolated tasks. Learners move repeatedly among theory practice, wrong-question review, mock exams, training appointments, and communication with coaches. Coaches need a consolidated view of each student’s answers, weaknesses, mock results, and appointments in order to give targeted guidance. In many driving schools, however, these activities are scattered across paper materials, generic quiz applications, chat tools, and offline scheduling, which makes learning data difficult to reuse and progress difficult to trace.', en=True)
    ctx.body('This report designs and implements a driving-exam tutoring system for students, coaches, and administrators. The backend uses Java 17, Spring Boot 3.2.5, MyBatis-Plus 3.5.7, MySQL 8, and JWT authentication. Access control combines path-based role rules, a database re-check of account status and current role, and resource-ownership checks in business services. The frontend uses Vue 3, TypeScript, Vite, and Element Plus and provides separate workspaces for the three roles. The system covers authentication and profiles, subject practice, wrong-question review, learning analysis, mock exams, appointments, student-coach chat, AI question answering, coach student management, and administrative functions for users, bindings, knowledge points, question banks, papers, appointments, and AI statistics.', en=True)
    ctx.body('Several consistency mechanisms go beyond basic CRUD. Exam start uses a transaction and a row lock on the paper; settlement uses a conditional transition from ONGOING to SUBMITTED or TIMEOUT so that concurrent submissions produce a single result. A scheduled task settles expired exams when the client is closed. Appointment creation locks the coach row before checking bindings and slot conflicts, and appointment state transitions lock the appointment row. Question-bank Excel import validates the complete file before a transactional write and rejects the whole batch on any row-level error. AI answers use BGE-M3 embeddings, Qdrant retrieval, and DeepSeek generation; retrieval failure is reported with an explicit error code rather than being disguised as a normal answer.', en=True)
    ctx.body('The report documents requirements, architecture, detailed design, implementation, and testing, and it distinguishes clearly among implemented source code, executed automated tests, historical API reports, front-end build checks, and runtime acceptance that has not yet been performed. It does not claim production-scale performance or completed Docker, MySQL, browser, Qdrant, or DeepSeek acceptance.', en=True)
    ctx.kv('Keywords:', 'driving-exam tutoring; large language model; retrieval-augmented generation; Spring Boot; Vue 3; JWT; data consistency; software engineering')

    ctx.page_break()
    ctx.h('目    录', 1)
    ctx.toc()
    ctx.page_break()
