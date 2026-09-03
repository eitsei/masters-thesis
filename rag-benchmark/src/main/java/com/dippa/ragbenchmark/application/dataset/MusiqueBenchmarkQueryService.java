package com.dippa.ragbenchmark.application.dataset;

import com.dippa.ragbenchmark.application.RagQueryResult;
import com.dippa.ragbenchmark.application.RagQueryService;
import com.dippa.ragbenchmark.application.RetrievedChunk;
import com.dippa.ragbenchmark.domain.ModuleId;
import com.dippa.ragbenchmark.domain.dataset.MusiqueQuestion;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Executes baseline RAG queries against MuSiQue benchmark questions and compares generated answers with ground truth.
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
     * Executes the baseline RAG query for the specified MuSiQue question ID.
     */
    public MusiqueBenchmarkQueryResult evaluateQuestion(String questionId) {
        MusiqueQuestion question = datasetService.getQuestionById(questionId)
                .orElseThrow(() -> new NoSuchElementException("MuSiQue question not found with id: " + questionId));

        RagQueryResult queryResult = ragQueryService.query(question.question(), Set.of());

        List<String> expectedTitles = question.getSupportingTitles();
        boolean supportingFound = false;

        if (queryResult.retrievedChunks() != null && expectedTitles != null) {
            for (RetrievedChunk chunk : queryResult.retrievedChunks()) {
                String snippet = chunk.snippet();
                if (snippet != null) {
                    for (String title : expectedTitles) {
                        if (snippet.contains(title)) {
                            supportingFound = true;
                            break;
                        }
                    }
                }
                if (supportingFound) break;
            }
        }

        List<String> modulesApplied = queryResult.modulesApplied() != null
                ? queryResult.modulesApplied().stream().map(ModuleId::name).toList()
                : List.of();

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
                expectedTitles != null ? expectedTitles : List.of(),
                supportingFound
        );
    }
}
