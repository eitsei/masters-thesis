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

    @Test
    void stripsCookieAndMarketingPlaceholderNotices() {
        String html = """
                <main>
                    <h1>Säästäminen ja sijoittaminen</h1>
                    <p>Säästämisen aloittaminen on helppoa ja kannattavaa.</p>
                    <div class="video-container">
                        <p>Hups! Jos haluat nähdä tällaista sisältöä, hyväksy ensin Nordean markkinointievästeet</p>
                    </div>
                    <p>Rahastosäästäminen sopii pitkäjänteiseen varallisuuden kerryttämiseen.</p>
                </main>
                """;

        Document doc = Jsoup.parse(html);
        String markdown = NordeaHtmlToMarkdownConverter.convert(doc);

        assertThat(markdown).contains("# Säästäminen ja sijoittaminen");
        assertThat(markdown).contains("Säästämisen aloittaminen on helppoa ja kannattavaa.");
        assertThat(markdown).contains("Rahastosäästäminen sopii pitkäjänteiseen varallisuuden kerryttämiseen.");
        assertThat(markdown).doesNotContain("Hups! Jos haluat nähdä tällaista sisältöä");
        assertThat(markdown).doesNotContain("markkinointievästeet");
    }

    @Test
    void stripsAvautuuUuteenIkkunaanInHeadingsAndLinks() {
        String html = """
                <main>
                    <h1>Asuntolainat ja luotot</h1>
                    <h2>Lainalaskuri (Avautuu uuteen ikkunaan)</h2>
                    <p>Laske itsellesi sopiva kuukausierä.</p>
                    <h2>Korkosuojaukset avautuu uuteen ikkunaan</h2>
                    <p>
                        <a href="https://www.nordea.fi/laskuri">Hae lainaa verkossa (Avautuu uuteen ikkunaan)</a>
                    </p>
                    <p>
                        Voit tutustua ehtoihin <a href="/ehdot">täällä</a>
                        <span class="sr-only">Avautuu uuteen ikkunaan</span>.
                    </p>
                </main>
                """;

        Document doc = Jsoup.parse(html);
        String markdown = NordeaHtmlToMarkdownConverter.convert(doc);

        assertThat(markdown).contains("## Lainalaskuri");
        assertThat(markdown).contains("## Korkosuojaukset");
        assertThat(markdown).contains("Hae lainaa verkossa");
        assertThat(markdown).contains("Voit tutustua ehtoihin täällä");
        assertThat(markdown).doesNotContainIgnoringCase("Avautuu uuteen ikkunaan");
    }

    @Test
    void stripsStandardApuaAsiointiinFooterBlock() {
        String html = """
                <main>
                    <h1>Päivittäiset pankkipalvelut</h1>
                    <p>Kortit, tilit ja verkkopalvelut arjen sujuvaan hoitamiseen.</p>
                    
                    <h2>Apua asiointiin</h2>
                    <h3>Henkilöasiakkaat</h3>
                    <ul>
                        <li>Kysy heti chatissa</li>
                        <li>Usein kysytyt</li>
                        <li>Soita meille</li>
                        <li>Nordea Omaposti</li>
                    </ul>
                </main>
                """;

        Document doc = Jsoup.parse(html);
        String markdown = NordeaHtmlToMarkdownConverter.convert(doc);

        assertThat(markdown).contains("# Päivittäiset pankkipalvelut");
        assertThat(markdown).contains("Kortit, tilit ja verkkopalvelut arjen sujuvaan hoitamiseen.");
        assertThat(markdown).doesNotContain("Apua asiointiin");
        assertThat(markdown).doesNotContain("Henkilöasiakkaat");
        assertThat(markdown).doesNotContain("Kysy heti chatissa");
        assertThat(markdown).doesNotContain("Nordea Omaposti");
    }
}
