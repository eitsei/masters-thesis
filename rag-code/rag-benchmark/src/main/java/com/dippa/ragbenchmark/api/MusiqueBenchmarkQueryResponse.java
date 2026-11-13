package com.dippa.ragbenchmark.api;

import com.dippa.ragbenchmark.application.RetrievedChunk;
import com.dippa.ragbenchmark.application.dataset.MusiqueBenchmarkQueryResult;
import com.dippa.ragbenchmark.domain.CacheStatus;
import java.util.List;

public record MusiqueBenchmarkQueryResponse(
        String questionId,
        String question,
        String groundTruthAnswer,
        List<String> groundTruthAliases,
        int hopCount,
        String generatedAnswer,
        List<String> modulesApplied,
        List<RetrievedChunk> retrievedChunks,
        TimingsDto timings,
        long totalLatencyMs,
        TokenUsageDto tokenUsage,
        CacheStatus cacheStatus,
        CacheMetadataDto cacheMetadata,
        String modelUsed,
        String tableNameUsed,
        List<String> expectedSupportingTitles,
        boolean supportingEvidenceFound,
        int supportingTitlesFound,
        boolean allSupportingEvidenceFound
) {
    public MusiqueBenchmarkQueryResponse(
            String questionId,
            String question,
            String groundTruthAnswer,
            List<String> groundTruthAliases,
            int hopCount,
            String generatedAnswer,
            List<String> modulesApplied,
            List<RetrievedChunk> retrievedChunks,
            TimingsDto timings,
            long totalLatencyMs,
            TokenUsageDto tokenUsage,
            CacheStatus cacheStatus,
            CacheMetadataDto cacheMetadata,
            String tableNameUsed,
            List<String> expectedSupportingTitles,
            boolean supportingEvidenceFound,
            int supportingTitlesFound,
            boolean allSupportingEvidenceFound) {
        this(
                questionId,
                question,
                groundTruthAnswer,
                groundTruthAliases,
                hopCount,
                generatedAnswer,
                modulesApplied,
                retrievedChunks,
                timings,
                totalLatencyMs,
                tokenUsage,
                cacheStatus,
                cacheMetadata,
                null,
                tableNameUsed,
                expectedSupportingTitles,
                supportingEvidenceFound,
                supportingTitlesFound,
                allSupportingEvidenceFound);
    }

    public MusiqueBenchmarkQueryResponse(
            String questionId,
            String question,
            String groundTruthAnswer,
            List<String> groundTruthAliases,
            int hopCount,
            String generatedAnswer,
            List<String> modulesApplied,
            List<RetrievedChunk> retrievedChunks,
            TimingsDto timings,
            long totalLatencyMs,
            TokenUsageDto tokenUsage,
            CacheStatus cacheStatus,
            String tableNameUsed,
            List<String> expectedSupportingTitles,
            boolean supportingEvidenceFound,
            int supportingTitlesFound,
            boolean allSupportingEvidenceFound) {
        this(
                questionId,
                question,
                groundTruthAnswer,
                groundTruthAliases,
                hopCount,
                generatedAnswer,
                modulesApplied,
                retrievedChunks,
                timings,
                totalLatencyMs,
                tokenUsage,
                cacheStatus,
                new CacheMetadataDto(cacheStatus, null, null, null),
                null,
                tableNameUsed,
                expectedSupportingTitles,
                supportingEvidenceFound,
                supportingTitlesFound,
                allSupportingEvidenceFound);
    }

    public MusiqueBenchmarkQueryResponse(
            String questionId,
            String question,
            String groundTruthAnswer,
            List<String> groundTruthAliases,
            int hopCount,
            String generatedAnswer,
            List<String> modulesApplied,
            List<RetrievedChunk> retrievedChunks,
            TimingsDto timings,
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
                timings,
                timings != null ? timings.totalMs() : 0L,
                TokenUsageDto.ZERO,
                CacheStatus.NONE,
                new CacheMetadataDto(CacheStatus.NONE, null, null, null),
                null,
                null,
                expectedSupportingTitles,
                supportingEvidenceFound,
                supportingEvidenceFound ? 1 : 0,
                supportingEvidenceFound);
    }

    public static MusiqueBenchmarkQueryResponse fromResult(MusiqueBenchmarkQueryResult result) {
        return new MusiqueBenchmarkQueryResponse(
                result.questionId(),
                result.question(),
                result.groundTruthAnswer(),
                result.groundTruthAliases(),
                result.hopCount(),
                result.generatedAnswer(),
                result.modulesApplied(),
                result.retrievedChunks(),
                new TimingsDto(
                        result.retrievalMs(),
                        result.rerankMs(),
                        result.generationMs(),
                        result.expansionMs(),
                        result.cacheMs(),
                        result.routeMs(),
                        result.ttftMs(),
                        result.prefillMs()),
                result.totalLatencyMs(),
                result.tokenUsage(),
                result.cacheStatus(),
                result.cacheMetadata(),
                result.modelUsed(),
                result.tableNameUsed(),
                result.expectedSupportingTitles(),
                result.supportingEvidenceFound(),
                result.supportingTitlesFound(),
                result.allSupportingEvidenceFound()
        );
    }
}
