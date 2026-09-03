package com.dippa.ragbenchmark.application.dataset;

import com.dippa.ragbenchmark.application.RetrievedChunk;
import java.util.List;

/**
 * Result of evaluating a MuSiQue benchmark question through the RAG pipeline.
 */
public record MusiqueBenchmarkQueryResult(
        String questionId,
        String question,
        String groundTruthAnswer,
        List<String> groundTruthAliases,
        int hopCount,
        String generatedAnswer,
        List<String> modulesApplied,
        List<RetrievedChunk> retrievedChunks,
        long retrievalMs,
        long generationMs,
        List<String> expectedSupportingTitles,
        boolean supportingEvidenceFound
) {}
