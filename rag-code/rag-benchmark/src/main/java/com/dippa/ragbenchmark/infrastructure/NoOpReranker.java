package com.dippa.ragbenchmark.infrastructure;

import com.dippa.ragbenchmark.domain.Reranker;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

/**
 * Passthrough {@link Reranker} used by the M0 baseline pipeline.
 *
 * <p>M0 architecturally still has a rerank stage — the {@link
 * com.dippa.ragbenchmark.application.RagQueryService} always calls the {@code Reranker} bean —
 * it's just that for the baseline this stage is a no-op. Candidates are returned unchanged and
 * in their original order, so that a real re-ranker (M2) is a true drop-in replacement later.
 */
@Component
public class NoOpReranker implements Reranker {

    @Override
    public List<Document> rerank(String query, List<Document> candidates) {
        return candidates;
    }
}
