# 【Day 39 工作日志】

## 一、日期与目标

- 日期：2026-10-20
- 目标：完成《综合课程设计报告》的 Word 正文、封面/成绩评定表合并、图表编排和最终版式检查。
- 范围：只新增/修改 `doc/report/` 下的报告、图表、生成脚本与说明；未修改业务源码、数据库结构、初始化 SQL、`.env` 或 Docker 数据。

## 二、模板与结构

- 读取 `参考模板.docx`：正文约 2.5/2.5/2.5/2.0 cm 页边距，宋体正文 12pt，标题层级为摘要、Abstract、绪论、关键技术、需求分析、总体设计、详细设计、系统实现、系统测试、总结、致谢、参考文献、附录。
- 读取 `综合课设封面及评分表V1.doc`：转换为 `cover.docx`，共 3 页，包含封面、成绩评定表和评阅签字区域。
- 最终文档以 `cover.docx` 为底座，追加新 section 编写正文；没有修改封面字段、评分项目、比例或签名栏。

## 三、正文完成内容

- 摘要、Abstract、关键词、目录。
- 第 1 章 绪论：背景、目的意义、技术/经济/操作安全/法律可行性、主要工作、论文结构。
- 第 2 章 关键技术：Vue 3、Spring Boot、MyBatis-Plus、JWT、RAG/BGE-M3/Qdrant、Docker Compose。
- 第 3 章 需求分析：三角色、功能需求、核心流程、非功能需求、范围边界。
- 第 4 章 总体设计：总体架构图、功能模块表、路径权限、部署设计、E-R 图、接口约定。
- 第 5 章 详细设计：16 张表、认证、练习/错题/学情、考试结算与超时、预约锁、聊天游标、RAG 入库/检索、管理员与 Excel 导入。
- 第 6 章 系统实现：环境、后端/前端结构、三端工作台、关键实现、Docker 部署。
- 第 7 章 系统测试：Day34 108 项自动化测试的历史执行证据、管理员/Excel 18 项、历史 Apifox 报告、前端 type-check/build、未执行运行时验收、缺陷与风险。
- 第 8 章 安全性、可靠性与可扩展性分析。
- 第 9 章 总结与展望、致谢、参考文献、附录 A/B/C。
- 参考文献 15 条，均为官方文档、RFC、arXiv 论文或可核验教材；未编造文献。

## 四、图表

已在 `doc/report/figures/` 生成并插入：

- 图 4-1 系统总体架构（`architecture.png`）
- 图 4-2 核心实体关系 E-R（`er.png`）
- 图 5-1 预约与模拟考试状态流转（`states.png`）
- 图 5-2 RAG 知识入库与问答检索流程（`rag_ingest.png` + `rag_query.png`）

表格：表 3-1 角色、表 4-1 功能模块、表 5-1 数据库表、表 6-1 系统环境、表 7-1 测试覆盖、表 7-2 历史 Apifox 证据、表 A-1 接口前缀。

## 五、生成与校验

实际执行：

```text
python3 doc/report/build_report.py
python3 /mnt/.claude/skills/docx/scripts/office/soffice.py --headless --convert-to pdf doc/report/综合课程设计报告.docx
python3 doc/report/make_toc.py 综合课程设计报告.pdf toc_entries.json
```

结果：

- 最终 `综合课程设计报告.docx` 与 `综合课程设计报告.pdf` 已生成。
- 总页数 44 页：封面/评分表 3 页 + 正文 41 页。
- 目录 60 条，页码由最终 PDF 版式反填；最终目录页码与正文页码复核一致。
- 封面 3 页与单独转换的 `cover.pdf` 逐页渲染，PNG 像素 MD5 完全一致，确认封面未被改动。
- 检查了目录、章节分页、表格跨页表头、图题、参考文献和附录。
- 生成脚本保留在 `doc/report/build_report.py`、`doc/report/make_toc.py`，目录数据在 `doc/report/toc_entries.json`。

## 六、证据边界

- Day34 的 108 项后端测试是历史执行记录，不是本轮重新执行；正文已明确标注。
- 前端 type-check/build 成功仅代表类型和打包通过，不等于浏览器页面/接口验收通过。
- 当前环境没有 Docker CLI/socket、MySQL 客户端、浏览器和完整 AI Key，因此未声称完成 Docker 当前镜像、真实 MySQL 并发、Qdrant/DeepSeek 调用和完整浏览器验收。
- 封面中的班级、姓名、学号、指导教师、职称、时间、成绩、评语和签名未提供，仍为空白或待填写。
- 未修改业务代码、数据库结构、初始化 SQL、`.env` 或 Docker 数据卷。

## 七、交付文件

- `doc/report/综合课程设计报告.docx`
- `doc/report/综合课程设计报告.pdf`
- `doc/report/综合课程设计报告-资料盘点与编写说明.md`
- `doc/report/figures/*.dot`、`*.png`
- `doc/report/build_report.py`、`make_toc.py`、`toc_entries.json`
- 本日志：`doc/Day/Day39-工作日志.md`
