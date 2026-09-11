package com.dippa.ragbenchmark.application.dataset;

import com.dippa.ragbenchmark.api.TokenUsageDto;
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
        long expansionMs,
        long totalLatencyMs,
        TokenUsageDto tokenUsage,
        String tableNameUsed,
        List<String> expectedSupportingTitles,
        boolean supportingEvidenceFound,
        int supportingTitlesFound,
        boolean allSupportingEvidenceFound
) {
    public MusiqueBenchmarkQueryResult(
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
            long totalLatencyMs,
            TokenUsageDto tokenUsage,
            String tableNameUsed,
            List<String> expectedSupportingTitles,
            boolean supportingEvidenceFound) {
        this(
                questionId,
                question,
                groundTruthAnswer,
                groundTruthAliases,
                hopCount,
                generatedAnswer,
                modulesApplied,
                retrievedChunks,
                retrievalMs,
                generationMs,
                0L,
                totalLatencyMs,
                tokenUsage,
                tableNameUsed,
                expectedSupportingTitles,
                supportingEvidenceFound,
                supportingEvidenceFound ? 1 : 0,
                supportingEvidenceFound);
    }

    public MusiqueBenchmarkQueryResult(
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
            boolean supportingEvidenceFound) {
        this(
                questionId,
                question,
                groundTruthAnswer,
                groundTruthAliases,
                hopCount,
                generatedAnswer,
                modulesApplied,
                retrievedChunks,
                retrievalMs,
                generationMs,
                0L,
                retrievalMs + generationMs,
                TokenUsageDto.ZERO,
                "vector_store_test",
                expectedSupportingTitles,
                supportingEvidenceFound,
                supportingEvidenceFound ? 1 : 0,
                supportingEvidenceFound);
    }
}
