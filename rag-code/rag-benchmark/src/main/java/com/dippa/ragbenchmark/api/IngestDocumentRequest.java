package com.dippa.ragbenchmark.api;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Request body for {@code POST /api/documents}.
 */
public record IngestDocumentRequest(
        @NotBlank(message = "text must not be blank") String text,
        Map<String, Object> metadata) {
}
