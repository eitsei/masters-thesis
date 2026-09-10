package com.dippa.ragbenchmark.ingest.musique;

import com.dippa.ragbenchmark.application.dataset.MusiqueDatasetService;
import com.dippa.ragbenchmark.application.dataset.MusiqueIngestionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Startup CommandLineRunner for ingesting MuSiQue benchmark paragraphs into pgvector.
 *
 * <p>Disabled by default; enable via {@code ingest.musique.enabled=true}.</p>
 */
@Component
@ConditionalOnProperty(prefix = "ingest.musique", name = "enabled", havingValue = "true")
public class MusiqueIngestionRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(MusiqueIngestionRunner.class);

    private final MusiqueDatasetService datasetService;
    private final boolean sampleOnly;
    private final int sampleSize;
    private final String mode;

    public MusiqueIngestionRunner(
            MusiqueDatasetService datasetService,
            @Value("${ingest.musique.sample-only:true}") boolean sampleOnly,
            @Value("${ingest.musique.sample-size:100}") int sampleSize,
            @Value("${ingest.musique.mode:original}") String mode) {
        this.datasetService = datasetService;
        this.sampleOnly = sampleOnly;
        this.sampleSize = sampleSize;
        this.mode = mode;
    }

    @Override
    public void run(String... args) {
        logger.info("Starting MuSiQue startup ingestion (sampleOnly={}, sampleSize={}, mode={})...", sampleOnly, sampleSize, mode);
        MusiqueIngestionSummary summary = datasetService.ingest(sampleOnly, sampleSize, mode);
        logger.info("MuSiQue startup ingestion complete: {} paragraphs from {} questions in {} ms",
                summary.uniqueParagraphsIngested(), summary.questionsProcessed(), summary.durationMs());
    }
}
