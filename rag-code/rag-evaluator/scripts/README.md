# RAG Benchmark & Evaluation Scripts (`rag-code/rag-evaluator/scripts`)

This directory contains executable scripts for running RAG pipeline sweeps, evaluating outputs with LLM-as-a-judge (RAGAS), and exporting publication-ready LaTeX tables and figures for the Master's thesis.

The benchmarking workflow is decoupled into a **two-stage architecture**:
1. **Stage 1 (Local Query Execution):** Run queries against the local Spring Boot service (`http://localhost:8080`) over GCP APIs. Evaluates latency, TTFT, token usage, and responses with `--judge none` (no LLM judge overhead).
2. **Stage 2 (CSC Roihu GPU Evaluation & Export):** Submit Slurm batch jobs (`sbatch`) to CSC Roihu (NVIDIA GH200 96GB GPU) to evaluate RAGAS Faithfulness & Answer Relevance with Qwen3.8-27B and BGE-M3, followed by automatic Wilcoxon signed-rank tests and LaTeX table/figure exports.

---

## 📁 Directory Structure

```
scripts/
├── benchmarks/              # Stage 1: Local query runners (Spring Boot backend)
│   ├── run_musique_all16.sh
│   ├── run_enterprise_closed_all16.sh
│   └── run_enterprise_raw_all16.sh
│
├── roihu/                   # Stage 2: CSC Roihu Slurm GPU evaluation jobs
│   ├── eval_musique.sbatch
│   ├── eval_enterprise_closed.sbatch
│   └── eval_enterprise_raw.sbatch
│
└── utils/                   # Utilities & offline evaluation
    └── evaluate_run.sh
```

---

## 🚀 Stage 1: Local Query Runners (`scripts/benchmarks/`)

These scripts execute the full $2^4 = 16$ factorial pipeline matrix ($C_0$ to $C_{15}$) against the Spring Boot application running locally at `http://localhost:8080`.

- **Judge Mode:** Suppressed (`--judge none`). Zero judge token cost, zero judge rate-limiting.
- **Cache Management:** Automatically flushes remote/local caches between configurations and pre-warms context caches before prompt-caching configurations ($M_3$).
- **Output:** Raw JSONL files saved in `runs/raw/<dataset>/` containing question metadata, response text, chunk references, TTFT, and latency breakdowns.

| Script | Dataset ($N=100$) | Vector Table | Output Run Directory |
| :--- | :--- | :--- | :--- |
| [`run_musique_all16.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/benchmarks/run_musique_all16.sh) | `data/musique/musique_100_final.jsonl` | `vector_store_musique` | `runs/raw/musique` |
| [`run_enterprise_closed_all16.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/benchmarks/run_enterprise_closed_all16.sh) | `data/nordea/nordea_100_final.jsonl` | `vector_store_nordea_closed` | `runs/raw/enterprise_closed` |
| [`run_enterprise_raw_all16.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/benchmarks/run_enterprise_raw_all16.sh) | `data/nordea/nordea_100_final.jsonl` | `vector_store_nordea` | `runs/raw/enterprise_raw` |

### Running Local Benchmarks
```bash
# Make sure Spring Boot backend is running:
# (cd ../rag-benchmark && ./gradlew bootRun)

# 1. Run MuSiQue benchmark
./scripts/benchmarks/run_musique_all16.sh

# 2. Run Enterprise Closed benchmark
./scripts/benchmarks/run_enterprise_closed_all16.sh

# 3. Run Enterprise Raw benchmark
./scripts/benchmarks/run_enterprise_raw_all16.sh
```

#### Environment Variable Overrides
All runner scripts accept standard environment variables:
```bash
# Run with smaller limit for quick sanity check:
LIMIT=5 ./scripts/benchmarks/run_musique_all16.sh

# Override target backend URL:
RAG_APP_URL="http://localhost:8080" ./scripts/benchmarks/run_enterprise_closed_all16.sh
```

---

## ⚡ Stage 2: CSC Roihu GPU Batch Jobs (`scripts/roihu/`)

These Slurm batch scripts are executed on the **CSC Roihu supercomputer** (`gpumedium` partition, 1x NVIDIA GH200 96GB GPU, `#SBATCH --time=04:00:00`).

- **Architecture:** Sequentially initializes two co-located vLLM servers on the GH200 GPU:
  1. `BAAI/bge-m3` on port 8001 (GPU memory utilization ~0.08) for embeddings.
  2. `Qwen/Qwen3.8-27B` on port 8000 (GPU memory utilization ~0.82) for judge generation.
- **RAGAS Evaluation:** Evaluates 16 runs in parallel across 16 worker threads with per-item checkpointing (`--resume`).
- **Thesis Export:** Automatically runs Wilcoxon signed-rank tests and exports LaTeX tables and vector figures into `thesis/tables/<dataset>` and `thesis/figures/<dataset>`.

| Batch Script | Evaluates Input | Output Evaluated Runs | Thesis Export Subfolder |
| :--- | :--- | :--- | :--- |
| [`eval_musique.sbatch`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/roihu/eval_musique.sbatch) | `runs/raw/musique` | `runs/evaluated/musique` | `thesis/tables/musique`<br>`thesis/figures/musique` |
| [`eval_enterprise_closed.sbatch`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/roihu/eval_enterprise_closed.sbatch) | `runs/raw/enterprise_closed` | `runs/evaluated/enterprise_closed` | `thesis/tables/enterprise_closed`<br>`thesis/figures/enterprise_closed` |
| [`eval_enterprise_raw.sbatch`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/roihu/eval_enterprise_raw.sbatch) | `runs/raw/enterprise_raw` | `runs/evaluated/enterprise_raw` | `thesis/tables/enterprise`<br>`thesis/figures/enterprise` |

### Submitting Slurm Jobs on Roihu
```bash
# Submit MuSiQue evaluation
sbatch scripts/roihu/eval_musique.sbatch

# Submit Enterprise Closed evaluation
sbatch scripts/roihu/eval_enterprise_closed.sbatch

# Submit Enterprise Raw evaluation
sbatch scripts/roihu/eval_enterprise_raw.sbatch
```

Jobs can be monitored with:
```bash
squeue --me
tail -f logs/eval_musique_<job_id>.out
```

---

## 🛠️ Standalone Offline Evaluation Utility (`scripts/utils/`)

[`evaluate_run.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/utils/evaluate_run.sh) allows evaluating any existing directory of `run_C*.jsonl` files offline (either locally or on a server), executing Wilcoxon comparisons vs $C_0$ baseline, and exporting LaTeX tables and figures without submitting Slurm jobs.

```bash
# Evaluate using local vLLM judge:
./scripts/utils/evaluate_run.sh runs/raw/musique --judge vllm --workers 16 --subfolder musique

# Evaluate using Gemini judge API:
./scripts/utils/evaluate_run.sh runs/raw/enterprise_closed --judge gemini --judge-model gemini-3.5-flash-lite --subfolder enterprise_closed

# Export tables/figures only without re-evaluating:
./scripts/utils/evaluate_run.sh runs/evaluated/musique --no-eval --subfolder musique
```

---

## 📦 Dataset Generation (Reference)

All evaluation datasets (`musique_100_final.jsonl`, `nordea_100_final.jsonl`, etc.) are pre-generated, verified, and committed under `data/`.

If you ever need to regenerate datasets from raw source materials, run the CLI commands directly rather than through shell wrappers:
```bash
# Regenerate Nordea synthetic testset:
python src/cli.py generate-nordea-testset --docs-dir ../rag-benchmark/data/nordea --output data/nordea/nordea_benchmark_100.jsonl --limit 100

# Rebuild combined Nordea-MuSiQue evaluation set:
python src/cli.py build-nordea-musique-dataset --nordea data/nordea/nordea_100_final.jsonl --musique data/musique/musique_100_final.jsonl --output data/combined/
```
