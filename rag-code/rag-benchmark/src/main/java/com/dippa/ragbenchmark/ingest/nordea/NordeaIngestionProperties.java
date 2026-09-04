package com.dippa.ragbenchmark.ingest.nordea;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config for the one-off Nordea private-banking site ingestion tool (see {@link
 * NordeaIngestionRunner}), bound from {@code ingest.nordea.*} in application.yml.
 *
 * <p>This is a local test-data generator, not part of the benchmarked RAG pipeline: it crawls a
 * real public site once to populate the {@code vector_store_test} table with realistic content
 * for local development, gated behind {@code enabled} so a normal {@code mvn spring-boot:run}
 * never triggers a live crawl (see README for how to run it explicitly).</p>
 */
@ConfigurationProperties(prefix = "ingest.nordea")
public record NordeaIngestionProperties(
        boolean enabled,
        String baseUrl,
        int maxPages,
        int maxPdfs,
        long maxPdfSizeBytes,
        long requestDelayMillis,
        String userAgent) {
}
