package com.dippa.ragbenchmark.ingest.enterprise;

import com.dippa.ragbenchmark.api.EnterpriseIngestController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnterpriseIngestionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void ingestOriginalExportsMarkdownAndChunksDocuments() throws IOException {
        EnterpriseCrawler crawler = mock(EnterpriseCrawler.class);
        EnterprisePdfExtractor pdfExtractor = mock(EnterprisePdfExtractor.class);
        VectorStore vectorStore = mock(VectorStore.class);

        EnterpriseIngestionProperties properties = new EnterpriseIngestionProperties(
                true,
                "https://example.com/private-banking/",
                10,
                5,
                10_000_000,
                0,
                "TestCrawler/1.0",
                tempDir.toString()
        );

        String sampleHtmlMarkdown = """
                # Private Banking lyhyesti
                
                Jokaisella vaurauden polulla on oma tarinansa.
                
                ## Henkilökohtainen palvelu
                
                Sijoitusjohtajasi auttaa sinua kaikissa talouden tilanteissa.
                """;

        String samplePdfMarkdown = """
                # Hinnasto ja ehdot
                
                ## 1. Palkkiot
                
                Salkunhoidon peruspalkkio on 0.50 prosenttia.
                """;

        when(crawler.crawl()).thenReturn(new EnterpriseCrawler.CrawlResult(
                List.of(new EnterpriseCrawler.CrawledPage("https://example.com/pb/page1", "Page 1 Title", sampleHtmlMarkdown)),
                List.of("https://example.com/pb/doc1.pdf")
        ));

        when(pdfExtractor.extract("https://example.com/pb/doc1.pdf")).thenReturn(Optional.of(
                new EnterprisePdfExtractor.CrawledPdf("https://example.com/pb/doc1.pdf", "Doc 1 Title", samplePdfMarkdown)
        ));

        EnterpriseIngestionService service = new EnterpriseIngestionService(
                crawler,
                pdfExtractor,
                vectorStore,
                null,
                null,
                properties
        );

        EnterpriseIngestionService.EnterpriseIngestionSummary summary = service.ingestOriginal();

        assertThat(summary.pagesCrawled()).isEqualTo(1);
        assertThat(summary.pdfsExtracted()).isEqualTo(1);
        assertThat(summary.childChunksWritten()).isGreaterThan(0);

        // Verify Markdown files exported to tempDir
        Path pagesDir = tempDir.resolve("pages");
        Path pdfsDir = tempDir.resolve("pdfs");
        assertThat(Files.isDirectory(pagesDir)).isTrue();
        assertThat(Files.isDirectory(pdfsDir)).isTrue();

        List<Path> exportedPages = Files.list(pagesDir).toList();
        List<Path> exportedPdfs = Files.list(pdfsDir).toList();
        assertThat(exportedPages).hasSize(1);
        assertThat(exportedPdfs).hasSize(1);

        String pageContent = Files.readString(exportedPages.getFirst());
        assertThat(pageContent).contains("url: \"https://example.com/pb/page1\"");
        assertThat(pageContent).contains("type: \"html\"");
        assertThat(pageContent).contains("# Private Banking lyhyesti");

        String pdfContent = Files.readString(exportedPdfs.getFirst());
        assertThat(pdfContent).contains("url: \"https://example.com/pb/doc1.pdf\"");
        assertThat(pdfContent).contains("type: \"pdf\"");
        assertThat(pdfContent).contains("# Hinnasto ja ehdot");

        // Verify VectorStore received chunks
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());

        List<Document> addedDocs = captor.getValue();
        assertThat(addedDocs).isNotEmpty();
        Document firstDoc = addedDocs.getFirst();
        assertThat(firstDoc.getMetadata()).containsKey("doc_type");
        assertThat(firstDoc.getMetadata().get("chunk_type")).isEqualTo("baseline");
    }

    @Test
    void ingestSmallToBigProducesLinkedParentAndChildChunks() throws IOException {
        EnterpriseCrawler crawler = mock(EnterpriseCrawler.class);
        EnterprisePdfExtractor pdfExtractor = mock(EnterprisePdfExtractor.class);
        VectorStore vectorStore = mock(VectorStore.class);

        EnterpriseIngestionProperties properties = new EnterpriseIngestionProperties(
                true,
                "https://example.com/private-banking/",
                10,
                5,
                10_000_000,
                0,
                "TestCrawler/1.0",
                tempDir.toString()
        );

        String sampleHtmlMarkdown = "# Otsikko\n\n" + "Tämä on pitkä tekstikappale varallisuudenhoidosta. ".repeat(30);

        when(crawler.crawl()).thenReturn(new EnterpriseCrawler.CrawlResult(
                List.of(new EnterpriseCrawler.CrawledPage("https://example.com/pb/s2b", "S2B Page", sampleHtmlMarkdown)),
                List.of()
        ));

        EnterpriseIngestionService service = new EnterpriseIngestionService(
                crawler,
                pdfExtractor,
                vectorStore,
                null,
                null,
                properties
        );

        EnterpriseIngestionService.EnterpriseIngestionSummary summary = service.ingestSmallToBig();

        assertThat(summary.pagesCrawled()).isEqualTo(1);
        assertThat(summary.parentChunksWritten()).isGreaterThan(0);
        assertThat(summary.childChunksWritten()).isGreaterThan(0);

        // Vector store add should be called once for parent chunks and once for child chunks
        verify(vectorStore, times(2)).add(anyList());
    }

    @Test
    void executePipelineCleanCleansNoiseAndPreservesFrontmatter() throws IOException {
        Path pagesDir = tempDir.resolve("pages");
        Files.createDirectories(pagesDir);
        Path testFile = pagesDir.resolve("001-test.md");

        String rawMarkdown = """
                ---
                url: "https://example.com/fi/palvelut/laina.html"
                title: "Asuntolainat"
                type: "html"
                crawled_at: "2026-09-25T05:59:02Z"
                ---

                # Asuntolainat (Avautuu uuteen ikkunaan)

                Hups! Jos haluat nähdä tällaista sisältöä, hyväksy ensin markkinointievästeet

                Hae asuntolainaa helposti ja nopeasti verkossa.

                ## Apua asiointiin

                ### Henkilöasiakkaat

                - Kysy heti chatissa
                - Usein kysytyt
                - Soita meille 0200 0000
                - Enterprise Portal
                """;
        Files.writeString(testFile, rawMarkdown);

        EnterpriseIngestionProperties properties = new EnterpriseIngestionProperties(
                true, "https://example.com/", 10, 5, 10_000_000, 0, "TestCrawler/1.0", tempDir.toString()
        );

        EnterpriseIngestionService service = new EnterpriseIngestionService(
                null, null, null, null, null, properties
        );

        EnterpriseIngestController.EnterpriseIngestRequest request = new EnterpriseIngestController.EnterpriseIngestRequest(
                "s2b", "clean", false, false
        );

        EnterpriseIngestionService.EnterpriseIngestionSummary summary = service.executePipeline(request);

        assertThat(summary.pagesCrawled()).isEqualTo(1);
        assertThat(summary.mode()).isEqualTo("clean");

        String cleaned = Files.readString(testFile);
        // Frontmatter preserved
        assertThat(cleaned).contains("url: \"https://example.com/fi/palvelut/laina.html\"");
        assertThat(cleaned).contains("title: \"Asuntolainat\"");
        assertThat(cleaned).contains("type: \"html\"");

        // Substantive content preserved
        assertThat(cleaned).contains("# Asuntolainat");
        assertThat(cleaned).contains("Hae asuntolainaa helposti ja nopeasti verkossa.");

        // Noise removed
        assertThat(cleaned).doesNotContainIgnoringCase("Avautuu uuteen ikkunaan");
        assertThat(cleaned).doesNotContain("Hups! Jos haluat nähdä");
        assertThat(cleaned).doesNotContain("markkinointievästeet");
        assertThat(cleaned).doesNotContain("Apua asiointiin");
        assertThat(cleaned).doesNotContain("Henkilöasiakkaat");
        assertThat(cleaned).doesNotContain("Kysy heti chatissa");
    }

    @Test
    void executePipelineIngestReadsMarkdownFilesAndChunksToVectorStore() throws IOException {
        Path pagesDir = tempDir.resolve("pages");
        Path pdfsDir = tempDir.resolve("pdfs");
        Files.createDirectories(pagesDir);
        Files.createDirectories(pdfsDir);

        Path pageFile = pagesDir.resolve("001-page.md");
        String pageContent = """
                ---
                url: "https://example.com/pb/page1"
                title: "PB Palvelut"
                type: "html"
                ---

                # Varallisuudenhoito

                Tämä on kattava opas henkilökohtaiseen sijoitusneuvontaan ja varallisuuden kartuttamiseen.
                """;
        Files.writeString(pageFile, pageContent);

        Path pdfFile = pdfsDir.resolve("002-doc.md");
        String pdfContent = """
                ---
                url: "https://example.com/pb/ehdot.pdf"
                title: "Palveluehdot"
                type: "pdf"
                ---

                # Ehdot ja hinnasto

                Tässä kuvataan salkunhoidon palkkiot ja sijoituspalvelun yleiset sopimusehdot.
                """;
        Files.writeString(pdfFile, pdfContent);

        VectorStore vectorStore = mock(VectorStore.class);
        EnterpriseIngestionProperties properties = new EnterpriseIngestionProperties(
                true, "https://example.com/", 10, 5, 10_000_000, 0, "TestCrawler/1.0", tempDir.toString()
        );

        EnterpriseIngestionService service = new EnterpriseIngestionService(
                null, null, vectorStore, null, null, properties
        );

        // Test S2B ingest
        EnterpriseIngestController.EnterpriseIngestRequest request = new EnterpriseIngestController.EnterpriseIngestRequest(
                "s2b", "ingest", false, false
        );

        EnterpriseIngestionService.EnterpriseIngestionSummary summary = service.executePipeline(request);

        assertThat(summary.pagesCrawled()).isEqualTo(1);
        assertThat(summary.pdfsExtracted()).isEqualTo(1);
        assertThat(summary.parentChunksWritten()).isGreaterThan(0);
        assertThat(summary.childChunksWritten()).isGreaterThan(0);
        assertThat(summary.mode()).isEqualTo("s2b");

        // Verify vectorStore.add was called for parent and child chunks
        verify(vectorStore, times(2)).add(anyList());
    }

    @Test
    void executePipelineIngestBaselineMode() throws IOException {
        Path pagesDir = tempDir.resolve("pages");
        Files.createDirectories(pagesDir);
        Path pageFile = pagesDir.resolve("001-baseline.md");
        String pageContent = """
                ---
                url: "https://example.com/pb/page1"
                title: "PB Baseline"
                type: "html"
                ---

                # Baseline Otsikko

                Tämä on baseline-testidokumentti varallisuudenhoidosta ja pankkipalveluista.
                """;
        Files.writeString(pageFile, pageContent);

        VectorStore vectorStore = mock(VectorStore.class);
        EnterpriseIngestionProperties properties = new EnterpriseIngestionProperties(
                true, "https://example.com/", 10, 5, 10_000_000, 0, "TestCrawler/1.0", tempDir.toString()
        );

        EnterpriseIngestionService service = new EnterpriseIngestionService(
                null, null, vectorStore, null, null, properties
        );

        EnterpriseIngestController.EnterpriseIngestRequest request = new EnterpriseIngestController.EnterpriseIngestRequest(
                "original", "ingest", false, false
        );

        EnterpriseIngestionService.EnterpriseIngestionSummary summary = service.executePipeline(request);

        assertThat(summary.pagesCrawled()).isEqualTo(1);
        assertThat(summary.childChunksWritten()).isGreaterThan(0);
        assertThat(summary.parentChunksWritten()).isEqualTo(0);
        assertThat(summary.mode()).isEqualTo("original");

        verify(vectorStore, times(1)).add(anyList());
    }

    @Test
    void clearFilesDeletesMdAndBakFilesInPagesAndPdfs() throws IOException {
        Path pagesDir = tempDir.resolve("pages");
        Path pdfsDir = tempDir.resolve("pdfs");
        Files.createDirectories(pagesDir);
        Files.createDirectories(pdfsDir);

        Path pageMd = Files.writeString(pagesDir.resolve("page1.md"), "content");
        Path pageBak = Files.writeString(pagesDir.resolve("page1.md.bak"), "content");
        Path pdfMd = Files.writeString(pdfsDir.resolve("doc1.md"), "content");
        Path keepOther = Files.writeString(pagesDir.resolve("keep.txt"), "keep me");

        EnterpriseIngestionProperties properties = new EnterpriseIngestionProperties(
                true, "https://example.com/", 10, 5, 10_000_000, 0, "TestCrawler/1.0", tempDir.toString()
        );

        EnterpriseIngestionService service = new EnterpriseIngestionService(
                null, null, null, null, null, properties
        );

        int deleted = service.clearFiles();

        assertThat(deleted).isEqualTo(3);
        assertThat(Files.exists(pageMd)).isFalse();
        assertThat(Files.exists(pageBak)).isFalse();
        assertThat(Files.exists(pdfMd)).isFalse();
        assertThat(Files.exists(keepOther)).isTrue();
    }

    @Test
    void clearDatabaseTruncatesTargetTablesPerMode() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EnterpriseIngestionProperties properties = new EnterpriseIngestionProperties(
                true, "https://example.com/", 10, 5, 10_000_000, 0, "TestCrawler/1.0", tempDir.toString()
        );

        EnterpriseIngestionService service = new EnterpriseIngestionService(
                null, null, null, jdbcTemplate, properties
        );

        // s2b mode truncates large and small tables
        int s2bCleared = service.clearDatabase("s2b");
        assertThat(s2bCleared).isEqualTo(2);
        verify(jdbcTemplate).execute("TRUNCATE TABLE vector_store_enterprise_large");
        verify(jdbcTemplate).execute("TRUNCATE TABLE vector_store_enterprise_small");

        // original mode truncates baseline table
        int origCleared = service.clearDatabase("original");
        assertThat(origCleared).isEqualTo(1);
        verify(jdbcTemplate).execute("TRUNCATE TABLE vector_store_enterprise");

        // all mode truncates all 3 tables
        int allCleared = service.clearDatabase("all");
        assertThat(allCleared).isEqualTo(3);

        // null jdbcTemplate safely returns 0
        EnterpriseIngestionService noDbService = new EnterpriseIngestionService(
                null, null, null, (JdbcTemplate) null, properties
        );
        assertThat(noDbService.clearDatabase("all")).isEqualTo(0);
    }

    @Test
    void executePipelineWithClearFlagsCleansResources() throws IOException {
        Path pagesDir = tempDir.resolve("pages");
        Files.createDirectories(pagesDir);
        Path pageMd = Files.writeString(pagesDir.resolve("001.md"), "test");

        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        VectorStore vectorStore = mock(VectorStore.class);
        EnterpriseCrawler crawler = mock(EnterpriseCrawler.class);
        when(crawler.crawl()).thenReturn(new EnterpriseCrawler.CrawlResult(List.of(), List.of()));

        EnterpriseIngestionProperties properties = new EnterpriseIngestionProperties(
                true, "https://example.com/", 10, 5, 10_000_000, 0, "TestCrawler/1.0", tempDir.toString()
        );

        EnterpriseIngestionService service = new EnterpriseIngestionService(
                crawler, null, vectorStore, jdbcTemplate, properties
        );

        EnterpriseIngestController.EnterpriseIngestRequest request = new EnterpriseIngestController.EnterpriseIngestRequest(
                "s2b", "crawl", true, true
        );

        EnterpriseIngestionService.EnterpriseIngestionSummary summary = service.executePipeline(request);

        assertThat(summary.mode()).isEqualTo("crawl");
        assertThat(Files.exists(pageMd)).isFalse();
        verify(jdbcTemplate).execute("TRUNCATE TABLE vector_store_enterprise_large");
        verify(jdbcTemplate).execute("TRUNCATE TABLE vector_store_enterprise_small");
    }
}
