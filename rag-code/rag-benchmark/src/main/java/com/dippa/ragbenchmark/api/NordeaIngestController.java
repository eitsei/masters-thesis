package com.dippa.ragbenchmark.api;

import com.dippa.ragbenchmark.ingest.nordea.NordeaIngestionService;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for triggering Nordea corpus ingestion (baseline or S2B mode).
 */
@RestController
@RequestMapping("/api/ingest/nordea")
public class NordeaIngestController {

    private final NordeaIngestionService ingestionService;

    public NordeaIngestController(NordeaIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    public record NordeaIngestRequest(String mode) {
        public String resolvedMode() {
            return mode != null && !mode.isBlank() ? mode : "s2b";
        }
    }

    @PostMapping
    public ResponseEntity<NordeaIngestionService.NordeaIngestionSummary> ingest(
            @RequestBody(required = false) NordeaIngestRequest request,
            @RequestParam(required = false) String mode) throws IOException {
        String effectiveMode = (request != null && request.mode() != null && !request.mode().isBlank())
                ? request.mode()
                : (mode != null && !mode.isBlank() ? mode : "s2b");

        NordeaIngestionService.NordeaIngestionSummary summary = ingestionService.ingest(effectiveMode);
        return ResponseEntity.ok(summary);
    }
}
