package com.dippa.ragbenchmark.api;

/**
 * Millisecond timings for the retrieval, re-ranking (M2), expansion (S2B), and generation stages of the RAG pipeline.
 */
public record TimingsDto(long retrievalMs, long rerankMs, long generationMs, long expansionMs) {

    public TimingsDto(long retrievalMs, long generationMs, long expansionMs) {
        this(retrievalMs, 0L, generationMs, expansionMs);
    }

    public TimingsDto(long retrievalMs, long generationMs) {
        this(retrievalMs, 0L, generationMs, 0L);
    }

    public long totalMs() {
        return retrievalMs + rerankMs + generationMs + expansionMs;
    }
}
