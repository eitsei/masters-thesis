package com.dippa.ragbenchmark.ingest.nordea;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * One-off admin tool: crawls the Nordea private-banking site (see {@link NordeaCrawler}),
 * extracts text from any linked PDFs (see {@link NordeaPdfExtractor}), chunks everything with
 * the same 512-token splitter as {@link com.dippa.ragbenchmark.application.DocumentIngestionService},
 * and writes the chunks into the {@code vector_store_test} table (a plain Postgres table backed
 * by a named Docker volume, so it survives {@code mvn spring-boot:run} restarts on its own -- no
 * extra persistence work needed here).
 *
 * <p>Disabled unless {@code ingest.nordea.enabled=true} is set explicitly (see README), so a
 * normal local run never triggers a live crawl. Each fetched page/PDF's extracted plain text is
 * staged in a temp file before chunking (per the original ask); only the cleaned text is ever
 * written to disk, never raw HTML/PDF bytes, and the whole staging directory is deleted once
 * ingestion finishes (success or failure).</p>
 *
 * <p>Chunk ids are deterministic ({@code UUID.nameUUIDFromBytes} of {@code sourceUrl#chunkIndex}),
 * so re-running this tool overwrites the same rows via {@link VectorStore#add(List)}'s upsert
 * behavior instead of duplicating them.</p>
 */
@Component
@ConditionalOnProperty(prefix = "ingest.nordea", name = "enabled", havingValue = "true")
public class NordeaIngestionRunner implements CommandLineRunner {

    private static final Log logger = LogFactory.getLog(NordeaIngestionRunner.class);
    private static final int CHUNK_SIZE_TOKENS = 512;

    private final NordeaCrawler crawler;
    private final NordeaPdfExtractor pdfExtractor;
    private final VectorStore vectorStore;
    private final TokenTextSplitter tokenTextSplitter;

    public NordeaIngestionRunner(NordeaCrawler crawler, NordeaPdfExtractor pdfExtractor, VectorStore vectorStore) {
        this.crawler = crawler;
        this.pdfExtractor = pdfExtractor;
        this.vectorStore = vectorStore;
        this.tokenTextSplitter = TokenTextSplitter.builder().withChunkSize(CHUNK_SIZE_TOKENS).build();
    }

    @Override
    public void run(String... args) throws IOException {
        Path stagingDir = Files.createTempDirectory("nordea-scrape-");
        try {
            NordeaCrawler.CrawlResult crawlResult = crawler.crawl();
            logger.info("Crawled " + crawlResult.pages().size() + " HTML page(s), found "
                    + crawlResult.pdfUrls().size() + " candidate PDF link(s)");

            List<Document> allChunks = new ArrayList<>();
            int fileCounter = 0;

            for (NordeaCrawler.CrawledPage page : crawlResult.pages()) {
                if (page.text().isBlank()) {
                    continue;
                }
                String stagedText = stageAndReload(stagingDir, "page-" + fileCounter++, page.text());
                allChunks.addAll(chunk(stagedText, page.url(), page.title()));
            }

            int pdfsExtracted = 0;
            for (String pdfUrl : crawlResult.pdfUrls()) {
                Optional<NordeaPdfExtractor.CrawledPdf> extracted = pdfExtractor.extract(pdfUrl);
                if (extracted.isEmpty() || extracted.get().text().isBlank()) {
                    continue;
                }
                NordeaPdfExtractor.CrawledPdf pdf = extracted.get();
                String stagedText = stageAndReload(stagingDir, "pdf-" + fileCounter++, pdf.text());
                allChunks.addAll(chunk(stagedText, pdf.url(), pdf.title()));
                pdfsExtracted++;
            }

            logger.info("Extracted text from " + pdfsExtracted + " PDF(s)");
            logger.info("Writing " + allChunks.size() + " chunk(s) to the vector store");
            if (!allChunks.isEmpty()) {
                vectorStore.add(allChunks);
            }
            logger.info("Nordea ingestion complete: " + allChunks.size() + " chunk(s) written");
        } finally {
            deleteRecursively(stagingDir);
        }
    }

    private static String stageAndReload(Path stagingDir, String fileNamePrefix, String text) throws IOException {
        Path stagedFile = stagingDir.resolve(fileNamePrefix + ".txt");
        Files.writeString(stagedFile, text, StandardCharsets.UTF_8);
        return Files.readString(stagedFile, StandardCharsets.UTF_8);
    }

    private List<Document> chunk(String text, String sourceUrl, String title) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source_url", sourceUrl);
        metadata.put("page_title", title);

        List<Document> chunks = tokenTextSplitter.split(new Document(text, metadata));
        List<Document> withDeterministicIds = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String deterministicId = UUID.nameUUIDFromBytes((sourceUrl + "#" + i).getBytes(StandardCharsets.UTF_8))
                    .toString();
            withDeterministicIds.add(chunks.get(i).mutate().id(deterministicId).build());
        }
        return withDeterministicIds;
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException ex) {
                    logger.warn("Failed to delete staging file " + path + ": " + ex.getMessage());
                }
            });
        } catch (IOException ex) {
            logger.warn("Failed to walk staging dir for cleanup " + dir + ": " + ex.getMessage());
        }
    }
}
