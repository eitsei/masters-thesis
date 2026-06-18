package com.example.rag.controller;

import com.example.rag.service.IngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private static final Logger logger = LoggerFactory.getLogger(RagController.class);

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final IngestionService ingestionService;

    public RagController(
        ChatClient.Builder chatClientBuilder, 
        VectorStore vectorStore, 
        IngestionService ingestionService) 
        {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.ingestionService = ingestionService;
    }

    @GetMapping("/ping")
    public String ping() {
        logger.info("Ping endpoint called");
        return "pong";
    }

    @GetMapping("/ask")
    public String ask(@RequestParam("query") String query) {
        logger.info("Received query: '{}'", query);
        
        long startTime = System.currentTimeMillis();
        
        // 1. Retrieve similar documents from Qdrant
        logger.info("Searching similarity in Qdrant for query...");
        List<Document> similarDocuments = 
        vectorStore
        .similaritySearch(
            SearchRequest
            .query(query)
            .withTopK(3)
        );
        
        logger.info("Retrieved {} similar documents from Qdrant", similarDocuments.size());
        for (int i = 0; i < similarDocuments.size(); i++) {
            Document doc = similarDocuments.get(i);
            String title = (String) doc.getMetadata().getOrDefault("title", "Unknown Title");
            String source = (String) doc.getMetadata().getOrDefault("source", "Unknown Source");
            logger.info("  Doc {} - Title: '{}' - Source: '{}'", i + 1, title, source);
        }
        
        // 2. Extract content from documents
        String context = similarDocuments.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n\n"));
        
        // 3. Augment prompt and generate response
        String systemPrompt = """
                You are a helpful banking assistant. Answer the user's question based ONLY on the following context.
                If you don't know the answer, say you don't know. Do not make up answers.
                \n\n
                """ + context;
        
        logger.info("Sending prompt to Ollama model...");
        String response = chatClient.prompt()
                .system(systemPrompt)
                .user(query)
                .call()
                .content();
                
        long duration = System.currentTimeMillis() - startTime;
        logger.info("Received response from Ollama in {} ms. Response length: {} chars", duration, response.length());
        
        return response;
    }
    
    @PostMapping("/ingest")
    public String ingest(@RequestParam("filePath") String filePath) {
        logger.info("Received request to ingest file: '{}'", filePath);
        try {
            ingestionService.ingestDocument(new FileSystemResource(filePath));
            logger.info("Ingestion completed successfully for: '{}'", filePath);
            return "Successfully ingested: " + filePath;
        } catch (Exception e) {
            logger.error("Failed to ingest file: '{}'", filePath, e);
            return "Failed to ingest: " + e.getMessage();
        }
    }
}
