package com.dippa.ragbenchmark.domain;

/**
 * Picks which model tier should serve a given query (e.g. a cheap/fast model vs. a
 * larger/more capable one).
 *
 * <p>Spring AI has no built-in opinion on cross-model routing/cascading, so this is a
 * project-owned port. Not needed by the M0 baseline — no bean is registered for it in v1.
 * A real implementation (M4) can satisfy this interface later.</p>
 */
public interface ModelRouter {

    /**
     * @param query the user query to be routed
     * @return an identifier for the model tier that should serve {@code query}
     */
    String routeModel(String query);
}
