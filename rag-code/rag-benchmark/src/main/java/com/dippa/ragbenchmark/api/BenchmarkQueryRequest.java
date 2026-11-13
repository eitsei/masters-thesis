package com.dippa.ragbenchmark.api;

import java.util.Set;

/**
 * Optional request body for single benchmark question evaluation.
 *
 * @param modules requested RAG optimization modules (e.g. "M1", "M2")
 * @param tableName optional vector store table override
 * @param topK optional retrieval top-k override
 */
public record BenchmarkQueryRequest(
        Set<String> modules,
        String tableName,
        Integer topK,
        String parentTableName
) {
    public BenchmarkQueryRequest {
        if (modules == null) {
            modules = Set.of();
        }
    }

    public BenchmarkQueryRequest(Set<String> modules, String tableName, Integer topK) {
        this(modules, tableName, topK, null);
    }
}
