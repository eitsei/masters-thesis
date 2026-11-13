package com.dippa.ragbenchmark.api;

import com.dippa.ragbenchmark.application.dataset.EnterpriseClosedDatasetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for triggering Enterprise closed-corpus ingestion into pgvector.
 *
 * <p>Supports modes:
 * <ul>
 *   <li>{@code both} (default): ingests both baseline (vector_store_enterprise_closed) and S2B (large/small)</li>
 *   <li>{@code original} or {@code baseline}: ingests only baseline table</li>
 *   <li>{@code s2b} or {@code small-to-big}: ingests only S2B large/small tables</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/ingest/enterprise-closed")
public class EnterpriseClosedIngestController {

    private final EnterpriseClosedDatasetService closedDatasetService;

    public EnterpriseClosedIngestController(EnterpriseClosedDatasetService closedDatasetService) {
        this.closedDatasetService = closedDatasetService;
    }

    public record EnterpriseClosedIngestRequest(
            String mode,
            Boolean clearDatabase
    ) {
        public String resolvedMode() {
            return mode != null && !mode.isBlank() ? mode : "both";
        }

        public boolean shouldClearDatabase() {
            return Boolean.TRUE.equals(clearDatabase);
        }
    }

    @PostMapping
    public ResponseEntity<EnterpriseClosedDatasetService.EnterpriseClosedIngestionSummary> ingest(
            @RequestBody(required = false) EnterpriseClosedIngestRequest request,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false, defaultValue = "false") boolean clearDatabase) {
        String effectiveMode = (request != null && request.mode() != null && !request.mode().isBlank())
                ? request.mode().toLowerCase(java.util.Locale.ROOT)
                : (mode != null && !mode.isBlank() ? mode.toLowerCase(java.util.Locale.ROOT) : "both");

        java.util.Set<String> validModes = java.util.Set.of("both", "all", "original", "baseline", "s2b", "small-to-big");
        if (!validModes.contains(effectiveMode)) {
            return ResponseEntity.badRequest().build();
        }

        boolean effectiveClearDb = request != null && request.clearDatabase() != null
                ? request.shouldClearDatabase()
                : clearDatabase;

        EnterpriseClosedDatasetService.EnterpriseClosedIngestionSummary summary =
                closedDatasetService.ingest(effectiveMode, effectiveClearDb);
        return ResponseEntity.ok(summary);
    }
}
