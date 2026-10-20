# 【Day 40 工作日志】

## 一、日期与目标

- 日期：2026-10-20
- 目标：对《综合课程设计报告》进行实质性扩写，而不是润色或堆页数。
- 范围：只修改/新增 `doc/report/` 下的报告、图表、生成脚本和说明；未修改业务源码、数据库结构、初始化 SQL、`.env` 或 Docker 数据，未覆盖第一版报告。

## 二、前置核对

- 真实模板路径：`doc/detail/参考模板.docx`、`doc/detail/综合课设封面及评分表V1.doc`。
- 第一版报告：`doc/report/综合课程设计报告.docx`，44 页（封面 3 + 正文 41）。
- 核对数据库 dump、后端 Controller/Service/Mapper、前端路由/API/Store、Surefire 报告、Day30–39 日志和 Apifox 历史报告后开始扩写。

## 三、扩写结果

- 新文件：`doc/report/综合课程设计报告-扩写版.docx`，89 页（封面 3 + 正文 86）。
- 新增/扩充：
  - 第 1 章：业务链条、分散工具问题、LLM/RAG 边界、目标与范围、章节安排表。
  - 第 2 章：每项技术的职责、选型理由、协作、限制，新增选型比较表。
  - 第 3 章：角色权限表、12 个用例表、功能需求、业务规则、非功能需求、追踪表。
  - 第 4 章：模块职责、接口契约、权限三层、部署设计、E-R，新增认证流程图和权限表。
  - 第 5 章：数据库、认证、练习、考试、预约、聊天、RAG、Excel 的详细流程和设计取舍，新增练习/Excel 流程图。
  - 第 6 章：Controller/Service/Mapper、DTO/VO、前端路由/API/Store、设计与实现对应。
  - 第 7 章：45 个测试类、300 个 @Test、328 次执行、8 个 Surefire 批次、18 个代表用例、历史 Apifox 和未执行项。
  - 第 8 章：风险—措施—作用—剩余限制结构。
  - 第 9 章：成果、未完成验收、优先级表。
- 图表：8 个图题、32 个表题；参考文献由 15 条增至 20 条；目录 78 条。

## 四、核对与修正

- 将 Controller 数量从旧版“14 个 Controller、86 个映射”修正为 **14 个、88 个方法级映射**。
- 将测试章节从“108 项”扩展为 **45 个测试类、300 个 @Test、参数展开 328 次执行**；108 项明确为 Day34 批次，不称为全量一次通过。
- 明确 `DbConnectionGuardTest`、`ExamSettlementConcurrencyTest` 在上下文加载阶段因 DeepSeek 属性绑定报错，不是 MySQL 断言失败。
- 明确 `R24QdrantIntegrationIT` 因缺少 `SILICONFLOW_API_KEY` 失败，`DevKbIngestIT/R3/R4` 因 `RAG_DEV_INGEST` 未开启跳过。
- 前端页面规模按路由核定为 27 条角色内路由、24 个不同角色组件。

## 五、校验

实际执行：

```text
python3 doc/report/build_expanded.py
python3 /mnt/.claude/skills/docx/scripts/office/validate.py doc/report/综合课程设计报告-扩写版.docx
python3 /mnt/.claude/skills/docx/scripts/office/soffice.py --headless --convert-to pdf doc/report/综合课程设计报告-扩写版.docx
python3 doc/report/make_toc.py 综合课程设计报告-扩写版.pdf toc_entries_expanded.json
```

结果：

- `.docx` 结构校验全部通过；
- PDF 共 89 页，A4；
- 封面前 3 页与模板单独转换 PDF 逐页渲染，PNG 像素 MD5 完全一致；
- 目录 78 条，页码按最终版式反填并与正文复核一致；
- 正文无残留反引号、无替换字符；图表题注 40 个（8 图、32 表）。

## 六、未完成/待人工核对

- 封面班级、姓名、学号、指导教师、职称、时间、成绩、评语、签名仍待填写。
- Surefire 精确执行时刻只能从 mtime 推断；Apifox 两次异常低通过率原因待核实。
- Docker 当前镜像、真实 MySQL 并发、Qdrant/DeepSeek 真实链路、浏览器三角色验收仍未执行。
- 学校如调整模板格式，应以最新模板为准。

## 七、交付文件

- `doc/report/综合课程设计报告-扩写版.docx`
- `doc/report/综合课程设计报告-扩写版.pdf`
- `doc/report/综合课程设计报告-扩写说明.md`
- `doc/Day/Day40-工作日志.md`
- 第一版报告 `doc/report/综合课程设计报告.docx` 保留未动。
