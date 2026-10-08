# Benchmark Runner Scripts (`rag-code/rag-evaluator/scripts`)

This directory contains executable shell scripts for launching automated benchmark evaluation runs, statistical Wilcoxon signed-rank tests, and publication-ready LaTeX table/figure exports.

## Execution Modes (Decoupled Workflow)

All benchmark runners support separated query execution and quality evaluation via stage flags. This allows fast query collection against the Spring Boot backend without waiting for the LLM judge, followed by offline RAGAS quality evaluation and thesis report generation:

```
┌─────────────────────────────────┐
│ Stage 1: Queries Only           │  ./scripts/run_nordea_all16.sh --queries-only
│ (Fast: latency, tokens, chunks) │  (Sets --judge none, saves run_C*.jsonl)
└────────────────┬────────────────┘
                 ▼
┌─────────────────────────────────┐
│ Stage 2: Offline Evaluation     │  ./scripts/run_nordea_all16.sh --eval-only
│ (RAGAS: Faithfulness, Relevance)│  OR: ./scripts/evaluate_run.sh runs/nordea_all16
└────────────────┬────────────────┘  (Updates run_C*.jsonl in-place, checkpointed)
                 ▼
┌─────────────────────────────────┐
│ Stage 3: Reporting & Exports    │  ./scripts/run_nordea_all16.sh --report-only
│ (Wilcoxon tests, LaTeX tables)  │  (Generates Wilcoxon terminal & LaTeX tables)
└─────────────────────────────────┘
```

| Flag | Description |
| :--- | :--- |
| `--queries-only` / `--queries` | Rapidly executes benchmark queries against the Spring Boot service with `--judge none`. Records response latency, token consumption, and retrieved chunks to `run_C*.jsonl` without blocking on the LLM judge. |
| `--eval-only` / `--eval` | Evaluates previously collected runs in `RUN_DIR` offline using the configured LLM judge (RAGAS Faithfulness & Relevance), updating records in-place with per-item checkpointing. Runs Wilcoxon tests and exports LaTeX tables/figures. |
| `--report-only` / `--report` | Re-generates statistical Wilcoxon comparisons and LaTeX publication tables/figures from existing JSONL run files without executing queries or calling the judge. |
| `--all` *(default)* | Executes end-to-end: queries Spring Boot, evaluates quality with the judge online, runs Wilcoxon tests, and exports LaTeX tables and figures. **(100% backward compatible)** |

---

## Available Scripts

| Script | Configurations Included | Output Run Directory | Thesis Export Subfolder |
| :--- | :--- | :--- | :--- |
| [`evaluate_run.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/evaluate_run.sh) | **Standalone Evaluator** (Offline RAGAS evaluation, Wilcoxon tests, and LaTeX export for any run directory) | Specified via argument | Configurable (`--subfolder`) |
| [`run_nordea_all16.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/run_nordea_all16.sh) | **$C_0$ through $C_{15}$** (Full 16-condition factorial matrix on Nordea production data) | `runs/nordea_all16` | `thesis/tables/nordea_all16`<br>`thesis/figures/nordea_all16` |
| [`run_musique_all16.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/run_musique_all16.sh) | **$C_0$ through $C_{15}$** (Full 16-condition factorial matrix on MuSiQue dataset) | `runs/musique_all16` | `thesis/tables/musique_all16`<br>`thesis/figures/musique_all16` |
| [`run_nordea_closed_all16.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/run_nordea_closed_all16.sh) | **$C_0$ through $C_{15}$** (Full 16-condition factorial matrix on closed-corpus Nordea data) | `runs/nordea_closed_all16` | `thesis/tables/nordea_closed_all16`<br>`thesis/figures/nordea_closed_all16` |
| [`run_sample5_all16.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/run_sample5_all16.sh) | **$C_0$ through $C_{15}$** (5-sample probe of full 16-condition factorial matrix) | `runs/sample5_all16` | `thesis/tables/sample5_all16`<br>`thesis/figures/sample5_all16` |
| [`run_sample10_all16.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/run_sample10_all16.sh) | **$C_0$ through $C_{15}$** (10-sample probe of full 16-condition factorial matrix) | `runs/sample10_all16` | `thesis/tables/sample10_all16`<br>`thesis/figures/sample10_all16` |
| [`run_nordea_m3_combos.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/run_nordea_m3_combos.sh) | **$C_0, C_1, C_2, C_3, C_5, C_6, C_8, C_{11}$** (All 8 combinations of M1, M2, M3 on Nordea data) | `runs/nordea_m3_combos` | `thesis/tables/nordea_m3_combos`<br>`thesis/figures/nordea_m3_combos` |
| [`run_sample5_m3_combos.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/run_sample5_m3_combos.sh) | **$C_0, C_1, C_2, C_3, C_5, C_6, C_8, C_{11}$** (All 8 combinations of M1, M2, M3 on MuSiQue) | `runs/sample5_m3_combos` | `thesis/tables/sample5_m3_combos`<br>`thesis/figures/sample5_m3_combos` |
| [`run_sample5_m3.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/run_sample5_m3.sh) | **$C_0, C_1, C_2, C_3, C_5$** (Baseline, S2B, Re-rank, Cache, S2B+Re-rank) | `runs/sample5_m3` | `thesis/tables/sample5_m3`<br>`thesis/figures/sample5_m3` |
| [`run_sample5_m1_m2.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/run_sample5_m1_m2.sh) | **$C_0, C_1, C_2, C_5$** (Baseline, S2B, Re-rank, S2B+Re-rank) | `runs/sample5` | `thesis/tables/sample5`<br>`thesis/figures/sample5` |
| [`run_sample5_m4.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/run_sample5_m4.sh) | **$C_0, C_4$** (Baseline vs Model Routing M4) | `runs/sample5_m4` | `thesis/tables/sample5_m4`<br>`thesis/figures/sample5_m4` |
| [`run_nordea_closed_1_M3_M4.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/run_nordea_closed_1_M3_M4.sh) | **$C_0, C_{12}$** (Baseline vs Prompt Caching + Model Routing M3+M4 on Closed Nordea) | `runs/nordea_closed_1_M3_M4` | `thesis/tables/nordea_closed_1_M3_M4`<br>`thesis/figures/nordea_closed_1_M3_M4` |
| [`run_nordea_closed_1_M3.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/run_nordea_closed_1_M3.sh) | **$C_0, C_3$** (Baseline vs Prompt Caching M3 on Closed Nordea) | `runs/nordea_closed_1_M3` | `thesis/tables/nordea_closed_1_M3`<br>`thesis/figures/nordea_closed_1_M3` |
| [`run_nordea_closed_1.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/run_nordea_closed_1.sh) | **$C_0, C_1$** (Baseline vs Small-to-Big M1 on Closed Nordea) | `runs/nordea_closed_1` | `thesis/tables/nordea_closed_1`<br>`thesis/figures/nordea_closed_1` |
| [`run_musique_1_M3_M4.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/run_musique_1_M3_M4.sh) | **$C_0, C_{12}$** (Baseline vs Prompt Caching + Model Routing M3+M4 on MuSiQue) | `runs/musique_1_M3_M4` | `thesis/tables/musique_1_M3_M4`<br>`thesis/figures/musique_1_M3_M4` |
| [`generate_nordea_testset.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/generate_nordea_testset.sh) | **Test Set Generator** (Generates 100-question benchmark with 40% single-hop, 40% multi-hop, 20% OOD) | `data/nordea/nordea_benchmark_100.jsonl` | N/A (Test set generation) |
| [`run_baseline_claude.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/run_baseline_claude.sh) | **$C_0$** (Baseline connectivity test with Claude Sonnet 5 judge) | `runs/baseline_claude` | N/A (Smoke/connection test) |

---

## Workflow Examples

### 1. Two-Step Run (Separate Query & Evaluation)

First, execute the queries against the backend service quickly without calling the judge LLM:
```bash
./scripts/run_nordea_all16.sh --queries-only
```
After the query collection completes, evaluate the saved run directory offline:
```bash
# Option A: using the runner script
./scripts/run_nordea_all16.sh --eval-only

# Option B: using the standalone evaluator script
./scripts/evaluate_run.sh runs/nordea_all16 --judge gemini --judge-model gemini-3.5-flash-lite --subfolder nordea_all16
```

### 2. Standalone Evaluator (`evaluate_run.sh`)

[`evaluate_run.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/evaluate_run.sh) evaluates any existing directory containing `run_C*.jsonl` files, executes Wilcoxon tests vs `run_C0_baseline.jsonl`, and exports LaTeX tables and figures:

```bash
# Evaluate an existing run directory:
./scripts/evaluate_run.sh runs/my_experiment

# Specify custom judge and model:
./scripts/evaluate_run.sh runs/my_experiment --judge claude --judge-model claude-sonnet-5

# Skip Wilcoxon comparisons or export:
./scripts/evaluate_run.sh runs/my_experiment --no-compare --no-export
```

### 3. Fault-Tolerant Resumption

The evaluation engine features per-item atomic checkpointing (`.tmp`). If an evaluation run is interrupted (e.g., quota limit, network disconnect, or process termination), re-running the same command automatically resumes from the last completed question without re-evaluating or incurring extra API costs:

```bash
# Automatically resumes incomplete evaluations:
./scripts/run_nordea_all16.sh --eval-only

# To force re-evaluation of all items (ignoring existing scores):
python src/cli.py evaluate --input runs/nordea_all16 --judge gemini --force
```

---

## Environment Variable Overrides

All scripts support configuration overrides without modifying the script file:

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `RAG_APP_URL` | `http://localhost:8080` | URL of Spring Boot benchmark service |
| `LIMIT` | `5` (or `100` in full runs) | Number of benchmark questions to evaluate |
| `TABLE_NAME` | `vector_store_musique` / `vector_store_nordea` | Target vector database table |
| `JUDGE` | `gemini` / `claude` | Evaluation judge provider (`gemini`, `claude`, `vertex`, `ollama`, `none`) |
| `JUDGE_MODEL` | `gemini-3.5-flash-lite` / `claude-sonnet-5` | Evaluation judge LLM identifier |
| `WARMUP` | `0` (or `2` in full runs) | Number of unmeasured warm-up queries |
| `RUN_DIR` | e.g. `runs/nordea_all16` | Directory where raw JSONL files are stored |
| `SUBFOLDER` | e.g. `nordea_all16` | Subfolder in `thesis/tables` & `thesis/figures` |

### Examples:

```bash
# Run queries only against deployed Cloud Run service with 100 questions:
RAG_APP_URL="https://rag-service-xyz.run.app" LIMIT=100 ./scripts/run_nordea_all16.sh --queries-only

# Evaluate quality offline using Claude Sonnet 5 as judge:
JUDGE=claude JUDGE_MODEL=claude-sonnet-5 ./scripts/run_nordea_all16.sh --eval-only
```
