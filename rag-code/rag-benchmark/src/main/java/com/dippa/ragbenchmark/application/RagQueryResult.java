package com.dippa.ragbenchmark.application;

import com.dippa.ragbenchmark.api.CacheMetadataDto;
import com.dippa.ragbenchmark.api.TokenUsageDto;
import com.dippa.ragbenchmark.domain.CacheStatus;
import com.dippa.ragbenchmark.domain.ModuleId;
import java.util.List;
import java.util.Set;

/**
 * Result of running the RAG pipeline for a single query: the generated answer, which
 * optimization modules were actually applied, the retrieved chunks,
 * the retrieval, rerank, expansion, cache, route, and generation timings in milliseconds,
 * model used, cache status, cache metadata, and token usage counts.
 */
public record RagQueryResult(
        String answer,
        Set<ModuleId> modulesApplied,
        List<RetrievedChunk> retrievedChunks,
        long retrievalMs,
        long generationMs,
        long expansionMs,
        long rerankMs,
        long cacheMs,
        long routeMs,
        long ttftMs,
        String modelUsed,
        CacheStatus cacheStatus,
        CacheMetadataDto cacheMetadata,
        TokenUsageDto tokenUsage) {

    public RagQueryResult(
            String answer,
            Set<ModuleId> modulesApplied,
            List<RetrievedChunk> retrievedChunks,
            long retrievalMs,
            long generationMs,
            long expansionMs,
            long rerankMs,
            long cacheMs,
            long routeMs,
            String modelUsed,
            CacheStatus cacheStatus,
            CacheMetadataDto cacheMetadata,
            TokenUsageDto tokenUsage) {
        this(answer, modulesApplied, retrievedChunks, retrievalMs, generationMs, expansionMs, rerankMs, cacheMs,
                routeMs, 0L, modelUsed, cacheStatus, cacheMetadata, tokenUsage);
    }

    public RagQueryResult(
            String answer,
            Set<ModuleId> modulesApplied,
            List<RetrievedChunk> retrievedChunks,
            long retrievalMs,
            long generationMs,
            long expansionMs,
            long rerankMs,
            long cacheMs,
            CacheStatus cacheStatus,
            CacheMetadataDto cacheMetadata,
            TokenUsageDto tokenUsage) {
        this(answer, modulesApplied, retrievedChunks, retrievalMs, generationMs, expansionMs, rerankMs, cacheMs,
                0L, null, cacheStatus, cacheMetadata, tokenUsage);
    }

    public RagQueryResult(
            String answer,
            Set<ModuleId> modulesApplied,
            List<RetrievedChunk> retrievedChunks,
            long retrievalMs,
            long generationMs,
            long expansionMs,
            long rerankMs,
            long cacheMs,
            CacheStatus cacheStatus,
            TokenUsageDto tokenUsage) {
        this(answer, modulesApplied, retrievedChunks, retrievalMs, generationMs, expansionMs, rerankMs, cacheMs,
                0L, null, cacheStatus, new CacheMetadataDto(cacheStatus, null, null, null), tokenUsage);
    }

    public RagQueryResult(
            String answer,
            Set<ModuleId> modulesApplied,
            List<RetrievedChunk> retrievedChunks,
            long retrievalMs,
            long generationMs,
            long expansionMs,
            long rerankMs,
            long cacheMs,
            long routeMs,
            CacheStatus cacheStatus,
            CacheMetadataDto cacheMetadata,
            TokenUsageDto tokenUsage) {
        this(answer, modulesApplied, retrievedChunks, retrievalMs, generationMs, expansionMs, rerankMs, cacheMs,
                routeMs, null, cacheStatus, cacheMetadata, tokenUsage);
    }

    public RagQueryResult(
            String answer,
            Set<ModuleId> modulesApplied,
            List<RetrievedChunk> retrievedChunks,
            long retrievalMs,
            long generationMs,
            long expansionMs,
            long rerankMs,
            TokenUsageDto tokenUsage) {
        this(answer, modulesApplied, retrievedChunks, retrievalMs, generationMs, expansionMs, rerankMs, 0L,
                0L, null, CacheStatus.NONE, new CacheMetadataDto(CacheStatus.NONE, null, null, null), tokenUsage);
    }

    public RagQueryResult(
            String answer,
            Set<ModuleId> modulesApplied,
            List<RetrievedChunk> retrievedChunks,
            long retrievalMs,
            long generationMs,
            long expansionMs,
            TokenUsageDto tokenUsage) {
        this(answer, modulesApplied, retrievedChunks, retrievalMs, generationMs, expansionMs, 0L, 0L,
                0L, null, CacheStatus.NONE, new CacheMetadataDto(CacheStatus.NONE, null, null, null), tokenUsage);
    }

    public RagQueryResult(
            String answer,
            Set<ModuleId> modulesApplied,
            List<RetrievedChunk> retrievedChunks,
            long retrievalMs,
            long generationMs,
            TokenUsageDto tokenUsage) {
        this(answer, modulesApplied, retrievedChunks, retrievalMs, generationMs, 0L, 0L, 0L,
                0L, null, CacheStatus.NONE, new CacheMetadataDto(CacheStatus.NONE, null, null, null), tokenUsage);
    }

    public RagQueryResult(
            String answer,
            Set<ModuleId> modulesApplied,
            List<RetrievedChunk> retrievedChunks,
            long retrievalMs,
            long generationMs) {
        this(answer, modulesApplied, retrievedChunks, retrievalMs, generationMs, 0L, 0L, 0L,
                0L, null, CacheStatus.NONE, new CacheMetadataDto(CacheStatus.NONE, null, null, null), TokenUsageDto.ZERO);
    }

    public long totalLatencyMs() {
        return retrievalMs + generationMs + expansionMs + rerankMs + cacheMs + routeMs;
    }
}
