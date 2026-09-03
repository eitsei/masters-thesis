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

    public MusiqueIngestionRunner(
            MusiqueDatasetService datasetService,
            @Value("${ingest.musique.sample-only:true}") boolean sampleOnly,
            @Value("${ingest.musique.sample-size:100}") int sampleSize) {
        this.datasetService = datasetService;
        this.sampleOnly = sampleOnly;
        this.sampleSize = sampleSize;
    }

    @Override
    public void run(String... args) {
        logger.info("Starting MuSiQue startup ingestion (sampleOnly={}, sampleSize={})...", sampleOnly, sampleSize);
        MusiqueIngestionSummary summary = datasetService.ingest(sampleOnly, sampleSize);
        logger.info("MuSiQue startup ingestion complete: {} paragraphs from {} questions in {} ms",
                summary.uniqueParagraphsIngested(), summary.questionsProcessed(), summary.durationMs());
    }
}
