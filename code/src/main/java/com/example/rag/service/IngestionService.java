package com.example.rag.service;

import com.example.rag.ingest.DocumentIngester;
import com.example.rag.ingest.IngestionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IngestionService {

    private static final Logger logger = LoggerFactory.getLogger(IngestionService.class);

    private final List<DocumentIngester> ingesters;

    public IngestionService(List<DocumentIngester> ingesters) {
        this.ingesters = ingesters;
    }

    public void ingestDocument(Resource resource, IngestionStrategy strategy) {
        logger.info("Routing ingestion of document: '{}' with strategy: {}", resource.getFilename(), strategy);
        
        DocumentIngester ingester = ingesters.stream()
                .filter(i -> i.supports(strategy))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported ingestion strategy: " + strategy));
                
        ingester.ingest(resource);
    }
}
