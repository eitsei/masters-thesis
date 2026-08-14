package com.dippa.ragbenchmark.api;

import com.dippa.ragbenchmark.application.RetrievedChunk;
import java.util.List;

/**
 * Response body for {@code POST /api/query}.
 */
public record QueryResponse(
        String answer,
        List<String> modulesApplied,
        List<RetrievedChunk> retrievedChunks,
        TimingsDto timingsMs) {
}
