package com.dippa.ragbenchmark.api;

import com.dippa.ragbenchmark.application.RetrievedChunk;
import com.dippa.ragbenchmark.application.dataset.MusiqueBenchmarkQueryResult;
import java.util.List;

public record MusiqueBenchmarkQueryResponse(
        String questionId,
        String question,
        String groundTruthAnswer,
        List<String> groundTruthAliases,
        int hopCount,
        String generatedAnswer,
        List<String> modulesApplied,
        List<RetrievedChunk> retrievedChunks,
        TimingsDto timings,
        List<String> expectedSupportingTitles,
        boolean supportingEvidenceFound
) {
    public static MusiqueBenchmarkQueryResponse fromResult(MusiqueBenchmarkQueryResult result) {
        return new MusiqueBenchmarkQueryResponse(
                result.questionId(),
                result.question(),
                result.groundTruthAnswer(),
                result.groundTruthAliases(),
                result.hopCount(),
                result.generatedAnswer(),
                result.modulesApplied(),
                result.retrievedChunks(),
                new TimingsDto(result.retrievalMs(), result.generationMs()),
                result.expectedSupportingTitles(),
                result.supportingEvidenceFound()
        );
    }
}
