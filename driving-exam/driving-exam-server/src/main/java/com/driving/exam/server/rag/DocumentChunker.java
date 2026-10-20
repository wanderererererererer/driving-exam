package com.driving.exam.server.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 基础分块器（RAG 第一层）。
 *
 * <h3>分块规则（本阶段刻意保持简单）</h3>
 * <ul>
 *   <li><b>空内容</b>（{@code null} / 全空白）→ 返回<b>空列表</b>（不产出空分块）。</li>
 *   <li><b>短文本</b>（长度 ≤ {@link #MAX_CHUNK_CHARS}）→ <b>单块</b>，不切碎
 *       （题目解析、知识点描述通常都很短）。</li>
 *   <li><b>超长文本</b>→ 按字符上限切分；优先在句子/换行边界（{@code 。！？；!?;\n}）处断开，
 *       靠近上限处找不到边界时按上限硬切（<b>不丢字符</b>）。</li>
 *   <li>各分块是原文的<b>连续片段</b>：从 0 到末尾首尾相接、无缝无重叠，因此
 *       “所有分块文本顺序拼接 == 原始文本”（内容不丢失）。</li>
 *   <li>本阶段<b>不做</b>重叠切片（overlap）——除非后续真实数据与测试证明必要。</li>
 * </ul>
 *
 * <h3>字符数 ≠ token 数（重要限制）</h3>
 * <p>{@link #MAX_CHUNK_CHARS} 是<b>字符长度</b>上限，<b>不是</b> BGE-M3 的 token 上限
 * （bge-m3 单条最大 8192 tokens）。中文一字≈1~2 token，故这里取较保守的字符上限，
 * 使每个分块在极端情况下也显著低于 8192 tokens。<b>不得</b>把字符数当作 token 数来宣称“已满足模型上下文限制”。</p>
 *
 * <p>无状态纯逻辑，单测可直接 {@code new DocumentChunker()}。</p>
 */
@Component
public class DocumentChunker {

    /**
     * 单个分块的<b>字符</b>上限（非 token）。取值保守，保证中文文本分块远低于 bge-m3 的 8192 token 上限。
     */
    public static final int MAX_CHUNK_CHARS = 800;

    /** 句子/段落边界字符（用于尽量在语义边界处切分）。 */
    private static final String SENTENCE_ENDINGS = "。！？；!?;\n";

    /**
     * 将一个知识文档切分为若干分块。
     *
     * @param document 知识文档
     * @return 分块列表；空文本返回空列表
     * @throws NullPointerException {@code document} 为 null
     */
    public List<DocumentChunk> chunk(KnowledgeDocument document) {
        Objects.requireNonNull(document, "document 不能为空");
        String text = document.text();
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<DocumentChunk> chunks = new ArrayList<>();
        if (text.length() <= MAX_CHUNK_CHARS) {
            chunks.add(newChunk(document, 0, text));
            return chunks;
        }

        int start = 0;
        int index = 0;
        int length = text.length();
        while (start < length) {
            int end = Math.min(start + MAX_CHUNK_CHARS, length);
            if (end < length) {
                int cut = lastSentenceEnd(text, start, end);
                if (cut > start) {
                    end = cut; // 在句子边界断开（仍与原文连续）
                }
            }
            chunks.add(newChunk(document, index++, text.substring(start, end)));
            start = end;
        }
        return chunks;
    }

    /**
     * 在 {@code [start, endExclusive)} 内从后向前找最近的句子边界，返回“断点下标”（边界字符之后）。
     * 找不到返回 -1。
     */
    private static int lastSentenceEnd(String s, int start, int endExclusive) {
        for (int i = endExclusive - 1; i >= start; i--) {
            if (SENTENCE_ENDINGS.indexOf(s.charAt(i)) >= 0) {
                return i + 1;
            }
        }
        return -1;
    }

    private static DocumentChunk newChunk(KnowledgeDocument document, int chunkIndex, String text) {
        String chunkId = document.docId() + "#" + chunkIndex;
        return new DocumentChunk(
                chunkId,
                document.docId(),
                document.sourceType(),
                document.sourceId(),
                chunkIndex,
                text,
                document.metadata());
    }
}
