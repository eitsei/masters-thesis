package com.dippa.ragbenchmark.api;

import com.dippa.ragbenchmark.application.dataset.MusiqueBatchQueryResult;
import java.util.List;

/**
 * REST response body for batch benchmark evaluation runs.
 */
public record BenchmarkBatchQueryResponse(
        int totalQuestions,
        long totalExecutionTimeMs,
        double averageLatencyMs,
        int supportingEvidenceFoundCount,
        TokenUsageDto totalTokenUsage,
        List<MusiqueBenchmarkQueryResponse> results
) {
    public static BenchmarkBatchQueryResponse fromResult(MusiqueBatchQueryResult result) {
        List<MusiqueBenchmarkQueryResponse> responses = result.results() != null
                ? result.results().stream().map(MusiqueBenchmarkQueryResponse::fromResult).toList()
                : List.of();
        return new BenchmarkBatchQueryResponse(
                result.totalQuestions(),
                result.totalExecutionTimeMs(),
                result.averageLatencyMs(),
                result.supportingEvidenceFoundCount(),
                result.totalTokenUsage(),
                responses
        );
    }
}
