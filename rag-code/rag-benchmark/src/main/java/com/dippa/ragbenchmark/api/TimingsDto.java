package com.dippa.ragbenchmark.api;

/**
 * Millisecond timings for the retrieval, re-ranking (M2), expansion (S2B), cache (M3),
 * model routing (M4), prefill (T_prefill: dispatch → first token), and generation stages
 * of the RAG pipeline.
 */
public record TimingsDto(
        long retrievalMs,
        long rerankMs,
        long generationMs,
        long expansionMs,
        long cacheMs,
        long routeMs,
        long ttftMs,
        long prefillMs) {

    public TimingsDto(long retrievalMs, long rerankMs, long generationMs, long expansionMs, long cacheMs, long routeMs, long ttftMs) {
        this(retrievalMs, rerankMs, generationMs, expansionMs, cacheMs, routeMs, ttftMs, 0L);
    }

    public TimingsDto(long retrievalMs, long rerankMs, long generationMs, long expansionMs, long cacheMs, long routeMs) {
        this(retrievalMs, rerankMs, generationMs, expansionMs, cacheMs, routeMs, 0L, 0L);
    }

    public TimingsDto(long retrievalMs, long rerankMs, long generationMs, long expansionMs, long cacheMs) {
        this(retrievalMs, rerankMs, generationMs, expansionMs, cacheMs, 0L, 0L, 0L);
    }

    public TimingsDto(long retrievalMs, long rerankMs, long generationMs, long expansionMs) {
        this(retrievalMs, rerankMs, generationMs, expansionMs, 0L, 0L, 0L, 0L);
    }

    public TimingsDto(long retrievalMs, long generationMs, long expansionMs) {
        this(retrievalMs, 0L, generationMs, expansionMs, 0L, 0L, 0L, 0L);
    }

    public TimingsDto(long retrievalMs, long generationMs) {
        this(retrievalMs, 0L, generationMs, 0L, 0L, 0L, 0L, 0L);
    }

    public long totalMs() {
        return retrievalMs + rerankMs + generationMs + expansionMs + cacheMs + routeMs;
    }
}
