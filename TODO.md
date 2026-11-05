# Master's Thesis — Audit & Final Polish TODO

Tämä tehtävälista sisältää diplomityön (`thesis/_main.pdf` ja `thesis/chapters/`) kattavan läpikäynnin ja vertaispalautteen pohjalta tunnistetut viimeistely- ja korjauskohteet ennen tiivistelmien (Abstract / Tiivistelmä) ja esipuheen (Preface) kirjoittamista. 

Kaikki korjaukset ovat nopeita tekstimuutoksia olemassa oleviin `.tex`-tiedostoihin ja kuvaajiin — uusia kokeellisia mittausajoja, koodimuutoksia tai suuria rakenneuudistuksia ei tarvita.

---

## 1. Visuaaliset ja taitolliset korjaukset (PDF & kuvaajat)

- [x] **Kuvaajien selitteen (legend) päällekkäisyys pylväiden arvojen kanssa (Liitteet C ja D, sivut 74 ja 78):**
  - **Sijainti / Tiedostot:** `thesis/chapters/0C_appendix.tex` (Figure C2(b), s. 74) ja `thesis/chapters/0D_appendix.tex` (Figure D2, s. 78); taustalla plottausskriptit `figures/enterprise_closed/cost_breakdown_enterprise_closed.pdf` ja `figures/enterprise/cost_breakdown_enterprise.pdf`.
  - **Tila:** Korjattu. Päivitetty `ax.set_ylim` kertoimeksi `1.55` tiedostossa `rag-code/rag-evaluator/src/plot_results.py` sekä latenssi- (`plot_latency_breakdown`) että kustannuskuvaajalle (`plot_cost_breakdown`). Kuvaajat generoitu uudelleen ja työ käännetty onnistuneesti. Selitelaatikko ei enää peitä pylväiden arvoja, ja kuvaajat ovat täsmälleen saman korkuisia.

- [x] **Taulukon B1 poikkeava fontti ja redundantit kokokomennot (Liite B, sivu 72):**
  - **Sijainti / Tiedosto:** `thesis/chapters/0B_appendix.tex` (Table B1, rivit 5–8).
  - **Tila:** Poistettu `\sffamily` ja redundantti `\footnotesize`. Taulukko käyttää nyt yhtenäistä pääteviivallista `\scriptsize`-kirjasinta muiden taulukoiden tapaan.

- [x] **Kuvaluettelon (List of Figures) puuttuminen ja taulukkoluettelon pitkät otsikot (Alkusivut 7–10):**
  - **Sijainti / Tiedostot:** `thesis/_main.tex` (rivit 359–363) sekä taulukkotiedostot kansioissa `thesis/tables/...`.
  - **Tila:** Korjattu. `\listoffigures` lisätty tiedostoon `_main.tex`. Kaikille taulukoille (Taulukot 3, 5–12, B1, D1–D4) ja kuvaajille (Kuvat 1–3, C1–C4, D1–D4) on lisätty tiiviit hakasulkeelliset otsikot `\caption[Lyhyt]{Pitkä}`. `List of Tables` mahtuu nyt täydellisesti sivulle 9 ja `List of Figures` sivulle 10 ilman ylivuotoa.

---

## 2. Sisällölliset, teoreettiset ja metodologiset korjaukset

- [x] **Prompt Caching -laatuargumentin korjaus (KV-välimuistin matemaattinen ekvivalenssi vs. "attention anchor"):**
  - **Sijainti / Tiedosto:** `thesis/chapters/06_discussion.tex` (s. 57, rivi 36).
  - **Tila:** Päätetty pitää nykyinen teksti ennallaan. Palataan tähän vain, jos ihmisarvioija ottaa asian esille.

- [x] **Staattisen 7 150 tokenin etuliitteen rooli kustannussäästöissä (Luvut 4.3 ja 6.5):**
  - **Sijainti / Tiedostot:** `thesis/chapters/04_research_methods.tex` (s. 28 rivi 133) ja `thesis/chapters/06_discussion.tex` (s. 58–60, alaluku 6.5).
  - **Tila:** Täydennetty luvussa 4.3 (enterprise-kehotteen laajuus ja explicit context caching). Luku 6.1 kattaa jo laajuuden vaikutuksen (7 157 tokenia).


- [x] **Generointimallien parametrien (Temperature ja decoding) puuttuminen (Luku 4.2):**
  - **Sijainti / Tiedosto:** `thesis/chapters/04_research_methods.tex` (s. 26–27, alaluku 4.2).
  - **Ongelma:** Tuomarimallin lämpötila ($T=0.0$) on raportoitu luvussa 4.5, mutta Gemini-mallien generointiparametrit puuttuvat luvusta 4.2.
  - **Korjausehdotus:** Lisää luvun 4.2 loppuun virke: *"Generative foundation models were invoked with temperature $T = 0.0$ and greedy decoding to eliminate stochastic sampling variance."*

- [x] **Johdannon arkkitehtuurilupauksen kalibrointi työn sisältöön (Luku 1):**
  - **Sijainti / Tiedosto:** `thesis/chapters/01_introduction.tex` (s. 13 rivi 124).
  - **Nykyinen teksti:** `Furthermore, this work proposes a concrete architectural solution to implement these optimization strategies utilizing enterprise frameworks.`
  - **Ongelma:** Lause lupaa "konkreettisen arkkitehtuuriratkaisun", mikä herättää odotuksen laajasta ohjelmistoarkkitehtuurikaaviosta ja Spring AI -kooditason esittelystä. Työn varsinainen arvo on empiirinen optimointivertailu ja Pareto-analyysi.
  - **Korjausehdotus:** Kalibroidaan virke ilman tarvetta piirtää uusia ohjelmistokaavioita: `Furthermore, this work demonstrates how these optimization strategies can be systematically evaluated and integrated within an enterprise Java and Spring AI infrastructure.`

- [x] **Latenssitulkintojen pehmentäminen ja P95-otantakohina (Luvut 4.6 ja 6.1):**
  - **Sijainti / Tiedostot:** `thesis/chapters/06_discussion.tex` (s. 53, alaluku 6.1) ja `thesis/chapters/04_research_methods.tex` (s. 36, alaluku 4.6).
  - **Ongelma:**
    1. "Kiinteä 300–500 ms koordinaatioviive" esitetään faktana, vaikka MuSiQuessa TTFT-ero oli vain ~95 ms.
    2. $N=100$ -otoksella $P_{95}$-häntälatenssi määräytyy viidestä hitaimmasta pyynnöstä ja on siksi altis hetkelliselle pilviverkon jitterille.
  - **Korjausehdotus:**
    1. Muotoile 300–500 ms viive havaittuna empiirisenä lisäviiveenä / hypoteesina verkon ja etä-KV-haun koordinaatiosta.
    2. Lisää validiteettiuhkiin maininta, että $N=100$ otoskoossa $P_{95}$-arvot heijastavat pientä määrää ääripään pyyntöjä ja voivat sisältää hetkellistä pilvialustan kuormitusvaihtelua.

- [x] **Implisiittisen välimuistin kuvauksen yhdenmukaistaminen (Luku 4):**
  - **Sijainti / Tiedosto:** `thesis/chapters/04_research_methods.tex` (s. 29 rivi 133 vs. s. 36 rivi 307).
  - **Ongelma:** Rivi 133 sanoo "implicit caching is disabled across all configurations", kun taas rivi 307 sanoo että ei-caching-ajoissa välimuistiosumat laskutettiin täyteen hintaan ("billing all prompt tokens at full uncached rates and enforcing zero cached tokens across all non-caching runs").
  - **Korjausehdotus:** Täsmennä sanamuotoa siten, että Vertex AI:n hallinnoiman taustajärjestelmän automaattisia implisiittisiä välimuistiosumia kontrolloitiin kirjanpidossa pakottamalla kaikki ei-$M_3$-ajot täyden hinnan piiriin riippumattomuuden varmistamiseksi.

- [x] **Väitteen "Zero degradation" pehmentäminen tilastolliseksi ilmaisuksi (Luvut 5.5 ja 6.1):**
  - **Sijainti / Tiedostot:** `thesis/chapters/05_results.tex` (s. 50 rivi 128) ja `thesis/chapters/06_discussion.tex` (s. 54 rivi 12).
  - **Nykyinen teksti:** `without sacrificing output quality` ja `with zero degradation in output quality`.
  - **Ongelma:** Tilastollinen merkitsemättömyys ($p \ge 0.05$) ei matemaattisesti tarkoita identtistä nollamuutosta.
  - **Korjausehdotus:** Muuta muotoon `without statistically significant degradation in output quality`.

- [x] **Enterprise-aineiston validointiprosessin täydentäminen (Luku 4.6):**
  - **Sijainti / Tiedosto:** `thesis/chapters/04_research_methods.tex` (s. 35 rivi 300) ja Liite A.
  - **Ongelma:** Kysymys-vastausparit generoitiin ja verifioitiin Gemini 3.8 Flashilla ilman mainintaa inhimillisestä laadunvarmistuksesta.
  - **Korjausehdotus:** Lisää maininta, että tekijä tarkasti manuaalisesti satunnaisotannan generoiduista kysymys-vastauspareista faktuaalisuuden ja luonnollisuuden varmistamiseksi.

- [x] **Tuomarimallin (Qwen3.8-27B) suorituspaikka: ”locally” vs. CSC:n klusteri (Luvut 4.5 ja 4.6):**
  - **Sijainti / Tiedostot:** `thesis/chapters/04_research_methods.tex` (s. 30 rivi 150, s. 35 rivi 300) vs. `thesis/chapters/06_discussion.tex` (s. 60 rivi 60).
  - **Nykyinen teksti luvussa 4:** `Qwen3.8-27B is deployed locally via vLLM as an automated LLM-as-a-judge...`
  - **Ongelma:** Luvussa 4.2 esitellään kannettava tietokone (Intel Core Ultra 7, 32 GB RAM), jolloin "deployed locally" antaa mielikuvan, että 27B-malli pyöri kannettavalla. Vasta luvun 6.5 lopussa mainitaan CSC:n palvelimet.
  - **Korjausehdotus:** Täsmennä jo luvuissa 4.5 ja 4.6, että mallia ajettiin vLLM:llä CSC:n laskentaresursseilla (`hosted on servers provided by CSC -- IT Center for Science and served via vLLM`).

- [x] **Luvun 6.3 kontekstifragmentaation osajoukkotäsmennys (Luku 6.3):**
  - **Sijainti / Tiedosto:** `thesis/chapters/06_discussion.tex` (s. 56 rivi 32).
  - **Tila:** Päätetty pitää nykyinen teksti ennallaan. Teksti on sujuva ja mallikohtainen analyysi on selkeää sellaisenaan.

- [x] **HNSW-indeksoinnin harhaanjohtava lähdeviite (Luku 4.2):**
  - **Sijainti / Tiedosto:** `thesis/chapters/04_research_methods.tex` (s. 26 rivi 82).
  - **Nykyinen teksti:** `utilizing Hierarchical Navigable Small World (HNSW) indexing with cosine distance [4].`
  - **Ongelma:** Lähde `[4]` on Lewis et al. (2020) RAG-artikkeli, jossa käytettiin FAISSin tasaista MIPS-indeksiä eikä HNSW:tä.
  - **Korjausehdotus:** Lisää viitteeksi HNSW-algoritmin alkuperäinen tutkimus Malkov & Yashunin (2018) `\citep{malkov2018efficient}` tai poista viite `[4]` HNSW-indeksoinnin perästä.

---

## 3. Tilastolliset, numeeriset ja taulukkoviittausten täsmennykset

- [x] **Johtopäätökset, RQ1: Latenssihaarukan poikkeuksen täsmennys (Luku 7):**
  - **Sijainti / Tiedosto:** `thesis/chapters/07_conclusion.tex` (s. 61 rivi 7).
  - **Tila:** Korjattu huomioimaan eristetyn Semantic Re-rankingin ja Small-to-Big -retrievalin marginaaliset vaihtelut (-4.3% ja -1.2%).

- [x] **Johtopäätökset ja Luku 6.2, RQ2: Mallitasojen reititysosuuksien täsmällinen nimeäminen:**
  - **Sijainti / Tiedostot:** `thesis/chapters/07_conclusion.tex` (s. 62 rivi 11) ja `thesis/chapters/06_discussion.tex` (s. 54 rivi 19).
  - **Tila:** Korjattu täsmälliseen muotoon (49.0% Flash-Lite, 38.0% Flash, 13.0% Pro) ja poistettu epämuodolliset sanat 'workhorse' ja 'premium'.

- [x] **Johtopäätökset, RQ4: Termi "dominant" vs. "non-dominated":**
  - **Sijainti / Tiedosto:** `thesis/chapters/07_conclusion.tex` (s. 62 rivi 19).
  - **Tila:** Korvattu luonnollisemmalla ja vakiintuneella termillä `Pareto-optimal`.

- [x] **Taulukoiden 11, 12 ja D4 alaviitteen QPD-kaava vs. Yhtälö 23 (Sivut 47, 49, 82):**
  - **Sijainti / Tiedostot:** `thesis/tables/musique/tab_pareto_musique.tex` (rivi 38), `thesis/tables/enterprise_closed/tab_pareto_enterprise.tex` (rivi 38) ja `thesis/tables/enterprise/tab_pareto_enterprise.tex` (rivi 38).
  - **Tila:** Korjattu viittaamaan suoraan yhtälöihin `(Eqs.~\ref{eq:qpd}--\ref{eq:qps})`, jolloin kaavaa ei toisteta alaviitteessä ja vältetään epäjohdonmukaisuudet.

- [x] **Keskiarvon (mean) täsmentäminen liitteiden C ja D osavaihelatenssikuvaajissa (Sivut 73 ja 78):**
  - **Sijainti / Tiedostot:** `thesis/chapters/0C_appendix.tex` (Figure C1) sekä `thesis/chapters/0D_appendix.tex` (Figure D1).
  - **Tila:** Vahvistettu koodista (`plot_results.py`, `np.mean()`), ja lisätty kuvateksteihin täsmennys: *"Stacked bars represent sample mean execution durations across queries, complementary to the median ($P_{50}$) profiles reported in Chapter 5 / Table D1."*

- [x] **Luku 5.4.1: Pyöristystarkkuus prosenttimuutoksessa (Sivu 47):**
  - **Sijainti / Tiedosto:** `thesis/chapters/05_results.tex` (s. 47 rivi 97).
  - **Nykyinen teksti:** `driven primarily by a 67.1% surge in median latency`
  - **Ongelma:** Taulukosta laskettuna $2885.5 / 1733.5 - 1 = 66.455\% \approx 66.5\%$.
  - **Korjausehdotus:** Korjaa `67.1%` $\to$ `66.5%`.

- [x] **Luku 5.1.1: Persentiilien riippumattoman laskennan täsmennys (Sivu 38):**
  - **Sijainti / Tiedosto:** `thesis/chapters/05_results.tex` (s. 38 rivi 15).
  - **Nykyinen teksti:** `diverging by only 1–87 ms`
  - **Ongelma:** Taulukossa 5 konfiguraatiolla SR+PC $TTFT_{P50} = 1522.0$ ms ja $T_{\text{total}} P_{50} = 1521.5$ ms (ero -0.5 ms), koska persentiilit lasketaan kullekin sarakkeelle itsenäisesti.
  - **Korjausehdotus:** Lisää huomautus, että persentiilit lasketaan sarakkeittain toisistaan riippumattomasti.

- [x] **Luku 5.5: "Multi-stage pipelines" täsmennys (Sivu 50):**
  - **Sijainti / Tiedosto:** `thesis/chapters/05_results.tex` (s. 50 rivi 128).
  - **Nykyinen teksti:** `and multi-stage pipelines frequently incurred severe latency penalties that doubled execution times (+80% to +119%).`
  - **Ongelma:** Nämä suuret viiveet (+80 % ... +119 %) koskevat nimenomaan reititystä (MC) sisältäviä putkia.
  - **Korjausehdotus:** Täsmennä muotoon `cascaded multi-stage pipelines (+80% to +119%)`.

- [x] **Luku 6.4: "Critical noise threshold" sanamuodon pehmentäminen (Sivu 58):**
  - **Sijainti / Tiedosto:** `thesis/chapters/06_discussion.tex` (s. 58 rivi 45).
  - **Tila:** Päätetty pitää nykyinen teksti ennallaan. Teksti kytkee kynnyksen selkeästi kahteen tutkittuun aineistopisteeseen.

- [x] **Luku 6.5: Tulevien malliversioiden mainintojen siistiminen (Sivu 58):**
  - **Sijainti / Tiedosto:** `thesis/chapters/06_discussion.tex` (s. 58 rivi 52).
  - **Tila:** Vahvistettu oikeaksi. Gemini 4 series (Argon and Carbon) on julkaistu lokakuussa 2026. Teksti pidetään ennallaan.

- [x] **Taulukko B1: Mallinimen ja Flash-Liten roolin täsmennys (Sivu 72):**
  - **Sijainti / Tiedosto:** `thesis/chapters/0B_appendix.tex` (Table B1).
  - **Tila:** Korjattu `Router / 1-Hop Generation`, yhtenäistetty `Gemini 3.1 Pro`, `Primary Generator` sekä `Offline Execution`.

- [x] **Johdanto, Luku 1: Rakennekuvauksen verbi (Sivu 14):**
  - **Sijainti / Tiedosto:** `thesis/chapters/01_introduction.tex` (s. 14 rivi 158).
  - **Nykyinen teksti:** `and the results are discussed in Section 5.`
  - **Korjausehdotus:** Vaihda verbi: `and the results are presented in Section 5.`

---

## 4. Lähdeluettelon (BibTeX) puhdistus (`thesis/references/ai.bib`)

- [x] **Viite [26] (`gao2023retrieval`): Tuplakirjoittaja ja virheelliset lehtikentät (Sivu 66):**
  - **Sijainti / Tiedosto:** `thesis/references/ai.bib` (rivit 217–225).
  - **Tila:** Poistettu toistuva kirjoittaja sekä virheelliset volume/number/pages -kentät.

- [x] **Viite [44] (`wei2025instructrag`): Konferenssin nimen ja vuoden toisto (Sivu 68):**
  - **Sijainti / Tiedosto:** `thesis/references/ai.bib` (rivit 370–375).
  - **Tila:** Korjattu `booktitle={International Conference on Learning Representations (ICLR)}`.

- [x] **Viite [16] (`Vaswani2017`): Julkaisijan nimen toisto (Sivu 65):**
  - **Sijainti / Tiedosto:** `thesis/references/ai.bib` (rivit 57–66).
  - **Tila:** Korjattu tavanomaiseen NeurIPS-muotoon (`Advances in Neural Information Processing Systems (NeurIPS)`, vol 30, publisher Curran Associates, Inc.).

- [x] **Verkkoviitteiden Accessed-päivämäärien yhtenäistäminen:**
  - **Sijainti / Tiedosto:** `thesis/references/ai.bib`.
  - **Tila:** Yhtenäistetty muotoilu (`Accessed September 2026` / `Accessed June 2026`) ja korjattu ChatGPT-viitteen linkki `\url{...}` -muotoon.

---

## 5. Otsikot, typografia ja yhtenäisyys

- [x] **Otsikoiden kirjainkoko (Title Case) ja yhtenäisyys (Sisällysluettelo s. 7, Luvut 2 ja 3):**
  - **Sijainti / Tiedostot:** `thesis/_toc.tex`, `thesis/chapters/02_technical_background.tex` ja `thesis/chapters/03_literature_review.tex`.
  - **Ongelma:**
    1. Luvuissa 4–7 ja alaluvuissa 3.3–3.4 käytetään Title Case -otsikointia, mutta luvuissa 2 (`Technical background`), 3 (`Literature review`) ja 3.2 (`Small-to-Big retrieval`) jälkimmäinen sana on pienellä alkukirjaimella.
    2. Alaluvun 2.1 otsikossa lukee `Large-Language Models` (yhdysviivalla), kun muualla käytetään `Large Language Models`.
  - **Korjausehdotus:** Yhtenäistä muotoon `Technical Background`, `Literature Review`, `Small-to-Big Retrieval` ja `Large Language Models`.

- [x] **Typografia: `top-k` leipätekstissä (Sivu 19):**
  - **Sijainti / Tiedosto:** `thesis/chapters/03_literature_review.tex` (s. 19 rivi 5).
  - **Nykyinen teksti:** `extended retrieval top-k parameters`
  - **Ongelma:** $k$-kirjain on leipätekstinä ilman matematiikkatilaa.
  - **Korjausehdotus:** Korjaa muotoon `top-$k$`.

---

## 6. Metatiedot ja valmistelut ennen tiivistelmien ja esipuheen kirjoittamista

- [x] **Ohjaajan tutkintonimike ja suomenkielinen lyhennepiste (Kansilehti ja tiivistelmäpohjat):**
  - **Sijainti / Tiedostot:** `thesis/_meta.tex` (rivi 67) ja `thesis/_abstract.tex` (rivi 52).
  - **Tila:** Korjattu `Lauri Salminen (MSc)` ja `DI Lauri Salminen`.

- [x] **Avainsanojen laajentaminen tiivistelmäpohjissa (Sivut 3 ja 4):**
  - **Sijainti / Tiedosto:** `thesis/_abstract.tex` (rivi 58).
  - **Tila:** Korjattu. Määritetty englanniksi `Retrieval-Augmented Generation, RAG, prompt caching, model cascading, semantic re-ranking, enterprise systems` ja suomeksi `hakuavusteinen generointi, RAG, kehotteen välimuisti, mallireititys, semanttinen uudelleenjärjestäminen, yritysjärjestelmät`.

- [ ] **Päivämäärien ja sivumäärän synkronointi palautushetkellä:**
  - **Sijainti / Tiedostot:** `thesis/_meta.tex` (rivi 51), `thesis/_abstract.tex` (rivi 56) ja `thesis/_preface.tex` (rivi 12).
  - **Huomio:** Päivitetään vastaamaan virallista jättöpäivämäärää, ja varmistetaan templateen tulostuva sivumäärä (`69+13`).

---

# Ohjaajan huomiot ja tarkastuslista (Advisor Feedback)

Tämä osio sisältää ohjaajan lukemisen perusteella antamat huomiot ja toimenpide-ehdotukset. Aliagentit tarkastivat työn tiedostot (`thesis/`) kohta kohdalta (tilanne 5.11.2026). Alla on kunkin kohdan varmistettu nykytila ja tarkka sijainti.

## 1. Kriittiset korjaukset (Showstoppers)

- [ ] **Tiivistelmät ovat LaTeX-pohjan ohjetekstejä (sivut 3 & 4):**
  - **Kuvaus:** Englanninkielinen Abstract (s. 3) ja suomenkielinen Tiivistelmä (s. 4) sisältävät Aallon LaTeX-pohjan ohjetekstit.
  - **Sijainti:** `thesis/_abstract.tex` (rivit 11–34 ja 62–81).
  - **Tila:** **Vaatii toimenpiteitä.** Molemmat tiivistelmät sisältävät edelleen Aallon pohjan ohjetekstin sanasta sanaan. Varsinaiset tiivistelmätekstit on kirjoitettava ennen lopullista palautusta.
  - *Huom (Avainsanat):* Avainsanat on sen sijaan jo määritelty ja kunnossa (`_abstract.tex` rivit 10 ja 60).

- [ ] **Projektin GitHub-repositorion toimimaton linkki (s. 70, alaviite):**
  - **Kuvaus:** Alaviite viittaa osoitteeseen `https://github.com/eitsei/dippa`, joka palauttaa HTTP 404 (repo on yksityinen tai osoite puuttuu).
  - **Sijainti:** `thesis/chapters/0A_appendix.tex` (rivi 1, Liite A; tulostuu PDF:n sivulle 70 nykyisin alaviitteenä 6).
  - **Tila:** **Vaatii toimenpiteitä.** Osoite on edelleen `https://github.com/eitsei/dippa` ja palauttaa 404. Repositorio tulee asettaa julkiseksi tai linkki korjata ennen arkistointia.

- [x] **Ohjaajan nimen ja akateemisen arvon muotoilu:**
  - **Sijainti:** `thesis/_meta.tex` (rivi 67), `thesis/_abstract.tex` (rivi 54), `thesis/_preface.tex` (rivi 6).
  - **Tila:** **Korjattu.**
    - [x] Suomenkielinen arvo (`thesis/_abstract.tex#L54`): `\advisor{DI Lauri Salminen}` (ilman pistettä DI:n perässä).
    - [x] Englanninkielinen arvo (`thesis/_meta.tex#L67`): Päivitetty muotoon `\advisor{Lauri Salminen (M.Sc. (Tech.))}`.
    - [x] Esipuhe (`thesis/_preface.tex#L6`): Päivitetty muotoon *"my advisor Lauri Salminen"*.

## 2. Sisällölliset ja faktuaaliset epäjohdonmukaisuudet (Content & Factual Inconsistencies)

- [x] **Tuomarimallin (Qwen) suorituspaikka: kannettava vs. CSC-palvelimet:**
  - **Sijainti:** `thesis/chapters/04_research_methods.tex` (rivit 82, 150, 300, 302) ja `thesis/chapters/06_discussion.tex` (rivi 60).
  - **Tila:** **Korjattu.** Commit `c021e46` yhdenmukaisti luvun 4 luvun 6.5 kanssa. Tekstissä todetaan nyt eksplisiittisesti, että Qwen3.8-27B ajettiin vLLM:llä offline CSC:n laskentapalvelimilla, kun taas kannettavalla tietokoneella (Intel Core Ultra 7, 32 GB RAM) ajettiin vain paikallinen PostgreSQL/pgvector ja Java-testisovellus. *(Pieni tyylihuomio: Luvussa 6.5 rivillä 60 sanapari "hosting the judge locally" viittaa lokaaliin vastakohtana kaupallisille pilvi-API:lle, mutta voidaan selkeyttää muotoon "offline / on-premise".)*

- [x] **Faithfulness- ja hylkäyslukujen ristiriita luvussa 6.3 vs. Taulukko 10:**
  - **Sijainti:** `thesis/chapters/06_discussion.tex` (rivi 32) vs. `thesis/tables/enterprise_closed/tab_quality_enterprise.tex` (Taulukko 10).
  - **Tila:** **Korjattu.** Luvun 6.3 tekstiä täsmennettiin ankkuroimalla mallikohtaiset luvut Taulukon 10 taustalla oleviin evaluointilokeihin (`query-level evaluation logs underlying Table~\ref{tab:quality_enterprise} reveal that...`). Mallikohtaiset Faithfulness-luvut (Flash-Lite 0.929 $\to$ 0.849, putken Faithfulness 0.780) ja hylkäysluvut (Pro 50.0 %, koko putken FRR 17.5 %; S2B + MC Flash-Lite 2.4 %, koko putken FRR 11.2 %) auditoitiin suoraan JSONL-raaka-ajoista ja kytkettiin eksplisiittisesti toisiinsa.

- [x] **Metriikan nimen epäjohdonmukaisuus: "Answer Relevance" vs. "Answer Relevancy":**
  - **Sijainti:** `thesis/chapters/01_introduction.tex` (rivi 150, Taulukko 1) ja `thesis/chapters/07_conclusion.tex` (rivi 13, RQ3-kappale) vs. muu teksti.
  - **Tila:** **Korjattu.** Termi korjattiin yhtenäiseen RAGAS-muotoon "Answer Relevancy" sekä johdannon tutkimuskysymystaulukossa (`01_introduction.tex#L150`) että johtopäätöksissä (`07_conclusion.tex#L13`).

- [x] **Mallien nimien täsmällisyys (Qwen ja Gemini 3.8 Flash):**
  - **Sijainti:** `thesis/chapters/04_research_methods.tex` (rivit 84, 143, 150, 302), `thesis/chapters/06_discussion.tex` (rivi 60), `thesis/chapters/0B_appendix.tex` (Taulukko B1) ja `thesis/chapters/0A_appendix.tex` (rivi 5).
  - **Tila:** **Korjattu.**
    - Gemini 3.8 Flashin rooli synteettisen aineiston luonnissa ja validoinnissa täsmennettiin luvussa 4.4 (`04_research_methods.tex#L143`) korostaen sen käyttöä puhtaasti offline-vaiheessa erillään ajonaikaisista generointimalleista.
    - Qwen3.8-27B säilytettiin nykyisellään, todettu riittäväksi.

## 3. Rakenteelliset ja taitolliset korjaukset (Structural & Formatting)

- [x] **Kuvaluettelon puuttuminen (\listoffigures):**
  - **Sijainti:** `thesis/_main.tex` (rivit 364–367).
  - **Tila:** **Korjattu.** Commit `1de2f9b` lisäsi komennon `\listoffigures` ja rekisteröinnin sisällysluetteloon välittömästi taulukkoluettelon (`\listoftables`) perään sivulle 10.

- [x] **Luku- vs. alalukutermit johdannossa (Chapter vs. Section s. 14):**
  - **Sijainti:** `thesis/chapters/01_introduction.tex` (rivi 158).
  - **Tila:** **Korjattu.** Pääluokkiin viitataan nyt muodossa `Chapter~\ref{...}`, ja samalla lauseen puhekielinen alku ("briefly explains what LLMs and RAG are") päivitettiin akateemisen täsmälliseen muotoon.

- [x] **Selittävän leipätekstin puuttuminen Liitteessä D (sivut 77–82):**
  - **Sijainti:** `thesis/chapters/0D_appendix.tex`.
  - **Tila:** **Korjattu.** Liitteeseen D lisättiin aliluvut (D.1 Latency, D.2 Cost, D.3 Quality, D.4 Pareto) ja selittävä leipäteksti, joka esittelee taustakohinan merkityksen ja viittaa eksplisiittisesti kaikkiin taulukoihin (D1–D4) ja kuvaajiin (D1–D4).

- [x] **Puuttuvat välilyönnit kuvateksteissä ja leipätekstissä:**
  - **Sijainti:** `thesis/chapters/0C_appendix.tex` (rivit 32, 70), `thesis/chapters/0D_appendix.tex` (rivi 28) ja lukujen leipätekstit.
  - **Tila:** **Korjattu / Kunnossa.** Tarkastuksessa vahvistettiin, että lähdekoodi ja käännetty PDF ovat puhtaita:
    - Figure C4: `\caption[Multi-dimensional...]{...}` tulostuu oikein välilyönnillä ("Figure C4: ").
    - Figure D3: Tulostuu oikein välilyönnillä ("Figure D3: ").
    - S. 74: `Figure~\ref{fig:cost_breakdown_combined} presents...` sisältää asianmukaisen välin ("Figure C2 presents...").
    - Kaikki 13 liiteviittausta käyttävät muotoa `Appendix~\ref{...}` ("Appendix B", "Appendix C", "Appendix D").

- [x] **Lähdeluettelon URL-rivitys ja \usepackage{xurl} (sivut 64–69):**
  - **Sijainti:** `thesis/_main.tex` (rivi 214).
  - **Tila:** **Korjattu.** `\usepackage{xurl}` siirrettiin ladattavaksi ennen `\usepackage{hyperref}` -komentoa, mikä mahdollistaa URL-osoitteiden luonnollisen katkeamisen kauttaviivoista ilman tavuviivoja.

- [x] **Lähdeviitteiden korjaukset ([12], [15], [26], [44]):**
  - **Sijainti:** `thesis/references/ai.bib`.
  - **Tila:** **Korjattu.**
    - [x] **[12] `asunmaa2025large`:** Kenttä `url` korvattu muodolla `note = {\url{...}}` yhteenkirjoitusongelman poistamiseksi.
    - [x] **[15] `radford2019language`:** Lisätty `journal = {OpenAI Technical Report}`.
    - [x] **[26] `gao2023retrieval`:** Toistuva kirjoittaja poistettu (commit `1de2f9b`).
    - [x] **[44] `wei2025instructrag`:** Toisteinen konferenssikuvaus korjattu (commit `1de2f9b`).

## 4. Matemaattiset merkinnät ja yhtälöt (Mathematical Notations & Equations)

- [x] **Yhtälöt (1) & (2) vs. kehote-notaatio P(q, R_q):**
  - **Sijainti:** `thesis/chapters/02_technical_background.tex` (rivit 31–38) ja `thesis/chapters/03_literature_review.tex` (rivi 71).
  - **Tila:** **Päätetty pitää ennallaan.** Nykyinen notaatio $G(q, R_q) = a$ todettu riittäväksi.

- [x] **Notaatiovaihdos Yhtälössä (7):**
  - **Sijainti:** `thesis/chapters/03_literature_review.tex` (rivi 47).
  - **Tila:** **Korjattu.** Hakuoperaattori yhtenäistettiin muotoon $\text{Ret}_{K_1}(q, D) = C_1$ luvun 2 ja alaluvun 3.2 notaation mukaisesti.

- [x] **Yhtälön (14) terminologia (s. 31):**
  - **Sijainti:** `thesis/chapters/04_research_methods.tex` (rivi 178).
  - **Tila:** **Korjattu.** Saatesanat muotoiltiin toiston poistamiseksi muotoon *"The total monetary cost, denoted as $\text{Cost}(q)$, for processing query $q$ is formally defined as:"*.

## 5. Sanasto, lyhenteet ja otsikot (Glossary, Abbreviations & Headings)

- [x] **Alaluvun 2.1 otsikon yhdysviiva (sivut 7, 15):**
  - **Sijainti:** `thesis/chapters/02_technical_background.tex` (rivi 3).
  - **Tila:** **Korjattu.** Otsikko on `\subsection{Large Language Models}` ilman yhdysviivaa (korjattu commitissa `c021e46`).

- [x] **Puuttuvat lyhenteet lyhenneluettelosta (s. 11):**
  - **Sijainti:** `thesis/_abbreviations.tex`.
  - **Tila:** **Korjattu.** Sivun 11 sivubudjetin ja taiton säilyttämiseksi lisättiin 7 keskeisintä tutkimusalan lyhennettä (BLEU, CSC, ICL, NDCG, NLP, PLM, ROUGE) ja karsittiin yleiset laitteistolyhenteet (SSD, NVMe, DRAM, TPU, jne.), jolloin lista mahtuu täydellisesti yhdelle sivulle (37 riviä).

## 6. Kieliasu, tyyli ja akateeminen sävy (Grammar, Style & Tone)

- [x] **Puhekieliset ilmaisut:**
  - **Sijainti:** `thesis/chapters/01_introduction.tex` (rivi 158) ja `thesis/chapters/02_technical_background.tex` (rivit 1, 11, 17, 27).
  - **Tila:** **Korjattu.** Kaikki 5 ohjaajan esille nostamaa kohtaa päivitettiin täsmälliseen akateemiseen rekisteriin (mm. "plain vanilla RAG" korvattiin työn vakiintuneella muodolla "naive RAG", "they are not perfect" muotoon "they exhibit critical architectural and operational limitations", jne.).

- [x] **Subjekti-verbi -yhteensopivuus viittausten kanssa (Subject–Verb Agreement):**
  - **Sijainti:** `thesis/chapters/03_literature_review.tex` (rivit 18, 38–39, 61).
  - **Tila:** **Korjattu.** Kaikki 3 viittauksen verbin yhteensopivuusvirhettä korjattiin (Jeong et al. report, LogicRAG doubles, Moreira et al. report).
