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
        assertThat(zero.cacheWriteTokens()).isZero();
        assertThat(zero.rerankDocs()).isZero();
        assertThat(zero.embedTokens()).isZero();
        assertThat(zero.routingTokens()).isZero();
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

    @Test
    void fullConstructor_serializesAllAuxiliaryTelemetry() throws Exception {
        TokenUsageDto dto = new TokenUsageDto(1000, 50, 1050, 4000, 0, 50, 18, 120);
        assertThat(dto.rerankDocs()).isEqualTo(50);
        assertThat(dto.embedTokens()).isEqualTo(18);
        assertThat(dto.routingTokens()).isEqualTo(120);

        String json = objectMapper.writeValueAsString(dto);
        assertThat(json).contains("\"rerankDocs\":50");
        assertThat(json).contains("\"embedTokens\":18");
        assertThat(json).contains("\"routingTokens\":120");

        TokenUsageDto deserialized = objectMapper.readValue(json, TokenUsageDto.class);
        assertThat(deserialized).isEqualTo(dto);
    }

    @Test
    void granularRoutingTokens_serializesAndDeserializesCorrectly() throws Exception {
        TokenUsageDto dto = new TokenUsageDto(1000, 50, 1050, 4000, 0, 50, 18, 25, 2);
        assertThat(dto.routingPromptTokens()).isEqualTo(25);
        assertThat(dto.routingCompletionTokens()).isEqualTo(2);
        assertThat(dto.routingTokens()).isEqualTo(27);

        String json = objectMapper.writeValueAsString(dto);
        assertThat(json).contains("\"routingPromptTokens\":25");
        assertThat(json).contains("\"routingCompletionTokens\":2");
        assertThat(json).contains("\"routingTokens\":27");

        TokenUsageDto deserialized = objectMapper.readValue(json, TokenUsageDto.class);
        assertThat(deserialized).isEqualTo(dto);
        assertThat(deserialized.routingPromptTokens()).isEqualTo(25);
        assertThat(deserialized.routingCompletionTokens()).isEqualTo(2);
        assertThat(deserialized.routingTokens()).isEqualTo(27);
    }
}
