package com.dippa.ragbenchmark.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.dippa.ragbenchmark.application.RetrievedChunk;
import com.dippa.ragbenchmark.application.dataset.MusiqueBenchmarkQueryResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class MusiqueBenchmarkQueryResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fromResult_mapsTokenUsageTotalLatencyAndTableName() throws Exception {
        TokenUsageDto usage = new TokenUsageDto(80, 20, 100, 10);
        MusiqueBenchmarkQueryResult result = new MusiqueBenchmarkQueryResult(
                "q-1",
                "question",
                "ground-truth",
                List.of("alias"),
                2,
                "generated",
                List.of(),
                List.of(new RetrievedChunk("c1", "snippet", "full text")),
                25L,
                75L,
                100L,
                usage,
                "custom_musique_table",
                List.of("Title"),
                true
        );

        MusiqueBenchmarkQueryResponse response = MusiqueBenchmarkQueryResponse.fromResult(result);

        assertThat(response.questionId()).isEqualTo("q-1");
        assertThat(response.totalLatencyMs()).isEqualTo(100L);
        assertThat(response.tokenUsage()).isEqualTo(usage);
        assertThat(response.tableNameUsed()).isEqualTo("custom_musique_table");

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"totalLatencyMs\":100");
        assertThat(json).contains("\"tableNameUsed\":\"custom_musique_table\"");
        assertThat(json).contains("\"tokenUsage\":{");
        assertThat(json).contains("\"promptTokens\":80");
    }

    @Test
    void fromResult_preservesTtftMsAndPrefillMsInTimings() {
        TokenUsageDto usage = new TokenUsageDto(100, 50, 150, 0);
        MusiqueBenchmarkQueryResult result = new MusiqueBenchmarkQueryResult(
                "q-ttft",
                "question",
                "ground-truth",
                List.of(),
                2,
                "answer",
                List.of("M1", "M2"),
                List.of(),
                25L,  // retrieval
                15L,  // rerank
                60L,  // generation
                10L,  // expansion
                5L,   // cache
                20L,  // route
                85L,  // ttftMs (retrieval 25 + rerank 15 + expand 10 + cache 5 + route 20 + prefill 10)
                10L,  // prefillMs
                "gemini-3.8-flash",
                com.dippa.ragbenchmark.domain.CacheStatus.NONE,
                CacheMetadataDto.NONE,
                135L, // totalLatencyMs
                usage,
                "custom_musique_table",
                List.of("Title"),
                true,
                1,
                true
        );

        MusiqueBenchmarkQueryResponse response = MusiqueBenchmarkQueryResponse.fromResult(result);

        assertThat(response.timings()).isNotNull();
        assertThat(response.timings().ttftMs()).isEqualTo(85L);
        assertThat(response.timings().prefillMs()).isEqualTo(10L);
        assertThat(response.timings().retrievalMs()).isEqualTo(25L);
        assertThat(response.timings().rerankMs()).isEqualTo(15L);
        assertThat(response.timings().expansionMs()).isEqualTo(10L);
        assertThat(response.timings().cacheMs()).isEqualTo(5L);
        assertThat(response.timings().routeMs()).isEqualTo(20L);
        assertThat(response.timings().generationMs()).isEqualTo(60L);
    }

    @Test
    void backwardCompatibleConstructor_defaultsNewFields() {
        MusiqueBenchmarkQueryResponse response = new MusiqueBenchmarkQueryResponse(
                "q-1",
                "question",
                "ground-truth",
                List.of(),
                2,
                "generated",
                List.of(),
                List.of(),
                new TimingsDto(20L, 80L),
                List.of(),
                false
        );

        assertThat(response.totalLatencyMs()).isEqualTo(100L);
        assertThat(response.tokenUsage()).isEqualTo(TokenUsageDto.ZERO);
        assertThat(response.tableNameUsed()).isNull();
    }
}
