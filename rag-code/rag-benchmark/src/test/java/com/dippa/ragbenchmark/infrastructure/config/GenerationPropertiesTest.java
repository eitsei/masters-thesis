package com.dippa.ragbenchmark.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GenerationPropertiesTest {

    @Test
    void defaultConstructor_defaultsToZero() {
        GenerationProperties properties = new GenerationProperties(null);
        assertThat(properties.thinkingBudget()).isEqualTo(0);
    }

    @Test
    void defaultProperties_defaultsToZero() {
        GenerationProperties properties = GenerationProperties.defaultProperties();
        assertThat(properties.thinkingBudget()).isEqualTo(0);
    }

    @Test
    void customBudget_isPreserved() {
        GenerationProperties properties = new GenerationProperties(256);
        assertThat(properties.thinkingBudget()).isEqualTo(256);
    }

    @Test
    void negativeBudget_isPreservedForDynamicThinking() {
        GenerationProperties properties = new GenerationProperties(-1);
        assertThat(properties.thinkingBudget()).isEqualTo(-1);
    }
}
