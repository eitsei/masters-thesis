package com.dippa.ragbenchmark.api;

import com.dippa.ragbenchmark.ingest.enterprise.EnterpriseIngestionService;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for triggering enterprise corpus ingestion (baseline or S2B mode).
 */
@RestController
@RequestMapping("/api/ingest/enterprise")
public class EnterpriseIngestController {

    private final EnterpriseIngestionService ingestionService;

    public EnterpriseIngestController(EnterpriseIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    public record EnterpriseIngestRequest(
            String mode,
            String action,
            Boolean clearDatabase,
            Boolean clearFiles
    ) {
        public EnterpriseIngestRequest(String mode) {
            this(mode, null, null, null);
        }

        public String resolvedMode() {
            return mode != null && !mode.isBlank() ? mode : "s2b";
        }

        public String resolvedAction() {
            return action != null && !action.isBlank() ? action.toLowerCase(java.util.Locale.ROOT) : "all";
        }

        public boolean shouldClearDatabase() {
            return Boolean.TRUE.equals(clearDatabase);
        }

        public boolean shouldClearFiles() {
            return Boolean.TRUE.equals(clearFiles);
        }
    }

    @PostMapping
    public ResponseEntity<EnterpriseIngestionService.EnterpriseIngestionSummary> ingest(
            @RequestBody(required = false) EnterpriseIngestRequest request,
            @RequestParam(required = false) String mode) throws IOException {
        String effectiveMode = (request != null && request.mode() != null && !request.mode().isBlank())
                ? request.mode()
                : (mode != null && !mode.isBlank() ? mode : "s2b");
        String effectiveAction = (request != null && request.action() != null)
                ? request.action()
                : ("s2b-baseline".equalsIgnoreCase(effectiveMode) || "small-to-big-baseline".equalsIgnoreCase(effectiveMode) ? "s2b-baseline" : "all");
        Boolean effectiveClearDb = request != null ? request.clearDatabase() : null;
        Boolean effectiveClearFiles = request != null ? request.clearFiles() : null;

        EnterpriseIngestRequest effectiveRequest = new EnterpriseIngestRequest(
                effectiveMode,
                effectiveAction,
                effectiveClearDb,
                effectiveClearFiles
        );

        EnterpriseIngestionService.EnterpriseIngestionSummary summary = ingestionService.executePipeline(effectiveRequest);
        return ResponseEntity.ok(summary);
    }
}
