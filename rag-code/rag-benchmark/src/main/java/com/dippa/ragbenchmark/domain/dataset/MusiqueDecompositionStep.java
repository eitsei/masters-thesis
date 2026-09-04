package com.dippa.ragbenchmark.domain.dataset;

/**
 * Represents a single decomposition step within a multi-hop MuSiQue question.
 *
 * @param id sub-question identifier
 * @param question sub-question text
 * @param answer intermediate answer for this reasoning hop
 * @param paragraphSupportIdx index of supporting paragraph in the paragraph array, if applicable
 */
public record MusiqueDecompositionStep(
        long id,
        String question,
        String answer,
        Integer paragraphSupportIdx
) {}
