package com.dippa.ragbenchmark.ingest.nordea;

import java.io.IOException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Startup CommandLineRunner for crawling and ingesting Nordea private-banking content.
 *
 * <p>Supports both baseline 512-token chunks and S2B (1024-token parent / 128-token child) chunks.</p>
 */
@Component
@ConditionalOnProperty(prefix = "ingest.nordea", name = "enabled", havingValue = "true")
public class NordeaIngestionRunner implements CommandLineRunner {

    private static final Log logger = LogFactory.getLog(NordeaIngestionRunner.class);

    private final NordeaIngestionService ingestionService;
    private final String mode;

    public NordeaIngestionRunner(
            NordeaIngestionService ingestionService,
            @Value("${ingest.nordea.mode:original}") String mode) {
        this.ingestionService = ingestionService;
        this.mode = mode;
    }

    @Override
    public void run(String... args) throws IOException {
        logger.info("Starting Nordea startup ingestion (mode=" + mode + ")...");
        NordeaIngestionService.NordeaIngestionSummary summary = ingestionService.ingest(mode);
        logger.info("Nordea startup ingestion complete: " + summary.pagesCrawled() + " page(s), "
                + summary.pdfsExtracted() + " PDF(s), " + summary.parentChunksWritten() + " parent chunk(s), "
                + summary.childChunksWritten() + " child chunk(s) in " + summary.durationMs() + " ms");
    }
}
