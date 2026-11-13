package com.dippa.ragbenchmark.infrastructure;

import com.dippa.ragbenchmark.domain.ComplexityTier;
import com.dippa.ragbenchmark.domain.ModelRouter;
import com.dippa.ragbenchmark.domain.RoutingDecision;
import com.dippa.ragbenchmark.domain.RoutingMode;
import com.dippa.ragbenchmark.domain.RoutingRequest;
import com.dippa.ragbenchmark.infrastructure.config.RouterProperties;

/**
 * Passthrough {@link ModelRouter} used when model routing is disabled or unconfigured.
 * Always routes to the default workhorse model with zero latency and zero routing tokens.
 */
public class NoOpModelRouter implements ModelRouter {

    private final String defaultModel;

    public NoOpModelRouter() {
        this("gemini-3.8-flash");
    }

    public NoOpModelRouter(String defaultModel) {
        this.defaultModel = (defaultModel != null && !defaultModel.isBlank()) ? defaultModel : "gemini-3.8-flash";
    }

    public NoOpModelRouter(RouterProperties routerProperties) {
        this(routerProperties != null ? routerProperties.fallbackModel() : "gemini-3.8-flash");
    }

    @Override
    public RoutingDecision route(RoutingRequest request) {
        return RoutingDecision.direct(defaultModel, ComplexityTier.TWO_HOP, 2, RoutingMode.NOOP);
    }

    @Override
    public boolean isConfigured() {
        return false;
    }
}
