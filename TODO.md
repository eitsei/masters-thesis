# TODO & Havaintoloki

## Testiajoihin valmistautumisen ja moniagenttitarkastuksen havainnot (9.10.2026)

### 🚨 Kriittiset korjattavat ennen testiajoja
- [x] **[KRIITTINEN] MuSiQue-aineiston kysymys-ID:iden törmäys (`musique_100_final.jsonl` & `MusiqueDatasetReader.java`):**
  - Korjattu: `MusiqueDatasetReader.parseLine()` suffiksoi vastaamattomat kysymykset `_unans`-päätteellä (`id + "_unans"`), jolloin kaikki 4 834 kysymystä indeksoituvat muistiin ilman ylikirjoituksia ja `findById()` tukee automaattista fallback-hakua.
  - Vahvistettu: `musique_100_final.jsonl` päivitetty deterministisesti; sisältää tasan 100 riviä ja tasan 100 uniikkia ID:tä (rivi 4 `2hop__837900_71701`, rivi 84 `2hop__837900_71701_unans`). Simuloitu `collector.py`-checkpointointi kerää täydet 100/100 kysymystä.

### ⚙️ Konfiguraatio- ja ympäristövalmistelut (`rag-benchmark` & `rag-evaluator`)
- [x] **Tuotantomallien oletukset `application.yml`-tiedostossa vs. Luvun 4 määrittely (syyskuu 2026):**
  - Tarkistettu: Koodin oletusarvot (`gemini-3.5-flash`, `gemini-3.5-flash-lite`, `gemini-3.1-pro-preview`, `gemini-embedding-001`) vastaavat täsmälleen Luvun §4.2 (r. 84), Taulukon 4.3 (r. 117) ja Liitteen B hinnaston määrittelyjä. Tekstit ja konfiguraatiot ovat 1:1 linjassa ilman ristiriitoja.
- [x] **RAGAS LLM-tuomarin asetus tuotantoajoihin (`rag-evaluator`):**
  - Evaluointituomariksi vahvistettu paikallinen `vllm` (`qwen3.8-27b` + `bge-m3`), joka ei kuluta pilvikrediittejä. `judge_factory.py` siivottu tukemaan oletuksena `vllm`-tuomaria (sekä vaihtoehtona `vertex-gemini`). Claude-, Ollama- ja Vertex MaaS -koodit poistettu tarpeettomina.
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
- [x] **Puhutaanko enterprisesta vai companysta? Kumppaniyhtiön termistö:**
  - Tarkistettu: Tekstissä käytetään johdonmukaisesti termiä *partner enterprise* (Luku 4 r. 143, Liite A r. 1). Yrityksen nimeä ei mainita leipätekstissä, ja termistö on yhdenmukainen.
- [ ] **Viimeistele tekstit ja kieliasu:**
  - Tarkista väliviivat (`--`), ajatusviivat (`---`) ja termistö. Keskustele vielä Laurin kanssa läpi.
  - [x] Päivitä Judge-malli teksteihin ja varmista krediitit: Evaluointituomariksi vahvistettu tutkielman mukainen Qwen3.8-27B (vLLM local), joka ei kuluta GCP-krediittejä. Tutkielmatekstiä ei muuteta (käyttää jo valmiiksi Qwen3.8-27B:tä). Judge-koodi siivottu tukemaan vLLM:ää oletuksena.

### Taustajärjestelmä (`rag-benchmark`)
- [x] **Generointimallin lämpötila (Temperature):**
  - `RagQueryService.java`:ssa generointikutsulle ei aseteta eksplisiittistä lämpötilaa (käyttää oletusta $T=1.0$), toisin kuin routerissa ($T=0.0$) tai evaluointituomarissa ($T=0.0$).
  - *Korjaus:* Asetettu `temperature: 0.0` oletukseksi `GenerationProperties.java`- ja `application.yml`-tiedostoihin sekä eksplisiittisesti `GoogleGenAiChatOptions`-olioon `RagQueryService`:ssä determinismin varmistamiseksi.
- [x] **HNSW-indeksin parametrit kantaan:**
  - Vahvistettu: Teksti määrittelee $M=16, ef\_construction=64, ef\_search=40$. pgvectorin sisäänrakennetut oletusarvot ovat täsmälleen $M=16, ef\_construction=64, ef\_search=40$, joten kanta toteuttaa metodin suoraan ilman erillistä koodimuutosta.
- [x] **M4 Quota 429 / Pro preview RPM Rate Limit retry-logiikka:**
  - Peräkkäiset Pro-mallin kutsut voivat ylittää 5 RPM -kiintiön.
  - *Korjaus:* Toteutettu `RagQueryService`-luokkaan automaattinen retry-backoff (oletus: 3 yritystä, 5 000 ms odotus, konfiguroitavissa `rag.generation.retry-max-attempts` ja `retry-backoff-ms`). Latenssin laskenta ja Micrometer-ajastimet resetoitu per yritys, jolloin vain onnistunut kutsu mitataan eikä backoff-aika vääristä tuloksia.
- [ ] **Ajoskriptien oletusotoskoko:**
  - Ajoskripteissä (esim. `run_musique_all16.sh`) oletus on `LIMIT="${LIMIT:-10}"`.
  - *Muistutus:* Varmista täysissä koeajoissa aina `LIMIT=100`.

## Diplomityön korjaukset ja tarkistettavat kohdat

### Luku 4: Metodologia ja arkkitehtuuri
- [ ] **MuSiQue & Small-to-Big ($M_1$) parent-ikkunat (Luku 4.3 / 4.4):** Siirrä tai kopioi selitys siitä, miten MuSiQuen 1024 tokenin parent-ikkunat muodostetaan (yhdistämällä saman Wikipedia-artikkelin otsikon alla olevat kappaleet) luvusta 4.6 (*Threats to Validity*, s. 36) jo lukuun 4.3 ($M_1$, s. 28) tai 4.4 (s. 29). Selvennä samalla lyhyesti, miten 128 tokenin child-chunk eroaa baselinesta, kun MuSiQuen kappaleet ovat luonnostaan ~100–150 tokenia.
- [ ] **Enterprise Closed & Small-to-Big ($M_1$) hierarkia (Luku 4.3 / Liite A):** Täsmennä, miten 1024 tokenin parent-ikkuna muodostetaan *Enterprise Closed* -korpuksessa (haetaanko parent-konteksti alkuperäisestä kokonaisesta Markdown-dokumentista vai valituista candidate passageista).
- [ ] **Kysymysten vaikeustasot vs. $M_4$ reititys (Luku 4.3 / 4.4):** 
  - Mainitse luvussa 4.3/4.4, että vaikka MuSiQue sisältää vain 2-hop, 3/4-hop ja unanswerable -kysymyksiä (ei 1-hop-kysymyksiä), zero-shot-reititin voi silti luokitella kysymyksiä 1-hop-tasolle (Flash-Lite).
  - Täsmennä Enterprise-datasetin osalta (40 % single-hop, 40 % multi-hop, 20 % OOD), miten reitittimen 2-hop vs. 3/4-hop -jako suhtautuu multi-hop-kysymyksiin.
- [ ] **Terminologian yhtenäistäminen ($M_4$):** Yhtenäistä termien *Model Cascading (MC)* ja *Hybrid Model Routing ($M_4$)* käyttö (Taulukko 2 vs. Taulukko 4 ja luku 4.3). Tee selväksi, että työssä cascading/routing viittaa luokittelijapohjaiseen etukäteisreititykseen (*pre-generation routing*).
- [x] **Yhtälö 14 (Reitittimen kustannus, s. 31):** Täsmennä tekstissä, että $N_{\text{route}}(q) \cdot P_{\text{route}}$ huomioi sekä reititinmallin (Flash-Lite) syöte- että tulostetokenien hinnoittelun (tai merkitse se muodossa $C_{\text{route}}(q)$).
- [x] **Yhtälöt 23 ja 24 (QPD ja QPS, s. 34):** Muuta yhtälöt konfiguraatiotason (pipeline-level) mittareiksi käyttäen aggregoituja arvoja ($\bar{Q}$, $\text{Cost}_{1\text{k}}$ ja $T_{\text{total}, P_{50}}$), jotta ne vastaavat Taulukoiden 11, 12 ja C4 laskentatapaa ja alaviitteitä.
- [x] **Yhtälö 20 (Composite Quality $Q(q)$, s. 33):** Mainitse tekstissä eksplisiittisesti, että taulukoissa raportoitava konfiguraatiotason $Q$ on kysymyskohtaisten harmonisten keskiarvojen $Q(q)$ aritmeettinen keskiarvo $\bar{Q}$.
- [x] **Yhtälö 12 ($T_{\text{embed}}(q)$ vs. $T_{\text{ret}}(q)$, s. 30):** Koska $T_{\text{embed}}$ mitataan yhdessä $T_{\text{ret}}$-vaiheen kanssa eikä sitä erotella kuvioissa 1, 2 ja C1, yhdistä ne yhtälössä 12 tai mainitse suoraan, että kuvioiden ja taulukoiden $T_{\text{ret}}$ sisältää $T_{\text{embed}}$-osuuden.
- [x] **Taulukko 3 (s. 27):** Poista ylimääräinen sulkumerkki `)` riviltä RQ3 sarakkeesta *Null Hypothesis ($H_0$)*.
- [x] **Merkintä $M_0$ vs. $C_0$ (s. 27–28):** Vaihda baselinen merkintä luvussa 4.3 ja Taulukossa 4 muotoon $C_0$ (tai perustele $M_0$), jotta se on linjassa luvun 4.1 ja Taulukon 2 kanssa.

### Luvut 1–3, alkuosa ja lähdeluettelo
- [x] **Päivämäärät ja placeholderit (s. 3–5):** Päivitä tiivistelmien (s. 3–4) ja Prefacen (s. 5) päivämäärät (`30 June 2025` -> `2026`) sekä korvaa Prefacen templaattinimet.
- [x] **Glossary & Abbreviations (s. 11–12):** Lisää puuttuvat keskeiset lyhenteet: `S2B`, `SR`, `PC`, `MC`, `E2E`, `QPD`, `QPS`, `FRR`, `OOD`, `ICC`, `DCC`, `MRL`, `HNSW`, `KV`, `EM`, `BLEU`, `ROUGE`, `NDCG`, `SLA`.
- [x] **Luku 1 – Prompt Caching -kuvaus (s. 13):**
  - Korjattu: Teksti luvussa §1 (r. 128) päivitetty kuvaamaan prefill-vaihetta nopeuttavaa ja syötetokenien hintaa alentavaa avain-arvo-tilan (KV) prefix-välimuistia (commit `c1cefc1`).
- [x] **Luku 2 – Lyöntivirheet (s. 17–18):**
  - s. 17: Poista tautologia `subsequently, after which` -> jätä vain `after which` tai `subsequently`.
  - s. 18: Lisää välilyönti `ifthe` -> `if the`.
- [x] **Luku 3 – Muotoilu- ja lyöntivirheet (s. 19–20):**
  - s. 19: Poista piste yhtälön (3) perästä, jotta lause jatkuu oikein sanalla `maps`.
  - s. 20: Korjaa sulkeen jälkeinen välilyönti `( 56%)` -> `(~56%)` tai `(56%)`.
  - s. 20: Lisää välilyönti ennen viitettä `summaries[37]` -> `summaries [37]`.
- [x] **References – BibTeX-korjaukset (s. 54–59):**
  - Viite `[1]`: Korjaa URL `https:///hai.stanford.edu/...` -> `https://hai.stanford.edu/...`.
  - Viite `[19]`: Poista ylimääräinen piste etunimen perästä (`Numa. Dhamani.`).
  - Suojaa akronyymit aaltosulkeilla `{}` BibTeXissä (esim. `{RAG}`, `{LLM}`, `{LLMs}`, `{NLP}`, `{GPU}`, `{GPUs}`, `{Q&A}`, `{KV}`, `{BC}`, `{AI}`, `{Spring AI}`, `{FrugalGPT}`, `{RankRAG}`, `{RAGRouter}`, `{Stop-RAG}`, `{Bridge-RAG}`, `{InfoGain-RAG}`, `{InstructRAG}`, `{RAGCache}`, `{TurboRAG}`, `{CacheBlend}`) viitteissä `[2, 4, 6, 7, 9, 11, 25, 27, 35, 36, 37, 39, 41, 43, 44, 45, 46, 47, 48]`.

### Mittausputki ja visualisointiskriptit (tarkistettava ennen täysiä ajoja)
- [ ] **Taulukko C2 (s. 65) – PC-konfiguraatioiden token-sarakkeet:**
  - Syy selvitetty: Vanha 25.9. pilottiajo (`runs/nordea_all16_final`) palautti `EXPLICIT_MISS` (esilämmitys puuttui), jolloin Google ei palauttanut `cachedTokens`-lukemia vaan kirjoitustokenit (`cacheWriteTokens: 5854`). `export_latex.py` luki vain luettuja tokeneita. Vaatii Enterprise Raw -ajon toiston `enterprise_closed`-putken tavoin.
- [x] **Kuviot 3, 4 ja C2 vs. Taulukot 7, 8 ja C2 (PC-kustannukset):**
  - Korjattu: Tiedostossa `plot_results.py` vähennetään nyt `cached_read_tok` prompt-tokeneista (`uncached_p_tok = max(0.0, p_tok - cached_read_tok)`), jolloin kuvaajat eivät enää laske välimuistitokeneita kahteen kertaan ja pylväiden summat vastaavat suoraan taulukoita.
- [ ] **Liite C (Taulukko C3 ja Kuvio C3, s. 66–67) – Answer Relevancy = 0.000:**
  - Syy selvitetty: Taulukko C3 generointiin 25.9. vanhasta `runs/nordea_all16_final` -ajosta ($N=10$), jossa kokeiltiin Llama 4 Scout -tuomaria. Tuomari epäonnistui suomenkielisten kysymysten evaluoinnissa palauttaen 0.0 kaikille näytteille. Uudessa `enterprise_closed` -ajossa paikallisella Qwen3.8-27B-tuomarilla Answer Relevancy toimii oikein (~0.55–0.79). Vaatii Liitteen C taulukoiden päivittämisen tuoreella ajolla.
- [x] **Taulukko 11 (s. 47) – QPS-laskenta:**
  - Korjattu: `reporter.py`:ssä korjattu QPS-laskenta käyttämään mediaanilatenssia ($T_{\text{total}, P_{50}}$) aiemman keskiarvolatenssin sijaan. Taulukossa `tab_pareto_musique.tex` S2B:n QPS on päivittynyt arvoon `0.26` ($0.4431 / 1.68$) ja kaikki 16 riviä täsmäävät täsmällisesti.