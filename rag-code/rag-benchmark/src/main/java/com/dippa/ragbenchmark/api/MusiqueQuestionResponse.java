package com.dippa.ragbenchmark.api;

import com.dippa.ragbenchmark.domain.dataset.MusiqueQuestion;
import java.util.List;

public record MusiqueQuestionResponse(
        String id,
        String question,
        String answer,
        List<String> answerAliases,
        int hopCount,
        boolean answerable,
        int paragraphCount,
        List<String> supportingTitles
) {
    public static MusiqueQuestionResponse fromDomain(MusiqueQuestion q) {
        return new MusiqueQuestionResponse(
                q.id(),
                q.question(),
                q.answer(),
                q.answerAliases() != null ? q.answerAliases() : List.of(),
                q.hopCount(),
                q.answerable(),
                q.paragraphs() != null ? q.paragraphs().size() : 0,
                q.getSupportingTitles()
        );
    }
}
