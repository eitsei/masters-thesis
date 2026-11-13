package com.dippa.ragbenchmark.api;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Request body for {@code POST /api/query}. {@code modules} is optional; a null/absent/empty
 * value means "no optimization modules requested" (the M0 baseline pipeline).
 */
public record QueryRequest(
        @NotBlank(message = "query must not be blank") String query,
        List<String> modules,
        String tableName,
        String parentTableName) {

    public QueryRequest(String query, List<String> modules) {
        this(query, modules, null, null);
    }

    public QueryRequest(String query, List<String> modules, String tableName) {
        this(query, modules, tableName, null);
    }
}
