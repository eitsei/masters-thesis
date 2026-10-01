---
name: thesis-spec-audit
description: "Audit and verify alignment between the RAG benchmarking codebase (rag-benchmark, rag-evaluator) and the thesis methodology specification (thesis/chapters/04_research_methods.tex). Dispatches targeted sub-agents across architecture/pipelines, dataset/ingestion, and metrics/statistics. Use when verifying code against thesis specifications, preparing experiment runs, or after modifying benchmark pipelines."
---

Multi-axis audit verifying whether the Java benchmark application (`rag-code/rag-benchmark`) and Python evaluation suite (`rag-code/rag-evaluator`) strictly conform to the methodology formulated in `thesis/chapters/04_research_methods.tex`.

The ground truth specification is documented in [`SPECIFICATION.md`](SPECIFICATION.md).

## Audit Axes

The audit divides into three parallel, non-overlapping axes:

1. **Architecture & Optimization Pipelines ($M_0$–$M_4$):**
   - Naive baseline ($M_0$): chunk size 512 tokens, overlap 50 tokens, retrieval top-$k=5$.
   - Small-to-Big ($M_1$): child chunk 128 tokens, parent context window 1024 tokens, retrieval top-$k=5$, deduplication and boundary interval-merging.
   - Semantic Re-ranking ($M_2$): candidate pool 50, rerank top-5, model `semantic-ranker-default-004`. Combined pipeline order ($C_5, C_{11}, C_{12}, C_{15}$): child chunks (128) must be reranked *before* parent expansion (1024).
   - Prompt Caching ($M_3$): $\ge 4096$ token threshold, GCP Explicit Context Caching, single-flight creation, cleanup on lifecycle close.
   - Model Cascading ($M_4$): zero-shot routing via Gemini 3.5 Flash-Lite, targets (Flash-Lite / Flash / Pro), recording $T_{\text{route}}$ latency and classifier token overhead.
   - Stack & Storage: Java 21, Spring Boot 4.1.0, Spring AI 2.0.0, pgvector 0.8.0 HNSW cosine index ($M=16, ef\_construction=64, ef\_search=40$).

2. **Datasets & Ingestion Workflows:**
   - Academic benchmark (MuSiQue): 100 QA pairs, stratification 40% 2-hop, 40% 3/4-hop (20% 3-hop, 20% 4-hop), 20% unanswerable.
   - Expected canonical refusal: *"The provided context does not contain sufficient information to answer this query."*
   - Enterprise benchmark (Nordea): 100 QA pairs, 40% 1-hop factual, 40% multi-hop relational, 20% out-of-domain.
   - Closed-corpus setup: 1,333 unique paragraphs matching MuSiQue candidate density to control search space disparity.
   - Experimental execution: exactly 2 unmeasured warm-up queries, sample size $N=100$ per dataset ($N=200$ total, 3,200 total runs across $2^4=16$ matrix).

3. **Evaluation Metrics, Cost Accounting & Statistical Testing:**
   - Latency (RQ1): $T_{\text{total}}$ micro-phase summation, $\text{TTFT} = T_{\text{total}} - T_{\text{stream}}$, reporting $P_{50}$ and $P_{95}$.
   - Cost accounting (RQ2): formula including embedding, uncached, 90% discounted cached context, completion, reranking, and router overhead. Cost normalization per 1,000 queries ($\text{Cost}_{1\text{k}}$). Accurate pricing catalog.
   - Quality (RQ3): RAGAS core metrics, deterministic lexical ($F_1, EM$), composite quality score $Q(q)$ with regularized harmonic mean ($\epsilon = 10^{-4}$). Abstention isolation (Refusal Accuracy & FRR computed strictly outside affirmative RAGAS metrics).
   - Pareto trade-offs (RQ4): $\text{QPD} = Q / \text{Cost}$, $\text{QPS} = Q / (T_{\text{total}} / 1000)$.
   - Statistical hypothesis testing: non-parametric two-tailed Wilcoxon signed-rank test against $C_0$, Holm-Bonferroni step-down correction per metric family ($m=15$), significance $\alpha = 0.05$.

## Process

### 1. Read the Specification
Inspect [`SPECIFICATION.md`](SPECIFICATION.md) and confirm if any recent changes occurred in `thesis/chapters/04_research_methods.tex`. Note any intentional temporary overrides (e.g. LLM judge model selection).

### 2. Dispatch Parallel Sub-agents
Launch three sub-agents in parallel to audit the codebases without context pollution:

- **Sub-agent 1: Architecture & Pipelines**
  - Files to inspect: `rag-benchmark` (`RagQueryService.java`, `application.yml`, `VertexAiReranker.java`, `GoogleGenAiPromptCache.java`, `GoogleGenAiModelRouter.java`, `PgVectorRetrievalExpander.java`, `DocumentIngestionService.java`).
  - Task: Check chunk sizes, overlap, models, HNSW parameters, and execution order of modules.

- **Sub-agent 2: Datasets & Ingestion**
  - Files to inspect: `rag-benchmark` (`MusiqueDatasetReader.java`, `MusiqueDatasetProperties.java`, `NordeaClosedDatasetService.java`, `NordeaClosedDatasetProperties.java`) and `rag-evaluator` (`collector.py`, run scripts).
  - Task: Check sample stratification ratios, unanswerable queries, refusal phrases, warm-up queries (2), and closed corpus tables.

- **Sub-agent 3: Metrics & Statistics**
  - Files to inspect: `rag-evaluator` (`metrics.py`, `evaluator.py`, `stats.py`, `export_latex.py`, `reporter.py`).
  - Task: Check mathematical formulas (TTFT, Cost, Q(q), QPD, QPS), pricing rates, Refusal Accuracy / FRR separation, Wilcoxon signed-rank test, and Holm-Bonferroni correction.

### 3. Classify Discrepancies
For every mismatch between code and thesis, classify into:
- **Critical Mismatch**: Direct divergence in experimental logic, metric formulation, or data distribution that biases results.
- **Configuration Discrepancy**: Default property or model string in config that differs from thesis text but can be set via environment variables.
- **Documented / Known Difference**: Conscious variance acknowledged by the author (such as judge model selection).
- **Thesis Text Discrepancy**: Where code implements a valid enterprise approach but thesis text specifies an unsupported parameter (e.g. splitter overlap support).

### 4. Aggregate Findings
Combine findings into a structured report grouped by the three axes, ending with a prioritized remediation checklist.
