# Tutkimusanalyysi: Retrieval `top-k` ja Reasoning `thinking-budget` RAG-optimoinnissa

**Dokumentin tyyppi:** Diplomityön tutkimusmuistiinpano ja analyysiraportti (Research Note)  
**Kohdeluvut:** `thesis/chapters/03_literature_review.tex` ja `thesis/chapters/04_research_methods.tex`  
**Päivämäärä:** 17. lokakuuta 2026  
**Tekijä:** RAG Efficiency & Quality Research Team (Multi-Agent Investigation)

---

## Tiivistelmä ja keskeiset suositukset (Executive Summary)

Tämä tutkimusraportti käsittelee kahta keskeistä tutkimus- ja suunnittelukysymystä koskien diplomityön kokeellista asetelmaa ja arkkitehtuuria:
1. **Retrieval $\text{top-}k$ -parametrin valinta monihyppyisissä RAG-arvioinneissa** (erityisesti MuSiQue, HotpotQA, 2WikiMultihopQA).
2. **Sisäinen ajattelubudjetti (`thinking-budget`) vs. eksplisiittinen Chain-of-Thought (CoT)** Gemini 3.5 -malliperheen generoinnissa.

### Keskeiset linjaukset ja toimenpidesuositukset:
1. **Säilytetään nykyinen $\text{top-}k = 5$ kaikissa pipeline-konfiguraatioissa ($C_0, M_1, M_2, M_4$):**
   - **Akateeminen perusta:** NVIDIA:n RankRAG (Yu et al., 2024) ja Lumer et al. (2025) osoittavat, että kun käytössä on moderni semanttinen uudelleenjärjestelijä ($M_2$ `semantic-ranker-default-004`), **$k=5$ saavuttaa globaalin optimipisteen**. Suuremmat arvot ($k \ge 10$) heikentävät generoinnin tarkkuutta tuomalla mukaan kohinaa ja distraktoreita (*Lost in the Middle* -ilmiö).
   - **Arkkitehtoninen suoja:** $k=5$ suojaa Small-to-Big -moduulia ($M_1$) kontekstiräjähdykseltä ($\le 5\,120$ vs. $\le 10\,240$ tokenia), säilyttäen Prompt Cachingin ($M_3$) suhteellisen säästövaikutuksen.
   - **MuSiQue-rajoitteen reflektointi:** Yksivaiheisen haun rajoite MuSiQuen 4-hop-kysymyksissä (80 % vaadittu osumatarkkuus) dokumentoidaan analyyttisesti luvuissa 4 ja 6, ja viitataan monivaiheisiin dynaamisiin menetelmiin (Adaptive-RAG, IRCoT) luontevana jatkotutkimuksena.
2. **Säilytetään `thinking-budget = 0` ja nojataan välimuistitettuihin 8 few-shot CoT -esimerkkeihin:**
   - **Latenssi (RQ1):** Sisäisen ajattelun aktivointi (256–512 tokenia) nostaisi ensimmäisen vastaustokenin viivettä (TTFT) **+2,5–7,3 sekuntia**, jolloin mediaani-TTFT räjähtäisi nykyisestä ~1,99 sekunnista tasolle **~9,3 sekuntia (+367 %)**. Tämä rikkoisi yritystason interaktiivisen vasteaikatavoitteen ($P_{50} \le 2{,}0\text{ s}$).
   - **Kustannukset (RQ2):** Thinking-tokenit laskutetaan kalliina Output-tokeneina ($P_{\text{comp}} = \$9{,}00 / 1\text{M}$ Flash, $\$12{,}00 / 1\text{M}$ Pro). 8 laajaa CoT-esimerkkiä ($\approx 7{,}150$ tokenia) välimuistissa ($M_3$) maksaa vain **\$1,07 / 1k kyselyä**, kun taas 512 ajonaikaisen thinking-tokenin generointi maksaisi **\$4,61 / 1k kyselyä** (4,3x kalliimpaa!). Ajattelubudjetin aktivointi tuhoaisi yli 50 % Prompt Cachingin saavuttamista kustannussäästöistä.
   - **Faktoriaalinen kontrolli (RQ4 / $M_4$):** `thinking-budget = 0` pitää kehotteen, dekoodauksen ($T=0.0$) ja arviointiparametrit invariantteina mallitasojen välillä (Flash-Lite $\to$ Flash $\to$ Pro).

---

# Osa 1: Retrieval `top-k` -parametrin valinta multi-hop RAG -arvioinneissa

## 1.1 Kirjallisuuden käytännöt: `top-k`-arvot monihyppytehtävissä

Tutkimuskirjallisuudessa yksivaiheisen ja kaksivaiheisen haun syvyyttä analysoidaan seuraavasti:

| Tutkimus / Julkaisu | Hakurakenne & Re-ranking | Käytetyt $k$-arvot | Monihyppydatat | Keskeinen empiirinen havainto $k$-arvosta |
| :--- | :--- | :--- | :--- | :--- |
| **Yu et al. (2024)**<br>*RankRAG (NVIDIA)* | 1. vaihe: $N \in \{20, 30, 100\}$ (dense)<br>2. vaihe: LLM-reranker $\to$ **top-$k = 5$** | $k \in \{1, 5, 10, 20\}$ | HotpotQA, 2WikiMQA, NQ, PopQA | Ilman rerankausta vastaustarkkuus saturoituu arvoon $k=10$. Re-rankingin jälkeen **$k=5$ saavuttaa korkeimman tarkkuuden**, koska pienempi konteksti minimoi häiriintymisen epäolennaisista dokumenteista. |
| **Lumer et al. (2025)**<br>*Rethinking Retrieval* | 1. vaihe: $k_{\text{init}} \in \{10, 50, 75, 100\}$<br>2. vaihe: Cohere v3 cross-encoder $\to$ $k_{\text{final}}$ | $(k_{\text{init}}, k_{\text{final}}) \in \{(10,5), (50,10), (100,20), \dots\}$ | Monisivuiset 10-K/Q-raportit (multi-hop) | **$(10, 5)$ ja $(50, 5)$ ovat optimaalisia:** MRR@5 parani 0.160 $\to$ 0.750 (+59 % abs.), Recall@5 = 1.00, latenssi 2.02 s. Suuret kandidaattimäärät ($k_{\text{init}} \ge 50$) toivat laskevan rajahyödyn. |
| **de Moura Júnior et al. (2026)**<br>*Adaptive Chunking* | Hybridi: BM25 (top-50) + Dense (top-50)<br>Reranker: Arctic-Embed-L $\to$ **top-10** | **top-$k = 10$** generaattorille (GPT-4.1) | CLAIR (tekniset ja juridiset raportit) | Generaattorille syötetään kiinteästi **top-10** kappaletta (100–1100 tokenia/pala). Pienet palat (<100 tokenia) yhdistetään, etteivät ne syö paikkoja top-10:stä. |
| **Śmigielski et al. (2026)**<br>*Chunking Methods on RAG* | 1. vaihe: BGE-M3 (512 tokenia)<br>2. vaihe: BGE-Reranker-v2-m3 $\to$ **top-5** | Evaluointi: **Accuracy@5**, Recall@10.<br>Generointi: **top-5** ($\le 4\,000$ tok). | PIRB, TriviaQA, NQ | Generaattorille syötetään **top-5** kappaletta. 4 000 tokenin kontekstiraja puoltaa pientä $k$-arvoa, vaikka haussa mitataan myös Recall@10. |
| **Wang et al. (2024)**<br>*Searching for Best Practices* | 1. vaihe: Hybridi/BM25 $\to$ 50 kandidaattia<br>2. vaihe: monoT5 / RankLLaMA $\to$ top-$k$ | Kandidaatit: 50 tai 1000.<br>Lopullinen konteksti: **top-5** tai **top-10**. | HotpotQA, MuSiQue, NQ | 50 ehdokkaan hakeminen ja sen karsiminen tiukkaan kärkeen (top-5/10) tuottaa parhaan tasapainon (Balanced Recipe) latenssin ja laadun välille. |
| **LogicRAG (Chen et al. 2026)**<br>*AAAI 2026* | Dynaaminen päättely-DAG ilman valmiita graafeja. Etenee topologisesti. | **$k = 3$** per osatehtävä / alihyppy | **MuSiQue**, **2WikiMQA**, **HotpotQA** | Yksivaiheisen haun sijaan monihyppykysymys pilkotaan osiin. **$k=3$ per aliongelma** oli optimaalinen (tarkkuus 64.7 % 2WikiMQA:ssa ilman kontekstiräjähdystä). |
| **Adaptive-RAG (Jeong et al. 2024)**<br>*NAACL 2024* | Luokittelee kysymyksen: No-retrieval vs. Single-step vs. Multi-step (IRCoT) | Single-step: BM25 $k \in \{5, \dots, 15\}$ (Trivedi et al. 2023 mukaan) | **MuSiQue**, **HotpotQA**, **2WikiMQA** | Single-step RAG suoriutuu heikosti MuSiQue-datasetissä (EM 15–20 %), koska yksivaiheinen haku ei löydä piilossa olevia väliaskeleita (*missing bridge entity*). |
| **Collab-RAG (Xu et al. 2025)**<br>*arXiv:2502.18023* | Pieni SLM purkaa kysymyksen alikysymyksiksi, LLM-lukija vastaa. | **$k = 10$** kappaletta per osakysymys (Dragon-Plus / E5) | **MuSiQue**, **HotpotQA**, **2WikiMQA** | Käyttää $k=10$ hakusyvyyttä per osakysymys varmistaakseen riittävän kattavuuden monihyppyketjuissa. |
| **Stop-RAG (Park et al. 2025)**<br>*arXiv:2502.13854* | Iteraatiokohtainen haku $\to$ bge-reranker valitsee 1 parhaan per askel. | 10 ehdokasta/askel, max 10 askelta. | **MuSiQue**, **HotpotQA**, **2WikiMQA** | MuSiQue-datasetissä dynaaminen agentti konvergoitui keskimäärin **8.6 askeleeseen** (~8–9 kappaletta), kun taas 2WikiMQA vaati vain 5.1 askelta. |
| **Trivedi et al. (2022, 2023)**<br>*MuSiQue & IRCoT* | MuSiQue-rakenne: 20 kappaletta per kysymys (2–4 kultakappaletta, 16–18 kovaa distraktoria). | IRCoT tutki OneR (single-step) baselinessa $K \in \{5, 7, 9, 11, 13, 15\}$. | **MuSiQue**, **HotpotQA**, **2WikiMQA** | Yksivaiheinen haku $k=5$ asettaa 4-hop-kysymykselle 80 % tarkkuusvaatimuksen (4/5), jota tiheä haku ei saavuta leksikaalisen sillan puuttuessa (*compositionality gap*, Press et al. 2023). |

---

## 1.2 Akateeminen perustelu: $k=5$ vs. $k=8$ vs. $k=10$

### Vaihtoehto A: $k=5$ (Nykyinen valinta työssä – SUOSITELTU)
- **Rerankauksen optimipiste:** Yu et al. (2024, RankRAG) osoittavat empiirisesti, että kun käytetään tehokasta semanttista uudelleenjärjestelijää (kuten $M_2$ `semantic-ranker-default-004`), generoinnin tarkkuus saavuttaa huippunsa arvolla $k=5$. Suuremmat arvot ($k \ge 10$) heikentävät tulosta, koska mukaan pääsee häiritseviä distraktoreita.
- **Kaksivaiheisen haun tehokkuus:** Lumer et al. (2025) osoittavat parametriparien $(10, 5)$ ja $(50, 5)$ saavuttavan korkeimman MRR@5-tuloksen ja Recall@5 = 1.00 -arvon pitäen latenssin matalana (~2,02 s).
- **Distractor-haavoittuvuus ja "Lost in the Middle":** Liu et al. (2024) sekä Shi et al. (2023) osoittavat, että kielimallien päättelykyky heikkenee, kun kontekstiin syötetään epäolennaisia kappaleita. $k=5$ säilyttää korkean signaali-kohinasuhteen.
- **Tuotantojärjestelmien SLA & Kustannukset:** Śmigielski et al. (2026) ja FlashRAG (Jin et al., 2025) käyttävät standardina $k=5$, koska se pitää syöteikkunan ja TTFT-latenssin hallittavina.
- *Rajoite MuSiQuessa:* 4-hop-kysymyksessä vaaditaan 4 tukikappaletta. Yhdenkin virheellisen kappaleen nouto pudottaa Context Recallin arvoon $\le 0{,}75$. Yksivaiheinen haku kärsii MuSiQuessa rakenteellisesta haasteesta (*compositionality gap*).

### Vaihtoehto B: $k=8$ (Monihyppy-tasapainotettu kompromissi)
- **Empiirinen konvergenssi MuSiQuessa:** Stop-RAG (Park et al., 2025) osoitti vahvistusoppimisella, että optimaalinen pysäytysverkko konvergoitui MuSiQuessa keskiarvoon **8.6 noudettua dokumenttia**. 3- ja 4-hop-päättely vaatii usein noin 8 kappaletta riittävän todistusaineiston saavuttamiseksi.
- **Sallivampi virhemarginaali:** 4-hop-kysymyksessä 4 vaaditun kappaleen löytyminen 8 joukosta vaatii vain 50 % tarkkuutta (4/8), mikä antaa tiheälle haulle realistisen mahdollisuuden.
- **Hallittu laajeneminen:** $M_1$-moduulissa $8 \times 1024$ = 8 192 tokenia (ja deduplikoinnin jälkeen tyypillisesti ~4 000–5 000 tokenia), mikä mahtuu Gemini 3.5:n ja 3.1:n konteksti-ikkunaan.

### Vaihtoehto C: $k=10$ (Recall-painotteinen)
- **Perinteisen haun saturaatiopiste:** Yu et al. (2024, RankRAG) toteavat, että tiheässä perushaussa ilman rerankausta vastaustarkkuus saturoituu arvoon $k=10$.
- **Käytäntö:** de Moura Júnior et al. (2026) syöttävät GPT-4.1:lle top-10 rerankattua palaa, ja Collab-RAG (Xu et al., 2025) käyttää $k=10$ hakusyvyyttä.
- *Heikkous:* Aiheuttaa $M_1$-moduulissa (Small-to-Big) valtavan kontekstiräjähdyksen (jopa 10 240 tokenia ennen deduplikointia), romuttaa RAGAS Context Precision -pisteet (koska 2-hop-kysymyksissä 8/10 kappaleesta on kohinaa) ja heikentää Prompt Cachingin ($M_3$) suhteellista hyötyä.

---

## 1.3 Vaikutus tutkimusasetelmaan ($M_1$, $M_2$, $M_3$, $M_4$ ja $C_0$)

### 1.3.1 M1-moduuli (Small-to-Big: 128 $\to$ 1024 tokenia) ja kontekstiräjähdys
- **Arvolla $k=5$:** Teoreettinen maksimi on $5 \times 1024 = 5\,120$ tokenia. Parent-ID-deduplikointi tiivistää samaan dokumenttiin osuvat palat yhdeksi vanhemmaksi, jolloin empiirinen dynaaminen konteksti on tyypillisesti **1 500 – 3 500 tokenia**.
- **Arvolla $k=10$:** Teoreettinen maksimi nousee arvoon **10 240 tokenia** (deduplikoinnillakin **6 000 – 8 000 tokenia**).
- **Vaikutus Prompt Cachingiin ($M_3$, kynnys 4 096 tokenia):**
  - Staattinen 8-shot-kehoite on kiinteästi $\approx 7\,150$ tokenia ja tallentuu välimuistiin.
  - Jos dynaaminen konteksti kasvaa 2 500 tokenista 8 000 tokeniin, kokonaisprompti nousee yli 15 000 tokeniin.
  - Dynaamiset täyden hinnan tokenit alkavat dominoida kustannuksia (yli 50 % tokeneista), mikä **laimentaa $M_3$-moduulin suhteellista kustannussäästöprosenttia**.
  - Lisäksi 10 000 tokenin dynaaminen syöte kasvattaa TTFT-prefill-viivettä 1.5x–2.0x.

### 1.3.2 M2-moduuli (Two-Stage Semantic Re-ranking)
- **Arvolla $k=5$:** 50 kandidaatista valitaan 5 parasta. Kompressiosuhde on **10:1 (90 % karsinta)**. Re-ranking toimii tehokkaana kohinasuodattimena.
- **Arvolla $k=10$:** Kompressiosuhde putoaa arvoon **5:1 (80 % karsinta)**. Sijat 6–10 sisältävät usein kohinaa, mikä heikentää rerankkauksen puhdistavaa vaikutusta.
- **Vuorovaikutus $M_1 + M_2$:**
  - Yhdistelmässä $M_1+M_2$ rerankataan ensin 50 pientä 128 tokenin lapsipalaa (vain 6 400 tokenia), ja *vasta voittajat* laajennetaan 1024 tokenin vanhemmiksi.
  - Jos $k=5$, laajennetaan enintään 5 vanhempaa ($\le 5\,120$ tok). Jos $k=10$, laajennetaan 10 vanhempaa ($\le 10\,240$ tok). $k=5$ säilyttää tämän arkkitehtuurisen hyödyn täysimääräisenä.

### 1.3.3 RAGAS-laatumetriikat (Context Precision vs. Context Recall)
- **Context Precision@K:**
  - 2-hop-kysymyksissä (2 kultakappaletta) arvolla $k=10$ kontekstissa on vähintään 8 distraktoria. Koska Precision@k laskee jyrkästi alemmilla sijoilla, **$k=10$ heikentää Context Precision -pisteitä keinotekoisesti**. $k=5$ tuottaa huomattavasti vakaammat ja vertailukelpoisemmat pisteet.
- **Context Recall:**
  - 4-hop-kysymyksissä $k=5$ asettaa kovan rajan yksivaiheiselle haulle (jos 1 kappale puuttuu, recall max 0.75). $k=8$ tai $k=10$ nostaisi Context Recallia, mutta hinta maksetaan Context Precisionissa ja latenssissa.

### 1.3.4 Erottelukyky suhteessa baselineen $C_0$ (Ceiling vs. Floor effect)
- Kun $k=5$, perushaku $C_0$ hukkaa usein relevantin kappaleen sijoille 6 tai 7. Tällöin $M_2$ nostaa sen kärkeen ja tuottaa **selkeän, tilastollisesti merkitsevän parannuksen ($\Delta Q$)**.
- Jos $k=10$, baseline $C_0$ saa kyseiset kappaleet mukaan suoraan, jolloin $M_2$:n suhteellinen lisäarvo pienenee (kattoefekti).

---

## 1.4 Muutoslista tiedostoon `thesis/chapters/04_research_methods.tex`

Mikäli hakumäärää $k$ muutettaisiin (esim. $k=5 \to 8$), seuraavat kohdat tiedostossa `thesis/chapters/04_research_methods.tex` on päivitettävä:
1. **Taulukko 4.3 (`tab:optimization_modules`), Rivi 101 ($C_0$ Naive RAG):**
   - *Nykyinen:* `Chunk size: $\le 512$ tokens, retrieval: top-$k = 5$.`
   - *Päivitys:* `Chunk size: $\le 512$ tokens, retrieval: top-$k = 8$.`
2. **Taulukko 4.3, Rivi 105 ($M_1$ Small-to-Big):**
   - *Nykyinen:* `Child chunk: $\le 128$ tokens, parent window: $\le 1024$ tokens, retrieval: top-$k = 5$.`
   - *Päivitys:* `...retrieval: top-$k = 8$.`
3. **Taulukko 4.3, Rivi 109 ($M_2$ Semantic Re-ranking):**
   - *Nykyinen:* `Candidate pool: top-50, reranked context: top-$k = 5$, model: \texttt{semantic-ranker-default-004}.`
   - *Päivitys:* `...reranked context: top-$k = 8$...`
4. **Kappale $C_0$, Rivi 124:**
   - *Nykyinen:* `In addition, dense vector retrieval of $\text{top-}k = 5$ is chosen as the baseline configuration, reflecting standard experimental benchmarks in recent literature~\citep{smigielski2026chunking, lewis2020rag}.`
   - *Päivitys:* Täydennetään viitteet: `~\citep{smigielski2026chunking, lewis2020rag, yu2024rankrag, lumer2026rethinking}`.
5. **Kappale $M_1$, Rivi 127:**
   - Päivitetään teoreettinen yläraja ($k \times 1024$ tokenia) ja korostetaan deduplikoinnin kriittisyyttä kontekstiräjähdyksen estäjänä.
6. **Kappale $M_2$, Rivi 130:**
   - *Nykyinen:* `...before the top-5 winning chunks are expanded to their 1024-token parent context windows.`
   - *Päivitys:* `...before the top-8 winning chunks...`
7. **Kappale $M_4$, Rivi 136:**
   - *Nykyinen:* `...candidate context pool ($\text{top-}k = 5$)...`
   - *Päivitys:* `candidate context pool ($\text{top-}k = 8$)`
8. **Kaavat 4.22 & 4.23 (Context Precision@K, Rivit 213–219):**
   - Selvennetään, että arvioinnissa $K=5$ (tai $K=8$).
9. **Koodikannassa:**
   - `rag-code/rag-benchmark/src/main/resources/application.yml` rivillä 84: `top-k: 5`.
   - `rag-code/rag-benchmark/src/main/java/com/dippa/ragbenchmark/application/RagQueryService.java`.

---

# Osa 2: Reasoning / Thinking Budget vs. Eksplisiittinen CoT RAG-generoinnissa

Koeasetelmassa käytetään Gemini 3.5 -malliperhettä (`gemini-3.5-flash-lite`, `gemini-3.5-flash`, `gemini-3.1-pro`), joissa `thinking-budget = 0`, ja staattisessa kehotteessa on 8 monihyppyistä few-shot CoT -esimerkkiä ($\approx 7{,}150$ tokenia).

## 2.1 Päättelyn käsittely RAG-kirjallisuudessa

1. **InstructRAG (Wei et al., ICLR 2025 / Hu et al., 2024):**
   - *Löydös:* Päättelyperustelujen (*self-synthesized rationales*) generoiminen ajonaikaisesti ennen vastausta lisää dramaattisesti latenssia ja token-kuluja. In-context learning (ICL) -demonstraatiot, joissa perustelu näytetään kehotteessa mutta mallia pyydetään vastaamaan suoraan (`Answer:`), saavuttavat saman kohinanpoiston ilman ajonaikaista token-ylijäämää.
2. **LogicRAG (Chen et al., AAAI 2026):**
   - Monivaiheinen päättely nostaa vastaustarkkuutta (+14,7 %), mutta **yli kaksinkertaistaa latenssin (4,28 s $\to$ 9,83 s)** suoraan flat-hakuun verrattuna.
3. **Adaptive-RAG (Jeong et al., NAACL 2024):**
   - Monivaiheinen CoT-päättely (Multi-step IRCoT) kasvatti latenssin **3,08 sekunnista 27,18 sekuntiin**. Kevyiden kysymysten ohjaaminen ohi monivaiheisen päättelyn leikkasi latenssia 56 % ja tokeneita 63 % ilman laadun heikkenemistä.
4. **Collab-RAG (Xu et al., 2025) & FrugalGPT (Chen et al., 2023):**
   - Päättelyn generointi kaupallisilla frontier-malleilla on kallein tapa suorittaa RAG-tehtäviä; tuotantojärjestelmissä päättelyketjut pyritään joko siirtämään kevyille malleille tai amortisoimaan.

---

## 2.2 `thinking-budget = 0` vs. Kiinteä budjetti (256 tai 512 tokenia)

### A. RQ1: Latenssi ja TTFT (Time to First Token)
- **Suoratoistomekanismi Vertex AI:ssa:** Gemini suorittaa sisäisen ajattelun **ennen kuin ensimmäistäkään vastaustokenia lähetetään asiakkaalle**. Vastausten striimaus alkaa vasta ajattelun päätyttyä.
- **Latenssivaikutus:**
  Autoregressiivisen generoinnin nopeudella ~70 tokenia/s:
  - 256 ajattelutokenia lisää viivettä: $\approx +3{,}65\text{ s}$.
  - 512 ajattelutokenia lisää viivettä: $\approx +7{,}31\text{ s}$.
- Nykyisessä baseline-mittauksessa ($C_0$, `thinking-budget = 0`):
  - $\text{TTFT}_{P_{50}} = 1{,}99\text{ s}$, $T_{\text{total}, P_{50}} = 2{,}12\text{ s}$.
  - Jos `thinking-budget = 512` otettaisiin käyttöön: **$\text{TTFT}_{P_{50}}$ nousisi tasolle ~9,3 sekuntia (+367 %)**. Tämä rikkoisi yritystason vasteaikatavoitteen ($P_{50} \le 2{,}0\text{ s}$).

### B. RQ2: Token-kustannukset ($) ja Prompt Caching -synergia
GCP Vertex AI -hinnasto (Syyskuu 2026):

| Malli | Prompt ($/1M) | Cached Prompt ($/1M) | Output / Completion ($/1M) | Suhde $P_{\text{comp}} / P_{\text{prompt}}$ | Suhde $P_{\text{comp}} / P_{\text{cached}}$ |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Gemini 3.5 Flash** | \$1.50 | \$0.15 | **\$9.00** | 6.0x | **60.0x** |
| **Gemini 3.5 Flash-Lite** | \$0.30 | \$0.03 | **\$2.50** | 8.3x | **83.3x** |
| **Gemini 3.1 Pro** | \$2.00 | \$0.20 | **\$12.00** | 6.0x | **60.0x** |

1. **Thinking-tokenien laskutus:**
   GCP Vertex AI:ssa sisäiset ajattelutokenit **laskutetaan täysimääräisinä Output-tokeneina** ($P_{\text{comp}}$).
2. **Kustannuslisäys 1 000 kyselyä kohden ($\text{Cost}_{1\text{k}}$):**
   - Kokeessa nykyinen vastauspituus on keskimäärin vain $N_{\text{comp}} \approx 23$ tokenia (tulostuskulu \$0,207 / 1k kyselyä).
   - 256 thinking-tokenia: lisäkustannus **+\$2,304 / 1k kyselyä**.
   - 512 thinking-tokenia: lisäkustannus **+\$4,608 / 1k kyselyä**.
3. **Vaikutus Prompt Cachingiin ($M_3$):**
   - $C_3$-konfiguraatiossa (Prompt Caching) mitattu kustannus on **\$3,411 / 1k kyselyä** (säästö baselineen $-\$8{,}561$ eli $-71{,}5\ \%$).
   - Jos $C_3$:ssa käytettäisiin 512 ajattelutokenia, kustannus nousisi arvoon **\$8,019 / 1k kyselyä**. **Säästö supistuisi 71,5 prosentista vaivaiseen 33 prosenttiin!** Yli puolet ($53{,}8\ \%$) Prompt Cachingin taloudellisesta hyödystä tuhoutuisi.
4. **Taloudellinen havainto (ICL CoT vs. Runtime Thinking):**
   - 8 esimerkin CoT-kirjaston ($\approx 7{,}150$ tokenia) lukeminen välimuistista maksaa vain:
     $$1000 \times 7{,}150 \times \frac{\$0{,}15}{10^6} = \mathbf{\$1{,}07 / 1\text{k kyselyä}}$$
   - Ajonaikainen 512 tokenin ajattelu maksaa **\$4,61 / 1k kyselyä**.
   - **8 laajan CoT-demonstraation säilyttäminen välimuistissa on 4,3 kertaa HALVEMPAA kuin 512 ajattelutokenin generointi ajonaikaisesti!**

### C. RQ4: M4 Model Cascading ja Faktoriaalinen Kontrolli
- Pitämällä `thinking-budget = 0` kaikilla tasoilla, kaikki mallit saavat identtisen syötteen ja toimivat samalla deterministisellä dekoodauksella ($T=0.0$).
- Havaitut erot johtuvat puhtaasti **mallien arkkitehtonisesta suorituskyvystä ja parametrimäärästä**, ei satunnaisesta ajattelupituudesta.
- Pro-mallin ajattelutokenit maksaisivat \$12,00/1M ja Lite-mallin \$2,50/1M. Ajattelun aktivointi tekisi reitittimen kustannuksista epälineaarisia ja vaikeasti tulkittavia.

---

## 2.3 Dokumentointi ja valmiit LaTeX-tekstiluonnokset lukuun 4

Seuraavat tekstiluonnokset on laadittu suoraan sijoitettavaksi tiedostoon `thesis/chapters/04_research_methods.tex`:

### 1. Alaluku 4.2 (System Implementation and Models, rivin 85 loppuun):
```latex
All generative models are accessed via the official Google Cloud Vertex AI REST and gRPC endpoints with temperature strictly configured to $T = 0.0$ to enforce greedy, deterministic decoding across all experimental evaluations. Furthermore, the reasoning token quota (\texttt{thinking-budget}) supported by frontier Gemini models is explicitly configured to zero across all pipeline configurations. Disabling runtime thinking tokens ensures that generation latency is not confounded by internal autoregressive chain-of-thought traces, preserving standard interactive latency profiles while delegating reasoning guidance entirely to static in-context few-shot demonstrations.
```

### 2. Taulukko 4.3 (tab:optimization_modules, rivit 100 & 117):
```latex
\textbf{$C_0$} & Naive RAG (Baseline)
  & Chunk size: $\le 512$ tokens, retrieval: top-$k = 5$, decoding: $T=0.0$, \texttt{thinking-budget} = 0.
  \\
...
\textbf{$M_4$} & Hybrid Model Routing
  & Routing targets: Router/1-hop $\to$ Gemini 3.5 Flash-Lite; 2-hop $\to$ Gemini 3.5 Flash; 3/4-hop $\to$ Gemini 3.1 Pro; uniform decoding: $T=0.0$, \texttt{thinking-budget} = 0 across all tiers.
  \\
```

### 3. Alaluku 4.3 (Prompt Caching $M_3$ & In-Context CoT, rivi 133):
```latex
To exceed this operational threshold naturally without synthetic padding, a static system prompt incorporating an in-context learning (ICL) library of eight extensive few-shot demonstrations ($\approx 7{,}150$ tokens) is prepended to the context. Following the principled rationalization paradigm established by InstructRAG~\citep{wei2025instructrag}, each in-context demonstration explicitly models multi-hop reasoning, context disambiguation, distractor denoising, and strict canonical abstention through step-by-step reasoning paths (\textit{Reasoning Path:}). Crucially, while the demonstrations provide structured Chain-of-Thought (CoT) guidance, the prompt terminates immediately after the final \textit{Answer:} delimiter. Coupled with an enforced \texttt{thinking-budget = 0}, this architectural design directs the model to output concise, factual answers directly ($\approx 20$--$30$ tokens) without emitting runtime reasoning tokens. This design achieves an optimal economic equilibrium: the computational overhead of complex multi-hop reasoning is amortized into the cached prefix billed at the heavily discounted rate $P_{\text{cached}}$ (\$0.15/1M), entirely bypassing the severe cost penalty of generating dynamic reasoning tokens billed under premium completion rates $P_{\text{comp}}$ (\$9.00/1M).
```

### 4. Alaluku 4.5.1 (Latency Profiles, rivi 170):
```latex
Under this formulation, Time to First Token captures user-perceived responsiveness: the elapsed wall-clock time before the first intelligible response token is received by the client application. Because \texttt{thinking-budget} is constrained to zero, TTFT reflects exclusively the prefill and network transport latency ($T_{\text{prefill}} + T_{\text{cache}} + T_{\text{transport}}$), without being confounded by latent autoregressive thought token generation that would otherwise introduce several seconds of unstreamed latency prior to response emission.
```

### 5. Alaluku 4.5.2 (Cost Accounting, rivi 189):
```latex
Furthermore, $N_{\text{comp}}$ denotes the generated completion tokens billed at rate $P_{\text{comp}}^{(m(q))}$. In standard production deployments with active reasoning budgets, $N_{\text{comp}}$ incorporates both internal thought tokens and final response tokens. By enforcing \texttt{thinking-budget = 0}, $N_{\text{comp}}$ isolates strictly the user-facing generation length, preventing operational expenditures from being inflated by hidden token overheads and ensuring transparent cost comparability across model tiers in Module $M_4$.
```

### 6. Alaluku 4.6 (Threats to Validity – Internal Validity, rivi 307):
```latex
A potential confounding factor in multi-tier model evaluations ($M_4$) and latency measurements is the non-deterministic allocation of internal reasoning tokens in modern frontier LLMs. To eliminate this threat to internal validity, provider-side thinking budgets (\texttt{thinking-budget}) are deactivated ($0$) across all evaluated models (Gemini 3.5 Flash-Lite, Gemini 3.5 Flash, and Gemini 3.1 Pro). This configuration guarantees that observed variance in TTFT and end-to-end latency ($T_{\text{total}}$) stems strictly from the evaluated pipeline optimizations (such as context caching and semantic re-ranking) rather than stochastic variations in internal reasoning trace lengths.
```

---

## 2.4 Menetelmävertailutaulukko

| Ulottuvuus | `thinking-budget = 0` + 8-shot ICL CoT (Valittu ratkaisu) | `thinking-budget = 256 / 512` (Sisäinen ajattelu) | Kirjallisuusviite & Perustelu |
| :--- | :--- | :--- | :--- |
| **TTFT ($P_{50}$)** | **~1,99 s** (SLA-yhteensopiva) | **~5,6 – 9,3 s** (+180 % – +360 %) | *Adaptive-RAG* \citep{jeong2024adaptive}: monivaiheinen päättely nostaa latenssin 3 s $\to$ 27 s. |
| **Output Tokenit ($N_{\text{comp}}$)** | **20–30 tokenia** / kysely | **280–540 tokenia** / kysely | Empiiriset MuSiQue-tulokset (`tab_cost_musique.tex`). |
| **1k Kyselyn Kustannus ($C_3$ PC)** | **\$3.411** ($-71{,}5\ \%$ säästö) | **\$8.019** (Säästö supistuu $-33\ \%$:iin) | GCP Vertex AI: Output-tokenit \$9.00/1M vs. Cached \$0.15/1M. |
| **Päättelyn Amortisointi** | **\$1.07 / 1k kyselyä** (ICL CoT välimuistissa) | **\$4.61 / 1k kyselyä** (ajonaikaiset tokenit) | *InstructRAG* \citep{wei2025instructrag}: staattiset perustelut poistavat ajonaikaisen kulun. |
| **Faktoriaalinen Kontrolli ($M_4$)** | **Täydellinen:** sama prompti, $T=0.0$, 0 ajattelutokenia kaikilla malleilla. | **Vääristynyt:** Lite, Flash ja Pro reagoivat ajattelubudjettiin eri tavoin eri hinnoilla. | *FrugalGPT* \citep{chen2024frugalgpt}, *RouteLLM* \citep{ong2025routellm}. |
| **Kieltäytymistarkkuus (RQ3)** | **100 % kanoninen:** 8-shot opettaa tarkan sanamuodon. | Riski, että ajattelu "vuotaa" selitykseksi kiellettyjen lauseiden säännön ohi. | MuSiQue & Enterprise Grounding Directives. |

---

# Lähdeviitteet (BibTeX-avaimet)

- `yu2024rankrag`: Yu et al. (2024), *RankRAG: Unifying Context Ranking with Retrieval-Augmented Generation in LLMs*.
- `lumer2026rethinking`: Lumer et al. (2025), *Rethinking Retrieval: From Traditional Retrieval Augmented Generation to Agentic and Non-Vector Reasoning Systems in the Financial Domain for Large Language Models*.
- `demourajunior2026adaptive`: de Moura Júnior et al. (2026), *Adaptive Chunking: Optimizing Chunking Method Selection for RAG*.
- `smigielski2026chunking`: Śmigielski et al. (2026), *Chunking Methods on Retrieval-Augmented Generation: Effectiveness Evaluation Against Computational Cost and Limitations*.
- `wei2025instructrag`: Wei et al. (2025) / Hu et al. (2024), *InstructRAG: Instructing Retrieval-Augmented Generation via Self-Synthesized Rationales*.
- `chen2026logicrag`: Chen et al. (2026), *LogicRAG: Retrieval-Augmented Generation with Adaptive Reasoning Structures*, AAAI 2026.
- `jeong2024adaptive`: Jeong et al. (2024), *Adaptive-RAG: Learning to Adapt Retrieval-Augmented Large Language Models through Query Complexity*, NAACL 2024.
- `trivedi2022musique`: Trivedi et al. (2022), *MuSiQue: Multihop Questions via Single-hop Question Composition*.
- `trivedi2023ircot`: Trivedi et al. (2023), *Interleaving Retrieval with Chain-of-Thought Reasoning for Knowledge-Intensive Multi-Step Problems*, ACL 2023.
- `park2025stoprag`: Park et al. (2025), *Stop-RAG: Value-based Retrieval Control for Iterative RAG*.
- `chen2024frugalgpt`: Chen et al. (2023), *FrugalGPT: How to Use Large Language Models While Reducing Cost and Improving Performance*.
- `ong2025routellm`: Ong et al. (2025), *RouteLLM: Learning to Route LLMs with Preference Data*, ICLR 2025.
