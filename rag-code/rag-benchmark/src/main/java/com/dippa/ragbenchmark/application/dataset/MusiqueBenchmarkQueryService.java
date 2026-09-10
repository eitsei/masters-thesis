package com.dippa.ragbenchmark.application.dataset;

import com.dippa.ragbenchmark.api.BenchmarkBatchQueryRequest;
import com.dippa.ragbenchmark.api.BenchmarkQueryRequest;
import com.dippa.ragbenchmark.api.TokenUsageDto;
import com.dippa.ragbenchmark.application.RagQueryResult;
import com.dippa.ragbenchmark.application.RagQueryService;
import com.dippa.ragbenchmark.application.RetrievedChunk;
import com.dippa.ragbenchmark.domain.ModuleId;
import com.dippa.ragbenchmark.domain.dataset.MusiqueQuestion;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Executes baseline and module-configured RAG queries against MuSiQue benchmark questions
 * and compares generated answers with ground truth.
 */
@Service
public class MusiqueBenchmarkQueryService {

    private final MusiqueDatasetService datasetService;
    private final RagQueryService ragQueryService;

    public MusiqueBenchmarkQueryService(MusiqueDatasetService datasetService, RagQueryService ragQueryService) {
        this.datasetService = datasetService;
        this.ragQueryService = ragQueryService;
    }

    /**
     * Executes baseline RAG query for the specified MuSiQue question ID.
     */
    public MusiqueBenchmarkQueryResult evaluateQuestion(String questionId) {
        return evaluateQuestion(questionId, null);
    }

    /**
     * Executes benchmark evaluation for a question with optional module, table, and top-K overrides.
     */
    public MusiqueBenchmarkQueryResult evaluateQuestion(String questionId, BenchmarkQueryRequest request) {
        MusiqueQuestion question = datasetService.getQuestionById(questionId)
                .orElseThrow(() -> new NoSuchElementException("MuSiQue question not found with id: " + questionId));

        Set<ModuleId> modules = parseModules(request != null ? request.modules() : null);
        String requestedTable = request != null ? request.tableName() : null;
        String parentTable = request != null ? request.parentTableName() : null;
        String tableNameUsed = (requestedTable != null && !requestedTable.isBlank())
                ? requestedTable
                : (modules.contains(ModuleId.M1) ? "vector_store_musique_small -> vector_store_musique_large" : ragQueryService.getDefaultTableName());
        Integer topK = request != null ? request.topK() : null;

        RagQueryResult queryResult = (parentTable != null)
                ? ragQueryService.query(question.question(), modules, requestedTable, parentTable, topK)
                : ragQueryService.query(question.question(), modules, requestedTable, topK);

        List<String> expectedTitles = question.getSupportingTitles();
        boolean supportingFound = false;

        if (queryResult.retrievedChunks() != null && expectedTitles != null) {
            for (RetrievedChunk chunk : queryResult.retrievedChunks()) {
                String snippet = chunk.snippet();
                String text = chunk.text();
                for (String title : expectedTitles) {
                    if ((snippet != null && snippet.contains(title)) || (text != null && text.contains(title))) {
                        supportingFound = true;
                        break;
                    }
                }
                if (supportingFound) break;
            }
        }

        List<String> modulesApplied = queryResult.modulesApplied() != null
                ? queryResult.modulesApplied().stream().map(ModuleId::name).toList()
                : List.of();

        TokenUsageDto tokenUsage = queryResult.tokenUsage() != null ? queryResult.tokenUsage() : TokenUsageDto.ZERO;
        long totalLatencyMs = queryResult.totalLatencyMs();

        return new MusiqueBenchmarkQueryResult(
                question.id(),
                question.question(),
                question.answer(),
                question.answerAliases() != null ? question.answerAliases() : List.of(),
                question.hopCount(),
                queryResult.answer(),
                modulesApplied,
                queryResult.retrievedChunks(),
                queryResult.retrievalMs(),
                queryResult.generationMs(),
                queryResult.expansionMs(),
                totalLatencyMs,
                tokenUsage,
                tableNameUsed,
                expectedTitles != null ? expectedTitles : List.of(),
                supportingFound
        );
    }

    /**
     * Runs batch evaluation over a list of question IDs or a filtered slice of the dataset.
     */
    public MusiqueBatchQueryResult evaluateBatch(BenchmarkBatchQueryRequest request) {
        long startTime = System.currentTimeMillis();

        List<MusiqueQuestion> questionsToEvaluate;
        if (request != null && request.questionIds() != null && !request.questionIds().isEmpty()) {
            questionsToEvaluate = request.questionIds().stream()
                    .map(datasetService::getQuestionById)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
        } else {
            int offset = request != null && request.offset() != null ? request.offset() : 0;
            int limit = request != null && request.limit() != null ? request.limit() : 20;
            Integer hops = request != null ? request.hops() : null;
            boolean sample = request != null && request.sample() != null && request.sample();
            questionsToEvaluate = datasetService.getQuestions(offset, limit, hops, sample);
        }

        BenchmarkQueryRequest singleQueryRequest = request != null
                ? new BenchmarkQueryRequest(request.modules(), request.tableName(), request.topK(), request.parentTableName())
                : null;

        List<MusiqueBenchmarkQueryResult> results = new ArrayList<>(questionsToEvaluate.size());
        long totalPromptTokens = 0;
        long totalCompletionTokens = 0;
        long totalTokens = 0;
        long totalCachedTokens = 0;
        int supportingEvidenceFoundCount = 0;

        for (MusiqueQuestion question : questionsToEvaluate) {
            MusiqueBenchmarkQueryResult result = evaluateQuestion(question.id(), singleQueryRequest);
            results.add(result);

            if (result.supportingEvidenceFound()) {
                supportingEvidenceFoundCount++;
            }
            if (result.tokenUsage() != null) {
                totalPromptTokens += result.tokenUsage().promptTokens();
                totalCompletionTokens += result.tokenUsage().completionTokens();
                totalTokens += result.tokenUsage().totalTokens();
                totalCachedTokens += result.tokenUsage().cachedTokens();
            }
        }

        long totalExecutionTimeMs = System.currentTimeMillis() - startTime;
        double averageLatencyMs = results.isEmpty() ? 0.0
                : (double) results.stream().mapToLong(MusiqueBenchmarkQueryResult::totalLatencyMs).sum() / results.size();
        TokenUsageDto aggregateUsage = new TokenUsageDto(totalPromptTokens, totalCompletionTokens, totalTokens, totalCachedTokens);

        return new MusiqueBatchQueryResult(
                results.size(),
                totalExecutionTimeMs,
                averageLatencyMs,
                supportingEvidenceFoundCount,
                aggregateUsage,
                results
        );
    }

    private Set<ModuleId> parseModules(Set<String> rawModules) {
        if (rawModules == null || rawModules.isEmpty()) {
            return Set.of();
        }
        return rawModules.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(ModuleId::parse)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ModuleId.class)));
    }
}
