package com.dippa.ragbenchmark.application.dataset;

import static org.assertj.core.api.Assertions.assertThat;

import com.dippa.ragbenchmark.api.TokenUsageDto;
import com.dippa.ragbenchmark.application.RetrievedChunk;
import java.util.List;
import org.junit.jupiter.api.Test;

class MusiqueBenchmarkQueryResultTest {

    @Test
    void fullConstructor_setsAllFieldsCorrectly() {
        TokenUsageDto tokenUsage = new TokenUsageDto(100, 50, 150, 20);
        MusiqueBenchmarkQueryResult result = new MusiqueBenchmarkQueryResult(
                "q-1",
                "Where was Tolkien born?",
                "Bloemfontein",
                List.of("Orange Free State"),
                2,
                "Bloemfontein, South Africa",
                List.of("M0"),
                List.of(new RetrievedChunk("c1", "snippet", "text")),
                50L,
                150L,
                200L,
                tokenUsage,
                "custom_table",
                List.of("Tolkien bio"),
                true
        );

        assertThat(result.questionId()).isEqualTo("q-1");
        assertThat(result.totalLatencyMs()).isEqualTo(200L);
        assertThat(result.tokenUsage()).isEqualTo(tokenUsage);
        assertThat(result.tableNameUsed()).isEqualTo("custom_table");
    }

    @Test
    void backwardCompatibleConstructor_defaultsNewFields() {
        MusiqueBenchmarkQueryResult result = new MusiqueBenchmarkQueryResult(
                "q-1",
                "Where was Tolkien born?",
                "Bloemfontein",
                List.of(),
                2,
                "Bloemfontein",
                List.of(),
                List.of(),
                30L,
                70L,
                List.of("Tolkien bio"),
                false
        );

        assertThat(result.totalLatencyMs()).isEqualTo(100L);
        assertThat(result.tokenUsage()).isEqualTo(TokenUsageDto.ZERO);
        assertThat(result.tableNameUsed()).isEqualTo("vector_store_test");
    }
}
