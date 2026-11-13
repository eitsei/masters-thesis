package com.dippa.ragbenchmark.domain;

/**
 * Outcome of a {@link ModelRouter} evaluation.
 *
 * @param selectedModel identifier of the generative model tier selected (e.g. "gemini-3.5-flash-lite")
 * @param complexityTier classified complexity tier (SINGLE_HOP, TWO_HOP, or MULTI_HOP)
 * @param predictedHops numerical hop count equivalent (1, 2, or 3)
 * @param routingLatencyMs duration ($T_{\text{route}}$) of the routing micro-phase in milliseconds
 * @param routingPromptTokens prompt tokens consumed by the classifier call
 * @param routingCompletionTokens completion tokens consumed by the classifier call
 * @param routingMode mechanism that generated this decision
 * @param rationale optional diagnostic note or raw classification output
 */
public record RoutingDecision(
        String selectedModel,
        ComplexityTier complexityTier,
        int predictedHops,
        long routingLatencyMs,
        int routingPromptTokens,
        int routingCompletionTokens,
        RoutingMode routingMode,
        String rationale) {

    public int totalRoutingTokens() {
        return routingPromptTokens + routingCompletionTokens;
    }

    public static RoutingDecision direct(String model, ComplexityTier tier, int hops, RoutingMode mode) {
        return new RoutingDecision(model, tier, hops, 0L, 0, 0, mode, null);
    }
}
