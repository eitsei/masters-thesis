package com.dippa.ragbenchmark.api;

import com.dippa.ragbenchmark.application.DocumentIngestionService;
import com.dippa.ragbenchmark.application.IngestionResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for ingesting raw text into the vector store.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentIngestionService documentIngestionService;

    public DocumentController(DocumentIngestionService documentIngestionService) {
        this.documentIngestionService = documentIngestionService;
    }

    @PostMapping
    public ResponseEntity<IngestionResult> ingest(@Valid @RequestBody IngestDocumentRequest request) {
        IngestionResult result = documentIngestionService.ingest(request.text(), request.metadata());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
