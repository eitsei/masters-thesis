package com.dippa.ragbenchmark.application;

import com.dippa.ragbenchmark.domain.ModuleId;
import java.util.List;
import java.util.Set;

/**
 * Result of running the RAG pipeline for a single query: the generated answer, which
 * optimization modules were actually applied (always empty in v1/M0), the retrieved chunks,
 * and the retrieval/generation timings in milliseconds.
 */
public record RagQueryResult(
        String answer,
        Set<ModuleId> modulesApplied,
        List<RetrievedChunk> retrievedChunks,
        long retrievalMs,
        long generationMs) {
}
