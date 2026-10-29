# Chapter 6 (`thesis/chapters/06_discussion.tex`) — Audit & Revision TODO

Tämä tehtävälista sisältää kattavan tarkistuksen pohjalta tunnistetut korjauskohteet luvussa `thesis/chapters/06_discussion.tex`, jaoteltuna neljään tarkistuskategoriaan ja vakavuusasteeseen.

---

## 1. Tulokset, luvut ja taulukko-/yhtälöviittaukset (vs. Luku 5, Luku 4 ja taulukot)

- [x] **Rivi 50 (KRIITTINEN — Virheelliset latenssiluvut ja väite MuSiQue-vertailussa):**
  - **Nykyinen teksti:** `with S2B and baseline configurations exhibiting virtually identical median execution latencies (2,143.0\,ms versus 2,187.0\,ms, Table~\ref{tab:latency_musique}).`
  - **Ongelma:** Taulukossa `tab_latency_musique` (ja luvussa 5, rivi 9) Naive RAG Baseline -mediaanilatenssi ($T_{\text{total}} P_{50}$) on **1,359.0\,ms** ja S2B on **1,684.0\,ms** ($+23.9\%$, $p < 0.01$). Lukuja `2,143.0\,ms` ja `2,187.0\,ms` ei ole missään tulosluvun taulukossa, eivätkä konfiguraatiot ole MuSiQuessa latenssiltaan identtisiä, vaan S2B kasvattaa mediaanilatenssia tilastollisesti merkitsevästi ($+23.9\%^{**}$).
  - **Korjausehdotus:** `with S2B exhibiting a moderate latency increase over the baseline (1,684.0\,ms versus 1,359.0\,ms, $+23.9\%$, $p < 0.01$, Table~\ref{tab:latency_musique}), as multi-hop queries require expanding multiple disjoint parent passages.`

- [x] **Rivi 55 (KRIITTINEN — Virheellinen $Q$-arvo SR + PC + MC -konfiguraatiolle):**
  - **Nykyinen teksti:** `with only SR + PC + MC exhibiting worse performance ($Q = 0.1384$, Table~\ref{tab:quality_musique}).`
  - **Ongelma:** Taulukossa `tab_quality_musique` (rivi 33) ja `tab_pareto_musique` (rivi 33) konfiguraation `SR + PC + MC` Composite Quality on **$Q = 0.1209$** (ei `0.1384`). Järjestysväite (heikoin kaikista) pitää paikkansa, mutta numeroarvo on väärin.
  - **Korjausehdotus:** Vaihda `$Q = 0.1384$` $\rightarrow$ `$Q = 0.1209$`.

- [x] **Rivi 60 (Sijoitusvirhe Enterprise Raw -korpuksen laatutaulukossa):**
  - **Nykyinen teksti:** `In this uncurated setting, S2B + SR yields the third-highest composite quality overall ($Q = 0.6433$, ... Table~\ref{tab:quality_enterprise_raw}) ... compounding prompt caching into this pipeline (S2B + SR + PC) delivers the second-highest quality across the raw benchmark ($Q = 0.6466$, $+28.8\%$, $p < 0.01$)`
  - **Ongelma:** Taulukossa `tab:quality_enterprise_raw` kokonaislaadun ($Q$) kärkinelikko on:
    1. `S2B + PC` ($Q = 0.6633$)
    2. `S2B + MC` ($Q = 0.6492$)
    3. `S2B + SR + PC` ($Q = 0.6466$)
    4. `S2B + SR` ($Q = 0.6433$)
    Koska `S2B + MC` ($0.6492$) on toisena, `S2B + SR + PC` on todellisuudessa **kolmanneksi korkein** (tai toiseksi korkein ilman reititystä olevista) ja `S2B + SR` on **neljänneksi korkein** kokonaisuutena.
  - **Korjausehdotus:** Päivitä sijoitukset muotoon `fourth-highest` (`S2B + SR`) ja `third-highest` (`S2B + SR + PC`), tai täsmennä `among non-cascaded pipelines`.

- [x] **Rivi 64 (Virheellinen kustannussäästöhaarukka S2B + PC -suosituksessa):**
  - **Nykyinen teksti:** `deploy Small-to-Big retrieval with prompt caching (S2B + PC), which establishes the Pareto-optimal benchmark by maximizing composite quality ($Q = 0.6633$--$0.6711$) while securing a 66.3\%--70.7\% operational cost reduction.`
  - **Ongelma:** Lukua `66.3%` ei esiinny missään kustannustaulukossa (se on näppäilyvirhe edeltävästä laatuarvosta `$Q = 0.6633$`), ja `70.7%` on pelkän eristetyn `PC`:n säästö Enterprise Closed -korpuksessa. Konfiguraation `S2B + PC` todelliset kustannussäästöt ovat **63.9\%** (Enterprise Closed, `tab:cost_enterprise`), **78.4\%** (MuSiQue, `tab:cost_musique`) ja **52.3\%** (Enterprise Raw, `tab:cost_enterprise_raw`).
  - **Korjausehdotus:** Vaihda `66.3\%--70.7\%` $\rightarrow$ `63.9\%--78.4\%` (päävertailukorpukset) tai `52.3\%--78.4\%` (kaikki kolme korpusta).

- [x] **Rivi 76 (Harhaanjohtava taulukkovittaus nolla-arvojen osuudelle):**
  - **Nykyinen teksti:** `On the multi-hop MuSiQue benchmark, 68.8\%--77.5\% of queries contained at least one zero across configurations (Table~\ref{tab:quality_musique}), mainly due to model refusals or missing retrieval context.`
  - **Ongelma:** Lukema `68.8%--77.5%` on täysin oikein kyselytason raakadatan (JSONL) perusteella, mutta taulukossa `tab:quality_musique` ei ole saraketta nolla-arvojen osuudelle.
  - **Korjausehdotus:** Täsmennä muotoon `across the configurations in Table~\ref{tab:quality_musique}, query-level evaluation logs reveal that 68.8\%--77.5\% of answerable queries exhibited at least one zero score`.

- [x] **Rivit 22, 35, 37, 39, 43, 48, 50, 60 (Vaihekohtaisten telemetrialukujen lähdeviittaus Liitteeseen C):**
  - **Huomio:** Luvussa 6 hyödynnetyt tarkat osavaiheluvut ($T_{\text{stream}}$ 105.0\,ms vs. 147.0\,ms ja 131.5\,ms; $T_{\text{expand}}$ 1--2\,ms; vektorihaun $P_{95}$ 674.0\,ms vs. 317.0\,ms; reititysviive $P_{50} \approx 600$\,ms; mallikohtaiset reititys- ja kieltäytymisosuudet ehdollistettuna $CR$-arvoihin) ovat 100 % oikein ajolokeissa, mutta niitä ei ole listattu Luvun 5 päätaulukoissa.
  - **Korjausehdotus:** Lisää osavaihelatenssien yhteyteen (esim. rivit 22, 43, 50) viittaus Liitteen C osavaihekuvaajiin (`Appendix~\ref{sec:appendix_breakdowns}`, `Figure~\ref{fig:latency_breakdown_enterprise}`) ja mainitse kyselytason telemetria (`per-query execution telemetry`), jotta lukija tietää täsmälleen mistä luvut ovat peräisin.

---

## 2. Kirjallisuusviittaukset: Oikeellisuus ja relevanssi (vs. Luku 3 ja `references/ai.bib`)

- [x] **Rivi 20 (Terminologinen epätarkkuus: `prefix-reuse frameworks`):**
  - **Nykyinen teksti:** `and prefix-reuse frameworks demonstrate $2.2\times$ to $9.4\times$ latency reductions across long-context benchmarks \citep{lu2025turborag, yao2025cacheblend, wang2026fusionrag}`
  - **Ongelma:** TurboRAG (`lu2025turborag`), CacheBlend (`yao2025cacheblend`) ja FusionRAG (`wang2026fusionrag`, otsikoltaan *"From Prefix Cache to Fusion RAG Cache"*) eivät ole staattisia `prefix-reuse`-kehyksiä, vaan nimenomaan **dynaamisia palatason KV-välimuisti- ja fuusioarkkitehtuureja** (*dynamic chunk-caching / KV-fusion frameworks*), jotka ratkaisevat etuliitevälimuistin rajoitteita (vrt. rivi 52, jossa niitä kutsutaan oikein nimellä `dynamic chunk-caching architectures`).
  - **Korjausehdotus:** Vaihda `prefix-reuse frameworks` $\rightarrow$ `dynamic chunk-caching frameworks`.

- [x] **Rivi 62 (Menetelmällinen virhe: `wei2025instructrag` kutsuminen cross-encoderiksi):**
  - **Nykyinen teksti:** `In such environments, the cross-encoder operates as an indispensable noise gate \citep{moreira2024enhancing, wei2025instructrag}, filtering out distractors prior to relational parent expansion.`
  - **Ongelma:** InstructRAG (`wei2025instructrag`) ei käytä cross-encoderia, vaan kielimallin generoimia päättelyketjuja (*self-synthesized rationales*) kohinan suodattamiseen. Sen sijaan luvussa 3 (rivi 59) esitelty `\citet{cheng2026enhancing}` tutkii nimenomaan cross-encoder-uudelleenjärjestelijää kohinan ja hallusinaatioiden karsimisessa.
  - **Korjausehdotus:** Vaihda viittaus muotoon `\citep{moreira2024enhancing, cheng2026enhancing}` tai täsmennä virke muotoon: `the cross-encoder operates as an indispensable noise gate \citep{moreira2024enhancing, cheng2026enhancing}, filtering out distractors prior to relational parent expansion and mirroring the benefits of explicit context denoising \citep{wei2025instructrag}.`

- [x] **Rivi 43 (Sisäkkäiset sulkeet viittauksessa):**
  - **Nykyinen teksti:** `While local routing classifiers incur negligible overhead ($\approx 11$\,ms \citep{zhang2025ragrouter}),`
  - **Ongelma:** Tuottaa sulkeiden sisälle hakasulkeet `($\approx 11$\,ms [12])`. Lisäksi luvussa 3 (rivi 18) mainitaan rinnalla RouteLLM:n $<0.4\%$ kustannuslisä (`\citep{ong2025routellm}`).
  - **Korjausehdotus:** Muotoile esim.: `While local routing classifiers incur negligible latency ($\approx 11$\,ms as reported by \citet{zhang2025ragrouter}) and minimal cost overhead \citep{ong2025routellm},`

- [x] **Rivi 41 ja Rivi 48 (Kirjallisuuspeilauksen vahvistaminen Luvun 3 lähteillä):**
  - **Rivi 41:** Lisää `\citep{guo2026deepsieve}` viittauksen `\citep{jeong2024adaptive}` rinnalle, sillä myös DeepSieve raportoi Luvussa 3 (rivi 20) EM/F1-mittareiden säilyvän ennallaan adaptiivisessa reitityksessä.
  - **Rivi 48:** Kontrastoi `SR + MC` -yhdistelmän kontekstin pirstaloituminen (joka pudottaa laadun tasolle $Q = 0.5418$ korkeasta $CP = 0.683$ -arvosta huolimatta) Luvussa 3 (rivit 59–61) esiteltyihin InfoGain-RAG- (`\citep{wang2025infogain}`) ja RankRAG-tutkimuksiin (`\citep{yu2024rankrag}`), joiden mukaan aggressiivinen kontekstin karsiminen parantaa suoraan tarkkuutta.

---

## 3. Kieliasu, sujuvuus, välimerkit ja LaTeX-typografia

- [x] **Rivi 15 (Kaksoispiste puuttuu luvun rakennekuvauksen edeltä):**
  - **Nykyinen teksti:** `The remainder of this chapter is structured as follows. Section~\ref{subsec:disc_prompt_caching}...`
  - **Korjausehdotus:** Vaihda piste kaksoispisteeksi (`structured as follows:`), kuten luvuissa 1, 3 ja 4 (sekä `writing-style/SKILL.md` luku 6).

- [x] **Rivit 20, 39, 43 ja 50 (Monikon 1. persoonan `our`-pronominin poisto):**
  - **Nykyiset kohdat:** `in our evaluations` (rivi 20), `across our enterprise evaluations` (rivi 39), `in our pipeline` (rivi 43), `our deterministic relational architecture` (rivi 50).
  - **Ongelma:** Rikkoo `writing-style/SKILL.md` -ohjeistusta (*"Avoid... first-person pronouns"*) ja poikkeaa luvuista 1–5, joissa `our`-sanaa ei käytetä kertaakaan.
  - **Korjausehdotus:** Korvaa muodoilla `in the empirical evaluations`, `across the enterprise evaluations`, `in the benchmark pipeline` ja `the deterministic relational architecture implemented in this study`.

- [x] **Rivi 20 (Desimaalitarkkuus ja korpusnimen täsmennys):**
  - **Nykyinen teksti:** `$4.0\times$ to $10\times$` ja `on the enterprise corpus.`
  - **Korjausehdotus:** Yhtenäistä muotoon `$4.0\times$ to $10.0\times$` (vrt. `$2.2\times$ to $9.4\times$`) ja täsmennä `on the enterprise closed corpus.`

- [x] **Rivi 22 (Yhdysviiva, monikkomuoto ja desimaalit):**
  - **Nykyinen teksti:** `hierarchical context expansions` ... `counter-intuitive` ... `(105\,ms for PC versus 147\,ms for the baseline)`
  - **Korjausehdotus:** US-akateemisen englannin mukaisesti yksikkö `hierarchical context expansion`, ilman yhdysviivaa `counterintuitive`, sekä yhden desimaalin tarkkuus `(105.0\,ms for PC versus 147.0\,ms for the baseline)`.

- [x] **Rivi 24 (Yksikkömerkinnän epäyhtenäisyys `seconds` vs. `s`):**
  - **Nykyinen teksti:** `slashed $P_{95}$ execution time by 26.6\,seconds (falling from 30.9\,s to 4.3\,s`
  - **Korjausehdotus:** Vaihda `26.6\,seconds` $\rightarrow$ `26.6\,s` (ja halutessasi epämuodollinen verbi `slashed` $\rightarrow$ `curtailed` tai `reduced`).

- [x] **Rivi 26 ja Rivi 28 (Toistot ja subjektin/verbin kongruenssi):**
  - **Rivi 26:** `pre-allocated cache allocations` (tautologia) $\rightarrow$ `pre-allocated context caches`.
  - **Rivi 28:** `such as dynamic ... via frameworks such as FrugalGPT ... which bypass` $\rightarrow$ Vaihda jälkimmäinen `such as` sanaan `including`, ja vaihda `which bypass` yksikköön `which bypasses` (viittaa käsitteeseen `application-level semantic response caching`).

- [x] **Rivi 32 (Alaotsikon toisto ja ristiriita rakennekuvauksen kanssa):**
  - **Nykyinen teksti:** `\subsection{Model Cascading and Model Refusal Dynamics}`
  - **Ongelma:** Sana `Model` toistuu kahdesti, ja rivillä 15 otsikkoon viitataan muodossa `model cascading and the resulting query refusal dynamics`.
  - **Korjausehdotus:** `\subsection{Model Cascading and Query Refusal Dynamics}`

- [x] **Rivi 39 (KRIITTINEN — Välilyönti ennen ajatusviivaa `---`):**
  - **Nykyinen teksti:** `in regulated domains ---reflected in the perfect 100.0\% out-of-domain refusal accuracy across our enterprise evaluations (Table~\ref{tab:quality_enterprise})---enforcing it`
  - **Ongelma:** Ensimmäisen `---`-ajatusviivan edellä on välilyönti (`domains ---reflected`), kun taas jälkimmäisessä ei ole. LaTeXissa kolmen yhdysmerkin ajatusviiva (`---`) kirjoitetaan ilman välilyöntejä molemmin puolin.
  - **Korjausehdotus:** `in regulated domains---reflected in ... (Table~\ref{tab:quality_enterprise})---enforcing it`. Samalla arkikielinen idiomi `represents a double-edged sword` kannattaa korvata akateemisemmalla ilmaisulla `presents a critical operational tension`.

- [x] **Rivi 41 (Matemaattinen merkintätapa `Token F1` $\rightarrow$ `token $F_1$`):**
  - **Nykyinen teksti:** `Exact Match and Token F1` sekä `Token F1 remained stable`
  - **Korjausehdotus:** Muuta muotoon `token $F_1$` (tai `Token $F_1$`), kuten rivillä 76 ja Luvussa 4.

- [x] **Rivi 43 ja Rivi 55 (Vaillinaiset substantiivilausekkeet):**
  - **Rivi 43:** `enterprise closed median latency` $\rightarrow$ `median latency on the enterprise closed corpus`.
  - **Rivi 55:** `in enterprise closed` $\rightarrow$ `on the enterprise closed corpus`.

- [x] **Rivi 48 (Epämuodollinen `\text{k}`-lyhenne leipätekstissä):**
  - **Nykyinen teksti:** `from $\approx 8.1\text{k}$ characters down to $4.5\text{k}$ characters.`
  - **Korjausehdotus:** Kirjoita auki: `from approximately 8,100 characters down to 4,500 characters.`

- [x] **Rivi 52 (Monikollisen subjektin ja verbin kongruenssi):**
  - **Nykyinen teksti:** `mitigating the attention dilution and ``lost-in-the-middle'' decay \citep{liu2024lost} that commonly occurs`
  - **Korjausehdotus:** Subjekti on monikollinen (`dilution and decay`), joten verbin tulee olla `that commonly occur`.

- [x] **Rivi 55 (KRIITTINEN — Suorat lainausmerkit `"` ja matematiikkatilan pilkut):**
  - **Nykyinen teksti:** `highlights a cumulative "serialization tax", where ... ($P_{50} = 2,532.5$\,ms, $P_{95} = 6,531.1$\,ms in enterprise closed) ... rendering the all-in configuration`
  - **Ongelmat:**
    1. Suorat ASCII-lainausmerkit `"serialization tax"` tulostuvat LaTeXissa väärin (kaksi lopetuslainausmerkkiä). Käytä `` ``serialization tax'' ``.
    2. Pilkut matematiikkatilan sisällä (`$P_{50} = 2,532.5$`) lisäävät pilkun jälkeen ylimääräisen matemaattisen välin (`2, 532.5`). Käytä aaltosulkeita `$2{,}532.5$` ja `$6{,}531.1$`.
    3. Puhekielinen ilmaus `the all-in configuration` $\rightarrow$ `the fully compounded configuration`.

- [x] **Rivi 60, 64 ja 70 (Nimeämiskäytäntöjen ja välimerkkien yhtenäistäminen):**
  - **Rivi 60:** Vaihda `closed enterprise corpus` $\rightarrow$ `enterprise closed corpus` ja `Enterprise Raw corpus` $\rightarrow$ `raw enterprise corpus` (yhtenäinen Luvun 5 kanssa).
  - **Rivi 64:** `($Q = 0.6633$--$0.6711$)` $\rightarrow$ yhtäläisyysmerkki vaihteluvälin edessä on matemaattisesti epätarkka; käytä `($Q \in [0.6633, 0.6711]$)` tai `($Q$ spanning $0.6633\text{--}0.6711$)`.
  - **Rivi 70:** `Flash 3.6, 3.7, and 3.8` $\rightarrow$ `Gemini 3.6 Flash, 3.7 Flash, and 3.8 Flash`.

- [x] **Rivi 74, 76 ja 78 (Siirtymävirhe `Finally` sekä kahden viimeisen kappaleen akateeminen rekisteri):**
  - **Rivi 74:** Kappale 3/5 alkaa sanalla `Finally, the evaluated optimizations...`, vaikka sen jälkeen tulee vielä kaksi kappaletta (rivit 76 ja 78, joista rivi 78 alkaa sanalla `Lastly,`). Vaihda rivin 74 aloitus muotoon `Furthermore, the evaluated optimizations...` tai `From an architectural scope perspective, the evaluated optimizations...` ja rivin 78 aloitus muotoon `Finally,`.
  - **Rivi 76 (Rekisterin nosto):** Korvaa puhekieliset ilmaisut (`acts very strictly`, `causes the composite score ... to drop to`, `locks the median`, `creating many ties`, `as companies have near-zero tolerance`, `regex validators`) täsmällisellä akateemisella tyylillä (esim. `exhibits high penalization sensitivity`, `asymptotically constrains the resulting query score to`, `constrains the median composite quality ... inducing extensive ties that attenuate statistical power`, `regular expression validators`).
  - **Rivi 78 (Rekisterin nosto):** Korvaa arkikieliset ilmaukset (`is on par with commercial data centers`, `has different practical characteristics`, `must respond quickly to users under strict latency limits`) muodollisemmalla akateemisella rekisterillä (`provides computational throughput comparable to commercial hyperscaler environments`, `exhibits distinct operational characteristics`, `operate under strict Service Level Agreements (SLAs)`).

---

## 4. Yleisasu, looginen eteneminen ja arvosanan 5 rakenteelliset parannukset

- [x] **Rivit 1–6, 9–14, 29–31, 53–54, 65–66, 79–81 (Tiedoston siistiminen):**
  - Poista Aalto-templaten ohjekommentit (rivit 1–6) ja suomenkieliset luonnoskommentit (rivit 9–14).
  - Poista ylimääräiset tyhjät rivit alajaksojen ja kappaleiden välistä (jätä 1 tyhjä rivi kappaleiden väliin).

- [x] **Rivi 50 (Kappalesiirtymän pehmentäminen alajaksossa 6.3):**
  - **Ongelma:** Alajakso 6.3 käsittelee monimoduuliyhdistelmien synergioita, mutta sen toinen kappale (rivi 50) hyppää äkisti yksittäisen S2B-moduulin latenssiin (`Regarding latency, Small-to-Big retrieval introduced no computational bottleneck...`).
  - **Korjausehdotus:** Sido kappaleen aloitus monimoduuliteemaan: korosta, että S2B:n olematon Relaatiotietokanta-laajennusviive (`1--2\,ms`) ja pienempien lapsivektorien nopeampi haku ovat nimenomaan se *arkkitehtuurinen mahdollistaja*, jonka ansiosta S2B voidaan yhdistää PC:n ja MC:n kanssa ilman kumulatiivista latenssipullonkaulaa.

- [x] **Rivi 64 (Arkkitehtuurisuositusten näkyvyys alajaksossa 6.4):**
  - **Ongelma:** Koko diplomityön tärkeimmät kolme käytännön Pareto-arkkitehtuurisuositusta (rivi 64) on sijoitettu alajakson 6.4 (`\subsection{Corpus Topology and Retrieval Noise Thresholds}`) viimeiseksi kappaleeksi, jolloin ne jäävät kapean otsikon alle piiloon.
  - **Korjausehdotus:** Joko laajenna alajakson 6.4 otsikko muotoon `\subsection{Corpus Topology, Noise Thresholds, and Deployment Guidelines}` tai erota rivi 64 omaksi lyhyeksi synteesialajaksokseen ennen rajoitteita (`\subsection{Architectural Guidelines for Enterprise Deployment}`).

- [x] **Rivi 78 (Paikallisen Qwen3.8-27B-tuomarimallin käsittely validiteettiuhkana alajaksossa 6.5):**
  - **Ongelma:** Alajakson 6.5 (*Methodological Limitations and Threats to Validity*) viimeinen kappale kuvailee paikallisen Qwen3.8-27B-arviointimallin ajamista CSC:n klusterissa pelkästään tuotantoetuna ja kustannussäästövinkkinä, mutta ei mainitse siihen liittyvää *metodologista rajoitetta / validiteettiuhkaa*.
  - **Korjausehdotus:** Lisää virke, joka tunnistaa validiteettiuhan: vaikka paikallinen ajo ($T = 0.0$) poistaa pilvi-API-vaihtelun ja toimittajakohtaisen suosimisvinouman (*vendor self-preference bias*), 27 miljardin parametrin avoimen kielimallin käyttö automaattisena tuomarina voi silti sisältää kalibrointieroja verrattuna ihmisasiantuntijoihin tai suurempiin frontier-tason tuomarimalleihin (erityisesti monimutkaisissa suomenkielisissä ja monihyppyisissä synteeseissä).
