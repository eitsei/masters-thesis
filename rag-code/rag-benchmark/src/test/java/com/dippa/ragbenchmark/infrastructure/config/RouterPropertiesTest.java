package com.dippa.ragbenchmark.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RouterPropertiesTest {

    @Test
    void defaultProperties_hasExpectedDefaults() {
        RouterProperties properties = RouterProperties.defaultProperties();

        assertThat(properties.provider()).isEqualTo(RouterProperties.Provider.NOOP);
        assertThat(properties.classifierModel()).isEqualTo("gemini-3.5-flash-lite");
        assertThat(properties.fallbackModel()).isEqualTo("gemini-3.8-flash");
        assertThat(properties.temperature()).isEqualTo(0.0);
        assertThat(properties.timeoutMs()).isEqualTo(5000);
    }

    @Test
    void providerParse_handlesSupportedValuesAndCaseInsensitivity() {
        assertThat(RouterProperties.Provider.parse("noop")).isEqualTo(RouterProperties.Provider.NOOP);
        assertThat(RouterProperties.Provider.parse("CLASSIFIER")).isEqualTo(RouterProperties.Provider.CLASSIFIER);
        assertThat(RouterProperties.Provider.parse(" Oracle ")).isEqualTo(RouterProperties.Provider.ORACLE);
        assertThat(RouterProperties.Provider.parse(null)).isEqualTo(RouterProperties.Provider.NOOP);
        assertThat(RouterProperties.Provider.parse("")).isEqualTo(RouterProperties.Provider.NOOP);
    }

    @Test
    void providerParse_unknownValue_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> RouterProperties.Provider.parse("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown router provider 'unknown'");
    }

    @Test
    void compactConstructor_sanitizesInvalidInputs() {
        RouterProperties properties = new RouterProperties(null, null, "", -1.0, 0);

        assertThat(properties.provider()).isEqualTo(RouterProperties.Provider.NOOP);
        assertThat(properties.classifierModel()).isEqualTo("gemini-3.5-flash-lite");
        assertThat(properties.fallbackModel()).isEqualTo("gemini-3.8-flash");
        assertThat(properties.temperature()).isEqualTo(0.0);
        assertThat(properties.timeoutMs()).isEqualTo(5000);
    }
}
