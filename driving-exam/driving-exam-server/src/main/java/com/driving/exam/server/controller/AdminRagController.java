package com.driving.exam.server.controller;

import com.driving.exam.common.result.ApiResponse;
import com.driving.exam.server.rag.ingest.KnowledgeBaseIngestReport;
import com.driving.exam.server.rag.ingest.KnowledgeBaseIngestService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员端 · 知识库运维接口。
 *
 * <p>角色鉴权由 {@code PathRoleRule} 在拦截器层完成（{@code /api/admin/**} 仅 ADMIN）。
 * 这里只提供一个“手动重建/增量同步知识向量”的入口，不暴露题库正文，也不接受任意 collection
 * 或文件参数；目标集合由 {@code qdrant.collection} 固定，并由入库守卫再次校验。</p>
 */
@RestController
@RequestMapping("/api/admin/rag")
public class AdminRagController {

    private final KnowledgeBaseIngestService ingestService;

    public AdminRagController(KnowledgeBaseIngestService ingestService) {
        this.ingestService = ingestService;
    }

    /**
     * 从业务库只读装配题目解析/知识点描述，重新计算 Embedding 并 upsert 到 Qdrant。
     *
     * <p>可重复执行：确定性 point ID 会覆盖同一条目的旧向量，不删除/清空集合。
     * 失败时返回 4006；若配置/外部服务不可用，不会伪造成功统计。</p>
     */
    @PostMapping("/ingest")
    public ApiResponse<KnowledgeBaseIngestReport> ingest() {
        return ApiResponse.success(ingestService.ingest());
    }
}
