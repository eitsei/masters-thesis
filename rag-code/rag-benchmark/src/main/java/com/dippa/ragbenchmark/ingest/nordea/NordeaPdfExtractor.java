package com.dippa.ragbenchmark.ingest.nordea;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Downloads a single PDF fully into memory (never written to disk — see {@link
 * NordeaIngestionRunner} for where the *extracted text* is staged instead) and extracts its text
 * via Apache PDFBox. Oversized downloads ({@code >} {@link NordeaIngestionProperties#maxPdfSizeBytes()})
 * and fetch/parse failures are skipped and logged rather than aborting the whole ingestion run.
 */
@Component
public class NordeaPdfExtractor {

    private static final Log logger = LogFactory.getLog(NordeaPdfExtractor.class);

    private final NordeaIngestionProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public NordeaPdfExtractor(NordeaIngestionProperties properties) {
        this.properties = properties;
    }

    public Optional<CrawledPdf> extract(String url) {
        try {
            // Some real-world links (e.g. Nordea's own "Yleiset ehdot.pdf") come back from
            // jsoup's absUrl() with unencoded spaces, which URI.create() rejects outright.
            // Percent-encoding just the space is enough to fix the observed case without
            // reimplementing a full URI sanitizer for a link that's otherwise well-formed.
            URI requestUri = URI.create(url.replace(" ", "%20"));
            HttpRequest request = HttpRequest.newBuilder(requestUri)
                    .header("User-Agent", properties.userAgent())
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                logger.warn("Skipping PDF " + url + ": HTTP " + response.statusCode());
                return Optional.empty();
            }

            byte[] body = response.body();
            if (body.length > properties.maxPdfSizeBytes()) {
                logger.warn("Skipping PDF " + url + ": " + body.length + " bytes exceeds cap of "
                        + properties.maxPdfSizeBytes());
                return Optional.empty();
            }

            try (PDDocument document = Loader.loadPDF(body)) {
                String title = Optional.ofNullable(document.getDocumentInformation().getTitle())
                        .map(String::strip)
                        .filter(t -> !t.isEmpty())
                        .orElseGet(() -> filenameFrom(url));
                String markdown = NordeaPdfToMarkdownConverter.convert(document, title);
                return Optional.of(new CrawledPdf(url, title, markdown));
            }
        } catch (IOException ex) {
            logger.warn("Skipping PDF " + url + ": " + ex.getMessage());
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IllegalArgumentException ex) {
            // Covers URI.create() rejecting a malformed URL that the space-encoding fix above
            // didn't happen to cover. One bad link found in the wild must not abort the whole
            // crawl -- skip it like any other per-item fetch failure.
            logger.warn("Skipping PDF " + url + ": malformed URL (" + ex.getMessage() + ")");
            return Optional.empty();
        } catch (RuntimeException ex) {
            // Last-resort boundary: a single malformed/corrupt PDF from an untrusted external
            // site (e.g. PDFBox choking on a broken stream) must not abort the whole crawl.
            logger.warn("Skipping PDF " + url + ": " + ex);
            return Optional.empty();
        } finally {
            politeDelay();
        }
    }

    private static String filenameFrom(String url) {
        String path = URI.create(url.replace(" ", "%20")).getPath();
        int slash = path.lastIndexOf('/');
        return slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : path;
    }

    private void politeDelay() {
        try {
            Thread.sleep(properties.requestDelayMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public record CrawledPdf(String url, String title, String text) {
    }
}
