package com.dippa.ragbenchmark.ingest.nordea;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NordeaHtmlToMarkdownConverterTest {

    @Test
    void convertsRealisticNordeaPageToCleanMarkdown() {
        String html = """
                <!DOCTYPE html>
                <html>
                <head><title>Private Banking lyhyesti</title></head>
                <body>
                    <header>
                        <h2>Kirjaudu</h2>
                        <p>Sisäänkirjautuminen henkilöasiakkaan verkkopankkiin</p>
                    </header>
                    <nav>
                        <h2>Muut palvelut</h2>
                        <ul><li>Henkilöasiakkaat</li><li>Yritysasiakkaat</li></ul>
                    </nav>
                    <div class="cookie-consent">
                        <p>Käytämme evästeitä parantaaksemme käyttökokemustasi.</p>
                    </div>
                    <main id="content-start">
                        <h1>Mikä tekee Nordea Private Bankingista ainutlaatuisen?</h1>
                        <p>Jokaisella vaurauden polulla on oma tarinansa. Asiakkaanamme jatkat matkaasi oman sijoitusjohtajasi avulla.</p>
                        
                        <div class="text-icon">
                            <a href="/yhteys"><p class="text">Jätä yhteydenottopyyntö</p></a>
                        </div>
                        
                        <h2>Private Bankingin edut</h2>
                        <h2>Henkilökohtainen sijoitusjohtaja</h2>
                        <p>Sijoitusjohtajasi on varallisuudenhoidon asiantuntija ja oma yhteyshenkilösi Nordeassa.</p>
                        <a href="/palvelut"><p><span>Lue lisää henkilökohtaisesta palvelusta</span></p></a>
                        
                        <h2>Kokonaisvaltaista varallisuudenhoitoa</h2>
                        <p>Otamme huomioon koko varallisuutesi, myös muun muassa yritykset, lainat ja kiinteistöt.</p>
                        
                        <h2>Jaa tämä sivu</h2>
                        <h2>Ota yhteyttä meihin</h2>
                        <p>Soita meille 0800 198 989 (pvm/mpm)</p>
                    </main>
                    <footer>
                        <h2>Tietoa Nordeasta</h2>
                        <p>© 2026 Nordea Bank Oyj</p>
                    </footer>
                </body>
                </html>
                """;

        Document doc = Jsoup.parse(html);
        String markdown = NordeaHtmlToMarkdownConverter.convert(doc);

        // Substantive content should be preserved with proper Markdown headings
        assertThat(markdown).contains("# Mikä tekee Nordea Private Bankingista ainutlaatuisen?");
        assertThat(markdown).contains("Jokaisella vaurauden polulla on oma tarinansa.");
        assertThat(markdown).contains("## Private Bankingin edut");
        assertThat(markdown).contains("## Henkilökohtainen sijoitusjohtaja");
        assertThat(markdown).contains("Sijoitusjohtajasi on varallisuudenhoidon asiantuntija");
        assertThat(markdown).contains("## Kokonaisvaltaista varallisuudenhoitoa");
        assertThat(markdown).contains("## Ota yhteyttä meihin");
        assertThat(markdown).contains("Soita meille 0800 198 989 (pvm/mpm)");

        // Chrome and noise should be stripped
        assertThat(markdown).doesNotContain("Kirjaudu");
        assertThat(markdown).doesNotContain("Muut palvelut");
        assertThat(markdown).doesNotContain("Käytämme evästeitä");
        assertThat(markdown).doesNotContain("Jätä yhteydenottopyyntö");
        assertThat(markdown).doesNotContain("Lue lisää");
        assertThat(markdown).doesNotContain("Jaa tämä sivu");
        assertThat(markdown).doesNotContain("Tietoa Nordeasta");
        assertThat(markdown).doesNotContain("© 2026 Nordea Bank Oyj");
    }

    @Test
    void formatsListsAndTablesProperly() {
        String html = """
                <main>
                    <h2>Palvelupaketit</h2>
                    <ul>
                        <li>Private Banking Basic</li>
                        <li>Private Banking Premium</li>
                    </ul>
                    <table>
                        <tr><th>Palvelu</th><th>Hinta</th></tr>
                        <tr><td>Salkunhoito</td><td>0.50%</td></tr>
                        <tr><td>Neuvonta</td><td>Maksuton</td></tr>
                    </table>
                </main>
                """;

        Document doc = Jsoup.parse(html);
        String markdown = NordeaHtmlToMarkdownConverter.convert(doc);

        assertThat(markdown).contains("## Palvelupaketit");
        assertThat(markdown).contains("- Private Banking Basic");
        assertThat(markdown).contains("- Private Banking Premium");
        assertThat(markdown).contains("| Palvelu | Hinta |");
        assertThat(markdown).contains("| --- | --- |");
        assertThat(markdown).contains("| Salkunhoito | 0.50% |");
        assertThat(markdown).contains("| Neuvonta | Maksuton |");
    }
}
