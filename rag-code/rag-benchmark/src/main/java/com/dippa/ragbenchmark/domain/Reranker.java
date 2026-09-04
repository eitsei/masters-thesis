package com.dippa.ragbenchmark.domain;

import java.util.List;
import org.springframework.ai.document.Document;

/**
 * Reorders a list of retrieved candidates for a given query.
 *
 * <p>Spring AI has no built-in opinion on re-ranking, so this is a project-owned port.
 * The M0 baseline is served by a no-op passthrough implementation; a real cross-encoder
 * or LLM-based re-ranker (M2) can later be dropped in behind this same interface.</p>
 */
public interface Reranker {

    /**
     * @param query      the original user query
     * @param candidates retrieved candidates, in their original retrieval order
     * @return candidates reordered (or left untouched) by relevance to {@code query}
     */
    List<Document> rerank(String query, List<Document> candidates);
}
