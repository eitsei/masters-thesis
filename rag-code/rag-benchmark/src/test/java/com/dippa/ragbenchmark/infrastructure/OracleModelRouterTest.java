package com.dippa.ragbenchmark.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.dippa.ragbenchmark.domain.ComplexityTier;
import com.dippa.ragbenchmark.domain.RoutingDecision;
import com.dippa.ragbenchmark.domain.RoutingMode;
import com.dippa.ragbenchmark.domain.RoutingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OracleModelRouterTest {

    private OracleModelRouter router;

    @BeforeEach
    void setUp() {
        router = new OracleModelRouter("gemini-3.5-flash-lite", "gemini-3.8-flash", "gemini-3.1-pro");
    }

    @Test
    void isConfigured_returnsTrue() {
        assertThat(router.isConfigured()).isTrue();
    }

    @Test
    void route_singleHop_mapsToFlashLite() {
        RoutingDecision decision = router.route(new RoutingRequest("What is capital of Finland?", 1));

        assertThat(decision.selectedModel()).isEqualTo("gemini-3.5-flash-lite");
        assertThat(decision.complexityTier()).isEqualTo(ComplexityTier.SINGLE_HOP);
        assertThat(decision.predictedHops()).isEqualTo(1);
        assertThat(decision.routingLatencyMs()).isZero();
        assertThat(decision.routingPromptTokens()).isZero();
        assertThat(decision.routingCompletionTokens()).isZero();
        assertThat(decision.routingMode()).isEqualTo(RoutingMode.ORACLE);
    }

    @Test
    void route_twoHop_mapsToFlash() {
        RoutingDecision decision = router.route(new RoutingRequest("Where was author born and educated?", 2));

        assertThat(decision.selectedModel()).isEqualTo("gemini-3.8-flash");
        assertThat(decision.complexityTier()).isEqualTo(ComplexityTier.TWO_HOP);
        assertThat(decision.predictedHops()).isEqualTo(2);
        assertThat(decision.routingLatencyMs()).isZero();
        assertThat(decision.routingPromptTokens()).isZero();
        assertThat(decision.routingCompletionTokens()).isZero();
        assertThat(decision.routingMode()).isEqualTo(RoutingMode.ORACLE);
    }

    @Test
    void route_threeOrFourHop_mapsToPro() {
        RoutingDecision decision3 = router.route(new RoutingRequest("Multi-hop 3", 3));
        assertThat(decision3.selectedModel()).isEqualTo("gemini-3.1-pro");
        assertThat(decision3.complexityTier()).isEqualTo(ComplexityTier.MULTI_HOP);
        assertThat(decision3.predictedHops()).isEqualTo(3);
        assertThat(decision3.routingMode()).isEqualTo(RoutingMode.ORACLE);

        RoutingDecision decision4 = router.route(new RoutingRequest("Multi-hop 4", 4));
        assertThat(decision4.selectedModel()).isEqualTo("gemini-3.1-pro");
        assertThat(decision4.complexityTier()).isEqualTo(ComplexityTier.MULTI_HOP);
        assertThat(decision4.predictedHops()).isEqualTo(4);
    }

    @Test
    void route_nullHopHint_defaultsToFlash() {
        RoutingDecision decision = router.route(new RoutingRequest("Ad-hoc query without metadata", null));

        assertThat(decision.selectedModel()).isEqualTo("gemini-3.8-flash");
        assertThat(decision.complexityTier()).isEqualTo(ComplexityTier.TWO_HOP);
        assertThat(decision.predictedHops()).isEqualTo(2);
        assertThat(decision.routingMode()).isEqualTo(RoutingMode.ORACLE);
    }
}
