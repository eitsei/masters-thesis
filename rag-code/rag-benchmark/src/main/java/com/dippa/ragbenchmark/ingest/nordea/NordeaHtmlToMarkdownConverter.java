package com.dippa.ragbenchmark.ingest.nordea;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
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

    private static final Pattern AVAUTUU_IKKUNAAN_PATTERN = Pattern.compile(
            "(?i)\\s*[,\\-–—]?\\s*\\(?\\s*avautuu uuteen ikkunaan\\s*\\)?"
    );
    private static final Pattern AVAUTUU_RAW_PATTERN = Pattern.compile(
            "(?i)avautuu uuteen ikkunaan"
    );
    private static final Pattern HUPS_LINE_PATTERN = Pattern.compile(
            "(?m)^.*Hups! Jos haluat nähdä tällaista sisältöä.*$\\R?"
    );
    private static final Pattern STANDARD_HELP_BLOCK_PATTERN = Pattern.compile(
            "##\\s*Apua asiointiin\\s*\\R+###\\s*Henkilöasiakkaat\\s*\\R+-\\s*Kysy heti chatissa\\s*\\R+-\\s*Usein kysytyt\\s*\\R+-\\s*Soita meille.*?\\R+-\\s*Nordea Omaposti[^\r\n]*",
            Pattern.CASE_INSENSITIVE
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
        copy.select("span.sr-only, span[class*=screen-reader], span[class*=visually-hidden]").remove();

        // Remove cookie/marketing placeholder elements containing 'markkinointievästeet' or 'Hups! Jos haluat nähdä tällaista sisältöä'
        for (Element el : copy.select("*:containsOwn(markkinointievästeet), *:containsOwn(Hups! Jos haluat nähdä tällaista sisältöä)")) {
            if (el.parent() == null) {
                continue;
            }
            Element block = el;
            while (block.parent() != null
                    && !block.parent().tagName().equalsIgnoreCase("main")
                    && !block.parent().tagName().equalsIgnoreCase("body")
                    && !block.parent().tagName().equalsIgnoreCase("article")
                    && block.parent().text().length() < 300) {
                block = block.parent();
            }
            block.remove();
        }

        for (Element el : copy.select("div, p, section, aside")) {
            if (el.parent() == null) {
                continue;
            }
            String text = el.text();
            if (text.contains("markkinointievästeet") || text.contains("Hups! Jos haluat nähdä tällaista sisältöä")) {
                if ("p".equalsIgnoreCase(el.tagName()) || text.length() < 300) {
                    el.remove();
                }
            }
        }

        // Handle 'Apua asiointiin' footer help block in Jsoup pass
        for (Element heading : copy.select("h1, h2, h3, h4, h5, h6")) {
            if (heading.parent() == null) {
                continue;
            }
            String headingText = normalizeWhitespace(heading.text()).toLowerCase(Locale.ROOT);
            if ("apua asiointiin".equals(headingText)) {
                Element parent = heading.parent();
                if (parent != null && !parent.tagName().equalsIgnoreCase("body")
                        && !parent.tagName().equalsIgnoreCase("main")
                        && !parent.tagName().equalsIgnoreCase("article")) {
                    String parentText = parent.text();
                    if (parentText.contains("Henkilöasiakkaat") || parentText.contains("chatissa") || parentText.contains("Omaposti")) {
                        parent.remove();
                        continue;
                    }
                }
                Element next = heading.nextElementSibling();
                if (next != null && next.text().toLowerCase(Locale.ROOT).contains("henkilöasiakkaat")) {
                    Element list = next.nextElementSibling();
                    if (list != null && ("ul".equalsIgnoreCase(list.tagName()) || "ol".equalsIgnoreCase(list.tagName())
                            || list.text().toLowerCase(Locale.ROOT).contains("chatissa")
                            || list.text().toLowerCase(Locale.ROOT).contains("omaposti"))) {
                        list.remove();
                    }
                    next.remove();
                }
                heading.remove();
            }
        }

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

        String res = md.toString();

        // Post-processing removals
        // 1. Remove any occurrence of 'Avautuu uuteen ikkunaan' (case-insensitive)
        res = AVAUTUU_IKKUNAAN_PATTERN.matcher(res).replaceAll("");
        res = AVAUTUU_RAW_PATTERN.matcher(res).replaceAll("");

        // 2. Remove any line matching 'Hups! Jos haluat nähdä tällaista sisältöä.*'
        res = HUPS_LINE_PATTERN.matcher(res).replaceAll("");

        // 3. Remove standard trailing help block
        res = STANDARD_HELP_BLOCK_PATTERN.matcher(res).replaceAll("");
        res = HELP_SUB_LIST_PATTERN.matcher(res).replaceAll("");
        res = APUA_HEADING_PATTERN.matcher(res).replaceAll("");

        // 4. Clean up any empty headings left behind
        res = EMPTY_HEADING_PATTERN.matcher(res).replaceAll("");

        // 5. Clean up space before punctuation
        res = res.replaceAll(" +([.,!?:;])", "$1");

        // 6. Clean up trailing spaces and consecutive blank lines (max 2 newlines)
        res = TRAILING_SPACES_PATTERN.matcher(res).replaceAll("");
        res = CONSECUTIVE_NEWLINES_PATTERN.matcher(res).replaceAll("\n\n");

        return res.trim();
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
