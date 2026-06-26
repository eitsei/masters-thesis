package com.example.rag.pipeline.steps;

import com.example.rag.pipeline.PipelineConfig;
import com.example.rag.pipeline.RagContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentRetrievalStep implements RagPipelineStep {

    private static final Logger logger = LoggerFactory.getLogger(DocumentRetrievalStep.class);

    private final VectorStore simpleVectorStore;
    private final VectorStore smallToBigVectorStore;

    public DocumentRetrievalStep(
            @Qualifier("simpleVectorStore") VectorStore simpleVectorStore,
            @Qualifier("smallToBigVectorStore") VectorStore smallToBigVectorStore) {
        this.simpleVectorStore = simpleVectorStore;
        this.smallToBigVectorStore = smallToBigVectorStore;
    }

    @Override
    public void execute(RagContext context) {
        logger.info("Executing Retrieval Step...");
        long start = System.currentTimeMillis();
        
        VectorStore selectedStore = context.getRequest().config().useSmallToBig() 
                ? smallToBigVectorStore 
                : simpleVectorStore;
                
        List<Document> docs = selectedStore.similaritySearch(
                SearchRequest.query(context.getProcessedQuery()).withTopK(10)
        );
        
        context.setRetrievedDocuments(docs);
        context.setRetrievalLatencyMs(System.currentTimeMillis() - start);
        logger.info("Retrieved {} documents", docs.size());
    }

    @Override
    public boolean shouldExecute(PipelineConfig config) {
        return true; // Retrieval is always required in basic RAG
    }

    @Override
    public int getOrder() {
        return 2;
    }
}
