package com.dippa.ragbenchmark.ingest.nordea;

import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NordeaPdfToMarkdownConverterTest {

    @Test
    void convertsPdfToCleanMarkdownStrippingHeadersAndFooters() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            // Page 1
            PDPage page1 = new PDPage();
            document.addPage(page1);
            try (PDPageContentStream cs = new PDPageContentStream(document, page1)) {
                cs.beginText();
                cs.setFont(font, 12);
                cs.setLeading(14.5f);
                cs.newLineAtOffset(50, 700);

                cs.showText("Nordea Bank Oyj");
                cs.newLine();
                cs.newLine();
                cs.showText("1. Yleiset ehdot");
                cs.newLine();
                cs.newLine();
                cs.showText("Tama sopimus koskee asiakkaan ja pankin valista");
                cs.newLine();
                cs.showText("varallisuudenhoitopalvelua ja siihen liittyvia ehtoja.");
                cs.newLine();
                cs.newLine();
                cs.showText("* Ensimmainen ehto");
                cs.newLine();
                cs.showText("* Toinen ehto");
                cs.newLine();
                cs.newLine();
                cs.showText("Sivu 1 / 2");
                cs.endText();
            }

            // Page 2
            PDPage page2 = new PDPage();
            document.addPage(page2);
            try (PDPageContentStream cs = new PDPageContentStream(document, page2)) {
                cs.beginText();
                cs.setFont(font, 12);
                cs.setLeading(14.5f);
                cs.newLineAtOffset(50, 700);

                cs.showText("Nordea Bank Oyj");
                cs.newLine();
                cs.newLine();
                cs.showText("2. Varallisuudenhoito");
                cs.newLine();
                cs.newLine();
                cs.showText("Pankki hoitaa asiakkaan varallisuutta sovittujen");
                cs.newLine();
                cs.showText("periaatteiden mukaisesti.");
                cs.newLine();
                cs.newLine();
                cs.showText("Sivu 2 / 2");
                cs.endText();
            }

            String markdown = NordeaPdfToMarkdownConverter.convert(document, "Private Banking Ehdot");

            assertThat(markdown).contains("# Private Banking Ehdot");
            assertThat(markdown).contains("## 1. Yleiset ehdot");
            assertThat(markdown).contains("Tama sopimus koskee asiakkaan ja pankin valista varallisuudenhoitopalvelua ja siihen liittyvia ehtoja.");
            assertThat(markdown).contains("- Ensimmainen ehto");
            assertThat(markdown).contains("- Toinen ehto");
            assertThat(markdown).contains("## 2. Varallisuudenhoito");
            assertThat(markdown).contains("Pankki hoitaa asiakkaan varallisuutta sovittujen periaatteiden mukaisesti.");

            // Running headers and footers should be stripped
            assertThat(markdown).doesNotContain("Nordea Bank Oyj");
            assertThat(markdown).doesNotContain("Sivu 1 / 2");
            assertThat(markdown).doesNotContain("Sivu 2 / 2");
        }
    }
}
