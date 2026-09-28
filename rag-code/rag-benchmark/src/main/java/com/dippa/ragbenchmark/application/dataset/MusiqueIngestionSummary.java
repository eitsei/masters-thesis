package com.dippa.ragbenchmark.application.dataset;

/**
 * Summary metrics of a MuSiQue dataset ingestion run.
 *
 * @param questionsProcessed number of questions from which paragraphs were extracted
 * @param uniqueParagraphsIngested number of distinct paragraphs in the sample
 * @param baselineChunksWritten number of chunks written to vector_store_musique
 * @param parentChunksWritten number of parent chunks written to vector_store_musique_large
 * @param childChunksWritten number of child chunks written to vector_store_musique_small
 * @param batchCount total number of vectorStore.add() batch calls made
 * @param durationMs total wall-clock duration of the ingestion in milliseconds
 * @param mode ingestion mode executed (e.g. "original", "baseline", "s2b")
 * @param databaseCleared whether target tables were truncated prior to ingestion
 */
public record MusiqueIngestionSummary(
        int questionsProcessed,
        int uniqueParagraphsIngested,
        int baselineChunksWritten,
        int parentChunksWritten,
        int childChunksWritten,
        int batchCount,
        long durationMs,
        String mode,
        boolean databaseCleared
) {
    public MusiqueIngestionSummary(
            int questionsProcessed,
            int uniqueParagraphsIngested,
            int batchCount,
            long durationMs) {
        this(questionsProcessed, uniqueParagraphsIngested, uniqueParagraphsIngested, 0, 0, batchCount, durationMs, "original", false);
    }
}
