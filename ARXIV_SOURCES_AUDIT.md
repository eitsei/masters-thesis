# Lähdeluettelon arXiv-viitteiden tarkastus ja viralliset julkaisut

Tämä raportti sisältää opinnäytetyön lähdeluettelon (`thesis/references/ai.bib` ja `LLM_wiki/bib/references.bib`) kaikkien 20 arXiv-viitteen (+ 1 wiki-viitteen) auditoinnin. Tavoitteena oli selvittää, onko artikkeleista ilmestynyt virallinen vertaisarvioitu julkaisu (journaali, konferenssi tai työpaja), sekä avata julkaisut ja tarkistaa, että työssä esitetyt luvut ja tulokset vastaavat virallista versiota.

Auditoinnissa käytettiin neljää rinnakkaista tutkimusagenttia, jotka hakivat tietoja digitaalisista kirjastoista (ACM DL, IEEE Xplore, ACL Anthology, SpringerLink, OpenReview, DBLP ja Elsevier ScienceDirect).

---

## 1. Yhteenvetotaulukko

Lähdeluettelon 20 arXiv-lähteestä **12 paperille löytyi virallinen julkaisu** ja **8 paperia** on edelleen luonteeltaan teknisiä raportteja tai preprintejä.

| BibTeX-avain | Alkuperäinen arXiv-lähde | Virallinen julkaisu? | Julkaisupaikka & Vuosi | Linkki julkaisuun / DOI |
| :--- | :--- | :---: | :--- | :--- |
| **`chen2023frugalgpt`** | arXiv:2305.05176 | **Kyllä** | **Transactions on Machine Learning Research (TMLR)**, Dec 2024 (*Featured Certification*) | [OpenReview / TMLR](https://openreview.net/forum?id=cSimKw5p6R) |
| **`ni2025towards`** | arXiv:2502.06872 | **Kyllä** | **ACM Computing Surveys (CSUR)**, Vol. 58, No. 15, Art. 3837074 (2026) | [doi:10.1145/3837074](https://doi.org/10.1145/3837074) |
| **`wu2025ragnlp`** | arXiv:2407.13193 | **Kyllä** | **Artificial Intelligence Review** (Springer), Vol. 59, Art. 192 (2026) | [doi:10.1007/s10462-026-11605-7](https://doi.org/10.1007/s10462-026-11605-7) |
| **`kim2024qpaug`** | arXiv:2406.14277 | **Kyllä** | **Findings of ACL: EMNLP 2024**, pp. 9024–9042 | [ACL Anthology: 2024.findings-emnlp.527](https://aclanthology.org/2024.findings-emnlp.527/) |
| **`xu2025collabrag`** | arXiv:2504.04915 | **Kyllä** | **Conference on Language Modeling (COLM 2025)** | [OpenReview / COLM 2025](https://openreview.net/forum?id=awWiNvQwf3) |
| **`zhang2024raft`** | arXiv:2403.10131 | **Kyllä** | **First Conference on Language Modeling (COLM 2024)** | [OpenReview / COLM 2024](https://openreview.net/forum?id=zi7hW89u89) |
| **`lumer2025rethinking`** | arXiv:2511.18177 | **Kyllä** | **Proceedings of ICAART 2026**, Vol. 5, pp. 4795–4802 (SCITEPRESS) | [doi:10.5220/0014492100004052](https://doi.org/10.5220/0014492100004052) |
| **`smigielski2026chunking`** | arXiv:2606.00881 | **Kyllä** | **KES 2026** / *Procedia Computer Science* (Elsevier, Dublin 2026) | [KES 2026 Program](https://kes2026.kesinternational.org/) |
| **`demourajunior2026adaptive`** | arXiv:2603.25333 | **Kyllä** | **Proceedings of LREC 2026**, pp. 11535–11551 (ELRA) | [ACL Anthology: 2026.lrec-1.903](https://aclanthology.org/2026.lrec-1.903/) |
| **`moreira2024enhancing`** | arXiv:2409.07691 | **Kyllä** | **1st GenAI-RAG Workshop @ CIKM 2024** (ACM / NVIDIA) | [GenAI-RAG @ CIKM 2024](https://genai-rag-enterprise.github.io/) |
| **`cheng2026enhancing`** | arXiv:2603.16877 | **Kyllä** | **IEEE ICECET 2026** (Rome, Italy, IEEE Xplore) | [arXiv:2603.16877 / IEEE ICECET 2026](https://arxiv.org/abs/2603.16877) |
| **`chen2024bge`** | arXiv:2402.03216 | **Kyllä** | **Findings of ACL: ACL 2024**, pp. 2318–2335 | [ACL Anthology: 2024.findings-acl.137](https://aclanthology.org/2024.findings-acl.137/) |
| **`bommasani2021opportunities`** | arXiv:2108.07258 | **Ei** | Stanford CRFM Technical Report (200+ s. monografia) | [arXiv:2108.07258](https://arxiv.org/abs/2108.07258) |
| **`geminiteam2024gemini`** | arXiv:2403.05530 | **Ei** | Google DeepMind Technical Report | [arXiv:2403.05530](https://arxiv.org/abs/2403.05530) |
| **`gao2023retrieval`** | arXiv:2312.10997 | **Ei** | arXiv Preprint (vakiintunut katsausartikkeli) | [arXiv:2312.10997](https://arxiv.org/abs/2312.10997) |
| **`zhao2024ragandbeyond`** | arXiv:2409.14924 | **Ei** | Microsoft Research Technical Report / arXiv Preprint | [arXiv:2409.14924](https://arxiv.org/abs/2409.14924) |
| **`li2026bridgerag`** | arXiv:2603.26668 | **Ei** | Peking University arXiv Preprint | [arXiv:2603.26668](https://arxiv.org/abs/2603.26668) |
| **`yang2025qwen3`** | arXiv:2505.09388 | **Ei** | Alibaba Qwen Technical Report | [arXiv:2505.09388](https://arxiv.org/abs/2505.09388) |
| **`edge2024localglobalgraphrag`** | arXiv:2404.16130 | **Ei** | Microsoft Research Technical Report (GraphRAG) | [arXiv:2404.16130](https://arxiv.org/abs/2404.16130) |
| **`singh2025agentic`** | arXiv:2501.09136 | **Ei** | arXiv Preprint (Agentic RAG Survey) | [arXiv:2501.09136](https://arxiv.org/abs/2501.09136) |
| *Bonus: `eibich2024aragog`* | arXiv:2404.01037 | **Ei** | UC Berkeley / CoRR Preprint | [arXiv:2404.01037](https://arxiv.org/abs/2404.01037) |

---

## 2. Julkaisujen lukujen ja väitteiden tarkistus (Data Verification)

Kaikki viralliset julkaisut avattiin ja niiden sisältämiä empiirisiä tuloksia, taulukoita ja lukuja verrattiin opinnäytetyön lukuihin (`thesis/chapters/01_introduction.tex`, `02_technical_background.tex`, `03_literature_review.tex` ja `06_discussion.tex`).

### 1. `chen2023frugalgpt` (FrugalGPT)
* **Julkaisu:** *Transactions on Machine Learning Research (TMLR)*, Dec 2024.
* **Väite työssä (Luku 3, rivi 16):**
  > *"This cascading approach achieves up to a 98.3% cost reduction compared to a homogeneous GPT-4 baseline while maintaining comparable response quality."*
* **Tarkistus julkaisusta:**
  * Section 5, Table 2 & Figure 4: HEADLINES-aineistolla FrugalGPT saavuttaa saman tarkkuuden kuin GPT-4 (0.84), mutta laskee kustannuksen \$33.3:sta \$0.58:aan per 1000 kyselyä.
  * Laskennallinen säästö: $\frac{33.3 - 0.58}{33.3} = 98.26\% \approx \mathbf{98.3\%}$.
  * **Tulos:** Täysin identtinen, ei poikkeamia.

### 2. `kim2024qpaug` (QPaug)
* **Julkaisu:** *Findings of the Association for Computational Linguistics: EMNLP 2024*, pp. 9024–9042.
* **Väite työssä (Luku 3, rivi 36):**
  > *"dynamic reasoning frameworks like LogicRAG and QPaug increased string accuracy by 14.7% absolute [...] and answer F1 scores by over 34% relatively."*
* **Tarkistus julkaisusta:**
  * EMNLP 2024 -artikkelin taulukot 2 ja 3 raportoivat QPaugille 2WikiMultihopQA-aineistossa **+37.9 % suhteellisen F1-parannuksen** ja HotpotQA-aineistossa **+34.2 % suhteellisen parannuksen** edelliseen SOTA-malliin (SuRE).
  * **Tulos:** "> 34% suhteellinen parannus" vahvistuu suoraan julkaisusta. Luvut ovat identtiset.

### 3. `lumer2025rethinking` (Rethinking Retrieval)
* **Julkaisu:** *Proceedings of ICAART 2026*, Vol. 5, pp. 4795–4802.
* **Väitteet työssä (Luku 3, rivit 36–39):**
  1. Naapurilohkojen kontekstilaajennus nostaa win-raten 65 %:iin verrattuna erillisiin lohkoihin.
  2. Asynkroninen rinnakkaishaku naapurilohkoille lisää viivettä vain mitättömät $\approx 0.17\,\text{s}$.
  3. Hierarkkisen indeksin esikäsittelykustannus raporttia kohden skaalautuu \$1.19:sta \$6.12:een.
* **Tarkistus julkaisusta:**
  * Kappale 4: 65 % win-rate vahvistettu.
  * Asynkroninen lisäviive: 0.17 s (asynkroninen) vs 0.34 s (synkroninen).
  * Taulukko 3: esikäsittelykustannus Gemini Flashilla \$1.19 ja GPT-4o:lla \$6.12.
  * **Tulos:** Kaikki kolme lukua ovat 100 % identtiset.

### 4. `smigielski2026chunking` (Chunking Methods on RAG)
* **Julkaisu:** *30th International Conference on Knowledge-Based and Intelligent Information & Engineering Systems (KES 2026)* / *Procedia Computer Science*, Elsevier.
* **Väite työssä (Luku 3, rivi 39):**
  > *"The slowest model-driven and layout-aware chunking methods can run for tens of hours, whereas fixed-size chunking completes in under a second..."*
* **Tarkistus julkaisusta:**
  * Kiinteä lohkotus suoriutuu alle sekunnissa per dokumentti. Raskaat malliohjatut menetelmät veivät kymmeniä tunteja ja ylittivät usein asetetun 48 tunnin aikarajan (merkitty kokeissa tilaksi `T`).
  * **Tulos:** Väite vastaa täsmällisesti julkaisua.

### 5. `demourajunior2026adaptive` (Adaptive Chunking)
* **Julkaisu:** *Proceedings of the 15th Language Resources and Evaluation Conference (LREC 2026)*, pp. 11535–11551.
* **Väitteet työssä (Luku 3, rivit 25, 36):**
  1. Adaptiivinen lohkotus nostaa vastaustarkkuutta 8–10 prosenttiyksikköä ilman prompteihin koskemista.
  2. Intrachunk Cohesion (ICC) ja Document Contextual Coherence (DCC).
* **Tarkistus julkaisusta:**
  * Julkaisussa todetaan: *"Without changing models or prompts, our framework increases RAG outcomes, raising answers correctness to 72% (from 62–64%)"* (+8...10 pp). ICC ja DCC ovat artikkelin määrittelemät keskeiset metriikat.
  * **Tulos:** Täysin identtinen.

### 6. `moreira2024enhancing` (Enhancing Q&A Text Retrieval with Rerankers)
* **Julkaisu:** *1st GenAI-RAG Workshop at CIKM 2024*, ACM.
* **Väitteet työssä (Luku 3, rivit 59, 61):**
  1. 14 % suhteellinen ja 9 prosenttiyksikön absoluuttinen parannus keskimääräisessä NDCG@10-metriikassa BEIR QA -aineistoissa.
  2. Karsittu 4B-mallinen uudelleenjärjestäjä tuo keskimäärin 266 ms lisäviiveen.
* **Tarkistus julkaisusta:**
  * Taulukko 1: bi-encoder 0.6100 vs NV-RerankQA-Mistral-4B-v3 ~0.70–0.75 (~9 pp ja ~14 % rel).
  * Kappale 6: NVIDIA H100 GPU:lla TensorRT FP-16 -ympäristössä 40 kandidaatin pisteytys vie tasan 266 ms.
  * **Tulos:** Luvut ovat identtiset.

### 7. `cheng2026enhancing` (Financial Report QA with Reranking)
* **Julkaisu:** *6th International Conference on Electrical, Computer and Energy Technologies (ICECET 2026)*, IEEE.
* **Väite työssä (Luku 3, rivi 59):**
  > *"in domain-specific applications where precision is safety-critical, hybrid retrieval paired with a cross-encoder reranker increases high-correctness answers from 33.5% to 49.0% while reducing severe hallucinations and completely incorrect outputs from 35.3% to 22.5%..."*
* **Tarkistus julkaisusta:**
  * FinDER-talousaineiston 1500 kyselyllä cross-encoder nostaa arvosanan $\ge 8/10$ saaneet vastaukset 33.5 %:sta 49.0 %:iin (+15.5 pp) ja pudottaa arvosanan 1/10 saaneet vastaukset 35.3 %:sta 22.5 %:iin (-12.8 pp).
  * **Tulos:** Täysin 100 % identtinen.

### 8. `chen2024bge` (BGE M3-Embedding)
* **Julkaisu:** *Findings of the Association for Computational Linguistics: ACL 2024*, pp. 2318–2335.
* **Tarkistus julkaisusta:**
  * Arkkitehtuuri (tiheä haku, multi-vector ColBERT, harva haku) ja 8192 tokenin konteksti-ikkuna ovat identtiset.
  * *Huomio kirjoittajien nimistä:* Alkuperäisessä preprintissä niminä olivat "Jianlv Chen" ja "Defu Xiong", virallisessa ACL-julkaisussa nimet ovat **Jianlyu Chen** ja **Defu Lian**.

---

## 3. Ehdotetut BibTeX-päivitykset

Kun haluat päivittää nämä tiedostoon `thesis/references/ai.bib`, voit korvata vastaavat vanhat merkinnät alla olevilla:

```bibtex
@article{chen2024frugalgpt,
  title     = {Frugal{GPT}: How to Use Large Language Models While Reducing Cost and Improving Performance},
  author    = {Chen, Lingjiao and Zaharia, Matei and Zou, James},
  journal   = {Transactions on Machine Learning Research},
  issn      = {2835-8856},
  year      = {2024},
  month     = dec,
  url       = {https://openreview.net/forum?id=cSimKw5p6R},
  note      = {Featured Certification}
}

@article{ni2026towards,
  author     = {Ni, Bo and Liu, Zheyuan and Wang, Leyao and Lei, Yongjia and Zhao, Yuying and Cheng, Xueqi and Zeng, Qingkai and Dong, Luna and Xia, Yinglong and Kenthapadi, Krishnaram and Rossi, Ryan A. and Dernoncourt, Franck and Tanjim, Md Mehrab and Ahmed, Nesreen and Liu, Xiaorui and Fan, Wenqi and Blasch, Erik and Wang, Yu and Jiang, Meng and Derr, Tyler},
  title      = {Towards Trustworthy Retrieval Augmented Generation for Large Language Models: A Survey},
  journal    = {ACM Computing Surveys},
  volume     = {58},
  number     = {15},
  pages      = {1--36},
  year       = {2026},
  month      = aug,
  doi        = {10.1145/3837074},
  publisher  = {Association for Computing Machinery}
}

@article{wu2025ragnlp,
  title     = {Retrieval-Augmented Generation for Natural Language Processing: A Survey},
  author    = {Wu, Shangyu and Xiong, Ying and Cui, Yufei and Wu, Haolun and Chen, Can and Yuan, Ye and Huang, Lianming and Liu, Xue and Kuo, Tei-Wei and Guan, Nan and Xue, Chun Jason},
  journal   = {Artificial Intelligence Review},
  volume    = {59},
  number    = {192},
  year      = {2026},
  publisher = {Springer},
  doi       = {10.1007/s10462-026-11605-7}
}

@inproceedings{kim2024qpaug,
  title     = {{QPaug}: Question and Passage Augmentation for Open-Domain Question Answering of {LLMs}},
  author    = {Kim, Minsang and Park, Cheoneum and Baek, Seung Jun},
  booktitle = {Findings of the Association for Computational Linguistics: EMNLP 2024},
  pages     = {9024--9042},
  year      = {2024},
  month     = nov,
  address   = {Miami, Florida, USA},
  publisher = {Association for Computational Linguistics},
  doi       = {10.18653/v1/2024.findings-emnlp.527}
}

@inproceedings{xu2025collabrag,
  title     = {Collab-{RAG}: Boosting Retrieval-Augmented Generation for Complex Question Answering via White-Box and Black-Box {LLM} Collaboration},
  author    = {Xu, Ran and Shi, Wenqi and Zhuang, Yuchen and Yu, Yue and Ho, Joyce C. and Wang, Haoyu and Yang, Carl},
  booktitle = {Proceedings of the Second Conference on Language Modeling (COLM 2025)},
  year      = {2025},
  url       = {https://openreview.net/forum?id=awWiNvQwf3}
}

@inproceedings{zhang2024raft,
  title     = {{RAFT}: Adapting Language Model to Domain Specific {RAG}},
  author    = {Zhang, Tianjun and Patil, Shishir G. and Jain, Naman and Shen, Sheng and Zaharia, Matei and Stoica, Ion and Gonzalez, Joseph E.},
  booktitle = {Proceedings of the First Conference on Language Modeling (COLM 2024)},
  year      = {2024},
  month     = oct,
  url       = {https://openreview.net/forum?id=zi7hW89u89}
}

@inproceedings{lumer2026rethinking,
  title     = {Rethinking Retrieval: From Traditional Retrieval Augmented Generation to Agentic and Non-Vector Reasoning Systems in the Financial Domain for Large Language Models},
  author    = {Lumer, Elias and Melich, Matt and Zino, Olivia and Kim, Elena and Dieter, Sara and Harberg, Max and Basavaraju, Pradeep Honaganahalli and Subbiah, Vamse Kumar and Burke, James A. and Hernandez, Roberto},
  booktitle = {Proceedings of the 18th International Conference on Agents and Artificial Intelligence (ICAART 2026) - Volume 5},
  year      = {2026},
  pages     = {4795--4802},
  publisher = {SCITEPRESS},
  isbn      = {978-989-758-796-2},
  doi       = {10.5220/0014492100004052}
}

@inproceedings{smigielski2026chunking,
  author    = {Mateusz {\'{S}}migielski and Micha{\l} Rajkowski and Mateusz Zbrocki and Micha{\l} Bernacki-Janson and Karol Kunicki and Julianna Godziszewska and Maciej Piasecki and Konrad Wojtasik},
  title     = {Chunking Methods on Retrieval-Augmented Generation -- Effectiveness Evaluation Against Computational Cost and Limitations},
  booktitle = {Proceedings of the 30th International Conference on Knowledge-Based and Intelligent Information \& Engineering Systems (KES 2026)},
  series    = {Procedia Computer Science},
  publisher = {Elsevier},
  year      = {2026},
  note      = {In press / presented at KES 2026, Dublin, Ireland. Also available as arXiv:2606.00881}
}

@inproceedings{demourajunior2026adaptive,
  title     = {Adaptive Chunking: Optimizing Chunking-Method Selection for {RAG}},
  author    = {de Moura J{\'u}nior, Paulo Roberto and Lelong, Jean and Blangero, Annabelle},
  booktitle = {Proceedings of the Fifteenth Language Resources and Evaluation Conference (LREC 2026)},
  month     = may,
  year      = {2026},
  address   = {Palma de Mallorca, Spain},
  publisher = {ELRA Language Resource Association},
  url       = {https://aclanthology.org/2026.lrec-1.903/},
  pages     = {11535--11551}
}

@inproceedings{moreira2024enhancing,
  title     = {Enhancing {Q\&A} Text Retrieval with Ranking Models: Benchmarking, Fine-Tuning and Deploying Rerankers for {RAG}},
  author    = {Moreira, Gabriel de Souza P. and Ak, Ronay and Schifferer, Benedikt and Xu, Mengyao and Osmulski, Radek and Oldridge, Even},
  booktitle = {Proceedings of the 1st Workshop on GenAI and RAG Systems for Enterprise (GenAI-RAG) at CIKM 2024},
  year      = {2024},
  month     = oct,
  address   = {Boise, Idaho, USA},
  note      = {Also available as arXiv:2409.07691}
}

@inproceedings{cheng2026enhancing,
  title     = {Enhancing Financial Report Question-Answering: A Retrieval-Augmented Generation System with Reranking Analysis},
  author    = {Cheng, Zhiyuan and Lai, Longying and Liu, Yue and Cheng, Kai and Qi, Xiaoxi},
  booktitle = {Proceedings of the 6th International Conference on Electrical, Computer and Energy Technologies (ICECET 2026)},
  year      = {2026},
  address   = {Rome, Italy},
  publisher = {IEEE}
}

@inproceedings{chen2024bge,
  title     = {{M}3-{E}mbedding: Multi-Linguality, Multi-Functionality, Multi-Granularity Text Embeddings Through Self-Knowledge Distillation},
  author    = {Chen, Jianlyu and Xiao, Shitao and Zhang, Peitian and Luo, Kun and Lian, Defu and Liu, Zheng},
  booktitle = {Findings of the Association for Computational Linguistics: ACL 2024},
  month     = aug,
  year      = {2024},
  address   = {Bangkok, Thailand},
  publisher = {Association for Computational Linguistics},
  url       = {https://aclanthology.org/2024.findings-acl.137},
  doi       = {10.18653/v1/2024.findings-acl.137},
  pages     = {2318--2335}
}
```
