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
 * @param modules requested optimization modules (e.g. "M1", "M2", "M3")
 * @param tableName optional vector store table override
 * @param topK optional retrieval top-k override
 * @param parentTableName optional parent vector store table override (for M1)
 * @param clearOwnedCachesBeforeRun if true, deletes all owned cached content before starting batch
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
        String parentTableName,
        Boolean clearOwnedCachesBeforeRun
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
            Integer topK,
            String parentTableName) {
        this(questionIds, limit, offset, hops, sample, modules, tableName, topK, parentTableName, false);
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
        this(questionIds, limit, offset, hops, sample, modules, tableName, topK, null, false);
    }
}
