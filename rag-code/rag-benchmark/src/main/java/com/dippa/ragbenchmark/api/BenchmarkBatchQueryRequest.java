package com.dippa.ragbenchmark.api;

import java.util.List;
import java.util.Set;

/**
 * Request body for batch benchmark question evaluation.
 *
 * @param questionIds specific question IDs to run, or null/empty to use filters/pagination
 * @param limit maximum number of questions to evaluate
 * @param offset offset within the question set
 * @param hops optional hop count filter (e.g. 2, 3, 4)
 * @param sample if true, selects from the canonical benchmark sample
 * @param modules requested optimization modules (e.g. "M1", "M2")
 * @param tableName optional vector store table override
 * @param topK optional retrieval top-k override
 */
public record BenchmarkBatchQueryRequest(
        List<String> questionIds,
        Integer limit,
        Integer offset,
        Integer hops,
        Boolean sample,
        Set<String> modules,
        String tableName,
        Integer topK,
        String parentTableName
) {
    public BenchmarkBatchQueryRequest {
        if (modules == null) {
            modules = Set.of();
        }
    }

    public BenchmarkBatchQueryRequest(
            List<String> questionIds,
            Integer limit,
            Integer offset,
            Integer hops,
            Boolean sample,
            Set<String> modules,
            String tableName,
            Integer topK) {
        this(questionIds, limit, offset, hops, sample, modules, tableName, topK, null);
    }
}
