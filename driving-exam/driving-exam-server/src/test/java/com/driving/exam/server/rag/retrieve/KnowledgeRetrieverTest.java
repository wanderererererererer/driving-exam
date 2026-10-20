package com.driving.exam.server.rag.retrieve;

import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.server.integration.embedding.EmbeddingClient;
import com.driving.exam.server.integration.qdrant.QdrantClient;
import com.driving.exam.server.integration.qdrant.QdrantProperties;
import com.driving.exam.server.integration.qdrant.ScoredPoint;
import com.driving.exam.server.rag.KnowledgeDocType;
import com.driving.exam.server.rag.ingest.VectorConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link KnowledgeRetriever} 离线单元测试。
 *
 * <p>Mockito 模拟 {@link EmbeddingClient}/{@link QdrantClient}，使用真实的
 * {@link VectorConverter} 与 {@link QdrantProperties}：<b>不启动 Spring、不连接数据库/Qdrant、
 * 不调用 Embedding API、不访问网络</b>。</p>
 */
@DisplayName("R3 知识检索（离线）")
class KnowledgeRetrieverTest {

    private static final int DIM = 3;
    private static final String UUID_A = "a59fc217-8e4e-3c1c-942a-bf3dadcf8ffb";
    private static final String UUID_B = "550e8400-e29b-41d4-a716-446655440000";

    private EmbeddingClient embeddingClient;
    private QdrantClient qdrantClient;
    private QdrantProperties properties;
    private KnowledgeRetriever retriever;

    @BeforeEach
    void setUp() {
        embeddingClient = mock(EmbeddingClient.class);
        qdrantClient = mock(QdrantClient.class);
        properties = new QdrantProperties();
        properties.setVectorSize(DIM);
        properties.setDistance("Cosine");
        retriever = new KnowledgeRetriever(embeddingClient, qdrantClient, properties, new VectorConverter());
    }

    /* ==================== 1. 入参/配置校验（零依赖调用） ==================== */

    @Test
    @DisplayName("query 为 null/空串/纯空白 → IllegalArgumentException，且不调用任何依赖")
    void blankQueryRejected() {
        for (String query : new String[]{null, "", "   \t\n"}) {
            assertThatThrownBy(() -> retriever.retrieve(query, 5, null))
                    .as("空白 query 应被拒绝")
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(embeddingClient, qdrantClient);
    }

    @Test
    @DisplayName("topK 为 0/-1/21 → IllegalArgumentException，且不调用任何依赖")
    void invalidTopKRejected() {
        for (int topK : new int[]{0, -1, 21}) {
            assertThatThrownBy(() -> retriever.retrieve("红灯", topK, null))
                    .as("非法 topK 应被拒绝：%d", topK)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(embeddingClient, qdrantClient);
    }

    @Test
    @DisplayName("vectorSize 未配置 → IllegalStateException（在 Embedding 之前），且不调用任何依赖")
    void vectorSizeNotConfigured() {
        properties.setVectorSize(null);

        assertThatThrownBy(() -> retriever.retrieve("红灯", 5, null))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(embeddingClient, qdrantClient);
    }

    /* ==================== 2. Embedding 衔接 ==================== */

    @Test
    @DisplayName("Embedding 返回 0/2/null 条 → IllegalStateException，且不调用 Qdrant")
    void embedCountMismatch() {
        when(embeddingClient.embed(anyList())).thenReturn(List.of());
        assertThatThrownBy(() -> retriever.retrieve("红灯", 5, null))
                .isInstanceOf(IllegalStateException.class);

        when(embeddingClient.embed(anyList())).thenReturn(List.of(vector(), vector()));
        assertThatThrownBy(() -> retriever.retrieve("红灯", 5, null))
                .isInstanceOf(IllegalStateException.class);

        when(embeddingClient.embed(anyList())).thenReturn(null);
        assertThatThrownBy(() -> retriever.retrieve("红灯", 5, null))
                .isInstanceOf(IllegalStateException.class);

        verify(qdrantClient, never()).search(any(float[].class), anyInt(), any());
    }

    @Test
    @DisplayName("向量维度不符/含 NaN → 异常，且不调用 Qdrant")
    void invalidVectorRejected() {
        when(embeddingClient.embed(anyList())).thenReturn(List.of(new double[]{0.1, 0.2}));
        assertThatThrownBy(() -> retriever.retrieve("红灯", 5, null))
                .isInstanceOf(IllegalArgumentException.class);

        when(embeddingClient.embed(anyList())).thenReturn(List.of(new double[]{0.1, Double.NaN, 0.3}));
        assertThatThrownBy(() -> retriever.retrieve("红灯", 5, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(qdrantClient, never()).search(any(float[].class), anyInt(), any());
    }

    @Test
    @DisplayName("Embedding 抛异常 → 原样传播，且不调用 Qdrant")
    void embedExceptionPropagated() {
        BusinessException boom = new BusinessException(ErrorCode.AI_CALL_FAILED);
        when(embeddingClient.embed(anyList())).thenThrow(boom);

        assertThatThrownBy(() -> retriever.retrieve("红灯", 5, null)).isSameAs(boom);
        verify(qdrantClient, never()).search(any(float[].class), anyInt(), any());
    }

    /* ==================== 3. Qdrant 查询 ==================== */

    @Test
    @DisplayName("Qdrant 抛异常 → 原样传播（不返回空列表）")
    void searchExceptionPropagated() {
        when(embeddingClient.embed(anyList())).thenReturn(List.of(vector()));
        BusinessException boom = new BusinessException(ErrorCode.SYSTEM_ERROR);
        when(qdrantClient.search(any(float[].class), anyInt(), any())).thenThrow(boom);

        assertThatThrownBy(() -> retriever.retrieve("红灯", 5, null)).isSameAs(boom);
    }

    @Test
    @DisplayName("正常映射：字段完整、顺序保持，search 收到校验后的向量/topK/subjectCode")
    void happyPathMapping() {
        when(embeddingClient.embed(anyList())).thenReturn(List.of(vector()));
        when(qdrantClient.search(any(float[].class), anyInt(), any())).thenReturn(List.of(
                point(UUID_A, 0.93, validPayload()),
                point(UUID_B, 0.77, validPayload())));

        List<RetrievedChunk> result = retriever.retrieve("红灯", 5, null);

        assertThat(result).extracting(RetrievedChunk::pointId).containsExactly(UUID_A, UUID_B);
        assertThat(result).extracting(RetrievedChunk::score).containsExactly(0.93, 0.77);
        RetrievedChunk first = result.get(0);
        assertThat(first.chunkId()).isEqualTo("QUESTION_ANALYSIS:1#0");
        assertThat(first.docId()).isEqualTo("QUESTION_ANALYSIS:1");
        assertThat(first.sourceType()).isEqualTo(KnowledgeDocType.QUESTION_ANALYSIS);
        assertThat(first.sourceId()).isEqualTo(1L);
        assertThat(first.text()).isEqualTo("红灯亮时禁止通行。");
        assertThat(first.subjectCode()).isEqualTo("SUBJECT_1");

        ArgumentCaptor<float[]> vectorCaptor = ArgumentCaptor.forClass(float[].class);
        verify(qdrantClient, times(1)).search(vectorCaptor.capture(), eq(5), any());
        assertThat(vectorCaptor.getValue()).hasSize(DIM);
        assertThat(vectorCaptor.getValue()[0]).isEqualTo(0.1f);
    }

    @Test
    @DisplayName("subjectCode 为 null/空白/SUBJECT_1 → 均原样传给 Qdrant")
    void subjectCodePassThrough() {
        when(embeddingClient.embed(anyList())).thenReturn(List.of(vector()));
        when(qdrantClient.search(any(float[].class), anyInt(), any())).thenReturn(List.of());

        retriever.retrieve("红灯", 5, null);
        retriever.retrieve("红灯", 5, "   ");
        retriever.retrieve("红灯", 5, "SUBJECT_1");

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(qdrantClient, times(3)).search(any(float[].class), eq(5), subjectCaptor.capture());
        assertThat(subjectCaptor.getAllValues()).containsExactly(null, "   ", "SUBJECT_1");
    }

    @Test
    @DisplayName("Qdrant 返回空列表 → 返回空列表（空结果与失败区分）")
    void emptyResultsReturnEmptyList() {
        when(embeddingClient.embed(anyList())).thenReturn(List.of(vector()));
        when(qdrantClient.search(any(float[].class), anyInt(), any())).thenReturn(List.of());

        List<RetrievedChunk> result = retriever.retrieve("红灯", 5, null);

        assertThat(result).isNotNull().isEmpty();
    }

    /* ==================== 4. 结果严格校验（任一条非法 → 整次失败） ==================== */

    @Test
    @DisplayName("point ID：非 UUID/非规范形式被拒绝；规范大写形式被接受（忽略大小写）")
    void pointIdValidation() {
        when(embeddingClient.embed(anyList())).thenReturn(List.of(vector()));
        for (String badId : new String[]{"not-a-uuid", "a59fc2178e4e3c1c942abf3dadcf8ffb", ""}) {
            when(qdrantClient.search(any(float[].class), anyInt(), any()))
                    .thenReturn(List.of(point(badId, 0.5, validPayload())));
            assertThatThrownBy(() -> retriever.retrieve("红灯", 5, null))
                    .as("非法 point id 应被拒绝：[%s]", badId)
                    .isInstanceOf(IllegalStateException.class);
        }

        when(qdrantClient.search(any(float[].class), anyInt(), any()))
                .thenReturn(List.of(point(UUID_A.toUpperCase(), 0.5, validPayload())));
        List<RetrievedChunk> result = retriever.retrieve("红灯", 5, null);
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("payload 为 null → IllegalStateException")
    void nullPayloadRejected() {
        when(embeddingClient.embed(anyList())).thenReturn(List.of(vector()));
        when(qdrantClient.search(any(float[].class), anyInt(), any()))
                .thenReturn(List.of(point(UUID_A, 0.5, null)));

        assertThatThrownBy(() -> retriever.retrieve("红灯", 5, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("chunkId/docId/text/subjectCode 缺失或空白 → IllegalStateException")
    void missingOrBlankStringFields() {
        when(embeddingClient.embed(anyList())).thenReturn(List.of(vector()));
        for (String key : new String[]{"chunkId", "docId", "text", "subjectCode"}) {
            Map<String, Object> missing = validPayload();
            missing.remove(key);
            when(qdrantClient.search(any(float[].class), anyInt(), any()))
                    .thenReturn(List.of(point(UUID_A, 0.5, missing)));
            assertThatThrownBy(() -> retriever.retrieve("红灯", 5, null))
                    .as("缺失 %s 应被拒绝", key)
                    .isInstanceOf(IllegalStateException.class);

            Map<String, Object> blank = validPayload();
            blank.put(key, "   ");
            when(qdrantClient.search(any(float[].class), anyInt(), any()))
                    .thenReturn(List.of(point(UUID_A, 0.5, blank)));
            assertThatThrownBy(() -> retriever.retrieve("红灯", 5, null))
                    .as("空白 %s 应被拒绝", key)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("sourceType 缺失/类型错误/未知枚举值 → IllegalStateException")
    void sourceTypeValidation() {
        when(embeddingClient.embed(anyList())).thenReturn(List.of(vector()));
        Object[] badValues = {null, 1, "FOO", ""};
        for (Object bad : badValues) {
            Map<String, Object> payload = validPayload();
            if (bad == null) {
                payload.remove("sourceType");
            } else {
                payload.put("sourceType", bad);
            }
            when(qdrantClient.search(any(float[].class), anyInt(), any()))
                    .thenReturn(List.of(point(UUID_A, 0.5, payload)));
            assertThatThrownBy(() -> retriever.retrieve("红灯", 5, null))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("sourceId：Integer/Long 正整数被接受；小数/字符串/布尔/null/0/负数/超 long 大整数被拒绝")
    void sourceIdValidation() {
        when(embeddingClient.embed(anyList())).thenReturn(List.of(vector()));

        // 合法：Integer 5、Long 7
        Map<String, Object> intPayload = validPayload();
        intPayload.put("sourceId", 5);
        when(qdrantClient.search(any(float[].class), anyInt(), any()))
                .thenReturn(List.of(point(UUID_A, 0.5, intPayload)));
        assertThat(retriever.retrieve("红灯", 5, null).get(0).sourceId()).isEqualTo(5L);

        Map<String, Object> longPayload = validPayload();
        longPayload.put("sourceId", 7L);
        when(qdrantClient.search(any(float[].class), anyInt(), any()))
                .thenReturn(List.of(point(UUID_A, 0.5, longPayload)));
        assertThat(retriever.retrieve("红灯", 5, null).get(0).sourceId()).isEqualTo(7L);

        // 非法：小数、BigDecimal、字符串、布尔、null、0、负数、超 long 的 BigInteger
        Object[] badValues = {
                1.0,                    // Double 小数
                new BigDecimal("1"),    // 小数类型
                "1",                    // 字符串
                true,                   // 布尔
                null,                   // 显式 null
                0,                      // 零
                -3,                     // 负数
                new BigInteger("9223372036854775808") // long 范围外
        };
        for (Object bad : badValues) {
            Map<String, Object> payload = validPayload();
            payload.put("sourceId", bad);
            when(qdrantClient.search(any(float[].class), anyInt(), any()))
                    .thenReturn(List.of(point(UUID_A, 0.5, payload)));
            assertThatThrownBy(() -> retriever.retrieve("红灯", 5, null))
                    .as("非法 sourceId 应被拒绝：[%s]", bad)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("多条结果中前面合法、后面非法 → 整次失败，不返回部分列表")
    void partialThenInvalidFailsWholeCall() {
        when(embeddingClient.embed(anyList())).thenReturn(List.of(vector()));
        Map<String, Object> invalidSecond = validPayload();
        invalidSecond.remove("text");
        when(qdrantClient.search(any(float[].class), anyInt(), any())).thenReturn(List.of(
                point(UUID_A, 0.9, validPayload()),
                point(UUID_B, 0.8, invalidSecond)));

        assertThatThrownBy(() -> retriever.retrieve("红灯", 5, null))
                .isInstanceOf(IllegalStateException.class);
    }

    /* ============================== 辅助 ============================== */

    private static double[] vector() {
        return new double[]{0.1, 0.2, 0.3};
    }

    private static ScoredPoint point(String id, double score, Map<String, Object> payload) {
        return new ScoredPoint(id, 1L, score, payload);
    }

    private static Map<String, Object> validPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chunkId", "QUESTION_ANALYSIS:1#0");
        payload.put("docId", "QUESTION_ANALYSIS:1");
        payload.put("sourceType", "QUESTION_ANALYSIS");
        payload.put("sourceId", 1);
        payload.put("text", "红灯亮时禁止通行。");
        payload.put("subjectCode", "SUBJECT_1");
        return payload;
    }
}
