package com.dippa.ragbenchmark.api;

import com.dippa.ragbenchmark.application.dataset.MusiqueIngestionSummary;

public record MusiqueIngestResponse(
        String status,
        String mode,
        boolean databaseCleared,
        int questionsProcessed,
        int uniqueParagraphsIngested,
        int baselineChunksWritten,
        int parentChunksWritten,
        int childChunksWritten,
        int batches,
        long durationMs
) {
    public static MusiqueIngestResponse fromSummary(MusiqueIngestionSummary summary) {
        return new MusiqueIngestResponse(
                "SUCCESS",
                summary.mode(),
                summary.databaseCleared(),
                summary.questionsProcessed(),
                summary.uniqueParagraphsIngested(),
                summary.baselineChunksWritten(),
                summary.parentChunksWritten(),
                summary.childChunksWritten(),
                summary.batchCount(),
                summary.durationMs()
        );
    }
}
