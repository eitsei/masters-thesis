# RAG Efficiency & Quality Evaluator (`rag-evaluator`)

Quantitative benchmarking, statistical analysis, and thesis artifact generation suite for the Master's thesis:

> *"Context is King — Optimizing Retrieval-Augmented Generation in Enterprise-Ready Systems"*

This tool integrates with the Spring Boot / Java 21 RAG service (`rag-benchmark`) and evaluation backends (local vLLM on CSC Roihu GPU, Google Cloud Vertex AI, or Ollama). It measures operational efficiency (latency, TTFT, token consumption, USD cost) and response quality (Exact Match, Token $F_1$, RAGAS, Composite $Q$) across the $2^4 = 16$ full-factorial configuration matrix ($C_0$–$C_{15}$) on the MuSiQue and Enterprise datasets.

---

## Architecture & Directory Layout

The benchmarking pipeline follows a decoupled two-stage workflow:

1. **Stage 1 — Raw Query Collection (`runs/raw/`):** Executes queries against the `rag-benchmark` Spring Boot service (`--judge none`) with cache pre-flight verification and JVM warm-up, recording raw responses, retrieved chunks, micro-phase timings, and token usage to `runs/raw/<dataset>/`.
2. **Stage 2 — Offline Evaluation & Export (`runs/evaluated/`):** Computes deterministic lexical metrics, operational costs, and LLM-as-a-judge RAGAS scores (`evaluate`), outputting enriched records to `runs/evaluated/<dataset>/`, followed by paired statistical tests (`compare`) and publication-ready LaTeX/PDF generation (`export-thesis`).

```text
rag-evaluator/
├── data/
│   ├── enterprise/          # Enterprise 100-question benchmark, 10-sample, and source CSVs
│   └── musique/             # MuSiQue 100-question canonical sample and 10-sample
├── runs/
│   ├── raw/                 # Stage 1 raw JSONL logs (musique/, enterprise_closed/, enterprise_raw/)
│   └── evaluated/           # Stage 2 RAGAS-scored JSONL logs (musique/, enterprise_closed/, enterprise_raw/)
├── scripts/
│   ├── benchmarks/          # Stage 1 full-16 factorial runner scripts
│   ├── roihu/               # Stage 2 CSC Roihu Slurm batch jobs (vLLM Qwen3.8-27B + BGE-M3)
│   └── utils/               # Helper scripts for single-run evaluation
├── src/                     # Core Python package (CLI, client, metrics, stats, judge, exporters)
├── tests/                   # Pytest unit and integration test suite
├── pytest.ini
├── requirements.txt
├── requirements-roihu.txt
└── setup_env.sh
```

---

## Implemented Thesis Equations (Chapter 4)

### 1. RQ1: Latency Formulations (§4.5.1)

- **Total Latency ($T_{\text{total}}$):**
  $$T_{\text{total}}(q) = T_{\text{embed}}(q) + T_{\text{route}}(q) + T_{\text{ret}}(q) + T_{\text{rerank}}(q) + T_{\text{expand}}(q) + T_{\text{cache}}(q) + T_{\text{prefill}}(q) + T_{\text{stream}}(q)$$
- **Time to First Token ($\text{TTFT}$):**
  $$\text{TTFT}(q) = T_{\text{total}}(q) - T_{\text{stream}}(q)$$
- **Percentiles:** Steady-state median ($P_{50}$) and tail SLA compliance ($P_{95}$).

### 2. RQ2: Operational Cost Accounting (§4.5.2)

$$\text{Cost}(q) = N_{\text{prompt}} \cdot P_{\text{prompt}}^{(m(q))} + N_{\text{comp}} \cdot P_{\text{comp}}^{(m(q))} + N_{\text{cached}} \cdot P_{\text{cached}}^{(m(q))} + N_{\text{rerank}} \cdot P_{\text{rerank}} + N_{\text{embed}} \cdot P_{\text{embed}}$$

Configurable pricing tiers defined in `src/metrics.py` (`PRICING_CATALOG`):
- **`gemini-3.8-flash`** (default generation tier): Prompt `$0.75/1M`, Completion `$3.75/1M`, Cached `$0.075/1M` (90% context caching discount).
- **`gemini-3.5-flash`**: Prompt `$1.50/1M`, Completion `$9.00/1M`, Cached `$0.15/1M`.
- **`gemini-3.5-flash-lite`** (routing / lightweight tier): Prompt `$0.30/1M`, Completion `$2.50/1M`, Cached `$0.03/1M`.
- **`gemini-3.7-flash` / `gemini-2.5-flash`**: Prompt `$0.15/1M`, Completion `$0.60/1M`, Cached `$0.0375/1M`.
- **`gemini-3.1-pro`** (complex multi-hop tier): Prompt `$2.00/1M`, Completion `$12.00/1M`, Cached `$0.20/1M`.
- **`claude-sonnet-5`**: Prompt `$3.00/1M`, Completion `$15.00/1M`, Cached `$0.30/1M`.
- **`ollama` / `local`**: `$0.00/1M`.

### 3. RQ3: Quality Metrics (§4.5.3)

- **Deterministic Lexical Overlap:**
  - Standard QA normalization (lowercase, punctuation removal, article stripping).
  - **Exact Match ($\text{EM}$):** Binary indicator against ground truth and known aliases.
  - **Token $F_1$:** Harmonic mean of token-level precision and recall maximized over aliases.
- **RAGAS Core Evaluation (Answerable Queries):**
  - Context Precision ($\text{CP}$)
  - Context Recall ($\text{CR}$)
  - Faithfulness ($\text{Faith}$)
  - Answer Relevancy ($\text{AR}$)
- **Composite Quality Score $Q(q)$:**
  $$Q(q) = \frac{4}{\frac{1}{\text{CP}(q) + \epsilon} + \frac{1}{\text{CR}(q) + \epsilon} + \frac{1}{\text{Faith}(q) + \epsilon} + \frac{1}{\text{AR}(q) + \epsilon}}$$
  with numerical smoothing parameter $\epsilon = 10^{-6}$, penalizing single-dimension failure modes.
- **Unanswerable / Out-of-Domain Queries:** Evaluated via refusal accuracy and false refusal rate without invoking RAGAS.

### 4. RQ4: Trade-off Ratios & Pareto Frontier (§4.5.4)

- **Quality per Dollar ($\text{QPD}$):**
  $$\text{QPD}(q) = \frac{Q(q)}{\text{Cost}(q)}$$
- **Quality per Second ($\text{QPS}$):**
  $$\text{QPS}(q) = \frac{Q(q)}{T_{\text{total}}(q) / 1000}$$
- **Pareto Dominance ($\succ$):** Configuration $C_a \succ C_b$ iff $Q(C_a) \ge Q(C_b)$, $\text{Cost}(C_a) \le \text{Cost}(C_b)$, and $T(C_a) \le T(C_b)$ with at least one strict inequality.

### 5. Non-Parametric Significance Testing (§4.1)

- Two-tailed **Wilcoxon signed-rank test** across paired queries ($C_k$ vs. $C_0$).
- **Holm-Bonferroni step-down correction** controlling Family-Wise Error Rate (FWER) across $m = 15$ pairwise comparisons:
  $$p^{\text{adj}}_{(i)} = \min\left(1.0, \max_{j \le i} \left((m - j + 1) \cdot p_{(j)}\right)\right)$$
- Significance flags: $^{***}\,(p < 0.001)$, $^{**}\,(p < 0.01)$, $^{*}\,(p < 0.05)$, $\text{ns}\,(p \ge 0.05)$.

---

## Quickstart

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

Copy `.env.example` to `.env` and adjust for your target backend and judge:

```bash
cp .env.example .env
```

Example `.env` configurations:

```env
# Spring Boot RAG service URL
RAG_APP_URL=http://localhost:8080

# Judge Provider: vllm, gemini, vertex, ollama, claude, or none
JUDGE_PROVIDER=vllm
JUDGE_MODEL=qwen3.8-27b
JUDGE_EMBEDDING_MODEL=bge-m3

# Local vLLM OpenAI-compatible endpoints (e.g. CSC Roihu GPU node)
VLLM_BASE_URL=http://127.0.0.1:8000/v1
VLLM_EMBEDDING_BASE_URL=http://127.0.0.1:8001/v1

# Google Cloud Vertex AI (Gemini 3.x requires location=global; text-embedding-004 uses regional e.g. us-central1)
# GOOGLE_CLOUD_PROJECT_ID=your-project-id
# GOOGLE_CLOUD_LOCATION=global
# GOOGLE_CLOUD_EMBEDDING_LOCATION=us-central1
```

### 3. Verify Judge Connectivity

```bash
# Local vLLM judge
venv/bin/python src/cli.py test-judge --judge vllm --judge-model qwen3.8-27b

# Vertex AI Gemini judge
venv/bin/python src/cli.py test-judge --judge gemini --judge-model gemini-3.5-flash-lite
```

---

## Execution Workflows

### Stage 1: Raw Benchmark Collection (`runs/raw/`)

Run individual configurations or full 16-configuration sweeps with `--judge none` against the local Spring Boot service:

```bash
# Baseline (C0) on canonical 100-question sample
venv/bin/python src/cli.py run \
  --url http://localhost:8080 \
  --sample \
  --warmup 2 \
  --judge none \
  --output runs/raw/musique/run_C0_baseline.jsonl

# Small-to-Big + Semantic Re-ranking (C5: M1+M2)
venv/bin/python src/cli.py run \
  --url http://localhost:8080 \
  --sample \
  --modules M1,M2 \
  --warmup 2 \
  --judge none \
  --output runs/raw/musique/run_C5_M1_M2.jsonl
```

Or execute the full $2^4 = 16$ factorial sweep via the provided runner scripts (see [scripts/README.md](scripts/README.md)):

```bash
./scripts/benchmarks/run_musique_all16.sh
./scripts/benchmarks/run_enterprise_closed_all16.sh
./scripts/benchmarks/run_enterprise_raw_all16.sh
```

### Stage 2: Offline RAGAS Evaluation (`runs/evaluated/`)

Score raw runs in `runs/raw/<dataset>/` and write evaluated records to `runs/evaluated/<dataset>/`:

```bash
# Evaluate a full directory using local vLLM (16 parallel workers)
venv/bin/python src/cli.py evaluate \
  --input runs/raw/musique \
  --output runs/evaluated/musique \
  --judge vllm \
  --judge-model qwen3.8-27b \
  --workers 16

# Evaluate a single run using Vertex AI Gemini
venv/bin/python src/cli.py evaluate \
  --input runs/raw/musique/run_C0_baseline.jsonl \
  --output runs/evaluated/musique/run_C0_baseline.jsonl \
  --judge gemini \
  --judge-model gemini-3.5-flash-lite
```

### Stage 3: Statistical Comparison & Thesis Export

```bash
# Paired Wilcoxon signed-rank comparison
venv/bin/python src/cli.py compare \
  --baseline runs/evaluated/musique/run_C0_baseline.jsonl \
  --treatment runs/evaluated/musique/run_C5_M1_M2.jsonl \
  --config-name "C5: M1+M2" \
  --output-latex ../../thesis/tables/musique/wilcoxon_tests.tex

# Export all thesis LaTeX tables and vector PDF figures
venv/bin/python src/cli.py export-thesis \
  --input runs/evaluated/musique \
  --subfolder musique \
  --dataset-name "MuSiQue"
```

---

## CLI Command Reference

### `run`

Executes an end-to-end benchmark run against the Spring Boot RAG service:

```text
Options:
  --url              TEXT     RAG service URL (default: RAG_APP_URL or http://localhost:8080)
  --limit, -l        INTEGER  Number of benchmark questions to evaluate
  --hops, -h         INTEGER  Filter by hop count (e.g. 2, 3, 4)
  --sample, -s                Use canonical 100-question sample
  --modules, -m      TEXT     Comma-separated optimization modules (e.g. M1,M2)
  --table-name       TEXT     Vector store table name override
  --judge, -j        TEXT     Judge provider: vllm, gemini, vertex, claude, ollama, none [default: none]
  --judge-model      TEXT     Judge model identifier
  --warmup, -w       INTEGER  Number of unmeasured warm-up queries [default: 2]
  --debug, -d                 Enable verbose debug logging
  --output, -o       TEXT     Path to save evaluation results JSONL
  --top-k            INTEGER  Retrieval top-k override
  --stream                    Use streaming query collector with real-time JSONL checkpointing
  --judge-delay      FLOAT    Pacing delay (seconds) between judge calls (default: 1.5s cloud, 0s local)
  --timeout          FLOAT    Request timeout in seconds [default: 300.0]
  --skip-cache-check          Skip pre-flight prompt cache verification
```

### `test-judge`

Tests connectivity, responsiveness, and latency of the configured evaluation judge and embeddings:

```bash
venv/bin/python src/cli.py test-judge --judge vllm --judge-model qwen3.8-27b
```

### `test-cache`

Verifies prompt cache isolation, explicit prewarming, and cache hit integrity (`--url`, `--models`, `--table-name`, `--timeout`, `--debug`).

### `evaluate`

Computes EM, $F_1$, operational cost, RAGAS metrics, and Composite $Q$ on existing raw JSONL run(s):

```text
Options:
  --input, -i        PATH     Input JSONL file or directory containing run JSONL files [required]
  --judge, -j        TEXT     Judge provider: vllm, gemini, vertex, claude, ollama, none [default: claude]
  --judge-model      TEXT     Judge model identifier
  --output, -o       PATH     Output JSONL path (or directory if input is a directory)
  --inplace                   Update input file(s) in place
  --resume/--no-resume        Skip samples that already have RAGAS scores [default: True]
  --force, -f                 Force re-evaluation of all samples
  --judge-delay      FLOAT    Pacing delay (seconds) between judge calls
  --pricing-tier     TEXT     Default pricing tier for cost calculations [default: gemini-3.8-flash]
  --workers, -w      INTEGER  Concurrent worker threads for parallel evaluation [default: 1]
```

### `compare`

Performs paired Wilcoxon signed-rank tests with Holm-Bonferroni correction (`--baseline`/`-b`, `--treatment`/`-t`, `--output-latex`, `--config-name`).

### `export-thesis`

Generates publication-ready LaTeX tables and vector PDF figures (`--input`/`-i`, `--tables-dir`, `--figures-dir`, `--subfolder`/`-s`, `--dataset-name`/`-d`, `--family-scope`).

### `query` & `run-questions`

- `query`: Executes and evaluates a single ad-hoc query against a specified `--table-name`.
- `run-questions`: Runs a batch of questions from a JSON/JSONL file (`--input`) against a `--table-name` with retry handling (`--max-retries`) and incremental checkpointing.

### `build-enterprise-musique-dataset`

Constructs a MuSiQue-schema enterprise benchmark JSONL dataset from Finnish question CSVs and crawled markdown corpus documents.

---

## Generated Output Artifacts

1. **Benchmark Run Logs (`runs/raw/<dataset>/*.jsonl`, `runs/evaluated/<dataset>/*.jsonl`):**
   Records containing `questionId`, `question`, `groundTruthAnswer`, `groundTruthAliases`, `hopCount`, `generatedAnswer`, `retrievedChunks`, `timings`, `total_latency_ms`, `ttft_ms`, `tokenUsage`, `cost_usd`, `exact_match`, `token_f1`, `faithfulness`, `answer_relevancy`, `context_precision`, `context_recall`, `composite_quality`, `qpd`, and `qps`.
2. **LaTeX Tables (`thesis/tables/<subfolder>/*.tex`):**
   Includes `benchmark_summary.tex`, `hop_breakdown.tex`, `wilcoxon_tests.tex`, `wilcoxon_rq*.tex`, and dataset-specific `tab_latency_*.tex`, `tab_cost_*.tex`, `tab_quality_*.tex`, and `tab_pareto_*.tex`.
3. **Vector PDF Figures (`thesis/figures/<subfolder>/*.pdf`):**
   Includes `pareto_frontier*.pdf`, `pareto_frontier_3d*.pdf`, `latency_breakdown*.pdf`, `cost_breakdown*.pdf`, `quality_lollipop*.pdf` (primary), `quality_radar*.pdf`, and `quality_delta_matrix*.pdf`.

---

## Testing Suite

Run the full test suite (177 tests across 11 modules in `tests/`):

```bash
venv/bin/pytest
```

Or run targeted test modules:

```bash
venv/bin/pytest tests/test_metrics.py tests/test_stats.py tests/test_export.py tests/test_cli.py
```
