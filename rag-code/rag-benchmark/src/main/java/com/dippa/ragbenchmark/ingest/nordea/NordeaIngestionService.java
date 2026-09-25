package com.dippa.ragbenchmark.ingest.nordea;

import com.dippa.ragbenchmark.api.NordeaIngestController;
import com.dippa.ragbenchmark.infrastructure.PgVectorStoreFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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

    private static final Pattern COOKIE_NOTICE_LINE_PATTERN = Pattern.compile(
            "(?im)^.*(?:Hups!\\s*Jos haluat nähdä tällaista sisältöä|hyväksy ensin Nordean markkinointievästeet).*$\\R?"
    );
    private static final Pattern AVAUTUU_IKKUNAAN_PATTERN = Pattern.compile(
            "(?i)\\s*[,\\-–—]?\\s*\\(?\\s*avautuu uuteen ikkunaan\\s*\\)?"
    );
    private static final Pattern AVAUTUU_RAW_PATTERN = Pattern.compile(
            "(?i)avautuu uuteen ikkunaan"
    );
    private static final Pattern STANDARD_HELP_BLOCK_PATTERN = Pattern.compile(
            "##\\s*Apua asiointiin\\s*\\R+###\\s*Henkilöasiakkaat\\s*\\R+-\\s*Kysy heti chatissa\\s*\\R+-\\s*Usein kysytyt\\s*\\R+-\\s*Soita meille.*?\\R+-\\s*Nordea Omaposti[^\r\n]*",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern HELP_FOOTER_BLOCK_PATTERN = Pattern.compile(
            "(?im)^##\\s*Apua asiointiin\\s*$\\R+(?:^###\\s*Henkilöasiakkaat\\s*$\\R+)?(?:^-\\s*[^\r\n]*$\\R*)*"
    );
    private static final Pattern HELP_SUB_LIST_PATTERN = Pattern.compile(
            "###\\s*Henkilöasiakkaat\\s*\\R+-\\s*Kysy heti chatissa\\s*\\R+-\\s*Usein kysytyt\\s*\\R+-\\s*Soita meille.*?\\R+-\\s*Nordea Omaposti[^\r\n]*",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern APUA_HEADING_PATTERN = Pattern.compile(
            "(?im)^##\\s*Apua asiointiin\\s*$\\R?"
    );
    private static final Pattern EMPTY_HEADING_PATTERN = Pattern.compile(
            "(?m)^#{1,6}\\s*$\\R?"
    );
    private static final Pattern TRAILING_SPACES_PATTERN = Pattern.compile(
            "(?m)[ \\t]+$"
    );
    private static final Pattern CONSECUTIVE_NEWLINES_PATTERN = Pattern.compile(
            "\\n{3,}"
    );

    private final NordeaCrawler crawler;
    private final NordeaPdfExtractor pdfExtractor;
    private final VectorStore defaultVectorStore;
    private final PgVectorStoreFactory vectorStoreFactory;
    private final JdbcTemplate jdbcTemplate;
    private final NordeaIngestionProperties properties;

    @Autowired
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

    public NordeaIngestionService(
            NordeaCrawler crawler,
            NordeaPdfExtractor pdfExtractor,
            VectorStore defaultVectorStore,
            JdbcTemplate jdbcTemplate,
            NordeaIngestionProperties properties) {
        this.crawler = crawler;
        this.pdfExtractor = pdfExtractor;
        this.defaultVectorStore = defaultVectorStore;
        this.vectorStoreFactory = null;
        this.jdbcTemplate = jdbcTemplate;
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

    public record CrawlExportResult(int pagesCrawled, int pdfsExtracted) {}

    public record ParsedMarkdown(String url, String title, String docType, String content) {}

    CrawlExportResult crawlAndExport() {
        if (crawler == null) {
            return new CrawlExportResult(0, 0);
        }
        NordeaCrawler.CrawlResult crawlResult = crawler.crawl();
        logger.info("Crawled " + crawlResult.pages().size() + " HTML page(s), found "
                + crawlResult.pdfUrls().size() + " candidate PDF link(s)");

        int fileCounter = 0;
        for (NordeaCrawler.CrawledPage page : crawlResult.pages()) {
            if (page.text().isBlank()) continue;
            exportMarkdown("pages", slugify(page.url(), page.title(), fileCounter++), page.url(), page.title(), page.text(), "html");
        }

        int pdfsExtracted = 0;
        if (pdfExtractor != null) {
            for (String pdfUrl : crawlResult.pdfUrls()) {
                Optional<NordeaPdfExtractor.CrawledPdf> extracted = pdfExtractor.extract(pdfUrl);
                if (extracted.isEmpty() || extracted.get().text().isBlank()) continue;
                NordeaPdfExtractor.CrawledPdf pdf = extracted.get();
                exportMarkdown("pdfs", slugify(pdf.url(), pdf.title(), fileCounter++), pdf.url(), pdf.title(), pdf.text(), "pdf");
                pdfsExtracted++;
            }
        }
        return new CrawlExportResult(crawlResult.pages().size(), pdfsExtracted);
    }

    public int clearDatabase(String mode) {
        if (jdbcTemplate == null) {
            logger.warn("JdbcTemplate is null, skipping database clearing");
            return 0;
        }

        List<String> tables;
        String normalizedMode = mode != null ? mode.trim().toLowerCase(Locale.ROOT) : "all";
        if ("s2b".equals(normalizedMode) || "small-to-big".equals(normalizedMode)) {
            tables = List.of("vector_store_nordea_large", "vector_store_nordea_small");
        } else if ("original".equals(normalizedMode) || "baseline".equals(normalizedMode)) {
            tables = List.of("vector_store_nordea");
        } else {
            tables = List.of("vector_store_nordea", "vector_store_nordea_large", "vector_store_nordea_small");
        }

        int count = 0;
        for (String table : tables) {
            try {
                jdbcTemplate.execute("TRUNCATE TABLE " + table);
                logger.info("Cleared table " + table);
                count++;
            } catch (Exception ex) {
                logger.warn("Failed to truncate table " + table + ": " + ex.getMessage());
            }
        }
        return count;
    }

    public int clearFiles() {
        if (properties == null || properties.markdownExportDir() == null || properties.markdownExportDir().isBlank()) {
            return 0;
        }
        Path baseDir = Path.of(properties.markdownExportDir());
        if (!Files.exists(baseDir)) {
            return 0;
        }
        int deletedCount = 0;
        for (String subDirName : List.of("pages", "pdfs")) {
            Path subDir = baseDir.resolve(subDirName);
            if (!Files.exists(subDir)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(subDir)) {
                List<Path> filesToDelete = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            String name = p.getFileName().toString();
                            return name.endsWith(".md") || name.endsWith(".bak");
                        })
                        .toList();
                for (Path file : filesToDelete) {
                    try {
                        Files.delete(file);
                        deletedCount++;
                    } catch (IOException ex) {
                        logger.warn("Failed to delete file " + file + ": " + ex.getMessage());
                    }
                }
            } catch (IOException ex) {
                logger.warn("Failed to walk directory " + subDir + ": " + ex.getMessage());
            }
        }
        logger.info("Cleared " + deletedCount + " markdown/bak file(s) from " + properties.markdownExportDir());
        return deletedCount;
    }

    public int cleanMarkdownFiles() {
        if (properties == null || properties.markdownExportDir() == null || properties.markdownExportDir().isBlank()) {
            return 0;
        }
        Path baseDir = Path.of(properties.markdownExportDir());
        if (!Files.exists(baseDir)) {
            return 0;
        }
        List<Path> mdFiles = new ArrayList<>();
        for (String subDirName : List.of("pages", "pdfs")) {
            Path subDir = baseDir.resolve(subDirName);
            if (!Files.exists(subDir)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(subDir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".md"))
                        .forEach(mdFiles::add);
            } catch (IOException ex) {
                logger.warn("Failed to list markdown files in " + subDir + ": " + ex.getMessage());
            }
        }
        if (mdFiles.isEmpty()) {
            try (Stream<Path> stream = Files.walk(baseDir, 1)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".md"))
                        .forEach(mdFiles::add);
            } catch (IOException ex) {
                logger.warn("Failed to list markdown files in " + baseDir + ": " + ex.getMessage());
            }
        }

        int cleanedCount = 0;
        for (Path file : mdFiles) {
            try {
                String rawContent = Files.readString(file, StandardCharsets.UTF_8);
                String cleaned = cleanMarkdownContent(rawContent);
                Files.writeString(file, cleaned, StandardCharsets.UTF_8);
                cleanedCount++;
            } catch (IOException ex) {
                logger.warn("Failed to clean markdown file " + file + ": " + ex.getMessage());
            }
        }
        logger.info("Cleaned " + cleanedCount + " markdown file(s) in " + properties.markdownExportDir());
        return cleanedCount;
    }

    public static String cleanMarkdownContent(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return "";
        }

        String frontmatter = "";
        String body = rawContent;

        String[] lines = rawContent.split("\\R");
        if (lines.length > 0 && lines[0].trim().equals("---")) {
            int secondDelimiterLine = -1;
            for (int i = 1; i < lines.length; i++) {
                if (lines[i].trim().equals("---")) {
                    secondDelimiterLine = i;
                    break;
                }
            }
            if (secondDelimiterLine != -1) {
                StringBuilder fm = new StringBuilder();
                for (int i = 0; i <= secondDelimiterLine; i++) {
                    fm.append(lines[i]).append("\n");
                }
                frontmatter = fm.toString().trim();

                StringBuilder b = new StringBuilder();
                for (int i = secondDelimiterLine + 1; i < lines.length; i++) {
                    b.append(lines[i]).append("\n");
                }
                body = b.toString();
            }
        }

        String cleanedBody = cleanMarkdownBody(body);

        if (!frontmatter.isEmpty()) {
            return frontmatter + "\n\n" + cleanedBody.strip() + "\n";
        }
        return cleanedBody.strip().isEmpty() ? "" : cleanedBody.strip() + "\n";
    }

    public static String cleanMarkdownBody(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }

        String res = body;

        // 1. Remove cookie notices ('Hups! Jos haluat nähdä tällaista sisältöä, hyväksy ensin Nordean markkinointievästeet')
        res = COOKIE_NOTICE_LINE_PATTERN.matcher(res).replaceAll("");

        // 2. Remove 'Avautuu uuteen ikkunaan' (case-insensitive)
        res = AVAUTUU_IKKUNAAN_PATTERN.matcher(res).replaceAll("");
        res = AVAUTUU_RAW_PATTERN.matcher(res).replaceAll("");

        // 3. Remove trailing '## Apua asiointiin' footer block
        res = STANDARD_HELP_BLOCK_PATTERN.matcher(res).replaceAll("");
        res = HELP_FOOTER_BLOCK_PATTERN.matcher(res).replaceAll("");
        res = HELP_SUB_LIST_PATTERN.matcher(res).replaceAll("");
        res = APUA_HEADING_PATTERN.matcher(res).replaceAll("");

        // 4. Clean empty headings
        res = EMPTY_HEADING_PATTERN.matcher(res).replaceAll("");

        // 5. Clean up spaces before punctuation
        res = res.replaceAll(" +([.,!?:;])", "$1");

        // 6. Clean up trailing spaces and normalize consecutive blank lines
        res = TRAILING_SPACES_PATTERN.matcher(res).replaceAll("");
        res = CONSECUTIVE_NEWLINES_PATTERN.matcher(res).replaceAll("\n\n");

        return res.trim();
    }

    public static ParsedMarkdown parseMarkdown(String rawContent, String fallbackType) {
        if (rawContent == null || rawContent.isBlank()) {
            return new ParsedMarkdown("", "", fallbackType, "");
        }

        String[] lines = rawContent.split("\\R");
        String url = "";
        String title = "";
        String docType = fallbackType;
        String content = rawContent;

        if (lines.length > 0 && lines[0].trim().equals("---")) {
            int secondDelimiterLine = -1;
            for (int i = 1; i < lines.length; i++) {
                if (lines[i].trim().equals("---")) {
                    secondDelimiterLine = i;
                    break;
                }
            }
            if (secondDelimiterLine != -1) {
                for (int i = 1; i < secondDelimiterLine; i++) {
                    String line = lines[i].trim();
                    if (line.startsWith("url:")) {
                        url = extractFrontmatterValue(line.substring(4));
                    } else if (line.startsWith("title:")) {
                        title = extractFrontmatterValue(line.substring(6));
                    } else if (line.startsWith("type:")) {
                        String t = extractFrontmatterValue(line.substring(5));
                        if (!t.isBlank()) {
                            docType = t;
                        }
                    }
                }
                StringBuilder sb = new StringBuilder();
                for (int i = secondDelimiterLine + 1; i < lines.length; i++) {
                    sb.append(lines[i]).append("\n");
                }
                content = sb.toString().trim();
            }
        }

        return new ParsedMarkdown(url, title, docType, content);
    }

    private static String extractFrontmatterValue(String raw) {
        if (raw == null) return "";
        String val = raw.trim();
        if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
            val = val.substring(1, val.length() - 1);
        }
        return val.replace("\\\"", "\"").trim();
    }

    public NordeaIngestionSummary ingestFromMarkdownFiles(String mode) throws IOException {
        long startTime = System.currentTimeMillis();
        String resolvedMode = (mode != null && !mode.isBlank()) ? mode.toLowerCase(Locale.ROOT) : "s2b";
        boolean isBaseline = "original".equals(resolvedMode) || "baseline".equals(resolvedMode);

        if (properties == null || properties.markdownExportDir() == null || properties.markdownExportDir().isBlank()) {
            logger.warn("markdownExportDir is not set, skipping ingestFromMarkdownFiles");
            return new NordeaIngestionSummary(0, 0, 0, 0, System.currentTimeMillis() - startTime, resolvedMode);
        }

        Path baseDir = Path.of(properties.markdownExportDir());
        if (!Files.exists(baseDir)) {
            logger.warn("markdownExportDir does not exist: " + baseDir);
            return new NordeaIngestionSummary(0, 0, 0, 0, System.currentTimeMillis() - startTime, resolvedMode);
        }

        List<Path> markdownFiles = new ArrayList<>();
        for (String sub : List.of("pages", "pdfs")) {
            Path subDir = baseDir.resolve(sub);
            if (Files.exists(subDir)) {
                try (Stream<Path> stream = Files.walk(subDir)) {
                    stream.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().endsWith(".md"))
                            .forEach(markdownFiles::add);
                }
            }
        }
        if (markdownFiles.isEmpty()) {
            try (Stream<Path> stream = Files.walk(baseDir, 1)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".md"))
                        .forEach(markdownFiles::add);
            }
        }
        markdownFiles.sort(Comparator.comparing(Path::toString));

        int pagesRead = 0;
        int pdfsRead = 0;

        TokenTextSplitter baselineSplitter = isBaseline
                ? TokenTextSplitter.builder().withChunkSize(BASELINE_CHUNK_SIZE_TOKENS).build()
                : null;
        TokenTextSplitter parentSplitter = !isBaseline
                ? TokenTextSplitter.builder().withChunkSize(S2B_PARENT_CHUNK_SIZE_TOKENS).build()
                : null;
        TokenTextSplitter childSplitter = !isBaseline
                ? TokenTextSplitter.builder().withChunkSize(S2B_CHILD_CHUNK_SIZE_TOKENS).build()
                : null;

        List<Document> baselineChunks = new ArrayList<>();
        List<Document> parentChunks = new ArrayList<>();
        List<Document> childChunks = new ArrayList<>();

        for (Path file : markdownFiles) {
            String rawContent = Files.readString(file, StandardCharsets.UTF_8);
            String fallbackType = (file.toString().contains("/pdfs/") || file.toString().contains("\\pdfs\\")) ? "pdf" : "html";
            ParsedMarkdown parsed = parseMarkdown(rawContent, fallbackType);
            if (parsed.content().isBlank()) {
                continue;
            }

            if ("pdf".equalsIgnoreCase(parsed.docType())) {
                pdfsRead++;
            } else {
                pagesRead++;
            }

            String docUrl = !parsed.url().isBlank() ? parsed.url() : file.getFileName().toString();

            if (isBaseline) {
                baselineChunks.addAll(chunkBaseline(baselineSplitter, parsed.content(), docUrl, parsed.title(), parsed.docType()));
            } else {
                splitAndLink(parentSplitter, childSplitter, parsed.content(), docUrl, parsed.title(), parsed.docType(), parentChunks, childChunks);
            }
        }

        if (isBaseline) {
            VectorStore targetStore = (vectorStoreFactory != null)
                    ? vectorStoreFactory.getStore("vector_store_nordea")
                    : defaultVectorStore;
            logger.info("Writing " + baselineChunks.size() + " baseline chunk(s) from files to vector_store_nordea");
            addInBatches(targetStore, baselineChunks, 100);
            long durationMs = System.currentTimeMillis() - startTime;
            logger.info("Nordea baseline file ingestion complete: " + baselineChunks.size() + " chunk(s) in " + durationMs + " ms");
            return new NordeaIngestionSummary(pagesRead, pdfsRead, 0, baselineChunks.size(), durationMs, "original");
        } else {
            VectorStore largeStore = (vectorStoreFactory != null)
                    ? vectorStoreFactory.getStore("vector_store_nordea_large")
                    : defaultVectorStore;
            VectorStore smallStore = (vectorStoreFactory != null)
                    ? vectorStoreFactory.getStore("vector_store_nordea_small")
                    : defaultVectorStore;

            logger.info("Writing " + parentChunks.size() + " parent chunks from files to vector_store_nordea_large");
            addInBatches(largeStore, parentChunks, 10);

            logger.info("Writing " + childChunks.size() + " child chunks from files to vector_store_nordea_small");
            addInBatches(smallStore, childChunks, 25);

            long durationMs = System.currentTimeMillis() - startTime;
            logger.info("Nordea S2B file ingestion complete: " + parentChunks.size() + " parent(s), " + childChunks.size()
                    + " child(ren) in " + durationMs + " ms");
            return new NordeaIngestionSummary(pagesRead, pdfsRead, parentChunks.size(), childChunks.size(), durationMs, "s2b");
        }
    }

    public NordeaIngestionSummary executePipeline(NordeaIngestController.NordeaIngestRequest request) throws IOException {
        long startTime = System.currentTimeMillis();
        NordeaIngestController.NordeaIngestRequest req = request != null
                ? request
                : new NordeaIngestController.NordeaIngestRequest(null, null, null, null);

        if (req.shouldClearFiles()) {
            clearFiles();
        }

        if (req.shouldClearDatabase()) {
            clearDatabase(req.mode() != null ? req.mode() : req.resolvedMode());
        }

        String action = req.resolvedAction();
        String mode = req.resolvedMode();

        switch (action) {
            case "crawl" -> {
                CrawlExportResult crawlResult = crawlAndExport();
                long durationMs = System.currentTimeMillis() - startTime;
                return new NordeaIngestionSummary(crawlResult.pagesCrawled(), crawlResult.pdfsExtracted(), 0, 0, durationMs, "crawl");
            }
            case "clean" -> {
                int cleanedCount = cleanMarkdownFiles();
                long durationMs = System.currentTimeMillis() - startTime;
                return new NordeaIngestionSummary(cleanedCount, 0, 0, 0, durationMs, "clean");
            }
            case "s2b-baseline", "small-to-big-baseline" -> {
                return ingestSmallToBigFromBaseline();
            }
            case "ingest" -> {
                if ("s2b-baseline".equalsIgnoreCase(mode) || "small-to-big-baseline".equalsIgnoreCase(mode)) {
                    return ingestSmallToBigFromBaseline();
                }
                return ingestFromMarkdownFiles(mode);
            }
            case "all" -> {
                if ("s2b-baseline".equalsIgnoreCase(mode) || "small-to-big-baseline".equalsIgnoreCase(mode)) {
                    return ingestSmallToBigFromBaseline();
                }
                CrawlExportResult crawlResult = crawlAndExport();
                cleanMarkdownFiles();
                NordeaIngestionSummary ingestSummary = ingestFromMarkdownFiles(mode);
                long totalDurationMs = System.currentTimeMillis() - startTime;
                return new NordeaIngestionSummary(
                        crawlResult.pagesCrawled() > 0 ? crawlResult.pagesCrawled() : ingestSummary.pagesCrawled(),
                        crawlResult.pdfsExtracted() > 0 ? crawlResult.pdfsExtracted() : ingestSummary.pdfsExtracted(),
                        ingestSummary.parentChunksWritten(),
                        ingestSummary.childChunksWritten(),
                        totalDurationMs,
                        ingestSummary.mode()
                );
            }
            default -> throw new IllegalArgumentException("Unknown action: " + action + ". Supported actions: crawl, clean, ingest, all");
        }
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
