package com.dippa.ragbenchmark.api;

import com.dippa.ragbenchmark.domain.dataset.MusiqueQuestion;
import java.util.List;

public record MusiqueQuestionDetailResponse(
        String id,
        String question,
        String answer,
        List<String> answerAliases,
        int hopCount,
        boolean answerable,
        List<ParagraphDto> paragraphs,
        List<DecompositionStepDto> questionDecomposition
) {
    public record ParagraphDto(int idx, String title, String paragraphText, boolean isSupporting) {}
    public record DecompositionStepDto(long id, String question, String answer, Integer paragraphSupportIdx) {}

    public static MusiqueQuestionDetailResponse fromDomain(MusiqueQuestion q) {
        List<ParagraphDto> paras = q.paragraphs() != null
                ? q.paragraphs().stream()
                        .map(p -> new ParagraphDto(p.idx(), p.title(), p.paragraphText(), p.isSupporting()))
                        .toList()
                : List.of();

        List<DecompositionStepDto> decomp = q.questionDecomposition() != null
                ? q.questionDecomposition().stream()
                        .map(d -> new DecompositionStepDto(d.id(), d.question(), d.answer(), d.paragraphSupportIdx()))
                        .toList()
                : List.of();

        return new MusiqueQuestionDetailResponse(
                q.id(),
                q.question(),
                q.answer(),
                q.answerAliases() != null ? q.answerAliases() : List.of(),
                q.hopCount(),
                q.answerable(),
                paras,
                decomp
        );
    }
}
