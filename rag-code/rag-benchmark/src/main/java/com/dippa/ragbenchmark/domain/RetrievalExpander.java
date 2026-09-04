package com.dippa.ragbenchmark.domain;

import java.util.List;
import org.springframework.ai.document.Document;

/**
 * Expands a list of retrieved (small) child chunks into their larger parent windows
 * ("small-to-big" retrieval).
 *
 * <p>Spring AI has no built-in opinion on child/parent chunk expansion, so this is a
 * project-owned port. Not needed by the M0 baseline — no bean is registered for it in v1.
 * A real implementation (M1) can satisfy this interface later.</p>
 */
public interface RetrievalExpander {

    /**
     * @param childChunks the small chunks returned by the initial retrieval step
     * @return the corresponding parent-window documents
     */
    List<Document> expand(List<Document> childChunks);
}
