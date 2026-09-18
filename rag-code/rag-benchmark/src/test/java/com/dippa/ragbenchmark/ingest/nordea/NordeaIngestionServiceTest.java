package com.dippa.ragbenchmark.ingest.nordea;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NordeaIngestionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void ingestOriginalExportsMarkdownAndChunksDocuments() throws IOException {
        NordeaCrawler crawler = mock(NordeaCrawler.class);
        NordeaPdfExtractor pdfExtractor = mock(NordeaPdfExtractor.class);
        VectorStore vectorStore = mock(VectorStore.class);

        NordeaIngestionProperties properties = new NordeaIngestionProperties(
                true,
                "https://www.nordea.fi/private-banking/",
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

        when(crawler.crawl()).thenReturn(new NordeaCrawler.CrawlResult(
                List.of(new NordeaCrawler.CrawledPage("https://www.nordea.fi/pb/page1", "Page 1 Title", sampleHtmlMarkdown)),
                List.of("https://www.nordea.fi/pb/doc1.pdf")
        ));

        when(pdfExtractor.extract("https://www.nordea.fi/pb/doc1.pdf")).thenReturn(Optional.of(
                new NordeaPdfExtractor.CrawledPdf("https://www.nordea.fi/pb/doc1.pdf", "Doc 1 Title", samplePdfMarkdown)
        ));

        NordeaIngestionService service = new NordeaIngestionService(
                crawler,
                pdfExtractor,
                vectorStore,
                null,
                null,
                properties
        );

        NordeaIngestionService.NordeaIngestionSummary summary = service.ingestOriginal();

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
        assertThat(pageContent).contains("url: \"https://www.nordea.fi/pb/page1\"");
        assertThat(pageContent).contains("type: \"html\"");
        assertThat(pageContent).contains("# Private Banking lyhyesti");

        String pdfContent = Files.readString(exportedPdfs.getFirst());
        assertThat(pdfContent).contains("url: \"https://www.nordea.fi/pb/doc1.pdf\"");
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
        NordeaCrawler crawler = mock(NordeaCrawler.class);
        NordeaPdfExtractor pdfExtractor = mock(NordeaPdfExtractor.class);
        VectorStore vectorStore = mock(VectorStore.class);

        NordeaIngestionProperties properties = new NordeaIngestionProperties(
                true,
                "https://www.nordea.fi/private-banking/",
                10,
                5,
                10_000_000,
                0,
                "TestCrawler/1.0",
                tempDir.toString()
        );

        String sampleHtmlMarkdown = "# Otsikko\n\n" + "Tämä on pitkä tekstikappale varallisuudenhoidosta. ".repeat(30);

        when(crawler.crawl()).thenReturn(new NordeaCrawler.CrawlResult(
                List.of(new NordeaCrawler.CrawledPage("https://www.nordea.fi/pb/s2b", "S2B Page", sampleHtmlMarkdown)),
                List.of()
        ));

        NordeaIngestionService service = new NordeaIngestionService(
                crawler,
                pdfExtractor,
                vectorStore,
                null,
                null,
                properties
        );

        NordeaIngestionService.NordeaIngestionSummary summary = service.ingestSmallToBig();

        assertThat(summary.pagesCrawled()).isEqualTo(1);
        assertThat(summary.parentChunksWritten()).isGreaterThan(0);
        assertThat(summary.childChunksWritten()).isGreaterThan(0);

        // Vector store add should be called once for parent chunks and once for child chunks
        verify(vectorStore, times(2)).add(anyList());
    }
}
