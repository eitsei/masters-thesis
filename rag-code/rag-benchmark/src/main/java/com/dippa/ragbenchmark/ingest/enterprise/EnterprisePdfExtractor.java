package com.dippa.ragbenchmark.ingest.enterprise;

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
import org.springframework.stereotype.Component;

/**
 * Downloads a single PDF fully into memory (never written to disk — see {@link
 * EnterpriseIngestionRunner} for where the *extracted text* is staged instead) and extracts its text
 * via Apache PDFBox. Oversized downloads ({@code >} {@link EnterpriseIngestionProperties#maxPdfSizeBytes()})
 * and fetch/parse failures are skipped and logged rather than aborting the whole ingestion run.
 */
@Component
public class EnterprisePdfExtractor {

    private static final Log logger = LogFactory.getLog(EnterprisePdfExtractor.class);
    private static final int MAX_DOWNLOAD_RETRIES = 3;
    private static final long[] DOWNLOAD_RETRY_BACKOFFS_MS = {1000L, 2000L, 4000L};

    private final EnterpriseIngestionProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public EnterprisePdfExtractor(EnterpriseIngestionProperties properties) {
        this.properties = properties;
    }

    public Optional<CrawledPdf> extract(String url) {
        try {
            // Some real-world links (e.g. "General terms.pdf") come back from
            // jsoup's absUrl() with unencoded spaces, which URI.create() rejects outright.
            // Percent-encoding just the space is enough to fix the observed case without
            // reimplementing a full URI sanitizer for a link that's otherwise well-formed.
            URI requestUri = URI.create(url.replace(" ", "%20"));
            HttpRequest request = HttpRequest.newBuilder(requestUri)
                    .header("User-Agent", properties.userAgent())
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = null;
            for (int attempt = 0; attempt <= MAX_DOWNLOAD_RETRIES; attempt++) {
                try {
                    response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                    if (response.statusCode() >= 500 && response.statusCode() < 600) {
                        if (attempt < MAX_DOWNLOAD_RETRIES) {
                            int retryAttempt = attempt + 1;
                            logger.warn("Retrying PDF download (attempt " + retryAttempt + "/3) for URL " + url
                                    + ": HTTP " + response.statusCode());
                            Thread.sleep(DOWNLOAD_RETRY_BACKOFFS_MS[attempt]);
                            continue;
                        }
                    }
                    break;
                } catch (IOException ex) {
                    if (attempt < MAX_DOWNLOAD_RETRIES) {
                        int retryAttempt = attempt + 1;
                        logger.warn("Retrying PDF download (attempt " + retryAttempt + "/3) for URL " + url
                                + ": " + ex.getMessage());
                        Thread.sleep(DOWNLOAD_RETRY_BACKOFFS_MS[attempt]);
                    } else {
                        throw ex;
                    }
                }
            }

            if (response == null || response.statusCode() / 100 != 2) {
                int status = response != null ? response.statusCode() : -1;
                logger.warn("Skipping PDF " + url + ": HTTP " + status);
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
                String markdown = EnterprisePdfToMarkdownConverter.convert(document, title);
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
