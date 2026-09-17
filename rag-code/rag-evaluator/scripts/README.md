# Benchmark Runner Scripts (`rag-code/rag-evaluator/scripts`)

This directory contains executable shell scripts for launching automated benchmark evaluation runs, statistical Wilcoxon signed-rank tests, and publication-ready LaTeX table/figure exports.

## Available Scripts

| Script | Configurations Included | Output Run Directory | Thesis Export Subfolder |
| :--- | :--- | :--- | :--- |
| [`run_sample5_all16.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/run_sample5_all16.sh) | **$C_0$ through $C_{15}$** (Full 16-condition factorial matrix of M1, M2, M3, M4 on MuSiQue, Gemini judge) | `runs/sample5_all16` | `thesis/tables/sample5_all16`<br>`thesis/figures/sample5_all16` |
| [`run_nordea_all16.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/run_nordea_all16.sh) | **$C_0$ through $C_{15}$** (Full 16-condition factorial matrix of M1, M2, M3, M4 on Nordea data, Gemini judge) | `runs/nordea_all16` | `thesis/tables/nordea_all16`<br>`thesis/figures/nordea_all16` |
| [`run_nordea_m3_combos.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/run_nordea_m3_combos.sh) | **$C_0, C_1, C_2, C_3, C_5, C_6, C_8, C_{11}$** (All 8 combinations of M1, M2, M3 on Nordea data) | `runs/nordea_m3_combos` | `thesis/tables/nordea_m3_combos`<br>`thesis/figures/nordea_m3_combos` |
| [`run_sample5_m4.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/run_sample5_m4.sh) | **$C_0, C_4$** (Baseline vs Model Routing M4, Gemini judge) | `runs/sample5_m4` | `thesis/tables/sample5_m4`<br>`thesis/figures/sample5_m4` |
| [`run_sample5_m3.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/run_sample5_m3.sh) | **$C_0, C_1, C_2, C_3, C_5$** (Baseline, S2B, Re-rank, Cache, S2B+Re-rank) | `runs/sample5_m3` | `thesis/tables/sample5_m3`<br>`thesis/figures/sample5_m3` |
| [`run_sample5_m3_combos.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/run_sample5_m3_combos.sh) | **$C_0, C_1, C_2, C_3, C_5, C_6, C_8, C_{11}$** (All 8 factorial combinations of M1, M2, M3) | `runs/sample5_m3_combos` | `thesis/tables/sample5_m3_combos`<br>`thesis/figures/sample5_m3_combos` |
| [`run_sample5_m1_m2.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/run_sample5_m1_m2.sh) | **$C_0, C_1, C_2, C_5$** (Baseline, S2B, Re-rank, S2B+Re-rank) | `runs/sample5` | `thesis/tables/sample5`<br>`thesis/figures/sample5` |
| [`run_baseline_claude.sh`](file:///home/eitsei/dippa/rag-code/rag-evaluator/scripts/run_baseline_claude.sh) | **$C_0$** (Baseline connectivity test with Claude Sonnet 5 judge) | `runs/baseline_claude` | N/A (Smoke/connection test) |

---

## Execution Instructions

From either the repository root or inside `rag-code/rag-evaluator`:

```bash
# Run the M3 benchmark suite (5 configurations)
./rag-code/rag-evaluator/scripts/run_sample5_m3.sh

# Or from inside rag-code/rag-evaluator:
./scripts/run_sample5_m3.sh
```

---

## Environment Variable Overrides

All scripts support configuration overrides without modifying the script file:

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `RAG_APP_URL` | `http://localhost:8080` | URL of Spring Boot benchmark service |
| `LIMIT` | `5` | Number of benchmark questions to evaluate |
| `TABLE_NAME` | `vector_store_musique` | Target vector database table |
| `JUDGE` | `claude` | Evaluation judge provider (`claude`, `vertex`, `gemini`, `ollama`, `none`) |
| `JUDGE_MODEL` | `claude-sonnet-5` | Evaluation judge LLM identifier (Claude Sonnet 5 via Vertex AI) |
| `WARMUP` | `0` | Number of unmeasured warm-up queries |
| `RUN_DIR` | `runs/sample5_m3` | Directory where raw JSONL files are stored |
| `SUBFOLDER` | `sample5_m3` | Subfolder in `thesis/tables` & `thesis/figures` |

### Examples:

```bash
# Evaluate against deployed Cloud Run service with 10 questions:
RAG_APP_URL="https://rag-service-xyz.run.app" LIMIT=10 ./scripts/run_sample5_m3.sh

# Fast smoke run without LLM judge:
JUDGE=none LIMIT=3 ./scripts/run_sample5_m3.sh
```
