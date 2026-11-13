package com.dippa.ragbenchmark.ingest.enterprise;

import java.io.IOException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Startup CommandLineRunner for crawling and ingesting enterprise content.
 *
 * <p>Supports both baseline 512-token chunks and S2B (1024-token parent / 128-token child) chunks.</p>
 */
@Component
@ConditionalOnProperty(prefix = "ingest.enterprise", name = "enabled", havingValue = "true")
public class EnterpriseIngestionRunner implements CommandLineRunner {

    private static final Log logger = LogFactory.getLog(EnterpriseIngestionRunner.class);

    private final EnterpriseIngestionService ingestionService;
    private final String mode;

    public EnterpriseIngestionRunner(
            EnterpriseIngestionService ingestionService,
            @Value("${ingest.enterprise.mode:original}") String mode) {
        this.ingestionService = ingestionService;
        this.mode = mode;
    }

    @Override
    public void run(String... args) throws IOException {
        logger.info("Starting enterprise startup ingestion (mode=" + mode + ")...");
        EnterpriseIngestionService.EnterpriseIngestionSummary summary = ingestionService.ingest(mode);
        logger.info("Enterprise startup ingestion complete: " + summary.pagesCrawled() + " page(s), "
                + summary.pdfsExtracted() + " PDF(s), " + summary.parentChunksWritten() + " parent chunk(s), "
                + summary.childChunksWritten() + " child chunk(s) in " + summary.durationMs() + " ms");
    }
}
