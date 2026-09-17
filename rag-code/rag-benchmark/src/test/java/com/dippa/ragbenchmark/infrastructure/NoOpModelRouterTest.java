package com.dippa.ragbenchmark.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.dippa.ragbenchmark.domain.ComplexityTier;
import com.dippa.ragbenchmark.domain.RoutingDecision;
import com.dippa.ragbenchmark.domain.RoutingMode;
import com.dippa.ragbenchmark.domain.RoutingRequest;
import org.junit.jupiter.api.Test;

class NoOpModelRouterTest {

    @Test
    void isConfigured_alwaysReturnsFalse() {
        NoOpModelRouter router = new NoOpModelRouter();
        assertThat(router.isConfigured()).isFalse();
    }

    @Test
    void route_returnsDefaultWorkhorseWithZeroLatencyAndTokens() {
        NoOpModelRouter router = new NoOpModelRouter("custom-default-model");
        RoutingDecision decision = router.route(new RoutingRequest("What is RAG?"));

        assertThat(decision.selectedModel()).isEqualTo("custom-default-model");
        assertThat(decision.complexityTier()).isEqualTo(ComplexityTier.TWO_HOP);
        assertThat(decision.predictedHops()).isEqualTo(2);
        assertThat(decision.routingLatencyMs()).isZero();
        assertThat(decision.routingPromptTokens()).isZero();
        assertThat(decision.routingCompletionTokens()).isZero();
        assertThat(decision.routingMode()).isEqualTo(RoutingMode.NOOP);
    }
}
