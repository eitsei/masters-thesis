package com.dippa.ragbenchmark.api;

/**
 * Token usage counts and cache statistics for an LLM query generation step.
 *
 * @param promptTokens number of tokens in the prompt/input
 * @param completionTokens number of tokens in the generated completion/output
 * @param totalTokens total tokens consumed
 * @param cachedTokens number of cached prompt tokens read without generating fresh context (cacheReadInputTokens)
 * @param cacheWriteTokens number of tokens written into the context cache (cacheWriteInputTokens)
 */
public record TokenUsageDto(
        long promptTokens,
        long completionTokens,
        long totalTokens,
        long cachedTokens,
        long cacheWriteTokens) {

    public static final TokenUsageDto ZERO = new TokenUsageDto(0, 0, 0, 0, 0);

    public TokenUsageDto(long promptTokens, long completionTokens, long totalTokens, long cachedTokens) {
        this(promptTokens, completionTokens, totalTokens, cachedTokens, 0L);
    }
}
