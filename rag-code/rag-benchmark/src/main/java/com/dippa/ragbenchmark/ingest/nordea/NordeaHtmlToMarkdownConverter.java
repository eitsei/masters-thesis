package com.dippa.ragbenchmark.ingest.nordea;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Converts crawled HTML documents from Nordea's website into clean, structured Markdown.
 *
 * <p>Performs aggressive boilerplate removal: strips navigation bars, headers, footers,
 * cookie banners, social share links, and repetitive call-to-action (CTA) buttons
 * (e.g. "Lue lisää", "Jätä yhteydenottopyyntö"), while preserving substantive body prose,
 * hierarchical headings, lists, tables, and official contact details.</p>
 */
public final class NordeaHtmlToMarkdownConverter {

    private static final Set<String> NOISE_HEADINGS = Set.of(
            "kirjaudu",
            "muut palvelut",
            "jaa tämä sivu",
            "tuki ja usein kysytyt",
            "palvelumme",
            "tietoa nordeasta"
    );

    private static final List<String> CTA_PATTERNS = List.of(
            "lue lisää",
            "jätä yhteydenottopyyntö",
            "pyydä private banking",
            "kirjaudu verkkopankkiin",
            "kirjaudu",
            "tilaa uutiskirje",
            "lue artikkeli"
    );

    private NordeaHtmlToMarkdownConverter() {
    }

    /**
     * Converts a Jsoup {@link Document} to clean Markdown text.
     *
     * @param doc the raw HTML document
     * @return cleaned Markdown representation of the main content
     */
    public static String convert(Document doc) {
        if (doc == null) {
            return "";
        }

        Document copy = doc.clone();

        // 1. Remove non-content and structural chrome tags across entire document
        copy.select("nav, header, footer, script, style, noscript, form, iframe, svg, button").remove();
        copy.select("[class*=cookie], [class*=consent], [id*=cookie], [id*=consent]").remove();
        copy.select("[class*=modal], [id*=modal], [class*=dialog]").remove();
        copy.select("[class*=social-share], [class*=share-buttons], [class*=share-bar]").remove();
        copy.select("[class*=quick-links], [class*=breadcrumb]").remove();

        // 2. Locate main content root
        Element main = copy.selectFirst("main");
        if (main == null) {
            main = copy.selectFirst("article");
        }
        if (main == null) {
            main = copy.body();
        }
        if (main == null) {
            return "";
        }

        // 3. Extract structured markdown elements in document order
        StringBuilder md = new StringBuilder();
        Set<Element> processedElements = new HashSet<>();

        Elements contentElements = main.select("h1, h2, h3, h4, h5, h6, p, ul, ol, table, blockquote");

        for (Element el : contentElements) {
            if (processedElements.contains(el)) {
                continue;
            }

            // Skip elements whose parents are already being formatted as container (e.g. p inside li)
            if (isDescendantOfProcessedContainer(el, processedElements)) {
                continue;
            }

            String tagName = el.tagName().toLowerCase(Locale.ROOT);
            String rawText = normalizeWhitespace(el.text());
            if (rawText.isBlank()) {
                continue;
            }

            switch (tagName) {
                case "h1" -> {
                    if (!isNoiseHeading(rawText)) {
                        md.append("# ").append(rawText).append("\n\n");
                        processedElements.add(el);
                    }
                }
                case "h2" -> {
                    if (!isNoiseHeading(rawText)) {
                        md.append("## ").append(rawText).append("\n\n");
                        processedElements.add(el);
                    }
                }
                case "h3" -> {
                    if (!isNoiseHeading(rawText)) {
                        md.append("### ").append(rawText).append("\n\n");
                        processedElements.add(el);
                    }
                }
                case "h4", "h5", "h6" -> {
                    if (!isNoiseHeading(rawText)) {
                        md.append("#### ").append(rawText).append("\n\n");
                        processedElements.add(el);
                    }
                }
                case "p" -> {
                    if (!isCtaElement(el, rawText)) {
                        md.append(rawText).append("\n\n");
                        processedElements.add(el);
                    }
                }
                case "ul", "ol" -> {
                    formatList(el, md, processedElements);
                }
                case "table" -> {
                    formatTable(el, md, processedElements);
                }
                case "blockquote" -> {
                    md.append("> ").append(rawText).append("\n\n");
                    processedElements.add(el);
                }
                default -> {}
            }
        }

        return md.toString().trim();
    }

    private static boolean isDescendantOfProcessedContainer(Element el, Set<Element> processedElements) {
        Element parent = el.parent();
        while (parent != null) {
            if (processedElements.contains(parent)) {
                return true;
            }
            if ("li".equals(parent.tagName()) || "table".equals(parent.tagName())) {
                return true;
            }
            parent = parent.parent();
        }
        return false;
    }

    private static boolean isNoiseHeading(String heading) {
        String lower = heading.toLowerCase(Locale.ROOT).trim();
        for (String noise : NOISE_HEADINGS) {
            if (lower.equals(noise) || lower.startsWith(noise + " ") || lower.endsWith(" " + noise)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCtaElement(Element el, String text) {
        String lower = text.toLowerCase(Locale.ROOT).trim();

        // Check if paragraph is wrapped in an anchor or has CTA class
        boolean insideLink = el.parents().stream().anyMatch(p -> "a".equalsIgnoreCase(p.tagName()));
        boolean hasCtaClass = el.className().toLowerCase(Locale.ROOT).contains("text-icon")
                || el.className().toLowerCase(Locale.ROOT).contains("cta")
                || el.className().toLowerCase(Locale.ROOT).contains("btn");

        // Short action texts
        if (text.length() < 60) {
            for (String cta : CTA_PATTERNS) {
                if (lower.contains(cta)) {
                    return true;
                }
            }
        }

        // Action links with short text
        if (insideLink && text.length() < 40 && (lower.startsWith("lue ") || lower.startsWith("tutustu "))) {
            return true;
        }

        return false;
    }

    private static void formatList(Element listEl, StringBuilder md, Set<Element> processed) {
        Elements items = listEl.children().select("li");
        if (items.isEmpty()) {
            items = listEl.select("li");
        }

        for (Element item : items) {
            String itemText = normalizeWhitespace(item.text());
            if (!itemText.isBlank()) {
                md.append("- ").append(itemText).append("\n");
            }
            processed.add(item);
        }
        md.append("\n");
        processed.add(listEl);
    }

    private static void formatTable(Element tableEl, StringBuilder md, Set<Element> processed) {
        Elements rows = tableEl.select("tr");
        if (rows.isEmpty()) {
            processed.add(tableEl);
            return;
        }

        boolean headerRendered = false;
        for (int r = 0; r < rows.size(); r++) {
            Element row = rows.get(r);
            Elements cells = row.select("th, td");
            if (cells.isEmpty()) continue;

            StringBuilder rowBuf = new StringBuilder("|");
            for (Element cell : cells) {
                rowBuf.append(" ").append(normalizeWhitespace(cell.text())).append(" |");
            }
            md.append(rowBuf).append("\n");

            if (!headerRendered && (row.select("th").size() > 0 || r == 0)) {
                StringBuilder sepBuf = new StringBuilder("|");
                for (int i = 0; i < cells.size(); i++) {
                    sepBuf.append(" --- |");
                }
                md.append(sepBuf).append("\n");
                headerRendered = true;
            }
        }
        md.append("\n");
        processed.add(tableEl);
    }

    private static String normalizeWhitespace(String text) {
        if (text == null) {
            return "";
        }
        // Replace non-breaking spaces (\u00A0) and collapse consecutive whitespace
        return text.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
