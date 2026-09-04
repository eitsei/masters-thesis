package com.dippa.ragbenchmark.domain.dataset;

import java.util.List;

/**
 * Pure domain model representing a MuSiQue QA benchmark item.
 *
 * @param id unique identifier, e.g. "2hop__460946_294723" or "3hop1__1234_5678"
 * @param question the natural language question
 * @param answer canonical golden answer
 * @param answerAliases alternate valid ground truth answers
 * @param hopCount number of reasoning hops (2, 3, or 4)
 * @param answerable whether the question is answerable given the context
 * @param paragraphs list of 20 paired context paragraphs
 * @param questionDecomposition reasoning chain breakdown
 */
public record MusiqueQuestion(
        String id,
        String question,
        String answer,
        List<String> answerAliases,
        int hopCount,
        boolean answerable,
        List<MusiqueParagraph> paragraphs,
        List<MusiqueDecompositionStep> questionDecomposition
) {
    public List<MusiqueParagraph> getSupportingParagraphs() {
        if (paragraphs == null) {
            return List.of();
        }
        return paragraphs.stream()
                .filter(MusiqueParagraph::isSupporting)
                .toList();
    }

    public List<String> getSupportingTitles() {
        return getSupportingParagraphs().stream()
                .map(MusiqueParagraph::title)
                .distinct()
                .toList();
    }
}
