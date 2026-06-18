package com.example.rag.controller;

import com.example.rag.service.IngestionService;
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
        return "pong";
    }


    @GetMapping("/ask")
    public String ask(@RequestParam("query") String query) {
        // 1. Retrieve similar documents from Qdrant
        List<Document> similarDocuments = 
        vectorStore
        .similaritySearch(
            SearchRequest
            .query(query)
            .withTopK(3)
        );
        
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
        
        return chatClient.prompt()
                .system(systemPrompt)
                .user(query)
                .call()
                .content();
    }
    
    @PostMapping("/ingest")
    public String ingest(@RequestParam("filePath") String filePath) {
        try {
            ingestionService.ingestDocument(new FileSystemResource(filePath));
            return "Successfully ingested: " + filePath;
        } catch (Exception e) {
            return "Failed to ingest: " + e.getMessage();
        }
    }
}
