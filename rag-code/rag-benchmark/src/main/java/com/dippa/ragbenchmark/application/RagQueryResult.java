package com.dippa.ragbenchmark.application;

import com.dippa.ragbenchmark.api.TokenUsageDto;
import com.dippa.ragbenchmark.domain.ModuleId;
import java.util.List;
import java.util.Set;

/**
 * Result of running the RAG pipeline for a single query: the generated answer, which
 * optimization modules were actually applied (always empty in v1/M0), the retrieved chunks,
 * the retrieval/generation timings in milliseconds, and token usage counts.
 */
public record RagQueryResult(
        String answer,
        Set<ModuleId> modulesApplied,
        List<RetrievedChunk> retrievedChunks,
        long retrievalMs,
        long generationMs,
        TokenUsageDto tokenUsage) {

    public RagQueryResult(
            String answer,
            Set<ModuleId> modulesApplied,
            List<RetrievedChunk> retrievedChunks,
            long retrievalMs,
            long generationMs) {
        this(answer, modulesApplied, retrievedChunks, retrievalMs, generationMs, TokenUsageDto.ZERO);
    }

    public long totalLatencyMs() {
        return retrievalMs + generationMs;
    }
}
