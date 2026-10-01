# Chapter 4 Ground Truth Specification Matrix

Distilled ground truth from `thesis/chapters/04_research_methods.tex` for the Master's Thesis:
*Empirical Evaluation of RAG Optimization Methods in Enterprise Software Systems*.

---

## 1. Experimental Design & Factorial Matrix (§4.1)

### Full Factorial Matrix ($2^4 = 16$ Configurations)
- **Baseline ($k=0$):**
  - $C_0$: Naive RAG Baseline (Dense HNSW retrieval + direct answer generation)
- **Stage 1: Single-Factor Isolated Testing ($k=1$):**
  - $C_1$: $M_1$ (Small-to-Big Retrieval)
  - $C_2$: $M_2$ (Two-Stage Semantic Re-ranking)
  - $C_3$: $M_3$ (Dual-Tier Prompt Caching)
  - $C_4$: $M_4$ (Hybrid Model Routing)
- **Stage 2: Pairwise Combinations ($k=2$):**
  - $C_5$: $M_1 + M_2$ (S2B + SR)
  - $C_6$: $M_1 + M_3$ (S2B + PC)
  - $C_7$: $M_1 + M_4$ (S2B + MC)
  - $C_8$: $M_2 + M_3$ (SR + PC)
  - $C_9$: $M_2 + M_4$ (SR + MC)
  - $C_{10}$: $M_3 + M_4$ (PC + MC)
- **Stage 2: Multi-Module Triplets ($k=3$):**
  - $C_{11}$: $M_1 + M_2 + M_3$
  - $C_{12}$: $M_1 + M_2 + M_4$
  - $C_{13}$: $M_1 + M_3 + M_4$
  - $C_{14}$: $M_2 + M_3 + M_4$
- **Stage 2: Full-Stack Integration ($k=4$):**
  - $C_{15}$: $M_1 + M_2 + M_3 + M_4$

### Sampling & Invocations
- **Warm-up queries:** Exactly 2 unmeasured warm-up queries precede evaluation to eliminate JVM JIT, DB connection pool initialization, and TLS handshakes.
- **Sample size per dataset:** $N = 100$ paired observations per dataset ($N = 200$ across both MuSiQue and Nordea closed corpora).
- **Total pipeline invocations:** $16 \text{ configs} \times 100 \text{ queries} \times 2 \text{ datasets} = 3\,200$ measured invocations.

### Statistical Testing
- **Test:** Two-tailed Wilcoxon signed-rank test across paired query observations ($C_k$ vs. $C_0$).
- **FWER Control:** Holm-Bonferroni step-down correction applied per evaluation metric across the 15 comparisons ($m = 15$).
- **Significance threshold:** $\alpha = 0.05$.

---

## 2. Infrastructure, Models & Stack (§4.2)

- **Software Stack:**
  - Java 21 LTS
  - Spring Boot 4.1.0
  - Spring AI 2.0.0
  - PostgreSQL 16 with `pgvector` 0.8.0
- **Vector Indexing:**
  - HNSW with cosine distance
  - Index parameters: $M = 16$, $ef\_construction = 64$, $ef\_search = 40$
  - Sub-5 ms retrieval target
- **Infrastructure:**
  - Google Cloud Platform (GCP)
  - Cloud SQL in dedicated VPC, region `europe-north1`, private IP peering
- **Model Ecosystem (September 2026):**
  - Primary generator (Workhorse): **Gemini 3.8 Flash**
  - Vector embeddings: **`gemini-embedding-2`**, output dimensionality 768 via Matryoshka Representation Learning (MRL)
  - Cross-encoder reranker ($M_2$): Vertex AI Ranking API, model **`semantic-ranker-default-004`**
  - Model cascading ($M_4$):
    - Low-latency tier / Router / 1-hop: **Gemini 3.5 Flash-Lite**
    - Workhorse / 2-hop: **Gemini 3.8 Flash**
    - High-capacity / 3/4-hop: **Gemini 3.1 Pro**

---

## 3. Optimization Modules ($M_0$–$M_4$) (§4.3, Table 4.3)

| Module | Strategy | Variables & Configuration |
|---|---|---|
| **$M_0$** | Naive Baseline | Chunk size: 512 tokens, overlap: 50 tokens, retrieval: top-$k = 5$ |
| **$M_1$** | Small-to-Big Retrieval | Child chunk: 128 tokens, parent window: 1024 tokens, retrieval: top-$k = 5$. Deduplication and interval-merging for overlapping parent boundaries. |
| **$M_2$** | Two-Stage Semantic Re-ranking | Candidate pool: top-50, reranked context: top-$k = 5$, model: `semantic-ranker-default-004`. In combinations with $M_1$: top-50 child chunks (128 tokens) are reranked *before* expanding winning top-5 to 1024-token parent windows. |
| **$M_3$** | Dual-Tier Prompt Caching | Threshold: $\ge 4096$ tokens. GCP Vertex AI Explicit Context Cache and KV prefix reuse. |
| **$M_4$** | Hybrid Model Routing | Zero-shot classifier via Gemini 3.5 Flash-Lite. Routing targets: 1-hop $\to$ Flash-Lite; 2-hop $\to$ Flash; 3/4-hop $\to$ Pro. Explicit tracking of routing latency $T_{\text{route}}$ and classifier tokens. |

---

## 4. Benchmark Datasets (§4.4)

### MuSiQue (Academic Benchmark)
- **Sample size:** 100 QA pairs
- **Stratification:**
  - 40% 2-hop questions
  - 40% 3- and 4-hop questions (20% 3-hop, 20% 4-hop)
  - 20% unanswerable questions
- **Standardized refusal reference:**
  `"The provided context does not contain sufficient information to answer this query."`

### Enterprise Closed-Corpus Dataset (Nordea)
- **Sample size:** 100 synthetically generated QA pairs
- **Stratification:**
  - 40% single-hop factual
  - 40% multi-hop relational
  - 20% out-of-domain (same standardized refusal requirement)
- **Closed corpus structure:**
  - 1,333 unique paragraphs matching MuSiQue structure and candidate density to control search space disparity.

---

## 5. Evaluation Setup & Metrics (§4.5)

### Evaluation Framework
- **RAGAS Suite** with automated LLM-as-a-judge.
- **Judge Model:** Claude Sonnet 5 at temperature $T = 0.0$ (deterministic scoring). Note: deliberate development override may be active in local runs.

### RQ1: Latency Metrics
- **Micro-phase decomposition:**
  $$T_{\text{total}}(q) = T_{\text{embed}}(q) + T_{\text{route}}(q) + T_{\text{ret}}(q) + T_{\text{rerank}}(q) + T_{\text{expand}}(q) + T_{\text{cache}}(q) + T_{\text{prefill}}(q) + T_{\text{stream}}(q)$$
- **Time to First Token (TTFT):**
  $$\text{TTFT}(q) = T_{\text{total}}(q) - T_{\text{stream}}(q)$$
- **Reporting:** Median ($P_{50}$) and tail ($P_{95}$) percentiles.

### RQ2: Operational Cost Accounting
- **Cost Formula:**
  $$\text{Cost}(q) = N_{\text{embed}}(q) \cdot P_{\text{embed}} + N_{\text{uncached}}(q) \cdot P_{\text{prompt}}^{(m(q))} + N_{\text{cached}}(q) \cdot P_{\text{cached}}^{(m(q))} + N_{\text{comp}}(q) \cdot P_{\text{comp}}^{(m(q))} + N_{\text{rerank}}(q) \cdot P_{\text{rerank}} + N_{\text{route}}(q) \cdot P_{\text{route}}$$
- **Discount:** 90% discount on cached input context ($P_{\text{cached}} = 0.10 \times P_{\text{prompt}}$).
- **Standardized reporting:** Normalized per 1,000 queries:
  $$\text{Cost}_{1\text{k}} = 1000 \times \frac{1}{|Q|} \sum_{q \in Q} \text{Cost}(q)$$

### RQ3: Quality Metrics
- **Deterministic Lexical:** Token $F_1$, Exact Match ($EM$).
- **Core RAGAS (evaluated exclusively on answerable partition $Q_{\text{ans}}$):**
  - Context Precision ($CP$)
  - Context Recall ($CR$)
  - Faithfulness ($Faith$)
  - Answer Relevancy ($AR$)
- **Composite Quality Score $Q(q)$:** Regularized harmonic mean ($\epsilon = 10^{-4}$):
  $$Q(q) = \frac{4}{\frac{1}{\text{CP}(q) + \epsilon} + \frac{1}{\text{CR}(q) + \epsilon} + \frac{1}{\text{Faith}(q) + \epsilon} + \frac{1}{\text{AR}(q) + \epsilon}}$$
- **Abstention Metrics (evaluated outside RAGAS):**
  - Refusal Accuracy:
    $$\text{Refusal Accuracy} = \frac{1}{|Q_{\text{unans}}|} \sum_{q \in Q_{\text{unans}}} \mathbb{I}_{\text{refusal}}(q)$$
  - False Refusal Rate (FRR):
    $$\text{FRR} = \frac{1}{|Q_{\text{ans}}|} \sum_{q \in Q_{\text{ans}}} \mathbb{I}_{\text{refusal}}(q)$$

### RQ4: Pareto Frontier & Trade-offs
- **Quality per Dollar (QPD):**
  $$\text{QPD}(q) = \frac{Q(q)}{\text{Cost}(q)}$$
- **Quality per Second (QPS):**
  $$\text{QPS}(q) = \frac{Q(q)}{T_{\text{total}}(q) / 1000}$$
