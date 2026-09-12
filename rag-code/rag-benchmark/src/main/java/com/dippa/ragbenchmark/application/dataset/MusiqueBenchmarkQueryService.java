package com.dippa.ragbenchmark.application.dataset;

import com.dippa.ragbenchmark.api.BenchmarkBatchQueryRequest;
import com.dippa.ragbenchmark.api.BenchmarkQueryRequest;
import com.dippa.ragbenchmark.api.TokenUsageDto;
import com.dippa.ragbenchmark.application.RagQueryResult;
import com.dippa.ragbenchmark.application.RagQueryService;
import com.dippa.ragbenchmark.application.RetrievedChunk;
import com.dippa.ragbenchmark.domain.ModuleId;
import com.dippa.ragbenchmark.domain.dataset.MusiqueQuestion;
import com.dippa.ragbenchmark.domain.PromptCache;
import com.dippa.ragbenchmark.infrastructure.NoOpPromptCache;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Executes baseline and module-configured RAG queries against MuSiQue benchmark questions
 * and compares generated answers with ground truth.
 */
@Service
public class MusiqueBenchmarkQueryService {

    private final MusiqueDatasetService datasetService;
    private final RagQueryService ragQueryService;
    private final PromptCache promptCache;

    @Autowired
    public MusiqueBenchmarkQueryService(
            MusiqueDatasetService datasetService,
            RagQueryService ragQueryService,
            ObjectProvider<PromptCache> promptCacheProvider) {
        this.datasetService = datasetService;
        this.ragQueryService = ragQueryService;
        this.promptCache = promptCacheProvider != null && promptCacheProvider.getIfAvailable() != null
                ? promptCacheProvider.getIfAvailable()
                : new NoOpPromptCache();
    }

    public MusiqueBenchmarkQueryService(MusiqueDatasetService datasetService, RagQueryService ragQueryService) {
        this(datasetService, ragQueryService, null);
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
        if (topK != null && topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than zero");
        }

        RagQueryResult queryResult = (parentTable != null)
                ? ragQueryService.query(question.question(), modules, requestedTable, parentTable, topK)
                : ragQueryService.query(question.question(), modules, requestedTable, topK);

        List<String> expectedTitles = question.getSupportingTitles();
        int supportingTitleCount = 0;

        if (queryResult.retrievedChunks() != null && expectedTitles != null) {
            for (String title : expectedTitles) {
                boolean found = queryResult.retrievedChunks().stream().anyMatch(chunk -> {
                    String snippet = chunk.snippet();
                    String text = chunk.text();
                    return (snippet != null && snippet.contains(title))
                            || (text != null && text.contains(title));
                });
                if (found) {
                    supportingTitleCount++;
                }
            }
        }
        boolean supportingFound = supportingTitleCount > 0;
        boolean allSupportingFound = expectedTitles != null
                && !expectedTitles.isEmpty()
                && supportingTitleCount == expectedTitles.size();

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
                queryResult.rerankMs(),
                queryResult.generationMs(),
                queryResult.expansionMs(),
                queryResult.cacheMs(),
                queryResult.cacheStatus(),
                queryResult.cacheMetadata(),
                totalLatencyMs,
                tokenUsage,
                tableNameUsed,
                expectedTitles != null ? expectedTitles : List.of(),
                supportingFound,
                supportingTitleCount,
                allSupportingFound
        );
    }

    /**
     * Runs batch evaluation over a list of question IDs or a filtered slice of the dataset.
     */
    public MusiqueBatchQueryResult evaluateBatch(BenchmarkBatchQueryRequest request) {
        long startTime = System.currentTimeMillis();
        if (request != null && Boolean.TRUE.equals(request.clearOwnedCachesBeforeRun()) && promptCache != null) {
            promptCache.clearOwned();
        }
        if (request != null) {
            if (request.limit() != null && request.limit() <= 0) {
                throw new IllegalArgumentException("limit must be greater than zero");
            }
            if (request.offset() != null && request.offset() < 0) {
                throw new IllegalArgumentException("offset must not be negative");
            }
            if (request.hops() != null && (request.hops() < 2 || request.hops() > 4)) {
                throw new IllegalArgumentException("hops must be between 2 and 4");
            }
            if (request.topK() != null && request.topK() <= 0) {
                throw new IllegalArgumentException("topK must be greater than zero");
            }
        }

        List<MusiqueQuestion> questionsToEvaluate;
        if (request != null && request.questionIds() != null && !request.questionIds().isEmpty()) {
            List<String> missingIds = new ArrayList<>();
            questionsToEvaluate = request.questionIds().stream()
                    .map(id -> {
                        Optional<MusiqueQuestion> question = datasetService.getQuestionById(id);
                        if (question.isEmpty()) {
                            missingIds.add(id);
                        }
                        return question;
                    })
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
            if (!missingIds.isEmpty()) {
                throw new NoSuchElementException("MuSiQue question(s) not found: " + missingIds);
            }
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
        long totalCacheWriteTokens = 0;
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
                totalCacheWriteTokens += result.tokenUsage().cacheWriteTokens();
            }
        }

        long totalExecutionTimeMs = System.currentTimeMillis() - startTime;
        double averageLatencyMs = results.isEmpty() ? 0.0
                : (double) results.stream().mapToLong(MusiqueBenchmarkQueryResult::totalLatencyMs).sum() / results.size();
        TokenUsageDto aggregateUsage = new TokenUsageDto(
                totalPromptTokens, totalCompletionTokens, totalTokens, totalCachedTokens, totalCacheWriteTokens);

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
