package com.dippa.ragbenchmark.domain.dataset;

/**
 * Represents a single context paragraph within a MuSiQue question entry.
 *
 * @param idx index of the paragraph in the question's paragraph array (0-19)
 * @param title Wikipedia page title the paragraph was extracted from
 * @param paragraphText plain text body of the paragraph
 * @param isSupporting true if this paragraph contains evidence needed to answer the question
 */
public record MusiqueParagraph(
        int idx,
        String title,
        String paragraphText,
        boolean isSupporting
) {}
