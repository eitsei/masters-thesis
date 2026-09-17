package com.dippa.ragbenchmark.domain;

/**
 * Port for Module M4 (Hybrid Model Routing): dynamically evaluates incoming queries
 * and directs them to the optimal generative model tier (Gemini 3.5 Flash-Lite,
 * Gemini 3.8 Flash, or Gemini 3.1 Pro) based on query reasoning complexity.
 */
public interface ModelRouter {

    /**
     * Evaluates the routing request and selects the target model tier.
     *
     * @param request the routing request containing query and optional reasoning metadata
     * @return a {@link RoutingDecision} containing the selected model, tier, latency, and token metrics
     */
    RoutingDecision route(RoutingRequest request);

    /**
     * Convenience method returning only the selected model identifier for a query.
     *
     * @param query the user query to be routed
     * @return an identifier for the model tier that should serve {@code query}
     */
    default String routeModel(String query) {
        return route(new RoutingRequest(query)).selectedModel();
    }

    /**
     * Indicates whether a real routing backend (e.g. classifier or oracle) is configured and active.
     */
    boolean isConfigured();
}
