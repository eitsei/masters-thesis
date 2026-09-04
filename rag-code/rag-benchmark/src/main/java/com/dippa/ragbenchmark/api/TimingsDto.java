package com.dippa.ragbenchmark.api;

/**
 * Millisecond timings for the retrieval and generation stages of the RAG pipeline.
 */
public record TimingsDto(long retrievalMs, long generationMs) {
    public long totalMs() {
        return retrievalMs + generationMs;
    }
}
