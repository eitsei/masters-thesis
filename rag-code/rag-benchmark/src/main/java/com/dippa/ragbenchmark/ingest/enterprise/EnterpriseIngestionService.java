package com.dippa.ragbenchmark.ingest.enterprise;

import com.dippa.ragbenchmark.api.EnterpriseIngestController;
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
 * Service orchestrating the crawling of the enterprise site, PDF extraction, chunking,
 * and persistence into the target vector store tables (baseline 512-token or S2B small/large tables).
 */
@Service
public class EnterpriseIngestionService {

    private static final Log logger = LogFactory.getLog(EnterpriseIngestionService.class);
    private static final int BASELINE_CHUNK_SIZE_TOKENS = 512;
    private static final int S2B_PARENT_CHUNK_SIZE_TOKENS = 1024;
    private static final int S2B_CHILD_CHUNK_SIZE_TOKENS = 128;

    private static final Pattern COOKIE_NOTICE_LINE_PATTERN = Pattern.compile(
            "(?im)^.*(?:Hups!\\s*Jos haluat nähdä tällaista sisältöä|hyväksy ensin.*markkinointievästeet).*$\\R?"
    );
    private static final Pattern AVAUTUU_IKKUNAAN_PATTERN = Pattern.compile(
            "(?i)\\s*[,\\-–—]?\\s*\\(?\\s*avautuu uuteen ikkunaan\\s*\\)?"
    );
    private static final Pattern AVAUTUU_RAW_PATTERN = Pattern.compile(
            "(?i)avautuu uuteen ikkunaan"
    );
    private static final Pattern STANDARD_HELP_BLOCK_PATTERN = Pattern.compile(
            "##\\s*Apua asiointiin\\s*\\R+###\\s*Henkilöasiakkaat\\s*\\R+-\\s*Kysy heti chatissa\\s*\\R+-\\s*Usein kysytyt\\s*\\R+-\\s*Soita meille.*?\\R+-\\s*(?:Enterprise Portal|Omaposti)[^\r\n]*",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern HELP_FOOTER_BLOCK_PATTERN = Pattern.compile(
            "(?im)^##\\s*Apua asiointiin\\s*$\\R+(?:^###\\s*Henkilöasiakkaat\\s*$\\R+)?(?:^-\\s*[^\r\n]*$\\R*)*"
    );
    private static final Pattern HELP_SUB_LIST_PATTERN = Pattern.compile(
            "###\\s*Henkilöasiakkaat\\s*\\R+-\\s*Kysy heti chatissa\\s*\\R+-\\s*Usein kysytyt\\s*\\R+-\\s*Soita meille.*?\\R+-\\s*(?:Enterprise Portal|Omaposti)[^\r\n]*",
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

    private final EnterpriseCrawler crawler;
    private final EnterprisePdfExtractor pdfExtractor;
    private final VectorStore defaultVectorStore;
    private final PgVectorStoreFactory vectorStoreFactory;
    private final JdbcTemplate jdbcTemplate;
    private final EnterpriseIngestionProperties properties;

    @Autowired
    public EnterpriseIngestionService(
            EnterpriseCrawler crawler,
            EnterprisePdfExtractor pdfExtractor,
            VectorStore defaultVectorStore,
            ObjectProvider<PgVectorStoreFactory> vectorStoreFactoryProvider,
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            EnterpriseIngestionProperties properties) {
        this.crawler = crawler;
        this.pdfExtractor = pdfExtractor;
        this.defaultVectorStore = defaultVectorStore;
        this.vectorStoreFactory = vectorStoreFactoryProvider != null ? vectorStoreFactoryProvider.getIfAvailable() : null;
        this.jdbcTemplate = jdbcTemplateProvider != null ? jdbcTemplateProvider.getIfAvailable() : null;
        this.properties = properties;
    }

    public EnterpriseIngestionService(
            EnterpriseCrawler crawler,
            EnterprisePdfExtractor pdfExtractor,
            VectorStore defaultVectorStore,
            JdbcTemplate jdbcTemplate,
            EnterpriseIngestionProperties properties) {
        this.crawler = crawler;
        this.pdfExtractor = pdfExtractor;
        this.defaultVectorStore = defaultVectorStore;
        this.vectorStoreFactory = null;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public record EnterpriseIngestionSummary(
            int pagesCrawled,
            int pdfsExtracted,
            int parentChunksWritten,
            int childChunksWritten,
            long durationMs,
            String mode
    ) {}

    public EnterpriseIngestionSummary ingest(String mode) throws IOException {
        if ("s2b-baseline".equalsIgnoreCase(mode) || "small-to-big-baseline".equalsIgnoreCase(mode)) {
            return ingestSmallToBigFromBaseline();
        }
        if ("s2b".equalsIgnoreCase(mode) || "small-to-big".equalsIgnoreCase(mode)) {
            return ingestSmallToBig();
        }
        return ingestOriginal();
    }

    public EnterpriseIngestionSummary ingestOriginal() throws IOException {
        long startTime = System.currentTimeMillis();
        TokenTextSplitter splitter = TokenTextSplitter.builder().withChunkSize(BASELINE_CHUNK_SIZE_TOKENS).build();
        VectorStore targetStore = (vectorStoreFactory != null)
                ? vectorStoreFactory.getStore("vector_store_enterprise")
                : defaultVectorStore;

        EnterpriseCrawler.CrawlResult crawlResult = crawler.crawl();
        logger.info("Crawled " + crawlResult.pages().size() + " HTML page(s), found "
                + crawlResult.pdfUrls().size() + " candidate PDF link(s)");

        List<Document> allChunks = new ArrayList<>();
        int fileCounter = 0;

        for (EnterpriseCrawler.CrawledPage page : crawlResult.pages()) {
            if (page.text().isBlank()) continue;
            exportMarkdown("pages", slugify(page.url(), page.title(), fileCounter++), page.url(), page.title(), page.text(), "html");
            allChunks.addAll(chunkBaseline(splitter, page.text(), page.url(), page.title(), "html"));
        }

        int pdfsExtracted = 0;
        for (String pdfUrl : crawlResult.pdfUrls()) {
            Optional<EnterprisePdfExtractor.CrawledPdf> extracted = pdfExtractor.extract(pdfUrl);
            if (extracted.isEmpty() || extracted.get().text().isBlank()) continue;
            EnterprisePdfExtractor.CrawledPdf pdf = extracted.get();
            exportMarkdown("pdfs", slugify(pdf.url(), pdf.title(), fileCounter++), pdf.url(), pdf.title(), pdf.text(), "pdf");
            allChunks.addAll(chunkBaseline(splitter, pdf.text(), pdf.url(), pdf.title(), "pdf"));
            pdfsExtracted++;
        }

        logger.info("Extracted markdown from " + pdfsExtracted + " PDF(s)");
        logger.info("Writing " + allChunks.size() + " baseline chunk(s) to vector_store_enterprise");
        addInBatches(targetStore, allChunks, 100);
        long durationMs = System.currentTimeMillis() - startTime;
        logger.info("Enterprise baseline ingestion complete: " + allChunks.size() + " chunk(s) in " + durationMs + " ms");
        return new EnterpriseIngestionSummary(crawlResult.pages().size(), pdfsExtracted, 0, allChunks.size(), durationMs, "original");
    }

    public EnterpriseIngestionSummary ingestSmallToBig() throws IOException {
        long startTime = System.currentTimeMillis();
        TokenTextSplitter parentSplitter = TokenTextSplitter.builder().withChunkSize(S2B_PARENT_CHUNK_SIZE_TOKENS).build();
        TokenTextSplitter childSplitter = TokenTextSplitter.builder().withChunkSize(S2B_CHILD_CHUNK_SIZE_TOKENS).build();

        VectorStore largeStore = (vectorStoreFactory != null)
                ? vectorStoreFactory.getStore("vector_store_enterprise_large")
                : defaultVectorStore;
        VectorStore smallStore = (vectorStoreFactory != null)
                ? vectorStoreFactory.getStore("vector_store_enterprise_small")
                : defaultVectorStore;

        EnterpriseCrawler.CrawlResult crawlResult = crawler.crawl();
        logger.info("S2B: Crawled " + crawlResult.pages().size() + " HTML page(s), found "
                + crawlResult.pdfUrls().size() + " candidate PDF link(s)");

        if (crawlResult.pages().isEmpty() && crawlResult.pdfUrls().isEmpty() && jdbcTemplate != null) {
            logger.warn("Live crawl returned 0 pages/PDFs. Falling back to ingestSmallToBigFromBaseline() from vector_store_enterprise...");
            return ingestSmallToBigFromBaseline();
        }

        List<Document> parentChunks = new ArrayList<>();
        List<Document> childChunks = new ArrayList<>();
        int fileCounter = 0;

        for (EnterpriseCrawler.CrawledPage page : crawlResult.pages()) {
            if (page.text().isBlank()) continue;
            exportMarkdown("pages", slugify(page.url(), page.title(), fileCounter++), page.url(), page.title(), page.text(), "html");
            splitAndLink(parentSplitter, childSplitter, page.text(), page.url(), page.title(), "html", parentChunks, childChunks);
        }

        int pdfsExtracted = 0;
        for (String pdfUrl : crawlResult.pdfUrls()) {
            Optional<EnterprisePdfExtractor.CrawledPdf> extracted = pdfExtractor.extract(pdfUrl);
            if (extracted.isEmpty() || extracted.get().text().isBlank()) continue;
            EnterprisePdfExtractor.CrawledPdf pdf = extracted.get();
            exportMarkdown("pdfs", slugify(pdf.url(), pdf.title(), fileCounter++), pdf.url(), pdf.title(), pdf.text(), "pdf");
            splitAndLink(parentSplitter, childSplitter, pdf.text(), pdf.url(), pdf.title(), "pdf", parentChunks, childChunks);
            pdfsExtracted++;
        }

        logger.info("Writing " + parentChunks.size() + " parent chunks to vector_store_enterprise_large");
        addInBatches(largeStore, parentChunks, 10);

        logger.info("Writing " + childChunks.size() + " child chunks to vector_store_enterprise_small");
        addInBatches(smallStore, childChunks, 25);

        long durationMs = System.currentTimeMillis() - startTime;
        logger.info("Enterprise S2B ingestion complete: " + parentChunks.size() + " parent(s), " + childChunks.size()
                + " child(ren) in " + durationMs + " ms");
        return new EnterpriseIngestionSummary(crawlResult.pages().size(), pdfsExtracted, parentChunks.size(), childChunks.size(), durationMs, "s2b");
    }

    public EnterpriseIngestionSummary ingestSmallToBigFromBaseline() {
        long startTime = System.currentTimeMillis();
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate is required for ingestSmallToBigFromBaseline");
        }

        VectorStore largeStore = (vectorStoreFactory != null)
                ? vectorStoreFactory.getStore("vector_store_enterprise_large")
                : defaultVectorStore;
        VectorStore smallStore = (vectorStoreFactory != null)
                ? vectorStoreFactory.getStore("vector_store_enterprise_small")
                : defaultVectorStore;

        TokenTextSplitter parentSplitter = TokenTextSplitter.builder().withChunkSize(S2B_PARENT_CHUNK_SIZE_TOKENS).build();
        TokenTextSplitter childSplitter = TokenTextSplitter.builder().withChunkSize(S2B_CHILD_CHUNK_SIZE_TOKENS).build();

        String sql = "SELECT metadata->>'source_url' AS source_url, "
                + "COALESCE(metadata->>'page_title', '') AS page_title, "
                + "content, "
                + "(metadata->>'chunk_index')::int AS chunk_index "
                + "FROM vector_store_enterprise "
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

        logger.info("Found " + textsByUrl.size() + " documents in vector_store_enterprise to convert to S2B");

        List<Document> parentChunks = new ArrayList<>();
        List<Document> childChunks = new ArrayList<>();

        for (Map.Entry<String, StringBuilder> entry : textsByUrl.entrySet()) {
            String url = entry.getKey();
            String title = titlesByUrl.getOrDefault(url, "");
            String docText = entry.getValue().toString().trim();
            splitAndLink(parentSplitter, childSplitter, docText, url, title, "baseline", parentChunks, childChunks);
        }

        logger.info("Writing " + parentChunks.size() + " parent chunks to vector_store_enterprise_large");
        addInBatches(largeStore, parentChunks, 10);

        logger.info("Writing " + childChunks.size() + " child chunks to vector_store_enterprise_small");
        addInBatches(smallStore, childChunks, 25);

        long durationMs = System.currentTimeMillis() - startTime;
        logger.info("Enterprise S2B baseline migration complete: " + parentChunks.size() + " parent(s), " + childChunks.size()
                + " child(ren) in " + durationMs + " ms");
        return new EnterpriseIngestionSummary(textsByUrl.size(), 0, parentChunks.size(), childChunks.size(), durationMs, "s2b-baseline");
    }

    public record CrawlExportResult(int pagesCrawled, int pdfsExtracted) {}

    public record ParsedMarkdown(String url, String title, String docType, String content) {}

    CrawlExportResult crawlAndExport() {
        if (crawler == null) {
            return new CrawlExportResult(0, 0);
        }
        EnterpriseCrawler.CrawlResult crawlResult = crawler.crawl();
        logger.info("Crawled " + crawlResult.pages().size() + " HTML page(s), found "
                + crawlResult.pdfUrls().size() + " candidate PDF link(s)");

        int fileCounter = 0;
        for (EnterpriseCrawler.CrawledPage page : crawlResult.pages()) {
            if (page.text().isBlank()) continue;
            exportMarkdown("pages", slugify(page.url(), page.title(), fileCounter++), page.url(), page.title(), page.text(), "html");
        }

        int pdfsExtracted = 0;
        if (pdfExtractor != null) {
            for (String pdfUrl : crawlResult.pdfUrls()) {
                Optional<EnterprisePdfExtractor.CrawledPdf> extracted = pdfExtractor.extract(pdfUrl);
                if (extracted.isEmpty() || extracted.get().text().isBlank()) continue;
                EnterprisePdfExtractor.CrawledPdf pdf = extracted.get();
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
            tables = List.of("vector_store_enterprise_large", "vector_store_enterprise_small");
        } else if ("original".equals(normalizedMode) || "baseline".equals(normalizedMode)) {
            tables = List.of("vector_store_enterprise");
        } else {
            tables = List.of("vector_store_enterprise", "vector_store_enterprise_large", "vector_store_enterprise_small");
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
                            return name.endsWith(".md") || name.endsWith(".md.bak");
                        })
                        .toList();
                for (Path p : filesToDelete) {
                    try {
                        Files.deleteIfExists(p);
                        deletedCount++;
                    } catch (IOException ex) {
                        logger.warn("Failed to delete file " + p + ": " + ex.getMessage());
                    }
                }
            } catch (IOException ex) {
                logger.warn("Failed to traverse " + subDir + ": " + ex.getMessage());
            }
        }
        logger.info("Cleared " + deletedCount + " markdown/backup file(s) from " + baseDir);
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
        int cleanedCount = 0;
        for (String subDirName : List.of("pages", "pdfs")) {
            Path subDir = baseDir.resolve(subDirName);
            if (!Files.exists(subDir)) {
                continue;
            }
            try (Stream<Path> stream = Files.list(subDir)) {
                List<Path> mdFiles = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".md"))
                        .toList();

                for (Path mdFile : mdFiles) {
                    try {
                        String originalContent = Files.readString(mdFile, StandardCharsets.UTF_8);
                        String cleanedContent = cleanMarkdownText(originalContent);
                        if (!originalContent.equals(cleanedContent)) {
                            Path backupFile = mdFile.resolveSibling(mdFile.getFileName() + ".bak");
                            Files.writeString(backupFile, originalContent, StandardCharsets.UTF_8);
                            Files.writeString(mdFile, cleanedContent, StandardCharsets.UTF_8);
                            cleanedCount++;
                        }
                    } catch (IOException ex) {
                        logger.warn("Failed to clean markdown file " + mdFile + ": " + ex.getMessage());
                    }
                }
            } catch (IOException ex) {
                logger.warn("Failed to list files in " + subDir + ": " + ex.getMessage());
            }
        }
        logger.info("Cleaned and created backups for " + cleanedCount + " markdown file(s)");
        return cleanedCount;
    }

    static String cleanMarkdownText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        // Split frontmatter and body
        String frontmatter = "";
        String body = text;

        if (text.startsWith("---\n") || text.startsWith("---\r\n")) {
            int secondDelim = text.indexOf("\n---", 3);
            if (secondDelim >= 0) {
                int endOfDelimLine = text.indexOf('\n', secondDelim + 4);
                if (endOfDelimLine >= 0) {
                    frontmatter = text.substring(0, endOfDelimLine + 1);
                    body = text.substring(endOfDelimLine + 1);
                }
            }
        }

        String res = body;

        // 1. Remove cookie notices ('Hups! Jos haluat nähdä tällaista sisältöä, hyväksy ensin markkinointievästeet')
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

        // 6. Clean up trailing spaces and consecutive blank lines
        res = TRAILING_SPACES_PATTERN.matcher(res).replaceAll("");
        res = CONSECUTIVE_NEWLINES_PATTERN.matcher(res).replaceAll("\n\n");
        res = res.trim();

        if (frontmatter.isEmpty()) {
            return res + "\n";
        }
        return frontmatter + "\n" + res + "\n";
    }

    static ParsedMarkdown parseMarkdownFile(String fileContent) {
        if (fileContent == null || fileContent.isBlank()) {
            return new ParsedMarkdown("", "", "html", "");
        }

        String url = "";
        String title = "";
        String docType = "html";
        String body = fileContent;

        if (fileContent.startsWith("---\n") || fileContent.startsWith("---\r\n")) {
            int secondDelim = fileContent.indexOf("\n---", 3);
            if (secondDelim >= 0) {
                int endOfDelimLine = fileContent.indexOf('\n', secondDelim + 4);
                if (endOfDelimLine >= 0) {
                    String fm = fileContent.substring(0, secondDelim);
                    body = fileContent.substring(endOfDelimLine + 1).trim();

                    for (String line : fm.split("\\r?\\n")) {
                        line = line.trim();
                        if (line.startsWith("url:")) {
                            url = extractYamlValue(line.substring(4));
                        } else if (line.startsWith("title:")) {
                            title = extractYamlValue(line.substring(6));
                        } else if (line.startsWith("type:")) {
                            docType = extractYamlValue(line.substring(5));
                        }
                    }
                }
            }
        }

        return new ParsedMarkdown(url, title, docType, body);
    }

    private static String extractYamlValue(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    public EnterpriseIngestionSummary ingestFromMarkdownFiles(String mode) throws IOException {
        long startTime = System.currentTimeMillis();
        String resolvedMode = mode != null && !mode.isBlank() ? mode.trim().toLowerCase(Locale.ROOT) : "s2b";
        boolean isBaseline = "original".equals(resolvedMode) || "baseline".equals(resolvedMode);

        if (properties == null || properties.markdownExportDir() == null || properties.markdownExportDir().isBlank()) {
            logger.warn("markdownExportDir is not set, skipping ingestFromMarkdownFiles");
            return new EnterpriseIngestionSummary(0, 0, 0, 0, System.currentTimeMillis() - startTime, resolvedMode);
        }

        Path baseDir = Path.of(properties.markdownExportDir());
        if (!Files.exists(baseDir)) {
            logger.warn("markdownExportDir " + baseDir + " does not exist, skipping");
            return new EnterpriseIngestionSummary(0, 0, 0, 0, System.currentTimeMillis() - startTime, resolvedMode);
        }

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

        int pagesRead = 0;
        int pdfsRead = 0;

        List<Path> allMdFiles = new ArrayList<>();
        for (String subDirName : List.of("pages", "pdfs")) {
            Path subDir = baseDir.resolve(subDirName);
            if (!Files.exists(subDir)) continue;

            try (Stream<Path> stream = Files.list(subDir)) {
                List<Path> files = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".md"))
                        .sorted(Comparator.comparing(Path::getFileName))
                        .toList();
                allMdFiles.addAll(files);
                if ("pages".equals(subDirName)) {
                    pagesRead = files.size();
                } else {
                    pdfsRead = files.size();
                }
            }
        }

        logger.info("Found " + allMdFiles.size() + " markdown file(s) across pages/ and pdfs/ directories");

        for (Path file : allMdFiles) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            ParsedMarkdown parsed = parseMarkdownFile(content);
            if (parsed.content().isBlank()) continue;

            String docUrl = !parsed.url().isBlank() ? parsed.url() : file.getFileName().toString();

            if (isBaseline) {
                baselineChunks.addAll(chunkBaseline(baselineSplitter, parsed.content(), docUrl, parsed.title(), parsed.docType()));
            } else {
                splitAndLink(parentSplitter, childSplitter, parsed.content(), docUrl, parsed.title(), parsed.docType(), parentChunks, childChunks);
            }
        }

        if (isBaseline) {
            VectorStore targetStore = (vectorStoreFactory != null)
                    ? vectorStoreFactory.getStore("vector_store_enterprise")
                    : defaultVectorStore;
            logger.info("Writing " + baselineChunks.size() + " baseline chunk(s) from files to vector_store_enterprise");
            addInBatches(targetStore, baselineChunks, 100);
            long durationMs = System.currentTimeMillis() - startTime;
            logger.info("Enterprise baseline file ingestion complete: " + baselineChunks.size() + " chunk(s) in " + durationMs + " ms");
            return new EnterpriseIngestionSummary(pagesRead, pdfsRead, 0, baselineChunks.size(), durationMs, "original");
        } else {
            VectorStore largeStore = (vectorStoreFactory != null)
                    ? vectorStoreFactory.getStore("vector_store_enterprise_large")
                    : defaultVectorStore;
            VectorStore smallStore = (vectorStoreFactory != null)
                    ? vectorStoreFactory.getStore("vector_store_enterprise_small")
                    : defaultVectorStore;

            logger.info("Writing " + parentChunks.size() + " parent chunks from files to vector_store_enterprise_large");
            addInBatches(largeStore, parentChunks, 10);

            logger.info("Writing " + childChunks.size() + " child chunks from files to vector_store_enterprise_small");
            addInBatches(smallStore, childChunks, 25);

            long durationMs = System.currentTimeMillis() - startTime;
            logger.info("Enterprise S2B file ingestion complete: " + parentChunks.size() + " parent(s), " + childChunks.size()
                    + " child(ren) in " + durationMs + " ms");
            return new EnterpriseIngestionSummary(pagesRead, pdfsRead, parentChunks.size(), childChunks.size(), durationMs, "s2b");
        }
    }

    public EnterpriseIngestionSummary executePipeline(EnterpriseIngestController.EnterpriseIngestRequest request) throws IOException {
        long startTime = System.currentTimeMillis();
        EnterpriseIngestController.EnterpriseIngestRequest req = request != null
                ? request
                : new EnterpriseIngestController.EnterpriseIngestRequest(null, null, null, null);

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
                return new EnterpriseIngestionSummary(crawlResult.pagesCrawled(), crawlResult.pdfsExtracted(), 0, 0, durationMs, "crawl");
            }
            case "clean" -> {
                int cleanedCount = cleanMarkdownFiles();
                long durationMs = System.currentTimeMillis() - startTime;
                return new EnterpriseIngestionSummary(cleanedCount, 0, 0, 0, durationMs, "clean");
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
                EnterpriseIngestionSummary ingestSummary = ingestFromMarkdownFiles(mode);
                long totalDurationMs = System.currentTimeMillis() - startTime;
                return new EnterpriseIngestionSummary(
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

    private void exportMarkdown(String subDir, String filename, String url, String title, String markdown, String docType) {
        if (properties == null || properties.markdownExportDir() == null || properties.markdownExportDir().isBlank()) {
            return;
        }

        try {
            Path targetDir = Path.of(properties.markdownExportDir(), subDir);
            Files.createDirectories(targetDir);

            String frontmatter = "---\n"
                    + "url: \"" + escapeYaml(url) + "\"\n"
                    + "title: \"" + escapeYaml(title) + "\"\n"
                    + "type: \"" + escapeYaml(docType) + "\"\n"
                    + "---\n\n";

            Path targetFile = targetDir.resolve(filename + ".md");
            Files.writeString(targetFile, frontmatter + markdown, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            logger.warn("Failed to export markdown to " + subDir + "/" + filename + ".md: " + ex.getMessage());
        }
    }

    private static String slugify(String url, String title, int sequenceIndex) {
        String seed = (title != null && !title.isBlank()) ? title : url;
        String slug = seed.toLowerCase(Locale.ROOT)
                .replaceAll("https?://", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        if (slug.length() > 50) {
            slug = slug.substring(0, 50).replaceAll("-+$", "");
        }
        if (slug.isBlank()) {
            slug = "doc";
        }
        return String.format(Locale.ROOT, "%03d-%s", sequenceIndex, slug);
    }

    private static String escapeYaml(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void splitAndLink(
            TokenTextSplitter parentSplitter,
            TokenTextSplitter childSplitter,
            String docText,
            String sourceUrl,
            String title,
            String docType,
            List<Document> outParents,
            List<Document> outChildren) {

        Document fullDoc = new Document(docText);
        List<Document> rawParents = parentSplitter.apply(List.of(fullDoc));

        Map<String, Object> baseMeta = new HashMap<>();
        baseMeta.put("source_url", sourceUrl);
        baseMeta.put("page_title", title);
        if (docType != null) {
            baseMeta.put("doc_type", docType);
        }

        for (int pIdx = 0; pIdx < rawParents.size(); pIdx++) {
            Document pDoc = rawParents.get(pIdx);
            String parentId = UUID.nameUUIDFromBytes((sourceUrl + "#parent#" + pIdx).getBytes(StandardCharsets.UTF_8)).toString();

            Map<String, Object> parentMeta = new HashMap<>(baseMeta);
            parentMeta.put("chunk_type", "parent");
            parentMeta.put("parent_id", parentId);
            parentMeta.put("chunk_index", pIdx);
            parentMeta.put("total_parent_chunks", rawParents.size());

            outParents.add(new Document(parentId, pDoc.getText(), parentMeta));

            List<Document> rawChildren = childSplitter.apply(List.of(pDoc));
            for (int cIdx = 0; cIdx < rawChildren.size(); cIdx++) {
                Document cDoc = rawChildren.get(cIdx);
                String childId = UUID.nameUUIDFromBytes((sourceUrl + "#child#" + parentId + "#" + cIdx).getBytes(StandardCharsets.UTF_8)).toString();

                Map<String, Object> childMeta = new HashMap<>(baseMeta);
                childMeta.put("chunk_type", "child");
                childMeta.put("parent_id", parentId);
                childMeta.put("parent_table", "vector_store_enterprise_large");
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

        Document baseDoc = new Document(text, metadata);
        List<Document> chunks = splitter.apply(List.of(baseDoc));

        List<Document> tagged = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Document c = chunks.get(i);
            String chunkId = UUID.nameUUIDFromBytes((sourceUrl + "#baseline#" + i).getBytes(StandardCharsets.UTF_8)).toString();
            Map<String, Object> meta = new HashMap<>(c.getMetadata());
            meta.put("chunk_type", "baseline");
            meta.put("chunk_index", i);
            meta.put("total_baseline_chunks", chunks.size());
            tagged.add(new Document(chunkId, c.getText(), meta));
        }
        return tagged;
    }

    private void addInBatches(VectorStore store, List<Document> docs, int batchSize) {
        for (int i = 0; i < docs.size(); i += batchSize) {
            int end = Math.min(i + batchSize, docs.size());
            store.add(docs.subList(i, end));
        }
    }
}
