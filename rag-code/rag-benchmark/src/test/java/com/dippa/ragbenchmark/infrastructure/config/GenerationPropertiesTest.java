package com.dippa.ragbenchmark.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GenerationPropertiesTest {

    @Test
    void defaultConstructor_defaultsToZero() {
        GenerationProperties properties = new GenerationProperties(null, null);
        assertThat(properties.thinkingBudget()).isEqualTo(0);
        assertThat(properties.temperature()).isEqualTo(0.0);
    }

    @Test
    void defaultProperties_defaultsToZero() {
        GenerationProperties properties = GenerationProperties.defaultProperties();
        assertThat(properties.thinkingBudget()).isEqualTo(0);
        assertThat(properties.temperature()).isEqualTo(0.0);
    }

    @Test
    void customBudget_isPreserved() {
        GenerationProperties properties = new GenerationProperties(256, null);
        assertThat(properties.thinkingBudget()).isEqualTo(256);
        assertThat(properties.temperature()).isEqualTo(0.0);
    }

    @Test
    void negativeBudget_isPreservedForDynamicThinking() {
        GenerationProperties properties = new GenerationProperties(-1, null);
        assertThat(properties.thinkingBudget()).isEqualTo(-1);
        assertThat(properties.temperature()).isEqualTo(0.0);
    }

    @Test
    void customTemperature_isPreserved() {
        GenerationProperties properties = new GenerationProperties(128, 0.7);
        assertThat(properties.thinkingBudget()).isEqualTo(128);
        assertThat(properties.temperature()).isEqualTo(0.7);
    }

    @Test
    void negativeTemperature_defaultsToZero() {
        GenerationProperties properties = new GenerationProperties(0, -0.5);
        assertThat(properties.temperature()).isEqualTo(0.0);
    }
}
