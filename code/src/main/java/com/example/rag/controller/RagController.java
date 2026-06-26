package com.example.rag.controller;

import com.example.rag.ingest.IngestionStrategy;
import com.example.rag.pipeline.PipelineConfig;
import com.example.rag.pipeline.RagPipeline;
import com.example.rag.pipeline.RagRequest;
import com.example.rag.pipeline.RagResponse;
import com.example.rag.service.IngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private static final Logger logger = LoggerFactory.getLogger(RagController.class);

    private final RagPipeline ragPipeline;
    private final IngestionService ingestionService;

    public RagController(RagPipeline ragPipeline, IngestionService ingestionService) {
        this.ragPipeline = ragPipeline;
        this.ingestionService = ingestionService;
    }

    @GetMapping("/ping")
    public String ping() {
        logger.info("Ping endpoint called");
        return "pong";
    }

    @GetMapping("/ask")
    public String ask(@RequestParam("query") String query) {
        logger.info("Received GET query: '{}'", query);
        PipelineConfig defaultConfig = new PipelineConfig(false, false, false, false);
        RagRequest request = new RagRequest(query, defaultConfig);
        RagResponse response = ragPipeline.execute(request);
        return response.answer();
    }

    @PostMapping("/ask")
    public RagResponse ask(@RequestBody RagRequest request) {
        logger.info("Received POST RAG request for query: '{}'", request.query());
        return ragPipeline.execute(request);
    }

    @PostMapping("/ingest")
    public String ingest(
            @RequestParam("filePath") String filePath,
            @RequestParam(value = "strategy", defaultValue = "SIMPLE") IngestionStrategy strategy) {
        logger.info("Received request to ingest file: '{}' with strategy '{}'", filePath, strategy);
        try {
            ingestionService.ingestDocument(new FileSystemResource(filePath), strategy);
            logger.info("Ingestion completed successfully for: '{}' using strategy {}", filePath, strategy);
            return "Successfully ingested: " + filePath + " with strategy " + strategy;
        } catch (Exception e) {
            logger.error("Failed to ingest file: '{}'", filePath, e);
            return "Failed to ingest: " + e.getMessage();
        }
    }
}
