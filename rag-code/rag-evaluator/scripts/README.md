# RAG Benchmark & Evaluation Scripts (`rag-code/rag-evaluator/scripts`)

This directory contains executable scripts for running RAG pipeline sweeps, evaluating outputs with LLM-as-a-judge (RAGAS), and exporting publication-ready LaTeX tables and figures for the Master's thesis.

The benchmarking workflow is decoupled into a **two-stage architecture**:
1. **Stage 1 (Local Query Execution):** Run queries against the local Spring Boot service (`http://localhost:8080`) over GCP APIs. Collects latency, TTFT, token usage, and retrieved chunks with `--judge none` (zero LLM judge overhead).
2. **Stage 2 (CSC Roihu GPU Evaluation & Export):** Submit Slurm batch jobs (`sbatch`) to CSC Roihu (NVIDIA GH200 96GB GPU) to evaluate RAGAS Faithfulness and Answer Relevance with `Qwen3.8-27B` and `bge-m3`, followed by LaTeX table and figure exports.

---

## Directory Structure

```text
scripts/
├── benchmarks/                      # Stage 1: Full 16-config local query runners
│   ├── run_musique_all16.sh
│   ├── run_enterprise_closed_all16.sh
│   └── run_enterprise_raw_all16.sh
├── roihu/                           # Stage 2: CSC Roihu Slurm GPU evaluation jobs
│   ├── eval_musique.sbatch
│   ├── eval_enterprise_closed.sbatch
│   └── eval_enterprise_raw.sbatch
├── utils/                           # Utilities & offline evaluation
│   └── evaluate_run.sh
├── run_musique_sample10.sh          # Stratified 10-question sample suite (MuSiQue)
└── run_enterprise_sample10.sh       # Stratified 10-question sample suite (Enterprise Closed)
```

---

## Stage 1: Local Query Runners (`scripts/benchmarks/`)

These scripts execute the full $2^4 = 16$ factorial pipeline matrix ($C_0$ to $C_{15}$) against the Spring Boot application running locally at `http://localhost:8080` (run from `rag-code/rag-evaluator`).

- **Judge Mode:** Suppressed (`--judge none`) to avoid judge token cost and rate-limiting during latency/cost measurement.
- **Cache Management:** Automatically clears application and Vertex AI prompt caches between configurations and pre-warms context caches before prompt-caching configurations ($M_3$).
- **Output:** Raw JSONL files saved in `runs/raw/<dataset>/` containing question metadata, response text, chunk references, TTFT, token counts, and latency breakdowns.

| Script | Dataset ($N=100$) | Vector Table | Output Run Directory |
| :--- | :--- | :--- | :--- |
| [`run_musique_all16.sh`](benchmarks/run_musique_all16.sh) | `data/musique/musique_100_final.jsonl` | `vector_store_musique` | `runs/raw/musique` |
| [`run_enterprise_closed_all16.sh`](benchmarks/run_enterprise_closed_all16.sh) | `data/enterprise/enterprise_100_final.jsonl` | `vector_store_enterprise_closed` | `runs/raw/enterprise_closed` |
| [`run_enterprise_raw_all16.sh`](benchmarks/run_enterprise_raw_all16.sh) | `data/enterprise/enterprise_100_final.jsonl` | `vector_store_enterprise` | `runs/raw/enterprise_raw` |

### Running Local Benchmarks

```bash
# Make sure Spring Boot backend is running:
# (cd ../rag-benchmark && mvn spring-boot:run)

# 1. Run MuSiQue benchmark
./scripts/benchmarks/run_musique_all16.sh

# 2. Run Enterprise Closed benchmark
./scripts/benchmarks/run_enterprise_closed_all16.sh

# 3. Run Enterprise Raw benchmark
./scripts/benchmarks/run_enterprise_raw_all16.sh
```

#### Environment Variable Overrides

All full-sweep runner scripts accept standard environment variables:

```bash
# Run with smaller limit for a quick sanity check:
LIMIT=5 ./scripts/benchmarks/run_musique_all16.sh

# Override target backend URL:
RAG_APP_URL="http://localhost:8080" ./scripts/benchmarks/run_enterprise_closed_all16.sh
```

### Sample Benchmark Runners (`N=10`)

For rapid smoke testing across a stratified 10-question subset (`C0`, `C1`, `C3`, `C4`, `C10`, `C13`), use the top-level sample scripts:

| Script | Dataset ($N=10$) | Vector Table | Default Output Directory |
| :--- | :--- | :--- | :--- |
| [`run_musique_sample10.sh`](run_musique_sample10.sh) | `data/musique/musique_10_sample.jsonl` | `vector_store_musique` | `runs/raw/musique_sample10_<timestamp>` |
| [`run_enterprise_sample10.sh`](run_enterprise_sample10.sh) | `data/enterprise/enterprise_10_sample.jsonl` | `vector_store_enterprise_closed` | `runs/raw/enterprise_sample10` |

```bash
# Run sample suite and evaluate offline with Gemini judge:
./scripts/run_musique_sample10.sh --eval --judge gemini

# Run Enterprise Closed sample suite with custom limit:
./scripts/run_enterprise_sample10.sh --limit 5 --skip-baseline
```

---

## Stage 2: CSC Roihu GPU Batch Jobs (`scripts/roihu/`)

These Slurm batch scripts are configured for the **CSC Roihu supercomputer** (`gpumedium` partition, 1x NVIDIA GH200 96GB GPU, 16 CPUs, 64G RAM, `--time=04:00:00`).

- **Account & Scratch Paths:** Set `#SBATCH --account` (or pass `--account=$CSC_PROJECT`) and ensure `SCRATCH_BASE` (`/scratch/${CSC_PROJECT}/${USER}`) contains the `venv-roihu` environment and local model weights (`models/Qwen3.8-27B` and `models/bge-m3`).
- **vLLM Architecture:** Sequentially starts two co-located vLLM servers on the GH200 GPU:
  1. `bge-m3` on `127.0.0.1:8001` (`--gpu-memory-utilization 0.08`) for embeddings.
  2. `qwen3.8-27b` on `127.0.0.1:8000` (`--gpu-memory-utilization 0.82`, `--max-model-len 8192`) for judge generation.
- **RAGAS Evaluation:** Evaluates all 16 configurations using 16 parallel workers (`WORKERS=16`) with per-item checkpointing (`--resume`).
- **Thesis Export:** Automatically runs `src/cli.py export-thesis` to export LaTeX tables and vector figures into `thesis/tables/<subfolder>` and `thesis/figures/<subfolder>`.

| Batch Script | Input Runs (Scratch or Repo-Relative) | Evaluated Output (`${SCRATCH_DIR}/...`) | Thesis Export Subfolder |
| :--- | :--- | :--- | :--- |
| [`eval_musique.sbatch`](roihu/eval_musique.sbatch) | `runs/raw/musique` | `runs/evaluated/musique` | `thesis/tables/musique`<br>`thesis/figures/musique` |
| [`eval_enterprise_closed.sbatch`](roihu/eval_enterprise_closed.sbatch) | `runs/raw/enterprise_closed` | `runs/evaluated/enterprise_closed` | `thesis/tables/enterprise_closed`<br>`thesis/figures/enterprise_closed` |
| [`eval_enterprise_raw.sbatch`](roihu/eval_enterprise_raw.sbatch) | `runs/raw/enterprise_raw` | `runs/evaluated/enterprise_raw` | `thesis/tables/enterprise`<br>`thesis/figures/enterprise` |

### Submitting Slurm Jobs on Roihu

```bash
# Submit MuSiQue evaluation
sbatch --account=$CSC_PROJECT scripts/roihu/eval_musique.sbatch

# Submit Enterprise Closed evaluation
sbatch --account=$CSC_PROJECT scripts/roihu/eval_enterprise_closed.sbatch

# Submit Enterprise Raw evaluation
sbatch --account=$CSC_PROJECT scripts/roihu/eval_enterprise_raw.sbatch
```

Jobs and logs can be monitored with:

```bash
squeue --me
tail -f logs/eval_musique_<job_id>.out
```

---

## Standalone Offline Evaluation Utility (`scripts/utils/`)

[`evaluate_run.sh`](utils/evaluate_run.sh) evaluates any existing directory of `run_C*.jsonl` files offline (locally or on a server), runs paired Wilcoxon signed-rank tests against the $C_0$ baseline, and exports LaTeX tables and figures without submitting Slurm jobs.

```bash
# Evaluate using local vLLM judge:
./scripts/utils/evaluate_run.sh runs/raw/musique --judge vllm --workers 16 --subfolder musique

# Evaluate using Gemini judge API:
./scripts/utils/evaluate_run.sh runs/raw/enterprise_closed --judge gemini --judge-model gemini-3.5-flash-lite --subfolder enterprise_closed

# Evaluate without running Wilcoxon comparisons or thesis export:
./scripts/utils/evaluate_run.sh runs/raw/musique --judge gemini --no-compare --no-export

# Export tables/figures only from already-evaluated runs:
python src/cli.py export-thesis --input runs/evaluated/musique --subfolder musique
```

---

## Dataset Generation (Reference)

All evaluation datasets (`musique_100_final.jsonl`, `enterprise_100_final.jsonl`, and the `*_10_sample.jsonl` subsets) are pre-generated, verified, and committed under `data/`.

If you need to regenerate the enterprise dataset from raw source materials, run the CLI command directly:

```bash
python src/cli.py build-enterprise-musique-dataset \
  --csv-file data/enterprise/qs_fi.csv \
  --corpus-dir ../rag-benchmark/data/enterprise/md \
  --output data/enterprise/enterprise_ans_v1.0_dev.jsonl
```
