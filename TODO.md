# Master's Thesis — Audit & Final Polish TODO

Tämä tehtävälista sisältää diplomityön (`thesis/_main.pdf` ja `thesis/chapters/`) kattavan läpikäynnin ja vertaispalautteen pohjalta tunnistetut viimeistely- ja korjauskohteet ennen tiivistelmien (Abstract / Tiivistelmä) ja esipuheen (Preface) kirjoittamista. 

Kaikki korjaukset ovat nopeita tekstimuutoksia olemassa oleviin `.tex`-tiedostoihin ja kuvaajiin — uusia kokeellisia mittausajoja, koodimuutoksia tai suuria rakenneuudistuksia ei tarvita.

---

## 1. Visuaaliset ja taitolliset korjaukset (PDF & kuvaajat)

- [ ] **Kuvaajien selitteen (legend) päällekkäisyys pylväiden arvojen kanssa (Liitteet C ja D, sivut 74 ja 78):**
  - **Sijainti / Tiedostot:** `thesis/chapters/0C_appendix.tex` (Figure C2(b), s. 74) ja `thesis/chapters/0D_appendix.tex` (Figure D2, s. 78); taustalla plottausskriptit `figures/enterprise_closed/cost_breakdown_enterprise_closed.pdf` ja `figures/enterprise/cost_breakdown_enterprise.pdf`.
  - **Ongelma:** Kuvaajan vasemman yläkulman selitelaatikko (`Prompt (C_prompt)`, `Context Cache (C_cache)` jne.) menee kolmen ensimmäisen pylvään (`Baseline`, `S2B`, `SR`) yläpuolella olevien dollarilukemien (`$13.580`, `$14.471`, `$18.881` sekä `$14.785`, `$16.690`, `$19.866`) päälle ja peittää tekstit osittain.
  - **Korjausehdotus:** Nosta plottausskripteissä y-akselin ylärajaa (esim. `ylim(0, 28)` tai `ylim(0, 30)` nykyisen `25` sijaan) tai aseta selitelaatikko toiseen sijaintiin / kuvaajan ulkopuolelle, ja generoi PDF-kuvaajat uudelleen.

- [ ] **Taulukon B1 poikkeava fontti ja redundantit kokokomennot (Liite B, sivu 72):**
  - **Sijainti / Tiedosto:** `thesis/chapters/0B_appendix.tex` (Table B1, rivit 5–8).
  - **Nykyinen koodi:** `\sffamily \footnotesize \setlength{\tabcolsep}{3pt} \scriptsize`
  - **Ongelma:** Eksplisiittisen `\sffamily`-komennon vuoksi Table B1 tulostuu päätteettömällä sans-serif -fontilla, kun taas kaikki muut työn 16 taulukkoa käyttävät normaalia pääteviivallista leipätekstifonttia. Lisäksi peräkkäiset kokokomennot `\footnotesize` ja `\scriptsize` ovat keskenään redundantteja.
  - **Korjausehdotus:** Poista `\sffamily` ja redundantti `\footnotesize`, jolloin taulukko käyttää yhtenäistä pääteviivallista `\scriptsize`- (tai `\footnotesize`-) kirjasinta muiden taulukoiden tapaan.

- [ ] **Kuvaluettelon (List of Figures) puuttuminen ja taulukkoluettelon pitkät otsikot (Alkusivut 7–10):**
  - **Sijainti / Tiedostot:** `thesis/_main.tex` (rivit 359–363) sekä taulukkotiedostot kansioissa `thesis/tables/...`.
  - **Ongelma:**
    1. Tiedostossa `_main.tex` kutsutaan `\listoftables`, mutta `\listoffigures` puuttuu kokonaan, vaikka työssä on 10 keskeistä analyysikuvaajaa.
    2. Taulukoilla 3, 5–12, B1 ja D1–D4 ei ole lyhyttä valinnaista otsikkoa `\caption[Lyhyt otsikko]{Pitkä otsikko}`. Tämän vuoksi koko monirivinen seliteteksti (kuten *"Statistical significance flags denote Holm-Bonferroni-adjusted paired Wilcoxon signed-rank tests against the naive baseline."*) tulostuu taulukkoluetteloon venyttäen sen sivuille 9–10.
  - **Korjausehdotus:**
    1. Lisää `\listoffigures` tiedostoon `_main.tex` taulukkoluettelon jälkeen.
    2. Lisää taulukoiden `\caption`-komentoihin lyhyet otsikot hakasulkeisiin (esim. `\caption[End-to-end execution latency and TTFT on MuSiQue]{End-to-end execution latency and Time-to-First-Token (TTFT) across 16 pipeline architectures on MuSiQue. Statistical significance...}`).

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

- [ ] **Taulukoiden 11, 12 ja D4 alaviitteen QPD-kaava vs. Yhtälö 23 (Sivut 47, 49, 82):**
  - **Sijainti / Tiedostot:** `thesis/tables/musique/tab_pareto_musique.tex` (rivi 38), `thesis/tables/enterprise_closed/tab_pareto_enterprise.tex` (rivi 38) ja `thesis/tables/enterprise/tab_pareto_enterprise.tex` (rivi 38).
  - **Nykyinen teksti:** `QPD = Q/Cost_1k and QPS = Q/T_total represent resource efficiency ratios.`
  - **Ongelma:** Luvussa 4.5.4 määritellään oikein $\text{QPD} = \frac{\bar{Q}}{\text{Cost}_{1\text{k}} / 1000}$, jolla taulukon arvot on laskettu. Alaviite jättää jakajan `/ 1000` pois.
  - **Korjausehdotus:** Korjaa alaviitteisiin `QPD = \bar{Q} / (\text{Cost}_{1\text{k}} / 1000)`.

- [ ] **Keskiarvon (mean) täsmentäminen liitteiden C ja D osavaihelatenssikuvaajissa (Sivut 73 ja 78):**
  - **Sijainti / Tiedostot:** `thesis/chapters/0C_appendix.tex` (Figure C1 ja luku C.1) sekä `thesis/chapters/0D_appendix.tex` (Figure D1).
  - **Ongelma:** Pinotuissa kuvaajissa pylväät summautuvat osavaiheiden keskiarvoista (`mean`), kun taas tulostaulukoissa raportoidaan mediaanit ($P_{50}$).
  - **Korjausehdotus:** Täsmennä kuvateksteihin tai luvun C.1 leipätekstiin, että pylväät esittävät osavaiheiden otoskeskiarvoja (`sample mean execution latency`).

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

- [ ] **Taulukko B1: Mallinimen ja Flash-Liten roolin täsmennys (Sivu 72):**
  - **Sijainti / Tiedosto:** `thesis/chapters/0B_appendix.tex` (Table B1).
  - **Ongelma:** Malli on taulukossa nimellä `Gemini 3.1 Pro Preview`, leipätekstissä `Gemini 3.1 Pro`. Lisäksi Flash-Liten rooliksi on merkitty vain reititin, vaikka se hoitaa myös 1-hop -generoinnin.
  - **Korjausehdotus:** Täsmennä rooliksi `Router / 1-Hop Generation` ja yhtenäistä mallinimi.

- [x] **Johdanto, Luku 1: Rakennekuvauksen verbi (Sivu 14):**
  - **Sijainti / Tiedosto:** `thesis/chapters/01_introduction.tex` (s. 14 rivi 158).
  - **Nykyinen teksti:** `and the results are discussed in Section 5.`
  - **Korjausehdotus:** Vaihda verbi: `and the results are presented in Section 5.`

---

## 4. Lähdeluettelon (BibTeX) puhdistus (`thesis/references/ai.bib`)

- [ ] **Viite [26] (`gao2023retrieval`): Tuplakirjoittaja ja virheelliset lehtikentät (Sivu 66):**
  - **Sijainti / Tiedosto:** `thesis/references/ai.bib` (rivit 217–225).
  - **Nykyinen BibTeX:** `author={... Wang, Haofen and Wang, Haofen and others}, journal={arXiv preprint arXiv:2312.10997}, volume={2}, number={1}, pages={32}, year={2023}`
  - **Ongelma:** Kirjoittaja Haofen Wang toistuu kahdesti peräkkäin, ja arXiv-preprintille on jäänyt Google Scholarin virheelliset `volume={2}, number={1}, pages={32}`.
  - **Korjausehdotus:** Poista toinen `Haofen Wang` ja poista virheelliset volume/number/pages-kentät.

- [ ] **Viite [44] (`wei2025instructrag`): Konferenssin nimen ja vuoden toisto (Sivu 68):**
  - **Sijainti / Tiedosto:** `thesis/references/ai.bib` (rivit 373–378).
  - **Nykyinen BibTeX:** `booktitle={Published as a conference paper at ICLR 2025}, year={2025}`
  - **Ongelma:** Tulostuu lähdeluetteloon muodossa *"In Published as a conference paper at ICLR 2025, 2025."*
  - **Korjausehdotus:** Muuta `booktitle = {International Conference on Learning Representations (ICLR 2025)}`.

- [ ] **Viite [16] (`Vaswani2017`): Julkaisijan nimen toisto (Sivu 65):**
  - **Sijainti / Tiedosto:** `thesis/references/ai.bib` (rivit 57–66).
  - **Nykyinen BibTeX:** Sisältää sekä `booktitle = {Advances in Neural Information Processing Systems...}` että `publisher = {Neural Information Processing Systems (NIPS)}`.
  - **Ongelma:** Sama nimi toistuu sekä konferenssin että julkaisijan kentässä.
  - **Korjausehdotus:** Siisti kentät tavanomaiseen NeurIPS-muotoon (`publisher = {Curran Associates, Inc.}`).

- [ ] **Verkkoviitteiden Accessed-päivämäärien yhtenäistäminen:**
  - **Sijainti / Tiedosto:** `thesis/references/ai.bib`.
  - **Ongelma:** Pääsypäivien muotoilu vaihtelee (`Accessed: 2026-06-24` vs. `Accessed September 2026`).
  - **Korjausehdotus:** Yhtenäistä muotoilu tyylikkäästi.

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

- [ ] **Ohjaajan tutkintonimike ja suomenkielinen lyhennepiste (Kansilehti ja tiivistelmäpohjat):**
  - **Sijainti / Tiedostot:** `thesis/_meta.tex` (rivi 67) ja `thesis/_abstract.tex` (rivi 52).
  - **Nykyinen teksti:** `\advisor{Lauri Salminen (Msc)}` tiedostossa `_meta.tex` ja `\advisor{DI. Lauri Salminen}` tiedostossa `_abstract.tex`.
  - **Ongelma:**
    1. Englanninkielisessä tutkintonimikkeessä on pieni s-kirjain (`Msc` $\to$ `MSc`).
    2. Suomen kielen vakiintuneen oikeinkirjoituksen mukaan diplomi-insinöörin lyhenne `DI` kirjoitetaan ilman pistettä (`DI. Lauri Salminen` $\to$ `DI Lauri Salminen`).
  - **Korjausehdotus:** Korjaa muotoihin `Lauri Salminen (MSc)` ja `DI Lauri Salminen`.

- [ ] **Avainsanojen laajentaminen tiivistelmäpohjissa (Sivut 3 ja 4):**
  - **Sijainti / Tiedosto:** `thesis/_abstract.tex` (rivi 58).
  - **Nykyinen teksti:** `\keywords{RAG}`
  - **Ongelma:** Yksi ainoa lyhenneavainsana on liian suppea diplomityölle.
  - **Korjausehdotus:** Laajenna esim. `Retrieval-Augmented Generation, RAG, prompt caching, model cascading, semantic re-ranking, enterprise systems`.

- [ ] **Päivämäärien ja sivumäärän synkronointi palautushetkellä:**
  - **Sijainti / Tiedostot:** `thesis/_meta.tex` (rivi 51), `thesis/_abstract.tex` (rivi 56) ja `thesis/_preface.tex` (rivi 12).
  - **Huomio:** Päivitetään vastaamaan virallista jättöpäivämäärää, ja varmistetaan templateen tulostuva sivumäärä (`69+13`).
