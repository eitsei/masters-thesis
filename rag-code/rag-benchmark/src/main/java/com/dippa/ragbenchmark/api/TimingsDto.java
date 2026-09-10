package com.dippa.ragbenchmark.api;

/**
 * Millisecond timings for the retrieval, expansion (S2B), and generation stages of the RAG pipeline.
 */
public record TimingsDto(long retrievalMs, long generationMs, long expansionMs) {

    public TimingsDto(long retrievalMs, long generationMs) {
        this(retrievalMs, generationMs, 0L);
    }

    public long totalMs() {
        return retrievalMs + generationMs + expansionMs;
    }
}
