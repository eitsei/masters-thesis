package com.dippa.ragbenchmark.ingest.nordea;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.LinkedHashSet;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Breadth-first crawl of pages under {@link NordeaIngestionProperties#baseUrl()}, following only
 * internal links whose absolute URL starts with that same prefix (never wanders into other
 * sections of nordea.fi), up to {@link NordeaIngestionProperties#maxPages()}. PDF links
 * discovered on in-scope pages are collected separately (they may be hosted on a different
 * path/host, e.g. an assets CDN) and capped at {@link NordeaIngestionProperties#maxPdfs()}.
 */
@Component
public class NordeaCrawler {

    private static final Log logger = LogFactory.getLog(NordeaCrawler.class);
    private static final int FETCH_TIMEOUT_MILLIS = 10_000;

    private final NordeaIngestionProperties properties;

    public NordeaCrawler(NordeaIngestionProperties properties) {
        this.properties = properties;
    }

    public CrawlResult crawl() {
        Set<String> visited = new LinkedHashSet<>();
        Set<String> pdfUrls = new LinkedHashSet<>();
        List<CrawledPage> pages = new ArrayList<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(properties.baseUrl());

        while (!queue.isEmpty() && pages.size() < properties.maxPages()) {
            String url = queue.poll();
            if (!visited.add(url)) {
                continue;
            }

            Document doc;
            try {
                doc = Jsoup.connect(url)
                        .userAgent(properties.userAgent())
                        .timeout(FETCH_TIMEOUT_MILLIS)
                        .get();
            } catch (IOException ex) {
                logger.warn("Skipping page " + url + ": " + ex.getMessage());
                continue;
            } finally {
                politeDelay();
            }

            pages.add(new CrawledPage(url, doc.title(), extractMainText(doc)));

            for (Element link : doc.select("a[href]")) {
                String absHref = stripFragment(link.absUrl("href"));
                if (absHref.isBlank()) {
                    continue;
                }
                if (absHref.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    if (pdfUrls.size() < properties.maxPdfs()) {
                        pdfUrls.add(absHref);
                    }
                } else if (absHref.startsWith(properties.baseUrl()) && !visited.contains(absHref)) {
                    queue.add(absHref);
                }
            }
        }

        return new CrawlResult(pages, List.copyOf(pdfUrls));
    }

    /**
     * Best-effort boilerplate stripping: drops nav/header/footer/script/style and common
     * cookie-consent-banner elements, then prefers a {@code <main>} element if the page has one.
     * Nordea's exact DOM structure wasn't available to verify ahead of time, so this heuristic
     * may need tuning if a spot-check of ingested chunks shows leftover chrome.
     */
    private static String extractMainText(Document doc) {
        Document copy = doc.clone();
        copy.select("nav, header, footer, script, style, noscript, form, iframe").remove();
        copy.select("[class*=cookie], [class*=consent], [id*=cookie], [id*=consent]").remove();
        Element main = copy.selectFirst("main");
        Element contentRoot = main != null ? main : copy.body();
        return contentRoot == null ? "" : contentRoot.text();
    }

    private static String stripFragment(String url) {
        int hashIndex = url.indexOf('#');
        return hashIndex >= 0 ? url.substring(0, hashIndex) : url;
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
