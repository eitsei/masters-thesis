package com.example.rag.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SimpleDocumentIngester implements DocumentIngester {

    private static final Logger logger = LoggerFactory.getLogger(SimpleDocumentIngester.class);

    private final VectorStore vectorStore;

    public SimpleDocumentIngester(@Qualifier("simpleVectorStore") VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void ingest(Resource resource) {
        logger.info("Executing SimpleDocumentIngester for: '{}'", resource.getFilename());
        
        TextReader textReader = new TextReader(resource);
        textReader.getCustomMetadata().put("filename", resource.getFilename());
        List<Document> documents = textReader.get();
        
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> splitDocuments = splitter.apply(documents);
        
        vectorStore.accept(splitDocuments);
        logger.info("Ingested {} chunks using SIMPLE strategy", splitDocuments.size());
    }

    @Override
    public boolean supports(IngestionStrategy strategy) {
        return strategy == IngestionStrategy.SIMPLE;
    }
}
