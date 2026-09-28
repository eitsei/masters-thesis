package com.dippa.ragbenchmark.infrastructure.dataset;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for loading, sampling, and ingesting the MuSiQue dataset.
 */
@ConfigurationProperties(prefix = "rag.dataset.musique")
public record MusiqueDatasetProperties(
        @DefaultValue("data/musique/musique_full_v1.0_dev.jsonl") String filePath,
        @DefaultValue("100") int sampleSize,
        @DefaultValue("0.40") double twoHopRatio,
        @DefaultValue("0.20") double threeHopRatio,
        @DefaultValue("0.20") double fourHopRatio,
        @DefaultValue("0.20") double unanswerableRatio,
        @DefaultValue("42") long randomSeed,
        @DefaultValue("100") int batchSize,
        @DefaultValue("true") boolean includeTitleInChunkText
) {}
