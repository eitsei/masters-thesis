package com.dippa.ragbenchmark.application.dataset;

/**
 * Summary metrics of a MuSiQue dataset ingestion run.
 *
 * @param questionsProcessed number of questions from which paragraphs were extracted
 * @param uniqueParagraphsIngested number of distinct paragraphs written to pgvector
 * @param batchCount number of vectorStore.add() batch calls made
 * @param durationMs total wall-clock duration of the ingestion in milliseconds
 */
public record MusiqueIngestionSummary(
        int questionsProcessed,
        int uniqueParagraphsIngested,
        int batchCount,
        long durationMs
) {}
