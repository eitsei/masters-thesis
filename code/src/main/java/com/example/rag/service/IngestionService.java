package com.example.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IngestionService {

    private static final Logger logger = LoggerFactory.getLogger(IngestionService.class);

    private final VectorStore vectorStore;

    public IngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void ingestDocument(Resource resource) {
        logger.info("Starting ingestion of document: '{}'", resource.getFilename());
        
        // 1. Read the document (assuming text for simplicity)
        logger.info("Reading document content...");
        TextReader textReader = new TextReader(resource);
        textReader.getCustomMetadata().put("filename", resource.getFilename());
        List<Document> documents = textReader.get();
        logger.info("Successfully read document. Initial document count: {}", documents.size());

        // 2. Split the document into chunks
        logger.info("Splitting document into token-based chunks...");
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> splitDocuments = splitter.apply(documents);
        logger.info("Document split completed. Total chunks generated: {}", splitDocuments.size());

        // 3. Store the chunks in Qdrant Vector Store
        logger.info("Sending chunks to Qdrant vector store...");
        vectorStore.accept(splitDocuments);
        
        logger.info("Ingested {} chunks from '{}' successfully into Qdrant", splitDocuments.size(), resource.getFilename());
    }
}
