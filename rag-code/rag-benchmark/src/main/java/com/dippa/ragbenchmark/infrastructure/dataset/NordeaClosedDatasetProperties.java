package com.dippa.ragbenchmark.infrastructure.dataset;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for the Nordea closed-corpus benchmark dataset,
 * bound from {@code rag.dataset.nordea-closed.*}.
 */
@ConfigurationProperties(prefix = "rag.dataset.nordea-closed")
public record NordeaClosedDatasetProperties(
        @DefaultValue("data/nordea/nordea_100_final.jsonl") String filePath,
        @DefaultValue("100") int batchSize,
        @DefaultValue("10") int parentBatchSize,
        @DefaultValue("25") int childBatchSize,
        @DefaultValue("512") int baselineChunkSize,
        @DefaultValue("1024") int parentChunkSize,
        @DefaultValue("128") int childChunkSize,
        @DefaultValue("true") boolean includeTitleInChunkText
) {}

