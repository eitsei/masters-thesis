#TODO

TUTKI TUO ROUNTING JUTTU NYT KUNNOLLA!

MITES TÄMÄ OVERLAP JUTTU?!

Tsekkaa promptit

Tsekkaa koko koodi läpi muutenkin
Tsekkaa, että kaikki metodit on oikeasti toteutettu oikein!
Vastaako koodi kirjoitettua tekstiä ja jos ei, niin korjaa
Viimeistele tekstit
Keskustele vielä Laurin kanssa nuo asiat läpi
Kreditit?
Päivitä vielä Judge malli
Varmista, että kaikki väliviivat on --, jos tarvis. Tsekkaa myös että ajatusviivat on oikein käytetty

Varmista, että data voidaan ajaa erikseen evaluaattorista

Puhutaanko enterprisesta vai companysta? Mitä termiä käytetään kumppaniyhtiöstä? Varmista, että on yhteneväinen kaikkialla.

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
- [ ] **M3 + M4 Pro-mallin Context Cache 32k -rajapyykki (HTTP 503 / puuttuvat näytteet):**
  - Kun M4 reitittää Gemini 3.1 Prolle, M3 yrittää luoda etävälimuistia. Google GenAI API vaatii Pro-malleilla vähintään 32 768 tokenin esitäytön (kehotteen prefiksi oli vain ~7 150t). Tämä palauttaa virheen 400 -> HTTP 503, jolloin kysymys putoaa pois datasta (aiheutti C13 N=8 ja C14 N=9).
  - *Korjaus:* `GoogleGenAiPromptCache`: Älä yritä luoda kontekstivälimuistia Pro-mallille, jos tokenit jäävät alle 32 768:n (merkitse `NOT_ELIGIBLE` tai ohita).
- [ ] **M4 Quota 429 / Pro preview RPM Rate Limit (MuSiQue C4 N=9):**
  - Peräkkäiset Pro-mallin kyselyt ylittivät 5 RPM -kiintiön.
  - *Korjaus:* Lisää Spring Bootin `RagQueryService`-luokkaan tai WebClientiin automaattinen retry-backoff 429-virheille.
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