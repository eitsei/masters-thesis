#!/usr/bin/env bash
# ==============================================================================
# Full Multi-Module Benchmark Suite on MuSiQue Data: 16 Configurations (C0 - C15)
# Evaluates all 2^4 = 16 combinations of M1, M2, M3, and M4 against
# vector_store_musique using musique_100_final.jsonl (N=100) and
# Llama 4 Scout judge via Vertex AI MaaS (OpenAI-compatible endpoint).
# ==============================================================================
set -euo pipefail

# Ensure working directory is rag-code/rag-evaluator
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

PYTHON="${PYTHON:-venv/bin/python}"
URL="${RAG_APP_URL:-http://localhost:8080}"
LIMIT="${LIMIT:-10}"
TABLE_NAME="${TABLE_NAME:-vector_store_musique}"
DEFAULT_QUESTIONS="data/musique/musique_100_final.jsonl"
TIMEOUT="${TIMEOUT:-300}"

QUESTIONS_FILE="${QUESTIONS_FILE:-$DEFAULT_QUESTIONS}"
JUDGE="${JUDGE:-llama}"
JUDGE_MODEL="${JUDGE_MODEL:-meta/llama-4-scout-17b-16e-instruct-maas}"

RUN_DIR="${RUN_DIR:-runs/musique_all16_final}"
TABLES_DIR="${TABLES_DIR:-../../thesis/tables}"
FIGURES_DIR="${FIGURES_DIR:-../../thesis/figures}"
SUBFOLDER="${SUBFOLDER:-musique}"

mkdir -p "$RUN_DIR"

echo "========================================================================"
echo " Starting MuSiQue Full Factorial Benchmark Suite: All 16 Configs (C0 - C15)"
echo " Service URL:     $URL"
echo " Run Directory:   $RUN_DIR"
echo " Questions File:  $QUESTIONS_FILE"
echo " Judge:           $JUDGE ($JUDGE_MODEL)"
echo " Target Table:    $TABLE_NAME | Limit: $LIMIT | Timeout: ${TIMEOUT}s"
echo " Export Folder:   $SUBFOLDER"
echo "========================================================================"

# Check questions file existence (fetch canonical sample from service if missing)
if [ ! -f "$QUESTIONS_FILE" ]; then
  if [ "$QUESTIONS_FILE" = "data/musique/musique_100_final.jsonl" ]; then
    echo "Notice: '$QUESTIONS_FILE' not found. Fetching canonical sample from ${URL}/api/benchmark/musique/sample..."
    mkdir -p "$(dirname "$QUESTIONS_FILE")"
    if curl -s -f "${URL}/api/benchmark/musique/sample" | "$PYTHON" -c '
import json, sys
data = json.load(sys.stdin)
with open("data/musique/musique_100_final.jsonl", "w") as f:
    for item in data:
        f.write(json.dumps(item) + "\n")
' 2>/dev/null; then
      echo "✓ Saved canonical sample to '$QUESTIONS_FILE'"
    fi
  fi
fi

if [ ! -f "$QUESTIONS_FILE" ]; then
  echo "Error: Questions file '$QUESTIONS_FILE' not found!" >&2
  exit 1
fi

# Helper: Clear remote and local prompt cache on the RAG service between configs
# to guarantee configuration independence, prevent state leaks, and avoid quota limits.
clear_cache() {
  echo -e "\n>>> Clearing prompt cache on service (${URL}/api/benchmark/cache/clear)..."
  curl -s -f -X POST "${URL}/api/benchmark/cache/clear" > /dev/null || echo "  Warning: Failed to clear prompt cache on ${URL}"
  sleep 2
}

prewarm_cache() {
  echo -e "\n>>> Pre-warming prompt cache on service (${URL}/api/benchmark/cache/prewarm)..."
  curl -s -f -X POST "${URL}/api/benchmark/cache/prewarm" > /dev/null || echo "  Warning: Failed to prewarm prompt cache on ${URL}"
  sleep 2
}

# 0. Check Judge Connectivity
echo -e "\n>>> [0/16] Verifying Judge Connectivity..."
"$PYTHON" src/cli.py test-judge --judge "$JUDGE" --judge-model "$JUDGE_MODEL"

# Pre-Flight Prompt Cache Verification (Isolation & Explicit Hit)
echo -e "\n>>> [0.5/16] Verifying Prompt Cache Pre-Flight Integrity..."
"$PYTHON" src/cli.py test-cache --url "$URL"

# Warm-up: 2 unmeasured queries to eliminate JVM JIT, connection pool, and TLS
# cold-start distortions (Thesis §4.1)
WARMUP="${WARMUP:-2}"
echo -e "\n>>> Executing $WARMUP unmeasured warm-up queries..."
for i in $(seq 1 "$WARMUP"); do
  curl -s -o /dev/null -w "  Warm-up $i: HTTP %{http_code} (%{time_total}s)\n" \
    "${URL}/api/v1/query" \
    -H "Content-Type: application/json" \
    -d "{\"query\": \"warm-up query $i\", \"tableName\": \"$TABLE_NAME\"}"
done
clear_cache

# ------------------------------------------------------------------------------
# Stage 0: Naive Baseline (k=0)
# ------------------------------------------------------------------------------
echo -e "\n>>> [1/16] Running C0: Baseline..."
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --table-name "$TABLE_NAME" \
  --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C0_baseline.jsonl"
clear_cache

# ------------------------------------------------------------------------------
# Stage 1: Single-Factor Isolated Optimizations (k=1)
# ------------------------------------------------------------------------------
echo -e "\n>>> [2/16] Running C1: Small-to-Big (M1)..."
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --modules M1 \
  --table-name "$TABLE_NAME" \
  --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C1_M1.jsonl"
clear_cache

echo -e "\n>>> [3/16] Running C2: Semantic Re-ranking (M2)..."
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --modules M2 \
  --table-name "$TABLE_NAME" \
  --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C2_M2.jsonl"
clear_cache

echo -e "\n>>> [4/16] Running C3: Prompt Caching (M3)..."
prewarm_cache
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --modules M3 \
  --table-name "$TABLE_NAME" \
  --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C3_M3.jsonl"
clear_cache

echo -e "\n>>> [5/16] Running C4: Model Routing (M4)..."
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --modules M4 \
  --table-name "$TABLE_NAME" \
  --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C4_M4.jsonl"
clear_cache

# ------------------------------------------------------------------------------
# Stage 2: Pairwise Combinations (k=2)
# ------------------------------------------------------------------------------
echo -e "\n>>> [6/16] Running C5: Small-to-Big + Re-ranking (M1+M2)..."
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --modules M1,M2 \
  --table-name "$TABLE_NAME" \
  --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C5_M1_M2.jsonl"
clear_cache

echo -e "\n>>> [7/16] Running C6: Small-to-Big + Prompt Caching (M1+M3)..."
prewarm_cache
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --modules M1,M3 \
  --table-name "$TABLE_NAME" \
  --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C6_M1_M3.jsonl"
clear_cache

echo -e "\n>>> [8/16] Running C7: Small-to-Big + Model Routing (M1+M4)..."
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --modules M1,M4 \
  --table-name "$TABLE_NAME" \
  --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C7_M1_M4.jsonl"
clear_cache

echo -e "\n>>> [9/16] Running C8: Re-ranking + Prompt Caching (M2+M3)..."
prewarm_cache
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --modules M2,M3 \
  --table-name "$TABLE_NAME" \
  --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C8_M2_M3.jsonl"
clear_cache

echo -e "\n>>> [10/16] Running C9: Re-ranking + Model Routing (M2+M4)..."
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --modules M2,M4 \
  --table-name "$TABLE_NAME" \
  --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C9_M2_M4.jsonl"
clear_cache

echo -e "\n>>> [11/16] Running C10: Prompt Caching + Model Routing (M3+M4)..."
prewarm_cache
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --modules M3,M4 \
  --table-name "$TABLE_NAME" \
  --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C10_M3_M4.jsonl"
clear_cache

# ------------------------------------------------------------------------------
# Stage 2: Multi-Module Triplets (k=3)
# ------------------------------------------------------------------------------
echo -e "\n>>> [12/16] Running C11: S2B + Re-ranking + Prompt Caching (M1+M2+M3)..."
prewarm_cache
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --modules M1,M2,M3 \
  --table-name "$TABLE_NAME" \
  --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C11_M1_M2_M3.jsonl"
clear_cache

echo -e "\n>>> [13/16] Running C12: S2B + Re-ranking + Model Routing (M1+M2+M4)..."
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --modules M1,M2,M4 \
  --table-name "$TABLE_NAME" \
  --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C12_M1_M2_M4.jsonl"
clear_cache

echo -e "\n>>> [14/16] Running C13: S2B + Prompt Caching + Model Routing (M1+M3+M4)..."
prewarm_cache
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --modules M1,M3,M4 \
  --table-name "$TABLE_NAME" \
  --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C13_M1_M3_M4.jsonl"
clear_cache

echo -e "\n>>> [15/16] Running C14: Re-ranking + Prompt Caching + Model Routing (M2+M3+M4)..."
prewarm_cache
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --modules M2,M3,M4 \
  --table-name "$TABLE_NAME" \
  --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C14_M2_M3_M4.jsonl"
clear_cache

# ------------------------------------------------------------------------------
# Stage 2: Full-Stack Integration (k=4)
# ------------------------------------------------------------------------------
echo -e "\n>>> [16/16] Running C15: Full Stack (M1+M2+M3+M4)..."
prewarm_cache
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --modules M1,M2,M3,M4 \
  --table-name "$TABLE_NAME" \
  --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C15_M1_M2_M3_M4.jsonl"
clear_cache

# ------------------------------------------------------------------------------
# Statistical Hypothesis Testing (Wilcoxon Signed-Rank Tests vs Baseline)
# ------------------------------------------------------------------------------
echo "========================================================================"
echo " Statistical Hypothesis Testing (Wilcoxon Signed-Rank Tests vs Baseline)"
echo "========================================================================"

"$PYTHON" src/cli.py compare \
  --baseline "${RUN_DIR}/run_C0_baseline.jsonl" \
  --treatment "${RUN_DIR}/run_C1_M1.jsonl" \
  --config-name "C1: Small-to-Big (M1)"

"$PYTHON" src/cli.py compare \
  --baseline "${RUN_DIR}/run_C0_baseline.jsonl" \
  --treatment "${RUN_DIR}/run_C2_M2.jsonl" \
  --config-name "C2: Semantic Re-ranking (M2)"

"$PYTHON" src/cli.py compare \
  --baseline "${RUN_DIR}/run_C0_baseline.jsonl" \
  --treatment "${RUN_DIR}/run_C3_M3.jsonl" \
  --config-name "C3: Prompt Caching (M3)"

"$PYTHON" src/cli.py compare \
  --baseline "${RUN_DIR}/run_C0_baseline.jsonl" \
  --treatment "${RUN_DIR}/run_C4_M4.jsonl" \
  --config-name "C4: Model Routing (M4)"

"$PYTHON" src/cli.py compare \
  --baseline "${RUN_DIR}/run_C0_baseline.jsonl" \
  --treatment "${RUN_DIR}/run_C5_M1_M2.jsonl" \
  --config-name "C5: S2B + Re-ranking (M1+M2)"

"$PYTHON" src/cli.py compare \
  --baseline "${RUN_DIR}/run_C0_baseline.jsonl" \
  --treatment "${RUN_DIR}/run_C6_M1_M3.jsonl" \
  --config-name "C6: S2B + Caching (M1+M3)"

"$PYTHON" src/cli.py compare \
  --baseline "${RUN_DIR}/run_C0_baseline.jsonl" \
  --treatment "${RUN_DIR}/run_C7_M1_M4.jsonl" \
  --config-name "C7: S2B + Routing (M1+M4)"

"$PYTHON" src/cli.py compare \
  --baseline "${RUN_DIR}/run_C0_baseline.jsonl" \
  --treatment "${RUN_DIR}/run_C8_M2_M3.jsonl" \
  --config-name "C8: Re-ranking + Caching (M2+M3)"

"$PYTHON" src/cli.py compare \
  --baseline "${RUN_DIR}/run_C0_baseline.jsonl" \
  --treatment "${RUN_DIR}/run_C9_M2_M4.jsonl" \
  --config-name "C9: Re-ranking + Routing (M2+M4)"

"$PYTHON" src/cli.py compare \
  --baseline "${RUN_DIR}/run_C0_baseline.jsonl" \
  --treatment "${RUN_DIR}/run_C10_M3_M4.jsonl" \
  --config-name "C10: Caching + Routing (M3+M4)"

"$PYTHON" src/cli.py compare \
  --baseline "${RUN_DIR}/run_C0_baseline.jsonl" \
  --treatment "${RUN_DIR}/run_C11_M1_M2_M3.jsonl" \
  --config-name "C11: S2B + Re-rank + Cache (M1+M2+M3)"

"$PYTHON" src/cli.py compare \
  --baseline "${RUN_DIR}/run_C0_baseline.jsonl" \
  --treatment "${RUN_DIR}/run_C12_M1_M2_M4.jsonl" \
  --config-name "C12: S2B + Re-rank + Route (M1+M2+M4)"

"$PYTHON" src/cli.py compare \
  --baseline "${RUN_DIR}/run_C0_baseline.jsonl" \
  --treatment "${RUN_DIR}/run_C13_M1_M3_M4.jsonl" \
  --config-name "C13: S2B + Cache + Route (M1+M3+M4)"

"$PYTHON" src/cli.py compare \
  --baseline "${RUN_DIR}/run_C0_baseline.jsonl" \
  --treatment "${RUN_DIR}/run_C14_M2_M3_M4.jsonl" \
  --config-name "C14: Re-rank + Cache + Route (M2+M3+M4)"

"$PYTHON" src/cli.py compare \
  --baseline "${RUN_DIR}/run_C0_baseline.jsonl" \
  --treatment "${RUN_DIR}/run_C15_M1_M2_M3_M4.jsonl" \
  --config-name "C15: Full Stack (M1+M2+M3+M4)"

# ------------------------------------------------------------------------------
# Consolidated LaTeX Tables & Publication Figures Export
# ------------------------------------------------------------------------------
echo "========================================================================"
echo " Consolidated LaTeX Tables & Publication Figures Export"
echo "========================================================================"

"$PYTHON" src/cli.py export-thesis \
  --input "$RUN_DIR" \
  --tables-dir "$TABLES_DIR" \
  --figures-dir "$FIGURES_DIR" \
  --subfolder "$SUBFOLDER"

echo -e "\n✓ MuSiQue full 16-configuration benchmark run and export completed successfully!"
