package com.dippa.ragbenchmark.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.dippa.ragbenchmark.application.RetrievedChunk;
import com.dippa.ragbenchmark.application.dataset.MusiqueBenchmarkQueryResult;
import com.dippa.ragbenchmark.application.dataset.MusiqueBatchQueryResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BenchmarkDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void benchmarkQueryRequest_defaultsNullModulesToEmptySet() {
        BenchmarkQueryRequest request = new BenchmarkQueryRequest(null, "table_a", 10);
        assertThat(request.modules()).isEmpty();
        assertThat(request.tableName()).isEqualTo("table_a");
        assertThat(request.topK()).isEqualTo(10);
    }

    @Test
    void benchmarkBatchQueryRequest_serializationRoundTrip() throws Exception {
        BenchmarkBatchQueryRequest request = new BenchmarkBatchQueryRequest(
                List.of("q1", "q2"),
                10,
                0,
                2,
                true,
                Set.of("M0"),
                "table_test",
                5
        );

        String json = objectMapper.writeValueAsString(request);
        BenchmarkBatchQueryRequest deserialized = objectMapper.readValue(json, BenchmarkBatchQueryRequest.class);

        assertThat(deserialized.questionIds()).containsExactly("q1", "q2");
        assertThat(deserialized.hops()).isEqualTo(2);
        assertThat(deserialized.sample()).isTrue();
        assertThat(deserialized.tableName()).isEqualTo("table_test");
        assertThat(deserialized.topK()).isEqualTo(5);
    }

    @Test
    void benchmarkBatchQueryResponse_fromResult_mapsCorrectly() throws Exception {
        TokenUsageDto usage = new TokenUsageDto(100, 50, 150, 10);
        MusiqueBenchmarkQueryResult qResult = new MusiqueBenchmarkQueryResult(
                "q-1",
                "Question",
                "Answer",
                List.of(),
                2,
                "Generated",
                List.of(),
                List.of(new RetrievedChunk("c1", "snip", "text")),
                20L,
                80L,
                100L,
                usage,
                "table_test",
                List.of("Title"),
                true
        );

        MusiqueBatchQueryResult batchResult = new MusiqueBatchQueryResult(
                1,
                150L,
                100.0,
                1,
                usage,
                List.of(qResult)
        );

        BenchmarkBatchQueryResponse response = BenchmarkBatchQueryResponse.fromResult(batchResult);

        assertThat(response.totalQuestions()).isEqualTo(1);
        assertThat(response.totalExecutionTimeMs()).isEqualTo(150L);
        assertThat(response.averageLatencyMs()).isEqualTo(100.0);
        assertThat(response.supportingEvidenceFoundCount()).isEqualTo(1);
        assertThat(response.totalTokenUsage()).isEqualTo(usage);
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).questionId()).isEqualTo("q-1");

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"totalQuestions\":1");
        assertThat(json).contains("\"supportingEvidenceFoundCount\":1");
    }
}
