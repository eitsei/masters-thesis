package com.dippa.ragbenchmark.application.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dippa.ragbenchmark.api.BenchmarkBatchQueryRequest;
import com.dippa.ragbenchmark.api.BenchmarkQueryRequest;
import com.dippa.ragbenchmark.api.TokenUsageDto;
import com.dippa.ragbenchmark.application.RagQueryResult;
import com.dippa.ragbenchmark.application.RagQueryService;
import com.dippa.ragbenchmark.application.RetrievedChunk;
import com.dippa.ragbenchmark.domain.ModuleId;
import com.dippa.ragbenchmark.domain.dataset.MusiqueParagraph;
import com.dippa.ragbenchmark.domain.dataset.MusiqueQuestion;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MusiqueBenchmarkQueryServiceTest {

    @Mock
    private MusiqueDatasetService datasetService;

    @Mock
    private RagQueryService ragQueryService;

    private MusiqueBenchmarkQueryService benchmarkQueryService;

    private MusiqueQuestion sampleQuestion;

    @BeforeEach
    void setUp() {
        benchmarkQueryService = new MusiqueBenchmarkQueryService(datasetService, ragQueryService);
        sampleQuestion = new MusiqueQuestion(
                "2hop__123",
                "Where was Tolkien born?",
                "Bloemfontein",
                List.of("Orange Free State"),
                2,
                true,
                List.of(
                        new MusiqueParagraph(0, "J.R.R. Tolkien", "Tolkien was born in Bloemfontein.", true),
                        new MusiqueParagraph(1, "Bloemfontein", "Bloemfontein is a city in South Africa.", true)
                ),
                List.of()
        );
    }

    @Test
    void evaluateQuestion_withRequest_invokesRagQueryServiceWithCustomParameters() {
        when(datasetService.getQuestionById("2hop__123")).thenReturn(Optional.of(sampleQuestion));

        TokenUsageDto usage = new TokenUsageDto(100, 40, 140, 15);
        RetrievedChunk chunk = new RetrievedChunk("c1", "Tolkien was born...", "J.R.R. Tolkien was born in Bloemfontein.");
        RagQueryResult queryResult = new RagQueryResult(
                "Bloemfontein",
                Set.of(),
                List.of(chunk),
                25L,
                75L,
                usage
        );
        when(ragQueryService.query(eq("Where was Tolkien born?"), any(), eq("custom_table"), eq(8)))
                .thenReturn(queryResult);

        BenchmarkQueryRequest request = new BenchmarkQueryRequest(Set.of(), "custom_table", 8);
        MusiqueBenchmarkQueryResult result = benchmarkQueryService.evaluateQuestion("2hop__123", request);

        assertThat(result.questionId()).isEqualTo("2hop__123");
        assertThat(result.generatedAnswer()).isEqualTo("Bloemfontein");
        assertThat(result.supportingEvidenceFound()).isTrue();
        assertThat(result.tokenUsage()).isEqualTo(usage);
        assertThat(result.totalLatencyMs()).isEqualTo(100L);
        assertThat(result.tableNameUsed()).isEqualTo("custom_table");

        verify(ragQueryService).query("Where was Tolkien born?", Set.of(), "custom_table", 8);
    }

    @Test
    void evaluateBatch_aggregatesTimingsAndTokens() {
        when(datasetService.getQuestionById("2hop__123")).thenReturn(Optional.of(sampleQuestion));

        TokenUsageDto usage = new TokenUsageDto(50, 20, 70, 5);
        RetrievedChunk chunk = new RetrievedChunk("c1", "J.R.R. Tolkien", "full text");
        RagQueryResult queryResult = new RagQueryResult(
                "Bloemfontein",
                Set.of(),
                List.of(chunk),
                10L,
                40L,
                usage
        );
        when(ragQueryService.query(anyString(), any(), anyString(), any()))
                .thenReturn(queryResult);

        BenchmarkBatchQueryRequest batchRequest = new BenchmarkBatchQueryRequest(
                List.of("2hop__123"),
                null,
                null,
                null,
                null,
                Set.of(),
                "vector_store_test",
                5
        );

        MusiqueBatchQueryResult batchResult = benchmarkQueryService.evaluateBatch(batchRequest);

        assertThat(batchResult.totalQuestions()).isEqualTo(1);
        assertThat(batchResult.supportingEvidenceFoundCount()).isEqualTo(1);
        assertThat(batchResult.averageLatencyMs()).isEqualTo(50.0);
        assertThat(batchResult.totalTokenUsage().promptTokens()).isEqualTo(50L);
        assertThat(batchResult.totalTokenUsage().completionTokens()).isEqualTo(20L);
        assertThat(batchResult.totalTokenUsage().totalTokens()).isEqualTo(70L);
        assertThat(batchResult.totalTokenUsage().cachedTokens()).isEqualTo(5L);
        assertThat(batchResult.results()).hasSize(1);
    }

    @Test
    void evaluateQuestion_withoutRequest_usesDefaultTableName() {
        when(datasetService.getQuestionById("2hop__123")).thenReturn(Optional.of(sampleQuestion));
        when(ragQueryService.getDefaultTableName()).thenReturn("vector_store_test");

        RagQueryResult queryResult = new RagQueryResult(
                "Bloemfontein",
                Set.of(),
                List.of(),
                10L,
                40L,
                TokenUsageDto.ZERO
        );
        when(ragQueryService.query(eq("Where was Tolkien born?"), any(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(queryResult);

        MusiqueBenchmarkQueryResult result = benchmarkQueryService.evaluateQuestion("2hop__123");

        assertThat(result.tableNameUsed()).isEqualTo("vector_store_test");
        verify(ragQueryService).query("Where was Tolkien born?", Set.of(), null, null);
    }
}
