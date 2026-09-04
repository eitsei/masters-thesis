package com.dippa.ragbenchmark.api;

import com.dippa.ragbenchmark.application.dataset.MusiqueIngestionSummary;

public record MusiqueIngestResponse(
        String status,
        int questionsProcessed,
        int uniqueParagraphsIngested,
        int batches,
        long durationMs
) {
    public static MusiqueIngestResponse fromSummary(MusiqueIngestionSummary summary) {
        return new MusiqueIngestResponse(
                "SUCCESS",
                summary.questionsProcessed(),
                summary.uniqueParagraphsIngested(),
                summary.batchCount(),
                summary.durationMs()
        );
    }
}
