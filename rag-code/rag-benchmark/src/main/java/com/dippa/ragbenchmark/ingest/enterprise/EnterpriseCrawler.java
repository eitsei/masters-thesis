package com.dippa.ragbenchmark.ingest.enterprise;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Breadth-first crawl of pages under {@link EnterpriseIngestionProperties#baseUrl()}, following only
 * internal links whose absolute URL starts with that same prefix, up to {@link EnterpriseIngestionProperties#maxPages()}.
 * PDF links discovered on in-scope pages are collected separately and capped at {@link EnterpriseIngestionProperties#maxPdfs()}.
 */
@Component
public class EnterpriseCrawler {

    private static final Log logger = LogFactory.getLog(EnterpriseCrawler.class);
    private static final int FETCH_TIMEOUT_MILLIS = 10_000;
    private static final int MAX_FETCH_RETRIES = 3;
    private static final long[] FETCH_RETRY_BACKOFFS_MS = {1000L, 2000L, 4000L};

    private final EnterpriseIngestionProperties properties;

    public EnterpriseCrawler(EnterpriseIngestionProperties properties) {
        this.properties = properties;
    }

    public CrawlResult crawl() {
        Set<String> visited = new LinkedHashSet<>();
        Set<String> pdfUrls = new LinkedHashSet<>();
        List<CrawledPage> pages = new ArrayList<>();
        Queue<String> queue = new ArrayDeque<>();
        String rawBase = properties.baseUrl();
        String normalizedBase = (rawBase != null && !rawBase.endsWith("/")) ? rawBase + "/" : (rawBase != null ? rawBase : "");
        queue.add(normalizedBase.isBlank() ? "https://example.com/" : normalizedBase);

        while (!queue.isEmpty() && pages.size() < properties.maxPages() && !Thread.currentThread().isInterrupted()) {
            String url = queue.poll();
            String cleanUrl = cleanUrl(url);
            if (!visited.add(cleanUrl)) {
                continue;
            }

            Document doc;
            try {
                doc = fetchDocumentWithRetry(cleanUrl);
            } finally {
                politeDelay();
            }

            if (doc == null) {
                continue;
            }

            String markdown = EnterpriseHtmlToMarkdownConverter.convert(doc);
            if (!markdown.isBlank()) {
                pages.add(new CrawledPage(cleanUrl, doc.title(), markdown));
            }

            for (Element link : doc.select("a[href]")) {
                String absHref = cleanUrl(link.absUrl("href"));
                if (absHref.isBlank()) {
                    continue;
                }
                if (absHref.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    if (pdfUrls.size() < properties.maxPdfs()) {
                        pdfUrls.add(absHref);
                    }
                } else if (isEligibleContentPage(absHref, normalizedBase) && !visited.contains(absHref)) {
                    queue.add(absHref);
                }
            }
        }

        return new CrawlResult(pages, List.copyOf(pdfUrls));
    }

    private static String cleanUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        int hashIndex = url.indexOf('#');
        String clean = hashIndex >= 0 ? url.substring(0, hashIndex) : url;
        int queryIndex = clean.indexOf('?');
        if (queryIndex >= 0) {
            String query = clean.substring(queryIndex + 1);
            if (query.contains("intcmp") || query.contains("icid") || query.contains("utm_") || query.contains("adobe_mc")) {
                clean = clean.substring(0, queryIndex);
            }
        }
        return clean.trim();
    }

    private static boolean isEligibleContentPage(String url, String basePrefix) {
        if (!url.startsWith(basePrefix)) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains("/kirjaudu") || lower.contains("/login") || lower.contains("/logout")
                || lower.contains("/sv/") || lower.contains("/en/")
                || lower.contains("login.") || lower.contains("auth.")) {
            return false;
        }
        if (lower.endsWith(".zip") || lower.endsWith(".xlsx") || lower.endsWith(".docx")
                || lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".svg")) {
            return false;
        }
        return true;
    }

    private Document fetchDocumentWithRetry(String cleanUrl) {
        for (int attempt = 0; attempt <= MAX_FETCH_RETRIES; attempt++) {
            try {
                return Jsoup.connect(cleanUrl)
                        .userAgent(properties.userAgent())
                        .timeout(FETCH_TIMEOUT_MILLIS)
                        .get();
            } catch (IOException ex) {
                if (attempt < MAX_FETCH_RETRIES) {
                    int retryAttempt = attempt + 1;
                    logger.warn("Retrying page fetch (attempt " + retryAttempt + "/3) for URL " + cleanUrl + ": " + ex.getMessage());
                    try {
                        Thread.sleep(FETCH_RETRY_BACKOFFS_MS[attempt]);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                } else {
                    logger.warn("Skipping page " + cleanUrl + ": " + ex.getMessage());
                }
            }
        }
        return null;
    }

    private void politeDelay() {
        try {
            Thread.sleep(properties.requestDelayMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public record CrawlResult(List<CrawledPage> pages, List<String> pdfUrls) {
    }

    public record CrawledPage(String url, String title, String text) {
    }
}
