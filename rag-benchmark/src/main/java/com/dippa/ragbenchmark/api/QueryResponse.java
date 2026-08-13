package com.dippa.ragbenchmark.api;

import java.util.List;

/**
 * Response body for {@code POST /api/query}.
 */
public record QueryResponse(
        String answer,
        List<String> modulesApplied,
        List<RetrievedChunkDto> retrievedChunks,
        TimingsDto timingsMs) {
}
