package com.dippa.ragbenchmark.ingest.nordea;

import com.dippa.ragbenchmark.infrastructure.PgVectorStoreFactory;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Service orchestrating the crawling of the Nordea site, PDF extraction, chunking,
 * and persistence into the target vector store tables (baseline 512-token or S2B small/large tables).
 */
@Service
public class NordeaIngestionService {

    private static final Log logger = LogFactory.getLog(NordeaIngestionService.class);
    private static final int BASELINE_CHUNK_SIZE_TOKENS = 512;
    private static final int S2B_PARENT_CHUNK_SIZE_TOKENS = 1024;
    private static final int S2B_CHILD_CHUNK_SIZE_TOKENS = 128;

    private final NordeaCrawler crawler;
    private final NordeaPdfExtractor pdfExtractor;
    private final VectorStore defaultVectorStore;
    private final PgVectorStoreFactory vectorStoreFactory;
    private final JdbcTemplate jdbcTemplate;
    private final NordeaIngestionProperties properties;

    public NordeaIngestionService(
            NordeaCrawler crawler,
            NordeaPdfExtractor pdfExtractor,
            VectorStore defaultVectorStore,
            ObjectProvider<PgVectorStoreFactory> vectorStoreFactoryProvider,
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            NordeaIngestionProperties properties) {
        this.crawler = crawler;
        this.pdfExtractor = pdfExtractor;
        this.defaultVectorStore = defaultVectorStore;
        this.vectorStoreFactory = vectorStoreFactoryProvider != null ? vectorStoreFactoryProvider.getIfAvailable() : null;
        this.jdbcTemplate = jdbcTemplateProvider != null ? jdbcTemplateProvider.getIfAvailable() : null;
        this.properties = properties;
    }

    public record NordeaIngestionSummary(
            int pagesCrawled,
            int pdfsExtracted,
            int parentChunksWritten,
            int childChunksWritten,
            long durationMs,
            String mode
    ) {}

    public NordeaIngestionSummary ingest(String mode) throws IOException {
        if ("s2b-baseline".equalsIgnoreCase(mode) || "small-to-big-baseline".equalsIgnoreCase(mode)) {
            return ingestSmallToBigFromBaseline();
        }
        if ("s2b".equalsIgnoreCase(mode) || "small-to-big".equalsIgnoreCase(mode)) {
            return ingestSmallToBig();
        }
        return ingestOriginal();
    }

    public NordeaIngestionSummary ingestOriginal() throws IOException {
        long startTime = System.currentTimeMillis();
        TokenTextSplitter splitter = TokenTextSplitter.builder().withChunkSize(BASELINE_CHUNK_SIZE_TOKENS).build();
        VectorStore targetStore = (vectorStoreFactory != null)
                ? vectorStoreFactory.getStore("vector_store_nordea")
                : defaultVectorStore;

        NordeaCrawler.CrawlResult crawlResult = crawler.crawl();
        logger.info("Crawled " + crawlResult.pages().size() + " HTML page(s), found "
                + crawlResult.pdfUrls().size() + " candidate PDF link(s)");

        List<Document> allChunks = new ArrayList<>();
        int fileCounter = 0;

        for (NordeaCrawler.CrawledPage page : crawlResult.pages()) {
            if (page.text().isBlank()) continue;
            exportMarkdown("pages", slugify(page.url(), page.title(), fileCounter++), page.url(), page.title(), page.text(), "html");
            allChunks.addAll(chunkBaseline(splitter, page.text(), page.url(), page.title(), "html"));
        }

        int pdfsExtracted = 0;
        for (String pdfUrl : crawlResult.pdfUrls()) {
            Optional<NordeaPdfExtractor.CrawledPdf> extracted = pdfExtractor.extract(pdfUrl);
            if (extracted.isEmpty() || extracted.get().text().isBlank()) continue;
            NordeaPdfExtractor.CrawledPdf pdf = extracted.get();
            exportMarkdown("pdfs", slugify(pdf.url(), pdf.title(), fileCounter++), pdf.url(), pdf.title(), pdf.text(), "pdf");
            allChunks.addAll(chunkBaseline(splitter, pdf.text(), pdf.url(), pdf.title(), "pdf"));
            pdfsExtracted++;
        }

        logger.info("Extracted markdown from " + pdfsExtracted + " PDF(s)");
        logger.info("Writing " + allChunks.size() + " baseline chunk(s) to vector_store_nordea");
        addInBatches(targetStore, allChunks, 100);
        long durationMs = System.currentTimeMillis() - startTime;
        logger.info("Nordea baseline ingestion complete: " + allChunks.size() + " chunk(s) in " + durationMs + " ms");
        return new NordeaIngestionSummary(crawlResult.pages().size(), pdfsExtracted, 0, allChunks.size(), durationMs, "original");
    }

    public NordeaIngestionSummary ingestSmallToBig() throws IOException {
        long startTime = System.currentTimeMillis();
        TokenTextSplitter parentSplitter = TokenTextSplitter.builder().withChunkSize(S2B_PARENT_CHUNK_SIZE_TOKENS).build();
        TokenTextSplitter childSplitter = TokenTextSplitter.builder().withChunkSize(S2B_CHILD_CHUNK_SIZE_TOKENS).build();

        VectorStore largeStore = (vectorStoreFactory != null)
                ? vectorStoreFactory.getStore("vector_store_nordea_large")
                : defaultVectorStore;
        VectorStore smallStore = (vectorStoreFactory != null)
                ? vectorStoreFactory.getStore("vector_store_nordea_small")
                : defaultVectorStore;

        NordeaCrawler.CrawlResult crawlResult = crawler.crawl();
        logger.info("S2B: Crawled " + crawlResult.pages().size() + " HTML page(s), found "
                + crawlResult.pdfUrls().size() + " candidate PDF link(s)");

        if (crawlResult.pages().isEmpty() && crawlResult.pdfUrls().isEmpty() && jdbcTemplate != null) {
            logger.warn("Live crawl returned 0 pages/PDFs. Falling back to ingestSmallToBigFromBaseline() from vector_store_nordea...");
            return ingestSmallToBigFromBaseline();
        }

        List<Document> parentChunks = new ArrayList<>();
        List<Document> childChunks = new ArrayList<>();
        int fileCounter = 0;

        for (NordeaCrawler.CrawledPage page : crawlResult.pages()) {
            if (page.text().isBlank()) continue;
            exportMarkdown("pages", slugify(page.url(), page.title(), fileCounter++), page.url(), page.title(), page.text(), "html");
            splitAndLink(parentSplitter, childSplitter, page.text(), page.url(), page.title(), "html", parentChunks, childChunks);
        }

        int pdfsExtracted = 0;
        for (String pdfUrl : crawlResult.pdfUrls()) {
            Optional<NordeaPdfExtractor.CrawledPdf> extracted = pdfExtractor.extract(pdfUrl);
            if (extracted.isEmpty() || extracted.get().text().isBlank()) continue;
            NordeaPdfExtractor.CrawledPdf pdf = extracted.get();
            exportMarkdown("pdfs", slugify(pdf.url(), pdf.title(), fileCounter++), pdf.url(), pdf.title(), pdf.text(), "pdf");
            splitAndLink(parentSplitter, childSplitter, pdf.text(), pdf.url(), pdf.title(), "pdf", parentChunks, childChunks);
            pdfsExtracted++;
        }

        logger.info("Writing " + parentChunks.size() + " parent chunks to vector_store_nordea_large");
        addInBatches(largeStore, parentChunks, 10);

        logger.info("Writing " + childChunks.size() + " child chunks to vector_store_nordea_small");
        addInBatches(smallStore, childChunks, 25);

        long durationMs = System.currentTimeMillis() - startTime;
        logger.info("Nordea S2B ingestion complete: " + parentChunks.size() + " parent(s), " + childChunks.size()
                + " child(ren) in " + durationMs + " ms");
        return new NordeaIngestionSummary(crawlResult.pages().size(), pdfsExtracted, parentChunks.size(), childChunks.size(), durationMs, "s2b");
    }

    public NordeaIngestionSummary ingestSmallToBigFromBaseline() {
        long startTime = System.currentTimeMillis();
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate is required for ingestSmallToBigFromBaseline");
        }

        VectorStore largeStore = (vectorStoreFactory != null)
                ? vectorStoreFactory.getStore("vector_store_nordea_large")
                : defaultVectorStore;
        VectorStore smallStore = (vectorStoreFactory != null)
                ? vectorStoreFactory.getStore("vector_store_nordea_small")
                : defaultVectorStore;

        TokenTextSplitter parentSplitter = TokenTextSplitter.builder().withChunkSize(S2B_PARENT_CHUNK_SIZE_TOKENS).build();
        TokenTextSplitter childSplitter = TokenTextSplitter.builder().withChunkSize(S2B_CHILD_CHUNK_SIZE_TOKENS).build();

        String sql = "SELECT metadata->>'source_url' AS source_url, "
                + "COALESCE(metadata->>'page_title', '') AS page_title, "
                + "content, "
                + "(metadata->>'chunk_index')::int AS chunk_index "
                + "FROM vector_store_nordea "
                + "ORDER BY source_url, chunk_index ASC";

        Map<String, StringBuilder> textsByUrl = new LinkedHashMap<>();
        Map<String, String> titlesByUrl = new LinkedHashMap<>();

        jdbcTemplate.query(sql, rs -> {
            String url = rs.getString("source_url");
            String title = rs.getString("page_title");
            String content = rs.getString("content");
            if (url != null && content != null) {
                textsByUrl.computeIfAbsent(url, k -> new StringBuilder()).append(content).append("\n\n");
                titlesByUrl.putIfAbsent(url, title != null ? title : "");
            }
        });

        logger.info("Found " + textsByUrl.size() + " documents in vector_store_nordea to convert to S2B");

        List<Document> parentChunks = new ArrayList<>();
        List<Document> childChunks = new ArrayList<>();

        for (Map.Entry<String, StringBuilder> entry : textsByUrl.entrySet()) {
            String url = entry.getKey();
            String title = titlesByUrl.getOrDefault(url, "");
            String docText = entry.getValue().toString().trim();
            splitAndLink(parentSplitter, childSplitter, docText, url, title, "baseline", parentChunks, childChunks);
        }

        logger.info("Writing " + parentChunks.size() + " parent chunks to vector_store_nordea_large");
        addInBatches(largeStore, parentChunks, 10);

        logger.info("Writing " + childChunks.size() + " child chunks to vector_store_nordea_small");
        addInBatches(smallStore, childChunks, 25);

        long durationMs = System.currentTimeMillis() - startTime;
        logger.info("Nordea S2B baseline migration complete: " + parentChunks.size() + " parent(s), " + childChunks.size()
                + " child(ren) in " + durationMs + " ms");
        return new NordeaIngestionSummary(textsByUrl.size(), 0, parentChunks.size(), childChunks.size(), durationMs, "s2b-baseline");
    }

    private void addInBatches(VectorStore store, List<Document> documents, int batchSize) {
        if (documents == null || documents.isEmpty()) return;
        int totalBatches = (documents.size() + batchSize - 1) / batchSize;
        for (int i = 0; i < documents.size(); i += batchSize) {
            int toIndex = Math.min(i + batchSize, documents.size());
            List<Document> batch = documents.subList(i, toIndex);
            int batchNum = (i / batchSize) + 1;
            int retries = 0;
            while (true) {
                try {
                    store.add(batch);
                    if (batchNum % 5 == 0 || batchNum == totalBatches) {
                        logger.info("Ingested batch " + batchNum + "/" + totalBatches + " (" + toIndex + "/" + documents.size() + " chunks)");
                    }
                    break;
                } catch (Exception ex) {
                    retries++;
                    if (retries > 3) {
                        logger.error("Failed adding batch " + batchNum + "/" + totalBatches + " after " + retries + " attempts: " + ex.getMessage(), ex);
                        throw ex;
                    }
                    logger.warn("Transient error adding batch " + batchNum + "/" + totalBatches + " (retry " + retries + "/3): " + ex.getMessage());
                    try {
                        Thread.sleep(2000L * retries);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(ie);
                    }
                }
            }
        }
    }

    private void splitAndLink(
            TokenTextSplitter parentSplitter,
            TokenTextSplitter childSplitter,
            String text,
            String sourceUrl,
            String title,
            String docType,
            List<Document> outParents,
            List<Document> outChildren) {
        Map<String, Object> baseMeta = new HashMap<>();
        baseMeta.put("source_url", sourceUrl);
        baseMeta.put("page_title", title);
        if (docType != null) {
            baseMeta.put("doc_type", docType);
        }

        List<Document> rawParents = parentSplitter.split(new Document(text, baseMeta));
        for (int pIdx = 0; pIdx < rawParents.size(); pIdx++) {
            Document pDoc = rawParents.get(pIdx);
            String parentId = UUID.nameUUIDFromBytes((sourceUrl + "#parent#" + pIdx).getBytes(StandardCharsets.UTF_8)).toString();

            Map<String, Object> parentMeta = new HashMap<>(baseMeta);
            parentMeta.put("chunk_type", "parent");
            parentMeta.put("chunk_index", pIdx);
            parentMeta.put("total_parent_chunks", rawParents.size());

            Document parent = new Document(parentId, pDoc.getText(), parentMeta);
            outParents.add(parent);

            List<Document> rawChildren = childSplitter.split(new Document(pDoc.getText()));
            for (int cIdx = 0; cIdx < rawChildren.size(); cIdx++) {
                Document cDoc = rawChildren.get(cIdx);
                String childId = UUID.nameUUIDFromBytes((sourceUrl + "#child#" + parentId + "#" + cIdx).getBytes(StandardCharsets.UTF_8)).toString();

                Map<String, Object> childMeta = new HashMap<>(baseMeta);
                childMeta.put("chunk_type", "child");
                childMeta.put("parent_id", parentId);
                childMeta.put("parent_table", "vector_store_nordea_large");
                childMeta.put("chunk_index", cIdx);
                childMeta.put("total_child_chunks", rawChildren.size());

                outChildren.add(new Document(childId, cDoc.getText(), childMeta));
            }
        }
    }

    private List<Document> chunkBaseline(TokenTextSplitter splitter, String text, String sourceUrl, String title, String docType) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source_url", sourceUrl);
        metadata.put("page_title", title);
        if (docType != null) {
            metadata.put("doc_type", docType);
        }

        List<Document> chunks = splitter.split(new Document(text, metadata));
        List<Document> withDeterministicIds = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String deterministicId = UUID.nameUUIDFromBytes((sourceUrl + "#" + i).getBytes(StandardCharsets.UTF_8))
                    .toString();
            Map<String, Object> chunkMetadata = new HashMap<>(chunks.get(i).getMetadata());
            chunkMetadata.put("chunk_index", i);
            chunkMetadata.put("chunk_type", "baseline");
            withDeterministicIds.add(new Document(deterministicId, chunks.get(i).getText(), chunkMetadata));
        }
        return withDeterministicIds;
    }

    private void exportMarkdown(String subDir, String filename, String url, String title, String markdown, String docType) {
        if (properties == null || properties.markdownExportDir() == null || properties.markdownExportDir().isBlank()) {
            return;
        }
        try {
            Path targetDir = Path.of(properties.markdownExportDir()).resolve(subDir);
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(filename);

            String frontmatter = """
                    ---
                    url: "%s"
                    title: "%s"
                    type: "%s"
                    crawled_at: "%s"
                    ---

                    """.formatted(
                            url.replace("\"", "\\\""),
                            (title != null ? title.replace("\"", "\\\"") : ""),
                            docType,
                            java.time.Instant.now().toString()
                    );

            Files.writeString(targetFile, frontmatter + markdown, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            logger.warn("Failed to export markdown to disk for " + url + ": " + ex.getMessage());
        }
    }

    private static String slugify(String url, String title, int index) {
        String base = (title != null && !title.isBlank()) ? title : url;
        String clean = base.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9äöå]+", "-")
                .replaceAll("^-+|-+$", "");
        if (clean.length() > 60) {
            clean = clean.substring(0, 60);
        }
        if (clean.isBlank()) {
            clean = "doc";
        }
        return String.format("%03d-%s.md", index, clean);
    }
}
