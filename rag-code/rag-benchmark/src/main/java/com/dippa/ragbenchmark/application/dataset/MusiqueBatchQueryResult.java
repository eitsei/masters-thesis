package com.dippa.ragbenchmark.application.dataset;

import com.dippa.ragbenchmark.api.TokenUsageDto;
import java.util.List;

/**
 * Result of evaluating a batch of MuSiQue benchmark questions through the pipeline.
 */
public record MusiqueBatchQueryResult(
        int totalQuestions,
        long totalExecutionTimeMs,
        double averageLatencyMs,
        int supportingEvidenceFoundCount,
        TokenUsageDto totalTokenUsage,
        List<MusiqueBenchmarkQueryResult> results
) {}
