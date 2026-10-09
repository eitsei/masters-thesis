# TODO & Havaintoloki

## Testiajoihin valmistautumisen ja moniagenttitarkastuksen havainnot (9.10.2026)

### 🚨 Kriittiset korjattavat ennen testiajoja
- [x] **[KRIITTINEN] MuSiQue-aineiston kysymys-ID:iden törmäys (`musique_100_final.jsonl` & `MusiqueDatasetReader.java`):**
  - Korjattu: `MusiqueDatasetReader.parseLine()` suffiksoi vastaamattomat kysymykset `_unans`-päätteellä (`id + "_unans"`), jolloin kaikki 4 834 kysymystä indeksoituvat muistiin ilman ylikirjoituksia ja `findById()` tukee automaattista fallback-hakua.
  - Vahvistettu: `musique_100_final.jsonl` päivitetty deterministisesti; sisältää tasan 100 riviä ja tasan 100 uniikkia ID:tä (rivi 4 `2hop__837900_71701`, rivi 84 `2hop__837900_71701_unans`). Simuloitu `collector.py`-checkpointointi kerää täydet 100/100 kysymystä.

### ⚙️ Konfiguraatio- ja ympäristövalmistelut (`rag-benchmark` & `rag-evaluator`)
- [ ] **Tuotantomallien oletukset `application.yml`-tiedostossa vs. Luvun 4 määrittely (syyskuu 2026):**
  - `application.yml`:ssä oletukset:
    - `rag.models.google-genai.flash: gemini-3.5-flash` $\to$ gradussa `gemini-3.8-flash`
    - `rag.models.google-genai.pro: gemini-3.1-pro-preview` $\to$ gradussa `gemini-3.1-pro`
    - `text.model: gemini-embedding-001` $\to$ gradussa `gemini-embedding-2` (768 d MRL)
  - *Toimenpide:* Päivitä oletusarvot tiedostoon [`rag-code/rag-benchmark/src/main/resources/application.yml`](rag-code/rag-benchmark/src/main/resources/application.yml) tai varmista ympäristömuuttujat (`GOOGLE_GENAI_CHAT_MODEL`, `GOOGLE_GENAI_PRO_MODEL`, `GOOGLE_GENAI_EMBEDDING_MODEL`) ennen ajoja.
- [ ] **RAGAS LLM-tuomarin asetus tuotantoajoihin (`rag-evaluator`):**
  - Koodissa on täysi Claude Sonnet 5 -tuki Vertex AI Model Gardenissa (`judge_factory.py`), mutta `.env`- ja `evaluator.py`-tiedostojen oletuksena on kehitysaikainen `gemini-3.5-flash-lite` / `ollama`.
  - *Toimenpide:* Varmista, että virallisissa testiajoissa ajetaan parametreilla `--judge claude --judge-model claude-sonnet-5` (tai `JUDGE_PROVIDER=claude`, `JUDGE_MODEL=claude-sonnet-5`).
- [ ] **Täyden otoskoon varmistus ajoskripteissä (`LIMIT=100`):**
  - Skripteissä [`run_musique_all16.sh`](rag-code/rag-evaluator/scripts/run_musique_all16.sh) ja [`run_nordea_closed_all16.sh`](rag-code/rag-evaluator/scripts/run_nordea_closed_all16.sh) oletusarvo on `LIMIT="${LIMIT:-10}"` (pilot-testaus).
  - *Toimenpide:* Aseta oletukseksi `LIMIT=100` tai aja komennolla `LIMIT=100 ./scripts/run_...` täyden 3 200 kutsun matriisin ($16 \times 100 \times 2$) tuottamiseksi.

### 📝 Tutkielmatekstin tarkennukset (`thesis/chapters/04_research_methods.tex`)
- [x] **Kieltäytymislausekkeen suomenkielinen vastine (§4.4 & §4.5.3):**
  - Luvussa §4.4 (r. 142) lisätty maininta suomenkielisestä kanonisesta kieltäytymislausekkeesta (*"Annettu konteksti ei sisällä riittävästi tietoa kysymykseen vastaamiseksi."*) ja englanninkielisen fraasin roolista hyväksyttynä aliaksena.
  - Luvussa §4.5.3 (r. 265) tarkennettu indikaattorifunktio $\mathbb{I}_{\text{refusal}}(q)$ ottamaan huomioon englanti (MuSiQue) ja suomi (Nordea).
- [x] **Small-to-Big terminologian tarkistus (Luku §4.3):**
  - Tarkistettu: Luvun §4.3 leipäteksti (r. 126) ja Taulukko 4.3 käyttävät jo valmiiksi oikeaa parent-ID -deduplikointitermiä. Ei ristiriitaa.

---

## Metodit vs. Koodi -tarkastuksen havainnot ja korjaukset (8.10.2026)

### Luku 4 (`thesis/chapters/04_research_methods.tex`) & Metoditeksti
- [x] **M0-chunkkaus ja overlappiteksti (§4.3 Table 4.3 & r. 100, 123):**
  - Tekstissä väitetään 512 tokenia ja 50 tokenin overlap. Spring AI:n `TokenTextSplitter` ei tue overlappia, eikä sitä ole koodissa. Lisäksi MuSiQue tallennetaan kappaleittain (~100–150 tok) sellaisenaan ilman splitteriä, ja Nordea-kappaleet ovat alle 512 tokenia.
  - *Korjaus:* Poistettu maininta 50 tokenin overlapista ja kuvattu todellinen kappalepohjainen / 512 tokenin ylärajalla toimiva lohkominen rehellisesti.
- [x] **M1 Boundary Interval-Merging -kuvaus (§4.3 r. 126):**
  - Tekstissä väitetään, että päällekkäiset parent-ikkunat yhdistetään intervallien yhdistämisellä (interval-merging). Todellisuudessa koodi tekee deduplikoinnin parent-ID:n mukaan, mutta varsinaista intervallien yhdistämistä ei ajeta, koska ingestiossa ei aseteta `window_start`/`window_end`-metatietoja.
  - *Korjaus:* Muotoiltu teksti kuvaamaan toteutunut logiikka: deduplikointi parent-ID:n mukaan säilyttää korkeimman relevanssijärjestyksen; poistettu väite intervallien yhdistämisestä.
- [x] **M4 Routerin timeout-arvo (§4.3 r. 224 TODO vs koodi):**
  - Tarkistettu: lopullisessa Luvun 4 leipätekstissä ei mainita aikakatkaisua (vanha 1 500 ms luonnoskommentti poistettu jo aiemmin). Koodissa käytettävä 5 000 ms on taustajärjestelmän tekninen fallback-varmistus, jota ei tarvitse erikseen avata leipätekstissä. Ei ristiriitaa.
- [x] **Pilvideployauksen korvaaminen lokaaliajolla (GCP europe-north1 vs lokaali suoritus):**
  - Alkuperäinen idea oli ajaa järjestelmä GCP:ssä samassa konesalissa (`europe-north1`) LLM:n kanssa. Koska frontier-mallit (Gemini 3.x) vaativat Googlen `global`-endpointin eikä niitä voi kiinnittää yhteen alueelliseen konesaliin, pilveen vieminen ei poista verkkoviiveitä. Järjestelmä ajetaan hallitusti lokaalisti API-kutsujen yli.
  - *Korjaus teksteihin:*
    - [x] **Luku 4.2 (§4.2 r. 81–82):** Poistettu väite `europe-north1` VPC- ja Cloud SQL -deployauksesta. Kuvattu lokaali laptop workstation (Intel Core Ultra 7, 32 GB RAM) + PostgreSQL (pgvector) -suoritusympäristö ja ulkoiset suojatut HTTPS API -kutsut GCP global endpointeihin.
    - [x] **Luku 4.6 (Internal Validity r. 457):** Päivitetty verkkolatenssin hallintakeinot lokaaliajoon (2 unmeasured warm-up -kyselyä, parillinen Wilcoxon-testi, mikrovaihetelemeteria).
    - [ ] **Luku 6 (Discussion / Threats to Validity):** Dokumentoi reititysrajoite (Google GenAI Global endpoint) ja perustele, miksi parillinen vertailu ($C_k$ vs $C_0$) ja mikrovaiheiden mittaus ($T_{\text{embed}}$, $T_{\text{ret}}$, $T_{\text{rerank}}$ jne.) eristävät optimointien todelliset hyödyt verkkoviiveestä.
- [ ] **Puhutaanko enterprisesta vai companysta? Kumppaniyhtiön termistö:**
  - Varmista, että kumppaniin viitataan yhtenäisellä termistöllä kaikkialla tutkielmassa ja liitteissä.
- [ ] **Viimeistele tekstit ja kieliasu:**
  - Tarkista väliviivat (`--`), ajatusviivat (`---`) ja termistö. Keskustele vielä Laurin kanssa läpi.
  - Päivitä Judge-malli teksteihin ja varmista krediitit.

### Taustajärjestelmä (`rag-benchmark`)
- [x] **Generointimallin lämpötila (Temperature):**
  - `RagQueryService.java`:ssa generointikutsulle ei aseteta eksplisiittistä lämpötilaa (käyttää oletusta $T=1.0$), toisin kuin routerissa ($T=0.0$) tai evaluointituomarissa ($T=0.0$).
  - *Korjaus:* Asetettu `temperature: 0.0` oletukseksi `GenerationProperties.java`- ja `application.yml`-tiedostoihin sekä eksplisiittisesti `GoogleGenAiChatOptions`-olioon `RagQueryService`:ssä determinismin varmistamiseksi.
- [ ] **HNSW-indeksin parametrit kantaan:**
  - Teksti määrittelee $M=16, ef\_construction=64, ef\_search=40$. Koodissa käytetään pgvectorin oletuksia ilman eksplisiittistä määrittelyä.
  - *Huomio:* pgvectorin sisäänrakennetut oletusarvot ovat täsmälleen $M=16, ef\_construction=64, ef\_search=40$, joten teksti vastaa oletuksia suoraan.
- [x] **M4 Quota 429 / Pro preview RPM Rate Limit retry-logiikka:**
  - Peräkkäiset Pro-mallin kutsut voivat ylittää 5 RPM -kiintiön.
  - *Korjaus:* Toteutettu `RagQueryService`-luokkaan automaattinen retry-backoff (oletus: 3 yritystä, 5 000 ms odotus, konfiguroitavissa `rag.generation.retry-max-attempts` ja `retry-backoff-ms`). Latenssin laskenta ja Micrometer-ajastimet resetoitu per yritys, jolloin vain onnistunut kutsu mitataan eikä backoff-aika vääristä tuloksia.
- [ ] **Ajoskriptien oletusotoskoko:**
  - Ajoskripteissä (esim. `run_musique_all16.sh`) oletus on `LIMIT="${LIMIT:-10}"`.
  - *Muistutus:* Varmista täysissä koeajoissa aina `LIMIT=100`.

---

## N=10 Testiajojen löydökset ja kriittiset korjaukset (1.10.2026)

### Evaluointi- ja metriikkakorjaukset (`rag-evaluator`)
- [x] **M3-kustannuslaskennan tuplalaskutusbugi (`cli.py` rivi 244):**
  - Kutsussa on kovakoodattu `prompt_is_uncached=True`. Koska `promptTokens` sisältää jo `cachedTokens`-määrän, staattinen prefiksi laskutetaan kahteen kertaan (100 % perushinta + 10 % välimuistimaksu), ja M3 näyttää virheellisesti kalliimmalta kuin C0.
  - *Korjaus:* Vaihda `prompt_is_uncached=False`, jolloin todellinen ~69 % kustannussäästö toteutuu oikein.
- [x] **Composite Quality (CompQ) -suodatusvirhe (`reporter.py` rivi 194):**
  - Ehto `valid_comp_qs = [q for q in comp_qs if q > 0.001]` luulee arvoa ~0.000399 roskadataksi ja suodattaa aiheettomat kieltäytymiset (False Refusal) pois CompQ-keskiarvosta. Tämän vuoksi esim. C7:n CompQ oli harhaanjohtavasti 0.928, vaikka Faithfulness oli vain 0.625.
  - *Korjaus:* Poista `q > 0.001` -suodatin, jotta epäonnistumiset huomioidaan rehellisesti laatumittareissa.
- [x] **Mallikatalogin hinnoittelutarkennus (`metrics.py`):**
  - `PRICING_CATALOG` ei sisällä avainta `gemini-3.5-flash`. Apumetodin osamerkkijonohaku antaa sille tällä hetkellä Flash-Liten puolitetun hinnan.
  - *Korjaus:* Lisää eksplisiittinen `gemini-3.5-flash`-hinta katalogiin.

### Taustajärjestelmän ja suorituksen korjaukset (`rag-benchmark`)
- [x] **Implisiittisen välimuistin poiskytkentä GCP-projektitasolla (M3-eristys & Internal Validity):**
  - Gemini 3.x -mallit käyttävät oletuksena automaattista implisiittistä prefix-välimuistia (kynnys: 4 096 tai 6 144 tokenia). Jotta peruskonfiguraatiot (C0, C4 jne.) eivät saa automaattisia alennuksia ja M3:n eksplisiittisen CachedContentin vaikutus voidaan mitata täysin puhtaana, implisiittinen välimuisti on kytketty pois päältä GCP-projektin asetuksista (`disableCache: true`).
  - Verifiointi ja automaattinen suojaus: varmistettu savutesteillä ja lisätty automaattinen pre-flight -varmistus `rag-evaluator`-ajuriin (`cli.py test-cache` ja `_verify_cache_preflight`), joka estää ajon mikäli ei-M3-ajossa cacheReadInputTokens > 0 tai M3-ajossa cacheReadInputTokens == 0.
- [x] **M4 Quota 429 / Pro preview RPM Rate Limit (MuSiQue C4 N=9):**
  - Peräkkäiset Pro-mallin kyselyt ylittivät 5 RPM -kiintiön.
  - *Korjaus:* Lisätty `RagQueryService`-luokkaan automaattinen retry-backoff 429- ja kiintiövirheille. Vain onnistunut generointi kirjataan latenssiin ja metriikoihin.
- [x] **M3 Context Cachen 300 s (5 min) TTL -latenssipiikit:**
  - LLM-as-a-judge -arvioinnin kestäessä kyselyiden välissä, 5 minuutin TTL ehti vanheta kesken ajon, mikä pakotti luomaan uuden välimuistin synkronisesti (kesti 46–70 s ja aiheutti P95-latenssipiikit).
  - *Korjaus:* Nostettu välimuistin oletus-TTL 3600 sekuntiin (60 minuuttiin / 1 tunti) tiedostoissa `application.yml` ja `PromptCacheProperties.java`. Tämä takaa kaikkien mallikohtaisten välimuistien (Lite, Flash, Pro) säilymisen koko mittausajon ajan.


### Tutkielman taulukkovienti ja Luvun 5 taulukkoviittaukset (`05_results.tex`)
- [x] **Luvun 5 `tab_*`-taulukoiden vienti ja päivitys (`export-thesis` vs `05_results.tex`):**
  - **Ongelma:** [`05_results.tex`](file:///home/eitsei/dippa/thesis/chapters/05_results.tex) viittaa `\input`-komennoilla vanhoihin taulukoihin:
    - `tables/musique/tab_latency_musique.tex` (24.9.)
    - `tables/musique/tab_cost_musique.tex` (24.9., Taulukko 7, jossa `Cached Prompt Tokens = 0.0`)
    - `tables/musique/tab_quality_musique.tex` (24.9.)
    - `tables/musique/tab_pareto_musique.tex` (24.9.)
    - `tables/enterprise_closed/tab_latency_enterprise.tex` (28.9.)
    - `tables/enterprise_closed/tab_cost_enterprise.tex` (28.9.)
    - `tables/enterprise_closed/tab_pareto_enterprise.tex` (28.9.)
  - Evaluointityökalun [`src/cli.py export-thesis`](file:///home/eitsei/dippa/rag-code/rag-evaluator/src/cli.py#L756) ei tällä hetkellä generoi noita `tab_*`-taulukoita (se luo vain `benchmark_summary.tex`, `hop_breakdown.tex`, `wilcoxon_*.tex` ja `tab_quality_enterprise.tex`).
  - **Korjaus:**
    - Lisää `src/export_latex.py`- ja `src/cli.py`-moduuleihin automaattinen vienti Luvun 5 taulukoille (`tab_latency_*`, `tab_cost_*`, `tab_quality_*`, `tab_pareto_*`) suoraan ajetusta JSONL-tuloshakemistosta, tai päivitä `05_results.tex` käyttämään `export-thesis`:n tuottamia dekomponoituja taulukoita (`wilcoxon_rq1_latency`, `wilcoxon_rq2_cost` jne.).
    - Aja taulukkovienti uudelleen tuoreimmille `musique_all16_final` ja `enterprise_closed` -ajoille, jotta Taulukkoon 7 päivittyy oikea ~7 153 tokenin välimuistiosuma.
- [x] **Kuvien ja taulukoiden aikaleimaristiriita Luvussa 5:**
  - `figures/musique/*` ja `figures/enterprise_closed/*` on generoitu 1.10. uusimmista ajoista, kun taas taulukot `tab_*` ovat peräisin 24.–28.9. Tämän vuoksi samaa konfiguraatiota esittävä taulukko ja viereinen kuvaaja näyttävät eri lukuja (esim. Taulukko 7 vs Kuva 5.3). Taulukot ja kuvat tulee generoida aina synkronoidusti samasta hyväksytystä testiajosta.
- [x] **Kovakoodattu "($N = 100$)" taulukko-otsikoissa (`tab_quality_*` ja `export_latex.py` rivi 673):**
  - Otsikossa lukee `($N = 100$)`, vaikka data on $N = 10$ testiajosta (josta seuraa esim. Exact Match 11.1 % ja FRR 12.5 %).
  - *Korjaus:* Tee otsikon $N$-arvosta dynaaminen (`N = {len(records)}`) `export_latex.py`:ssä tai varmista että $N=100$ -otsikkoa käytetään vasta kun lopullinen täysi aineisto on ajettu.
- [x] **Kovakoodattu MuSiQue-teksti Enterprise-taulukon otsikossa (`hop_breakdown.tex` / `export_latex.py` rivi 120):**
  - `export_hop_breakdown_latex`-funktiossa kuvateksti on kovakoodattu: `"Benchmark evaluation performance stratified by MuSiQue reasoning depth tiers."`, jolloin Enterprise-kansioihin vietäessä otsikko väittää aineistoa virheellisesti MuSiQueksi.
  - *Korjaus:* Tee otsikosta aineistoparametrin mukaan mukautuva (`f"Benchmark evaluation performance stratified by {dataset_name} reasoning depth tiers."`).
- [x] **Tuplaleimat (`\label`) kuvaympäristöissä (`05_results.tex` ja `0C_appendix.tex`):**
  - Rinnakkaiset `\label{fig:quality_lollipop_*}` ja `\label{fig:quality_radar_*}` samassa `figure`-lohkossa (riveillä 67–68 ja 80–81 Luvussa 5 sekä 27–28 Liitteessä C) voivat aiheuttaa LaTeX-viittausvirheitä ja varoituksia.
  - *Korjaus:* Yhtenäistä tekstiviittaukset käyttämään ensisijaista lollipop-leimaa ja poista vanhentuneet radar-leimat.
- [x] **Päivitä taulukko-ohjeistus (`thesis/tables/README.md`):**
  - Päivitä dokumentaatio vastaamaan nykyistä rakennetta (`enterprise/`, `enterprise_closed/`, `musique/` ja Luvun 5 `tab_*`-tiedostot vanhan `nordea/`-rakenteen sijaan). 