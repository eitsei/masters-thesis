package com.example.rag.pipeline.steps;

import com.example.rag.pipeline.PipelineConfig;
import com.example.rag.pipeline.RagContext;
import org.springframework.stereotype.Component;

@Component
public class DocumentRerankStep implements RagPipelineStep {

    @Override
    public void execute(RagContext context) {
        // Baseline: No-op. Just pass retrieved documents to reranked documents.
        context.setRerankedDocuments(context.getRetrievedDocuments());
        context.setRerankingLatencyMs(0L);
    }

    @Override
    public boolean shouldExecute(PipelineConfig config) {
        return true;
    }

    @Override
    public int getOrder() {
        return 3;
    }
}
