package com.dippa.ragbenchmark.infrastructure;

import com.dippa.ragbenchmark.domain.ComplexityTier;
import com.dippa.ragbenchmark.domain.ModelRouter;
import com.dippa.ragbenchmark.domain.RoutingDecision;
import com.dippa.ragbenchmark.domain.RoutingMode;
import com.dippa.ragbenchmark.domain.RoutingRequest;
import com.dippa.ragbenchmark.infrastructure.config.ModelCatalogProperties;
import com.dippa.ragbenchmark.infrastructure.config.RouterProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deterministic oracle implementation of {@link ModelRouter} for benchmark evaluation.
 * Directly maps ground-truth reasoning depth metadata (hopCountHint) to the corresponding
 * model tier without incurring classification latency or router token costs.
 * Represents the theoretical upper-bound ceiling for cascading efficiency.
 *
 * <ul>
 *     <li>hopCount == 1 &rarr; Gemini 3.5 Flash-Lite (Single-hop / Enterprise factual)</li>
 *     <li>hopCount == 2 &rarr; Gemini 3.8 Flash (Intermediate / MuSiQue 40%)</li>
 *     <li>hopCount &ge; 3 &rarr; Gemini 3.1 Pro (Complex Multi-hop / MuSiQue 60%)</li>
 *     <li>hopCount == null &rarr; Gemini 3.8 Flash (Default workhorse fallback for ad-hoc queries)</li>
 * </ul>
 */
public class OracleModelRouter implements ModelRouter {

    private static final Logger logger = LoggerFactory.getLogger(OracleModelRouter.class);

    private final String flashLiteModel;
    private final String flashModel;
    private final String proModel;

    public OracleModelRouter(ModelCatalogProperties catalogProperties, RouterProperties routerProperties) {
        String lite = null;
        String workhorse = null;
        String reasoning = null;

        if (catalogProperties != null && catalogProperties.googleGenai() != null) {
            lite = catalogProperties.googleGenai().flashLite();
            workhorse = catalogProperties.googleGenai().flash();
            reasoning = catalogProperties.googleGenai().pro();
        }

        this.flashLiteModel = (lite != null && !lite.isBlank()) ? lite : "gemini-3.5-flash-lite";
        this.flashModel = (workhorse != null && !workhorse.isBlank()) ? workhorse
                : (routerProperties != null ? routerProperties.fallbackModel() : "gemini-3.8-flash");
        this.proModel = (reasoning != null && !reasoning.isBlank()) ? reasoning : "gemini-3.1-pro";
    }

    public OracleModelRouter(String flashLiteModel, String flashModel, String proModel) {
        this.flashLiteModel = (flashLiteModel != null && !flashLiteModel.isBlank()) ? flashLiteModel : "gemini-3.5-flash-lite";
        this.flashModel = (flashModel != null && !flashModel.isBlank()) ? flashModel : "gemini-3.8-flash";
        this.proModel = (proModel != null && !proModel.isBlank()) ? proModel : "gemini-3.1-pro";
    }

    @Override
    public RoutingDecision route(RoutingRequest request) {
        Integer hops = request != null ? request.hopCountHint() : null;
        RoutingDecision decision;
        if (hops == null) {
            decision = new RoutingDecision(
                    flashModel,
                    ComplexityTier.TWO_HOP,
                    2,
                    0L,
                    0,
                    0,
                    RoutingMode.ORACLE,
                    "Oracle route: no hopCountHint provided, defaulted to workhorse");
        } else if (hops <= 1) {
            decision = new RoutingDecision(
                    flashLiteModel,
                    ComplexityTier.SINGLE_HOP,
                    1,
                    0L,
                    0,
                    0,
                    RoutingMode.ORACLE,
                    "Oracle route: 1-hop mapped to Flash-Lite");
        } else if (hops == 2) {
            decision = new RoutingDecision(
                    flashModel,
                    ComplexityTier.TWO_HOP,
                    2,
                    0L,
                    0,
                    0,
                    RoutingMode.ORACLE,
                    "Oracle route: 2-hop mapped to Flash");
        } else {
            decision = new RoutingDecision(
                    proModel,
                    ComplexityTier.MULTI_HOP,
                    hops,
                    0L,
                    0,
                    0,
                    RoutingMode.ORACLE,
                    "Oracle route: " + hops + "-hop mapped to Pro");
        }
        logger.info("Oracle router mapped query (hopCountHint={}) -> routed to model '{}' (tier={})",
                hops, decision.selectedModel(), decision.complexityTier());
        return decision;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }
}
