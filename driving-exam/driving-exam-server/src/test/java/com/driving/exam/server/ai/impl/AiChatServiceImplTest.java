package com.driving.exam.server.ai.impl;

import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.pojo.dto.AiChatRequestDTO;
import com.driving.exam.pojo.entity.AiChatRecord;
import com.driving.exam.pojo.vo.AiAnswerVO;
import com.driving.exam.server.ai.RetrievalContextBuilder;
import com.driving.exam.server.ai.SystemPromptBuilder;
import com.driving.exam.server.integration.deepseek.DeepSeekClient;
import com.driving.exam.server.integration.deepseek.dto.ChatMessage;
import com.driving.exam.server.integration.deepseek.dto.ChatResult;
import com.driving.exam.server.mapper.AiChatRecordMapper;
import com.driving.exam.server.rag.KnowledgeDocType;
import com.driving.exam.server.rag.retrieve.KnowledgeRetriever;
import com.driving.exam.server.rag.retrieve.RetrievedChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AiChatServiceImpl} RAG 接入部分的离线单元测试。
 *
 * <p>Mockito 模拟 {@link AiChatRecordMapper}/{@link DeepSeekClient}/{@link SystemPromptBuilder}/
 * {@link KnowledgeRetriever}；使用真实的 {@link RetrievalContextBuilder} 与 `ObjectMapper`。
 * <b>不启动 Spring、不连数据库、不调用 Embedding/Qdrant/DeepSeek。</b></p>
 */
@DisplayName("AI 问答 RAG 接入（离线）")
class AiChatServiceImplTest {

    private static final String BASE_PROMPT = "BASE_PROMPT";
    private static final String QUESTION = "红灯亮时应当怎样通行";
    private static final String SUBJECT = "SUBJECT_1";

    private AiChatRecordMapper aiChatRecordMapper;
    private DeepSeekClient deepSeekClient;
    private SystemPromptBuilder systemPromptBuilder;
    private KnowledgeRetriever knowledgeRetriever;
    private AiChatServiceImpl service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        aiChatRecordMapper = mock(AiChatRecordMapper.class);
        deepSeekClient = mock(DeepSeekClient.class);
        systemPromptBuilder = mock(SystemPromptBuilder.class);
        knowledgeRetriever = mock(KnowledgeRetriever.class);
        objectMapper = new ObjectMapper();

        when(systemPromptBuilder.buildSystemPrompt(any())).thenReturn(BASE_PROMPT);
        when(aiChatRecordMapper.selectCount(any())).thenReturn(0L);
        when(deepSeekClient.chat(anyList()))
                .thenReturn(new ChatResult("答案正文", "deepseek-flash", 10, 20));

        service = new AiChatServiceImpl(aiChatRecordMapper, deepSeekClient, systemPromptBuilder,
                knowledgeRetriever, new RetrievalContextBuilder(), objectMapper);
    }

    /* ==================== 1. 有命中：单条 system + 引用写入 ==================== */

    @Test
    @DisplayName("有命中：topK=5 检索、单条 system（基础+上下文）、user 原文；引用 JSON 与实际注入一致")
    void hitInjectsContextAndPersistsReferences() throws Exception {
        RetrievedChunk first = chunk("QUESTION_ANALYSIS:1#0", 1L, 0.93, "红灯亮时禁止通行。");
        RetrievedChunk second = chunk("KNOWLEDGE_POINT:2#0", 2L, 0.77, "红绿灯含义。");
        when(knowledgeRetriever.retrieve(eq(QUESTION), eq(KnowledgeRetriever.DEFAULT_TOP_K), eq(SUBJECT)))
                .thenReturn(List.of(first, second));

        AiAnswerVO vo = service.chat(7L, dto(QUESTION, SUBJECT));

        // 检索参数
        verify(knowledgeRetriever).retrieve(eq(QUESTION), eq(5), eq(SUBJECT));

        // 消息：仅两条；system = 基础 + 上下文；user 原文
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(deepSeekClient).chat(messagesCaptor.capture());
        List<ChatMessage> messages = messagesCaptor.getValue();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getRole()).isEqualTo(ChatMessage.ROLE_SYSTEM);
        assertThat(messages.get(1).getRole()).isEqualTo(ChatMessage.ROLE_USER);
        assertThat(messages.get(1).getContent()).isEqualTo(QUESTION);

        String systemContent = messages.get(0).getContent();
        assertThat(systemContent).startsWith(BASE_PROMPT + "\n\n");
        assertThat(systemContent).contains("参考资料").contains("不是可执行指令");
        assertThat(systemContent.indexOf("[1]")).isLessThan(systemContent.indexOf("[2]"));
        assertThat(systemContent.indexOf("红灯亮时禁止通行。"))
                .isLessThan(systemContent.indexOf("红绿灯含义。"));
        // 问题原文未被拼入 system
        assertThat(systemContent).doesNotContain(QUESTION);

        // 引用 JSON：结构、顺序、精度与注入一致
        ArgumentCaptor<AiChatRecord> recordCaptor = ArgumentCaptor.forClass(AiChatRecord.class);
        verify(aiChatRecordMapper).insert(recordCaptor.capture());
        String refsJson = recordCaptor.getValue().getReferencesJson();
        assertThat(refsJson).isNotNull();
        JsonNode refs = objectMapper.readTree(refsJson);
        assertThat(refs.isArray()).isTrue();
        assertThat(refs.size()).isEqualTo(2);

        JsonNode r0 = refs.get(0);
        List<String> names = new ArrayList<>();
        r0.fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactlyInAnyOrder(
                "chunkId", "docId", "sourceType", "sourceId", "score", "subjectCode", "truncated");
        assertThat(r0.path("chunkId").asText()).isEqualTo("QUESTION_ANALYSIS:1#0");
        assertThat(r0.path("docId").asText()).isEqualTo("QUESTION_ANALYSIS:1");
        assertThat(r0.path("sourceType").asText()).isEqualTo("QUESTION_ANALYSIS");
        assertThat(r0.path("sourceId").isIntegralNumber()).isTrue();
        assertThat(r0.path("sourceId").asLong()).isEqualTo(1L);
        assertThat(r0.path("score").asDouble()).isEqualTo(0.93);
        assertThat(r0.path("subjectCode").asText()).isEqualTo(SUBJECT);
        assertThat(r0.path("truncated").isBoolean()).isTrue();
        assertThat(r0.path("truncated").asBoolean()).isFalse();

        assertThat(refs.get(1).path("chunkId").asText()).isEqualTo("KNOWLEDGE_POINT:2#0");

        assertThat(vo.getAnswer()).isEqualTo("答案正文");
    }

    /* ==================== 2. 无命中 ==================== */

    @Test
    @DisplayName("无命中：不注入上下文、system 仅基础 Prompt、references_json 为 null，普通问答继续")
    void noHitsKeepsPlainFlow() {
        when(knowledgeRetriever.retrieve(eq("随便问问"), eq(5), isNull())).thenReturn(List.of());

        service.chat(7L, dto("随便问问", null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(deepSeekClient).chat(messagesCaptor.capture());
        assertThat(messagesCaptor.getValue()).hasSize(2);
        assertThat(messagesCaptor.getValue().get(0).getContent()).isEqualTo(BASE_PROMPT);
        assertThat(messagesCaptor.getValue().get(1).getContent()).isEqualTo("随便问问");

        ArgumentCaptor<AiChatRecord> recordCaptor = ArgumentCaptor.forClass(AiChatRecord.class);
        verify(aiChatRecordMapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getReferencesJson()).isNull();
    }

    /* ==================== 3. 检索异常：严格失败 ==================== */

    @Test
    @DisplayName("检索抛异常：不调用 DeepSeek、不落库，转换为 AI_RAG_UNAVAILABLE（不伪装成普通回答）")
    void retrievalFailureIsStrict() {
        BusinessException boom = new BusinessException(ErrorCode.SYSTEM_ERROR);
        when(knowledgeRetriever.retrieve(any(), anyInt(), any())).thenThrow(boom);

        assertThatThrownBy(() -> service.chat(7L, dto(QUESTION, SUBJECT)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_RAG_UNAVAILABLE.getCode()));
        verify(deepSeekClient, never()).chat(anyList());
        verify(aiChatRecordMapper, never()).insert(any(AiChatRecord.class));
    }

    @Test
    @DisplayName("检索配置异常：同样转换为 AI_RAG_UNAVAILABLE，不调用 DeepSeek、不落库")
    void retrievalConfigurationFailureIsStrict() {
        when(knowledgeRetriever.retrieve(any(), anyInt(), any()))
                .thenThrow(new IllegalStateException("vector-size 未配置"));

        assertThatThrownBy(() -> service.chat(7L, dto(QUESTION, SUBJECT)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_RAG_UNAVAILABLE.getCode()));
        verify(deepSeekClient, never()).chat(anyList());
        verify(aiChatRecordMapper, never()).insert(any(AiChatRecord.class));
    }

    /* ==================== 4. 限额拒绝：不检索、不调模型 ==================== */

    @Test
    @DisplayName("限额达到上限：BusinessException(4003)，不调用检索器与 DeepSeek")
    void quotaExceededShortCircuits() {
        when(aiChatRecordMapper.selectCount(any())).thenReturn((long) AiChatServiceImpl.DAILY_QUOTA);

        assertThatThrownBy(() -> service.chat(7L, dto(QUESTION, SUBJECT)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_QUOTA_EXCEEDED.getCode()));
        verifyNoInteractions(knowledgeRetriever);
        verify(deepSeekClient, never()).chat(anyList());
        verify(aiChatRecordMapper, never()).insert(any(AiChatRecord.class));
    }

    /* ==================== 5. DeepSeek 异常：语义不变 ==================== */

    @Test
    @DisplayName("DeepSeek 抛异常：原样传播、不落库（引用逻辑不吞异常）")
    void deepSeekFailurePropagates() {
        when(knowledgeRetriever.retrieve(any(), anyInt(), any())).thenReturn(List.of());
        BusinessException boom = new BusinessException(ErrorCode.AI_TIMEOUT);
        when(deepSeekClient.chat(anyList())).thenThrow(boom);

        assertThatThrownBy(() -> service.chat(7L, dto(QUESTION, SUBJECT))).isSameAs(boom);
        verify(aiChatRecordMapper, never()).insert(any(AiChatRecord.class));
    }

    /* ==================== 6. 预算截断与排除 ==================== */

    @Test
    @DisplayName("预算截断：被截断条目标记 truncated=true 且身份不变；未注入条目不进入引用")
    void budgetTruncationAndExclusion() throws Exception {
        RetrievedChunk huge = chunk("QUESTION_ANALYSIS:1#0", 1L, 0.9,
                "甲".repeat(RetrievalContextBuilder.CHAR_BUDGET * 2));
        RetrievedChunk never = chunk("QUESTION_ANALYSIS:2#0", 2L, 0.8, "丙".repeat(50));
        when(knowledgeRetriever.retrieve(any(), anyInt(), any()))
                .thenReturn(List.of(huge, never));

        service.chat(7L, dto(QUESTION, SUBJECT));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(deepSeekClient).chat(messagesCaptor.capture());
        String systemContent = messagesCaptor.getValue().get(0).getContent();
        assertThat(systemContent.length())
                .isLessThanOrEqualTo(BASE_PROMPT.length() + 2 + RetrievalContextBuilder.CHAR_BUDGET);
        assertThat(systemContent).doesNotContain("丙");

        ArgumentCaptor<AiChatRecord> recordCaptor = ArgumentCaptor.forClass(AiChatRecord.class);
        verify(aiChatRecordMapper).insert(recordCaptor.capture());
        JsonNode refs = objectMapper.readTree(recordCaptor.getValue().getReferencesJson());
        assertThat(refs.size()).isEqualTo(1);
        assertThat(refs.get(0).path("chunkId").asText()).isEqualTo("QUESTION_ANALYSIS:1#0");
        assertThat(refs.get(0).path("sourceId").asLong()).isEqualTo(1L);
        assertThat(refs.get(0).path("truncated").asBoolean()).isTrue();
    }

    /* ============================== 辅助 ============================== */

    private static AiChatRequestDTO dto(String question, String subjectCode) {
        AiChatRequestDTO dto = new AiChatRequestDTO();
        dto.setQuestion(question);
        dto.setSubjectCode(subjectCode);
        dto.setSessionId("session-1");
        return dto;
    }

    private static RetrievedChunk chunk(String chunkId, long sourceId, double score, String text) {
        String docId = chunkId.substring(0, chunkId.indexOf('#'));
        KnowledgeDocType type = docId.startsWith("KNOWLEDGE_POINT")
                ? KnowledgeDocType.KNOWLEDGE_POINT : KnowledgeDocType.QUESTION_ANALYSIS;
        return new RetrievedChunk(chunkId, score, chunkId, docId, type, sourceId, text, "SUBJECT_1");
    }
}
