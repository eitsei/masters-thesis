package com.dippa.ragbenchmark.ingest.nordea;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Converts Apache PDFBox {@link PDDocument}s into clean, structured Markdown.
 *
 * <p>Identifies and removes running headers, footers, and page numbers across pages,
 * reconstructs hard-wrapped lines into coherent paragraphs, formats bullet points,
 * and detects section headings.</p>
 */
public final class NordeaPdfToMarkdownConverter {

    private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile(
            "^\\s*(sivu|page)?\\s*\\d+\\s*([/(]\\s*\\d+\\s*[)]?)?\\s*$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SECTION_HEADING_PATTERN = Pattern.compile(
            "^\\s*(\\d+(\\.\\d+)*\\.?|[A-Z]\\.)\\s+\\S.*");

    private static final Pattern BULLET_PATTERN = Pattern.compile(
            "^\\s*[•·\\*\\-]\\s+(.*)");

    private NordeaPdfToMarkdownConverter() {
    }

    /**
     * Converts a {@link PDDocument} to structured Markdown.
     *
     * @param document the loaded PDF document
     * @param title    optional document title (can be null or blank)
     * @return cleaned Markdown string
     * @throws IOException if PDF text stripping fails
     */
    public static String convert(PDDocument document, String title) throws IOException {
        if (document == null) {
            return "";
        }

        int pageCount = document.getNumberOfPages();
        List<List<String>> pagesLines = new ArrayList<>(pageCount);
        PDFTextStripper stripper = new PDFTextStripper();

        for (int p = 1; p <= pageCount; p++) {
            stripper.setStartPage(p);
            stripper.setEndPage(p);
            String pageText = stripper.getText(document);
            pagesLines.add(extractLines(pageText));
        }

        // Detect recurring headers and footers across pages if document has multiple pages
        Set<String> repeatingLines = findRepeatingHeadersFooters(pagesLines);

        StringBuilder md = new StringBuilder();
        if (title != null && !title.isBlank()) {
            md.append("# ").append(title.trim()).append("\n\n");
        }

        for (List<String> rawLines : pagesLines) {
            List<String> cleanedLines = filterNoiseLines(rawLines, repeatingLines);
            formatPageLines(cleanedLines, md);
        }

        return md.toString().trim();
    }

    private static List<String> extractLines(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        String[] split = text.replace('\u00A0', ' ').split("\\r?\\n");
        List<String> lines = new ArrayList<>(split.length);
        for (String s : split) {
            lines.add(s.trim());
        }
        return lines;
    }

    private static Set<String> findRepeatingHeadersFooters(List<List<String>> pages) {
        if (pages.size() < 2) {
            return Collections.emptySet();
        }

        Map<String, Integer> topFrequency = new HashMap<>();
        Map<String, Integer> bottomFrequency = new HashMap<>();

        for (List<String> lines : pages) {
            if (lines.isEmpty()) continue;

            // Check first 2 lines
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                String line = lines.get(i);
                if (!line.isBlank()) {
                    topFrequency.merge(line, 1, Integer::sum);
                }
            }

            // Check last 2 lines
            for (int i = Math.max(0, lines.size() - 2); i < lines.size(); i++) {
                String line = lines.get(i);
                if (!line.isBlank()) {
                    bottomFrequency.merge(line, 1, Integer::sum);
                }
            }
        }

        Set<String> repeating = new HashSet<>();
        int threshold = Math.max(2, (int) (pages.size() * 0.5));

        topFrequency.forEach((line, count) -> {
            if (count >= threshold && line.length() < 100) {
                repeating.add(line);
            }
        });

        bottomFrequency.forEach((line, count) -> {
            if (count >= threshold && line.length() < 100) {
                repeating.add(line);
            }
        });

        return repeating;
    }

    private static List<String> filterNoiseLines(List<String> lines, Set<String> repeatingLines) {
        List<String> result = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                result.add("");
                continue;
            }

            // Page numbers on first or last 2 lines
            if ((i < 2 || i >= lines.size() - 2) && PAGE_NUMBER_PATTERN.matcher(line).matches()) {
                continue;
            }

            // Recurring header/footer
            if (repeatingLines.contains(line)) {
                continue;
            }

            result.add(line);
        }
        return result;
    }

    private static void formatPageLines(List<String> lines, StringBuilder md) {
        StringBuilder currentParagraph = new StringBuilder();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            if (line.isBlank()) {
                flushParagraph(currentParagraph, md);
                continue;
            }

            // Check if bullet point
            var bulletMatcher = BULLET_PATTERN.matcher(line);
            if (bulletMatcher.matches()) {
                flushParagraph(currentParagraph, md);
                md.append("- ").append(bulletMatcher.group(1).trim()).append("\n");
                continue;
            }

            // Check if section heading
            if (isLikelyHeading(line, lines, i)) {
                flushParagraph(currentParagraph, md);
                md.append("## ").append(line).append("\n\n");
                continue;
            }

            // Regular prose line: reconstruct wrapped paragraphs
            if (currentParagraph.isEmpty()) {
                currentParagraph.append(line);
            } else {
                char lastChar = currentParagraph.charAt(currentParagraph.length() - 1);
                if (lastChar == '-') {
                    // De-hyphenate if wrapped mid-word: e.g. "varallisuuden-" -> "varallisuuden"
                    currentParagraph.setLength(currentParagraph.length() - 1);
                    currentParagraph.append(line);
                } else {
                    currentParagraph.append(" ").append(line);
                }
            }
        }

        flushParagraph(currentParagraph, md);
    }

    private static boolean isLikelyHeading(String line, List<String> lines, int index) {
        if (line.length() > 70) {
            return false;
        }

        if (SECTION_HEADING_PATTERN.matcher(line).matches()) {
            return true;
        }

        // Heading heuristic: relatively short, followed or preceded by empty line,
        // does not end with sentence punctuation (, . : ;)
        boolean endsWithPunctuation = line.endsWith(".") || line.endsWith(",") || line.endsWith(";") || line.endsWith(":");
        if (endsWithPunctuation) {
            return false;
        }

        boolean prevBlank = (index == 0) || lines.get(index - 1).isBlank();
        boolean nextBlank = (index == lines.size() - 1) || lines.get(index + 1).isBlank();

        return (prevBlank || nextBlank) && line.length() < 55 && Character.isUpperCase(line.charAt(0));
    }

    private static void flushParagraph(StringBuilder currentParagraph, StringBuilder md) {
        if (!currentParagraph.isEmpty()) {
            md.append(currentParagraph.toString().trim()).append("\n\n");
            currentParagraph.setLength(0);
        }
    }
}
