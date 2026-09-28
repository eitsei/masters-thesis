package com.dippa.ragbenchmark.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dippa.ragbenchmark.application.RetrievedChunk;
import com.dippa.ragbenchmark.application.dataset.MusiqueBenchmarkQueryResult;
import com.dippa.ragbenchmark.application.dataset.MusiqueBenchmarkQueryService;
import com.dippa.ragbenchmark.application.dataset.MusiqueDatasetService;
import com.dippa.ragbenchmark.application.dataset.MusiqueIngestionSummary;
import com.dippa.ragbenchmark.domain.dataset.MusiqueParagraph;
import com.dippa.ragbenchmark.domain.dataset.MusiqueQuestion;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MusiqueBenchmarkController.class)
class MusiqueBenchmarkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MusiqueDatasetService datasetService;

    @MockitoBean
    private MusiqueBenchmarkQueryService benchmarkQueryService;

    @Test
    void getQuestions_returnsQuestionsList() throws Exception {
        MusiqueQuestion q1 = new MusiqueQuestion(
                "2hop__123",
                "Who is the author?",
                "Tolkien",
                List.of("J.R.R. Tolkien"),
                2,
                true,
                List.of(new MusiqueParagraph(0, "Lord of the Rings", "Tolkien wrote...", true)),
                List.of()
        );

        when(datasetService.getQuestions(eq(0), eq(20), eq(null), eq(false)))
                .thenReturn(List.of(q1));

        mockMvc.perform(get("/api/benchmark/musique/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("2hop__123"))
                .andExpect(jsonPath("$[0].question").value("Who is the author?"))
                .andExpect(jsonPath("$[0].answer").value("Tolkien"))
                .andExpect(jsonPath("$[0].hopCount").value(2))
                .andExpect(jsonPath("$[0].supportingTitles[0]").value("Lord of the Rings"));
    }

    @Test
    void getQuestionById_returns404WhenNotFound() throws Exception {
        when(datasetService.getQuestionById("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/benchmark/musique/questions/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSample_returnsCanonicalSample() throws Exception {
        MusiqueQuestion q1 = new MusiqueQuestion(
                "2hop__sample_1",
                "Question 1",
                "Answer 1",
                List.of(),
                2,
                true,
                List.of(),
                List.of()
        );

        when(datasetService.getCanonicalBenchmarkSample()).thenReturn(List.of(q1));

        mockMvc.perform(get("/api/benchmark/musique/sample"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("2hop__sample_1"));
    }

    @Test
    void getStats_returnsSummaryMap() throws Exception {
        when(datasetService.getDatasetStats()).thenReturn(Map.of(
                "totalQuestions", 4834,
                "twoHopCount", 2504L,
                "threeHopCount", 1520L,
                "fourHopCount", 810L,
                "answerableCount", 2417L,
                "unanswerableCount", 2417L,
                "canonicalSampleSize", 100,
                "datasetPath", "data/musique/musique_full_v1.0_dev.jsonl"
        ));

        mockMvc.perform(get("/api/benchmark/musique/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuestions").value(4834))
                .andExpect(jsonPath("$.twoHopCount").value(2504))
                .andExpect(jsonPath("$.answerableCount").value(2417))
                .andExpect(jsonPath("$.unanswerableCount").value(2417))
                .andExpect(jsonPath("$.canonicalSampleSize").value(100));
    }

    @Test
    void ingest_triggersIngestionAndReturnsSummary() throws Exception {
        when(datasetService.ingest(eq(true), eq(100), eq("original"), eq(false)))
                .thenReturn(new MusiqueIngestionSummary(100, 1619, 17, 350L));

        mockMvc.perform(post("/api/benchmark/musique/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sampleOnly\": true, \"sampleSize\": 100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.questionsProcessed").value(100))
                .andExpect(jsonPath("$.uniqueParagraphsIngested").value(1619))
                .andExpect(jsonPath("$.batches").value(17))
                .andExpect(jsonPath("$.durationMs").value(350))
                .andExpect(jsonPath("$.databaseCleared").value(false));
    }

    @Test
    void ingest_withQueryParamsAndClearDatabase() throws Exception {
        when(datasetService.ingest(eq(true), eq(50), eq("original"), eq(true)))
                .thenReturn(new MusiqueIngestionSummary(50, 800, 800, 750, 1200, 45, 1200L, "original", true));

        mockMvc.perform(post("/api/benchmark/musique/ingest?mode=original&clearDatabase=true&sampleOnly=true&sampleSize=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.mode").value("original"))
                .andExpect(jsonPath("$.databaseCleared").value(true))
                .andExpect(jsonPath("$.questionsProcessed").value(50))
                .andExpect(jsonPath("$.uniqueParagraphsIngested").value(800))
                .andExpect(jsonPath("$.baselineChunksWritten").value(800))
                .andExpect(jsonPath("$.parentChunksWritten").value(750))
                .andExpect(jsonPath("$.childChunksWritten").value(1200));
    }

    @Test
    void queryQuestion_evaluatesBaselineAndReturnsResult() throws Exception {
        MusiqueBenchmarkQueryResult evalResult = new MusiqueBenchmarkQueryResult(
                "2hop__123",
                "Who is the author?",
                "Tolkien",
                List.of(),
                2,
                "Tolkien wrote it.",
                List.of(),
                List.of(new RetrievedChunk("chunk-1", "Lord of the Rings by Tolkien")),
                15L,
                45L,
                List.of("Lord of the Rings"),
                true
        );

        when(benchmarkQueryService.evaluateQuestion("2hop__123")).thenReturn(evalResult);

        mockMvc.perform(post("/api/benchmark/musique/questions/2hop__123/query"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionId").value("2hop__123"))
                .andExpect(jsonPath("$.groundTruthAnswer").value("Tolkien"))
                .andExpect(jsonPath("$.generatedAnswer").value("Tolkien wrote it."))
                .andExpect(jsonPath("$.supportingEvidenceFound").value(true))
                .andExpect(jsonPath("$.timings.retrievalMs").value(15))
                .andExpect(jsonPath("$.timings.generationMs").value(45));
    }

    @Test
    void queryQuestion_withRequestBody_passesParametersAndReturnsEnrichedResponse() throws Exception {
        TokenUsageDto usage = new TokenUsageDto(120, 30, 150, 10);
        MusiqueBenchmarkQueryResult evalResult = new MusiqueBenchmarkQueryResult(
                "2hop__123",
                "Who is the author?",
                "Tolkien",
                List.of(),
                2,
                "Tolkien wrote it.",
                List.of(),
                List.of(new RetrievedChunk("chunk-1", "Lord of the Rings by Tolkien", "Full chunk text")),
                15L,
                45L,
                60L,
                usage,
                "custom_tbl",
                List.of("Lord of the Rings"),
                true
        );

        when(benchmarkQueryService.evaluateQuestion(eq("2hop__123"), any(BenchmarkQueryRequest.class)))
                .thenReturn(evalResult);

        mockMvc.perform(post("/api/benchmark/musique/questions/2hop__123/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modules\": [], \"tableName\": \"custom_tbl\", \"topK\": 10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionId").value("2hop__123"))
                .andExpect(jsonPath("$.tableNameUsed").value("custom_tbl"))
                .andExpect(jsonPath("$.totalLatencyMs").value(60))
                .andExpect(jsonPath("$.tokenUsage.promptTokens").value(120))
                .andExpect(jsonPath("$.tokenUsage.cachedTokens").value(10));
    }

    @Test
    void batchQuery_returnsBatchAggregateMetrics() throws Exception {
        TokenUsageDto usage = new TokenUsageDto(200, 50, 250, 20);
        MusiqueBenchmarkQueryResult itemResult = new MusiqueBenchmarkQueryResult(
                "2hop__123",
                "Who is the author?",
                "Tolkien",
                List.of(),
                2,
                "Tolkien wrote it.",
                List.of(),
                List.of(new RetrievedChunk("chunk-1", "Lord of the Rings", "Full chunk")),
                20L,
                60L,
                80L,
                usage,
                "vector_store_test",
                List.of("Lord of the Rings"),
                true
        );

        com.dippa.ragbenchmark.application.dataset.MusiqueBatchQueryResult batchResult =
                new com.dippa.ragbenchmark.application.dataset.MusiqueBatchQueryResult(
                        1,
                        85L,
                        80.0,
                        1,
                        usage,
                        List.of(itemResult)
                );

        when(benchmarkQueryService.evaluateBatch(any())).thenReturn(batchResult);

        mockMvc.perform(post("/api/benchmark/musique/batch-query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionIds\": [\"2hop__123\"], \"topK\": 5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuestions").value(1))
                .andExpect(jsonPath("$.supportingEvidenceFoundCount").value(1))
                .andExpect(jsonPath("$.totalExecutionTimeMs").value(85))
                .andExpect(jsonPath("$.averageLatencyMs").value(80.0))
                .andExpect(jsonPath("$.totalTokenUsage.promptTokens").value(200))
                .andExpect(jsonPath("$.results[0].questionId").value("2hop__123"));
    }
}
