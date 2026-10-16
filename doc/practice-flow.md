# 刷题模块 · 完整链路图

> 模块：学员端「科目一 / 科目四」刷题（顺序 / 章节 / 随机练习 + 判分 + 错题归集）
> 归档：源文件 `doc/practice-flow.md`，渲染图 `photo/design/practice-flow.png`

## 一、链路图（Mermaid）

```mermaid
flowchart TD
    A[学员进入刷题页] --> B[选择科目 SUBJECT_1 / SUBJECT_4]
    B --> C["加载知识点树<br/>GET /api/student/study/knowledge-points"]
    C --> D{选择练习模式}
    D -->|顺序练习| E1["GET /questions/sequence<br/>按题目 id 分页"]
    D -->|章节练习| E2["GET /questions/chapter<br/>按 knowledgePointId"]
    D -->|随机练习| E3["GET /questions/random<br/>随机抽取 N 题"]
    E1 --> F["装配 QuestionVO<br/>（剔除 answer / analysis）"]
    E2 --> F
    E3 --> F
    F --> G[前端逐题展示题干 + 选项]
    G --> H["学员作答 → POST /study/answer<br/>提交 questionId + userAnswer"]
    H --> I["答案归一化<br/>去空格 / 转大写 / 排序 / 去重"]
    I --> J{按题型判分<br/>SINGLE / MULTIPLE / JUDGE}
    J --> K["写 answer_record<br/>source = PRACTICE, exam_id = null"]
    K --> L{是否答对}
    L -->|是| M["返回 AnswerResultVO<br/>isCorrect = true"]
    L -->|否| N["upsert wrong_book<br/>wrong_count+1, mastered=0"]
    N --> M2["返回 AnswerResultVO<br/>isCorrect=false + 正确答案 + 解析"]
    M --> O[前端展示对错与解析]
    M2 --> O
    O --> P{继续下一题？}
    P -->|是| G
    P -->|否| Q[查看答题卡 / 结束本轮]

    R[错题本重练] --> S[从 wrong_book 抽取错题]
    S --> G
    N -. "重练答对" .-> T["wrong_book.mastered = 1"]

    style N fill:#FDEBD0,stroke:#B9770E
    style M2 fill:#FADBD8,stroke:#B03A2E
    style M fill:#D5F5E3,stroke:#1E8449
    style T fill:#D5F5E3,stroke:#1E8449
```

## 二、链路要点

1. **两段式防作弊**：拉题阶段返回的 `QuestionVO` **不含** `answer` 与 `analysis`；正确答案与解析只在
   `POST /study/answer` 判分后由 `AnswerResultVO` 下发。
2. **判分是唯一的写入口**：`answer_record` 与 `wrong_book` 的写入**只发生在判分接口**，刷题接口纯查询。
3. **错题归集是 upsert**：以 `(user_id, question_id)` 为唯一键，已存在则 `wrong_count+1`、`mastered` 置 0；
   不存在则新增（`wrong_count=1`）。
4. **三种练习模式共用一条链路**：差异只在"取题目"这一步（排序/过滤/随机），从装配 VO 往后的判分、
   记录、错题归集完全复用。
5. **考试复用**：`answer_record.source` 区分 `PRACTICE` / `EXAM`，判分组件对两处通用（考试判分在后续模块接入）。
