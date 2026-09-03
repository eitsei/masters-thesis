package com.dippa.ragbenchmark.api;

import com.dippa.ragbenchmark.application.dataset.MusiqueBenchmarkQueryResult;
import com.dippa.ragbenchmark.application.dataset.MusiqueBenchmarkQueryService;
import com.dippa.ragbenchmark.application.dataset.MusiqueDatasetService;
import com.dippa.ragbenchmark.application.dataset.MusiqueIngestionSummary;
import com.dippa.ragbenchmark.domain.dataset.MusiqueQuestion;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for exploring the MuSiQue benchmark dataset, fetching questions,
 * managing vector store ingestion, and running baseline benchmark evaluations.
 */
@RestController
@RequestMapping("/api/benchmark/musique")
public class MusiqueBenchmarkController {

    private final MusiqueDatasetService datasetService;
    private final MusiqueBenchmarkQueryService benchmarkQueryService;

    public MusiqueBenchmarkController(MusiqueDatasetService datasetService, MusiqueBenchmarkQueryService benchmarkQueryService) {
        this.datasetService = datasetService;
        this.benchmarkQueryService = benchmarkQueryService;
    }

    /**
     * Lists MuSiQue questions with optional pagination and filters.
     */
    @GetMapping("/questions")
    public ResponseEntity<List<MusiqueQuestionResponse>> getQuestions(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Integer hops,
            @RequestParam(defaultValue = "false") boolean sample) {
        List<MusiqueQuestion> questions = datasetService.getQuestions(offset, limit, hops, sample);
        List<MusiqueQuestionResponse> response = questions.stream()
                .map(MusiqueQuestionResponse::fromDomain)
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * Returns full details for a single question by its unique identifier.
     */
    @GetMapping("/questions/{id}")
    public ResponseEntity<MusiqueQuestionDetailResponse> getQuestionById(@PathVariable String id) {
        return datasetService.getQuestionById(id)
                .map(MusiqueQuestionDetailResponse::fromDomain)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Returns the canonical 100-question benchmark evaluation sample (thesis §4.2.1).
     */
    @GetMapping("/sample")
    public ResponseEntity<List<MusiqueQuestionResponse>> getCanonicalSample() {
        List<MusiqueQuestionResponse> sample = datasetService.getCanonicalBenchmarkSample().stream()
                .map(MusiqueQuestionResponse::fromDomain)
                .toList();
        return ResponseEntity.ok(sample);
    }

    /**
     * Returns dataset statistics including hop counts and corpus sizes.
     */
    @GetMapping("/stats")
    public ResponseEntity<MusiqueDatasetStatsResponse> getDatasetStats() {
        return ResponseEntity.ok(MusiqueDatasetStatsResponse.fromMap(datasetService.getDatasetStats()));
    }

    /**
     * Ingests paragraphs from MuSiQue questions into the pgvector store.
     */
    @PostMapping("/ingest")
    public ResponseEntity<MusiqueIngestResponse> ingest(@RequestBody(required = false) MusiqueIngestRequest request) {
        boolean sampleOnly = request == null || request.isSampleOnly();
        int sampleSize = request != null && request.sampleSize() != null ? request.sampleSize() : 0;

        MusiqueIngestionSummary summary = datasetService.ingest(sampleOnly, sampleSize);
        return ResponseEntity.ok(MusiqueIngestResponse.fromSummary(summary));
    }

    /**
     * Runs a baseline RAG query for the specified MuSiQue question and compares the output with ground truth.
     */
    @PostMapping("/questions/{id}/query")
    public ResponseEntity<MusiqueBenchmarkQueryResponse> queryQuestion(@PathVariable String id) {
        MusiqueBenchmarkQueryResult result = benchmarkQueryService.evaluateQuestion(id);
        return ResponseEntity.ok(MusiqueBenchmarkQueryResponse.fromResult(result));
    }
}
