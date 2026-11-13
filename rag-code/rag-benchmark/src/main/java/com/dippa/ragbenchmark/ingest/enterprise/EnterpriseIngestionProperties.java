package com.dippa.ragbenchmark.ingest.enterprise;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config for the enterprise site ingestion tool (see {@link
 * EnterpriseIngestionRunner}), bound from {@code ingest.enterprise.*} in application.yml.
 *
 * <p>This is a local test-data generator, not part of the benchmarked RAG pipeline: it crawls an
 * enterprise site once to populate the {@code vector_store_enterprise} tables with realistic content
 * for local development, gated behind {@code enabled} so a normal {@code mvn spring-boot:run}
 * never triggers a live crawl (see README for how to run it explicitly).</p>
 */
@ConfigurationProperties(prefix = "ingest.enterprise")
public record EnterpriseIngestionProperties(
        boolean enabled,
        String baseUrl,
        int maxPages,
        int maxPdfs,
        long maxPdfSizeBytes,
        long requestDelayMillis,
        String userAgent,
        String markdownExportDir) {
}
