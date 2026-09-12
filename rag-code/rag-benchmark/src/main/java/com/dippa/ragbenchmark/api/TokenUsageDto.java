package com.dippa.ragbenchmark.api;

/**
 * Token usage counts, cache statistics, and auxiliary service telemetry for an LLM query generation step.
 *
 * @param promptTokens number of tokens in the prompt/input
 * @param completionTokens number of tokens in the generated completion/output
 * @param totalTokens total tokens consumed
 * @param cachedTokens number of cached prompt tokens read without generating fresh context (cacheReadInputTokens)
 * @param cacheWriteTokens number of tokens written into the context cache (cacheWriteInputTokens)
 * @param rerankDocs number of candidate passages scored by the cross-encoder reranker
 * @param embedTokens number of query tokens embedded by dense vectorizer
 * @param routingTokens number of tokens consumed by the dynamic model routing classifier
 */
public record TokenUsageDto(
        long promptTokens,
        long completionTokens,
        long totalTokens,
        long cachedTokens,
        long cacheWriteTokens,
        int rerankDocs,
        int embedTokens,
        int routingTokens) {

    public static final TokenUsageDto ZERO = new TokenUsageDto(0, 0, 0, 0, 0, 0, 0, 0);

    public TokenUsageDto(long promptTokens, long completionTokens, long totalTokens, long cachedTokens, long cacheWriteTokens) {
        this(promptTokens, completionTokens, totalTokens, cachedTokens, cacheWriteTokens, 0, 0, 0);
    }

    public TokenUsageDto(long promptTokens, long completionTokens, long totalTokens, long cachedTokens) {
        this(promptTokens, completionTokens, totalTokens, cachedTokens, 0L, 0, 0, 0);
    }
}
