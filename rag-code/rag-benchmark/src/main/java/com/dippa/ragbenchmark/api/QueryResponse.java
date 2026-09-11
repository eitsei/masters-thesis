package com.dippa.ragbenchmark.api;

import com.dippa.ragbenchmark.application.RetrievedChunk;
import com.dippa.ragbenchmark.domain.CacheStatus;
import java.util.List;

/**
 * Response body for {@code POST /api/query}.
 */
public record QueryResponse(
        String answer,
        List<String> modulesApplied,
        List<RetrievedChunk> retrievedChunks,
        TimingsDto timingsMs,
        TokenUsageDto tokenUsage,
        CacheStatus cacheStatus,
        long totalLatencyMs) {

    public QueryResponse(
            String answer,
            List<String> modulesApplied,
            List<RetrievedChunk> retrievedChunks,
            TimingsDto timingsMs,
            TokenUsageDto tokenUsage,
            long totalLatencyMs) {
        this(answer, modulesApplied, retrievedChunks, timingsMs, tokenUsage, CacheStatus.NONE, totalLatencyMs);
    }

    public QueryResponse(
            String answer,
            List<String> modulesApplied,
            List<RetrievedChunk> retrievedChunks,
            TimingsDto timingsMs) {
        this(answer, modulesApplied, retrievedChunks, timingsMs, TokenUsageDto.ZERO, CacheStatus.NONE,
                timingsMs != null ? timingsMs.totalMs() : 0L);
    }
}
