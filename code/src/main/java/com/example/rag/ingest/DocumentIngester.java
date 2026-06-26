package com.example.rag.ingest;

import org.springframework.core.io.Resource;

public interface DocumentIngester {
    void ingest(Resource resource);
    boolean supports(IngestionStrategy strategy);
}
