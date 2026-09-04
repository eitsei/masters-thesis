package com.dippa.ragbenchmark.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TokenUsageDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void zeroConstant_hasAllZeroValues() {
        TokenUsageDto zero = TokenUsageDto.ZERO;
        assertThat(zero.promptTokens()).isZero();
        assertThat(zero.completionTokens()).isZero();
        assertThat(zero.totalTokens()).isZero();
        assertThat(zero.cachedTokens()).isZero();
    }

    @Test
    void serializationAndDeserialization_roundTrip() throws Exception {
        TokenUsageDto dto = new TokenUsageDto(120, 45, 165, 30);
        String json = objectMapper.writeValueAsString(dto);

        assertThat(json).contains("\"promptTokens\":120");
        assertThat(json).contains("\"completionTokens\":45");
        assertThat(json).contains("\"totalTokens\":165");
        assertThat(json).contains("\"cachedTokens\":30");

        TokenUsageDto deserialized = objectMapper.readValue(json, TokenUsageDto.class);
        assertThat(deserialized).isEqualTo(dto);
    }
}
