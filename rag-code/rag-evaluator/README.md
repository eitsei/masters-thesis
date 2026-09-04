# RAG Efficiency & Quality Evaluator (`rag-evaluator`)

The quantitative benchmarking, statistical analysis, and reporting suite for the Master's thesis:
> *"Context is King — Optimizing Retrieval-Augmented Generation in Enterprise-Ready Systems"*

This tool integrates directly with the enterprise Spring Boot / Java 21 RAG service (`rag-benchmark`) and Google Cloud Platform (GCP) infrastructure. It measures operational efficiency (latency, token costs) and response quality (RAGAS, EM, F1) across the full factorial matrix ($2^4 = 16$ configurations) on the MuSiQue multi-hop reasoning dataset.

---

## 📐 Implemented Thesis Equations (Chapter 4)

### 1. RQ1 Latency Formulations (§4.5.1)
- **Total Latency ($T_{\text{total}}$):**
  $$T_{\text{total}}(q) = T_{\text{embed}}(q) + T_{\text{route}}(q) + T_{\text{ret}}(q) + T_{\text{rerank}}(q) + T_{\text{expand}}(q) + T_{\text{cache}}(q) + T_{\text{prefill}}(q) + T_{\text{stream}}(q)$$
- **Time to First Token ($\text{TTFT}$):**
  $$\text{TTFT}(q) = T_{\text{total}}(q) - T_{\text{stream}}(q)$$
- **Percentiles:** Steady-state median ($P_{50}$) and tail SLA compliance ($P_{95}$).

### 2. RQ2 Operational Cost Accounting (Thesis Eq. 178 / 181)
$$\text{Cost}(q) = N_{\text{prompt}} \cdot P_{\text{prompt}}^{(m(q))} + N_{\text{comp}} \cdot P_{\text{comp}}^{(m(q))} + N_{\text{cached}} \cdot P_{\text{cached}}^{(m(q))} + N_{\text{rerank}} \cdot P_{\text{rerank}} + N_{\text{embed}} \cdot P_{\text{embed}}$$
- Configurable pricing tiers:
  - **Gemini 2.5 Flash / 3.7 Flash:** Prompt: $0.15/1M, Comp: $0.60/1M, Cached: $0.0375/1M (75% context caching discount).
  - **Gemini 3.5 Flash-Lite:** Prompt: $0.075/1M, Comp: $0.30/1M, Cached: $0.01875/1M.
  - **Gemini 3.1 Pro:** Prompt: $1.25/1M, Comp: $5.00/1M, Cached: $0.3125/1M.
  - **Claude Sonnet 5:** Prompt: $3.00/1M, Comp: $15.00/1M, Cached: $0.30/1M.
  - **Ollama / Local:** $0.00 / 1M.

### 3. RQ3 Quality Metrics (§4.5.3)
- **Deterministic Lexical Overlap:**
  - Standard QA normalization (lowercase, punctuation splitting, article removal).
  - **Exact Match ($\text{EM}$):** Binary indicator against gold standard ground truth and known aliases.
  - **Token $F_1$:** Harmonic mean of token-level precision and recall maximized over aliases.
- **RAGAS Core Evaluation:**
  - Context Precision ($\text{CP}$)
  - Context Recall ($\text{CR}$)
  - Faithfulness ($\text{Faith}$)
  - Answer Relevancy ($\text{AR}$)
- **Composite Quality Score $Q(q)$ (Thesis Eq. 238):**
  $$Q(q) = \frac{4}{\frac{1}{\text{CP}(q) + \epsilon} + \frac{1}{\text{CR}(q) + \epsilon} + \frac{1}{\text{Faith}(q) + \epsilon} + \frac{1}{\text{AR}(q) + \epsilon}}$$
  with numerical smoothing parameter $\epsilon = 10^{-6}$, guaranteeing stability and severely penalizing single-dimension failure modes.

### 4. RQ4 Trade-off Ratios & Pareto Frontier (§4.5.4)
- **Quality per Dollar ($\text{QPD}$, Eq. 250):**
  $$\text{QPD}(q) = \frac{Q(q)}{\text{Cost}(q)}$$
- **Quality per Second ($\text{QPS}$, Eq. 255):**
  $$\text{QPS}(q) = \frac{Q(q)}{T_{\text{total}}(q) / 1000}$$
- **Pareto Dominance ($\succ$):** A configuration $C_a \succ C_b$ iff $Q(C_a) \ge Q(C_b)$, $\text{Cost}(C_a) \le \text{Cost}(C_b)$, and $T(C_a) \le T(C_b)$ with at least one strict inequality.

### 5. Non-Parametric Significance Testing (§4.1, Table 4.2)
- Two-tailed **Wilcoxon signed-rank test** across paired queries ($C_k$ vs $C_0$).
- **Holm-Bonferroni step-down correction** to control Family-Wise Error Rates (FWER) across the 15 pairwise comparisons:
  $$p^{\text{adj}}_{(i)} = \min\left(1.0, \max_{j \le i} \left((m - j + 1) \cdot p_{(j)}\right)\right)$$
- Significance flags: $^{***}\,(p < 0.001)$, $^{**}\,(p < 0.01)$, $^{*}\,(p < 0.05)$, $\text{ns}\,(p \ge 0.05)$.

---

## 🚀 Quickstart

### 1. Setup Virtual Environment
```bash
cd rag-code/rag-evaluator
./setup_env.sh
source venv/bin/activate
```

Or manually:
```bash
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

### 2. Environment Configuration
Copy `.env.example` to `.env`:
```bash
cp .env.example .env
```
Edit `.env` to configure your RAG service URL and Judge model:
```env
# Spring Boot RAG service URL
RAG_APP_URL=http://localhost:8080

# Judge Provider (ollama, gemini, or claude)
JUDGE_PROVIDER=ollama
JUDGE_MODEL=gemma4:e2b
JUDGE_EMBEDDING_MODEL=nomic-embed-text:latest
OLLAMA_BASE_URL=http://localhost:11434

# API Keys (for cloud judges)
GOOGLE_AI_API_KEY=your_gemini_key_here
ANTHROPIC_API_KEY=your_anthropic_key_here
```

### 3. Verify Judge Connectivity (Smoke Test)
```bash
venv/bin/python src/cli.py test-judge --judge ollama
```

---

## 💻 Execution Commands

### Local Ollama Evaluation
```bash
# Baseline (C0) evaluation on 100-question benchmark sample
venv/bin/python src/cli.py run \
  --url http://localhost:8080 \
  --sample \
  --warmup 2 \
  --judge ollama \
  --output runs/run_C0_baseline.jsonl

# Small-to-Big Retrieval (C1: S2B)
venv/bin/python src/cli.py run \
  --url http://localhost:8080 \
  --sample \
  --modules M1 \
  --warmup 2 \
  --judge ollama \
  --output runs/run_C1_S2B.jsonl

# Small-to-Big + Semantic Re-ranking (C5: S2B+SR)
venv/bin/python src/cli.py run \
  --url http://localhost:8080 \
  --sample \
  --modules M1,M2 \
  --warmup 2 \
  --judge ollama \
  --output runs/run_C5_S2B_SR.jsonl
```

### GCP Cloud Run / Cloud SQL Evaluation
```bash
# Run against deployed Cloud Run service using Gemini 2.5 Flash judge
venv/bin/python src/cli.py run \
  --url https://rag-benchmark-service-xyz.run.app \
  --sample \
  --warmup 2 \
  --judge gemini \
  --judge-model gemini-2.5-flash \
  --debug \
  --output runs/run_C15_FullStack.jsonl
```

### Offline / Rapid Smoke Test (No Judge LLM)
```bash
venv/bin/python src/cli.py run \
  --url http://localhost:8080 \
  --limit 10 \
  --modules M1 \
  --judge none \
  --output runs/smoke_test.jsonl
```

---

## 📊 CLI Command Reference

### `run`
Executes an end-to-end benchmark run against the Spring Boot service:
```
Options:
  --url          TEXT     Spring Boot service base URL
  --limit, -l    INTEGER  Number of questions to evaluate
  --hops, -h     INTEGER  Filter questions by hop count (2, 3, or 4)
  --sample, -s            Use canonical 100-question benchmark sample
  --modules, -m  TEXT     Optimization modules comma-separated (e.g. M1,M2)
  --table-name   TEXT     Vector store pgvector table override
  --judge, -j    TEXT     Judge provider: ollama, gemini, claude, none [default: none]
  --judge-model  TEXT     Judge model identifier
  --warmup, -w   INTEGER  Number of unmeasured warm-up queries [default: 2]
  --debug, -d             Enable detailed client payload and timing logs
  --output, -o   TEXT     Path to output JSONL file
  --top-k        INTEGER  Retrieval top-k candidate override
```

### `test-judge`
Tests connectivity and latency to the LLM judge:
```bash
venv/bin/python src/cli.py test-judge --judge gemini --judge-model gemini-2.5-flash
```

### `evaluate`
Re-scores or attaches RAGAS evaluation to an existing raw JSONL run:
```bash
venv/bin/python src/cli.py evaluate \
  --input runs/raw_run.jsonl \
  --judge gemini \
  --output runs/run_evaluated.jsonl
```

### `compare`
Performs paired Wilcoxon signed-rank tests and Holm-Bonferroni step-down correction:
```bash
venv/bin/python src/cli.py compare \
  --baseline runs/run_C0_baseline.jsonl \
  --treatment runs/run_C1_S2B.jsonl \
  --config-name "C1: S2B" \
  --output-latex ../../thesis/tables/wilcoxon_tests.tex
```

### `export-thesis`
Generates all publication-grade LaTeX tables and vector PDF figures:
```bash
venv/bin/python src/cli.py export-thesis \
  --input runs/ \
  --tables-dir ../../thesis/tables \
  --figures-dir ../../thesis/figures
```

---

## 📦 Generated Output Artifacts

### 1. Benchmark Run Logs (`runs/*.jsonl`)
Contains JSON-lines records for every evaluated query, including:
- `questionId`, `question`, `groundTruthAnswer`, `groundTruthAliases`, `hopCount`
- `generatedAnswer`, `supportingEvidenceFound`, `retrievedChunks`
- `timings` (`retrievalMs`, `generationMs`), `totalLatencyMs`
- `tokenUsage` (`promptTokens`, `completionTokens`, `cachedTokens`, `totalTokens`)
- `cost_usd`, `exact_match`, `token_f1`
- `faithfulness`, `answer_relevancy`, `context_precision`, `context_recall`
- `composite_quality`, `qpd`, `qps`

### 2. Publication-Ready LaTeX Tables (`thesis/tables/*.tex`)
- `thesis/tables/benchmark_summary.tex`: Comprehensive comparison table with $P_{50}, P_{95}$ latency, tokens, cost, EM, F1, RAGAS scores, and Composite $Q$. Ready for `\input{tables/benchmark_summary.tex}`.
- `thesis/tables/hop_breakdown.tex`: Performance and quality stratified by 2-hop, 3-hop, and 4-hop query complexity tiers.
- `thesis/tables/wilcoxon_tests.tex`: Formatted Table 4.2 with $\tilde{\mu}_\Delta$, $W$-statistic, $p_{\text{raw}}$, $p_{\text{adj}}$, and significance stars.

### 3. Vector PDF Figures (`thesis/figures/*.pdf`)
- `thesis/figures/pareto_frontier.pdf`: Dual-panel figure showing Quality vs. Latency and Quality vs. Cost with empirical Pareto frontier curves ($\mathcal{P}$).
- `thesis/figures/latency_breakdown.pdf`: Stacked bar chart isolating Retrieval ($T_{\text{ret}}$) and Generation ($T_{\text{gen}}$) micro-phases.
- `thesis/figures/ragas_metrics_radar.pdf`: Polar radar chart comparing configurations across the 4 core RAGAS dimensions.

---

## 🧪 Testing Suite

Run the complete test suite with 100% coverage across metrics, statistics, LaTeX export, and CLI commands:
```bash
venv/bin/pytest tests/test_metrics.py tests/test_stats.py tests/test_export.py tests/test_cli.py
```
