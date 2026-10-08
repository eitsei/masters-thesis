#!/usr/bin/env bash
# ==============================================================================
# Full Factorial Benchmark Suite: 16 Configurations (C0 through C15)
# Evaluates all 2^4 = 16 combinations of M1, M2, M3, and M4 against
# vector_store_musique on a 5-sample slice with Gemini judge.
# ==============================================================================
set -euo pipefail

# Ensure working directory is rag-code/rag-evaluator
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

PYTHON="${PYTHON:-venv/bin/python}"
URL="${RAG_APP_URL:-http://localhost:8080}"
LIMIT="${LIMIT:-5}"
TABLE_NAME="${TABLE_NAME:-vector_store_musique}"
JUDGE="${JUDGE:-gemini}"
JUDGE_MODEL="${JUDGE_MODEL:-gemini-3.5-flash-lite}"
WARMUP="${WARMUP:-0}"

RUN_DIR="${RUN_DIR:-runs/sample5_all16}"
TABLES_DIR="${TABLES_DIR:-../../thesis/tables}"
FIGURES_DIR="${FIGURES_DIR:-../../thesis/figures}"
SUBFOLDER="${SUBFOLDER:-sample5_all16}"

STAGE="${STAGE:-all}"
for arg in "$@"; do
  case "$arg" in
    --queries-only|--query-only|--queries) STAGE="queries" ;;
    --eval-only|--evaluate-only|--eval)    STAGE="eval" ;;
    --report-only|--export-only|--report)  STAGE="report" ;;
    --all)                                 STAGE="all" ;;
  esac
done

RUN_JUDGE="$JUDGE"
if [ "$STAGE" = "queries" ]; then
  RUN_JUDGE="none"
fi

mkdir -p "$RUN_DIR"

echo "========================================================================"
echo " Starting Full Factorial Benchmark Suite: All 16 Configurations (C0 - C15)"
echo " Service URL:    $URL"
echo " Run Directory:  $RUN_DIR"
echo " Stage:          $STAGE"
echo " Judge:          $JUDGE ($JUDGE_MODEL)"
echo " Target Table:   $TABLE_NAME | Limit: $LIMIT | Warmup: $WARMUP"
echo " Export Folder:  $SUBFOLDER"
echo "========================================================================"

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
if [ "$STAGE" = "eval" ] || [ "$STAGE" = "all" ]; then
  echo -e "\n>>> [0/16] Verifying Judge Connectivity..."
  "$PYTHON" src/cli.py test-judge --judge "$JUDGE" --judge-model "$JUDGE_MODEL"
fi

if [ "$STAGE" = "queries" ] || [ "$STAGE" = "all" ]; then
  # Pre-Flight Prompt Cache Verification (Isolation & Explicit Hit)
  echo -e "\n>>> [0.5/16] Verifying Prompt Cache Pre-Flight Integrity..."
  "$PYTHON" src/cli.py test-cache --url "$URL"

# Warm-up queries to eliminate JVM JIT, connection pool, and TLS cold-start distortions (Thesis §4.1)
if [ "$WARMUP" -gt 0 ]; then
  echo -e "\n>>> Executing $WARMUP unmeasured warm-up queries..."
  for i in $(seq 1 "$WARMUP"); do
    curl -s -o /dev/null -w "  Warm-up $i: HTTP %{http_code} (%{time_total}s)\n" \
      "${URL}/api/v1/query" \
      -H "Content-Type: application/json" \
      -d "{\"query\": \"warm-up query $i\", \"tableName\": \"$TABLE_NAME\"}"
  done
fi
clear_cache

# ------------------------------------------------------------------------------
# Stage 0: Naive Baseline (k=0)
# ------------------------------------------------------------------------------
echo -e "\n>>> [1/16] Running C0: Baseline..."
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$RUN_JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C0_baseline.jsonl"
clear_cache

# ------------------------------------------------------------------------------
# Stage 1: Single-Factor Isolated Optimizations (k=1)
# ------------------------------------------------------------------------------
echo -e "\n>>> [2/16] Running C1: Small-to-Big (M1)..."
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --modules M1 \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$RUN_JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C1_M1.jsonl"
clear_cache

echo -e "\n>>> [3/16] Running C2: Semantic Re-ranking (M2)..."
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --modules M2 \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$RUN_JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C2_M2.jsonl"
clear_cache

echo -e "\n>>> [4/16] Running C3: Prompt Caching (M3)..."
prewarm_cache
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --modules M3 \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$RUN_JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C3_M3.jsonl"
clear_cache

echo -e "\n>>> [5/16] Running C4: Model Routing (M4)..."
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --modules M4 \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$RUN_JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C4_M4.jsonl"
clear_cache

# ------------------------------------------------------------------------------
# Stage 2: Pairwise Combinations (k=2)
# ------------------------------------------------------------------------------
echo -e "\n>>> [6/16] Running C5: Small-to-Big + Re-ranking (M1+M2)..."
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --modules M1,M2 \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$RUN_JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C5_M1_M2.jsonl"
clear_cache

echo -e "\n>>> [7/16] Running C6: Small-to-Big + Prompt Caching (M1+M3)..."
prewarm_cache
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --modules M1,M3 \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$RUN_JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C6_M1_M3.jsonl"
clear_cache

echo -e "\n>>> [8/16] Running C7: Small-to-Big + Model Routing (M1+M4)..."
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --modules M1,M4 \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$RUN_JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C7_M1_M4.jsonl"
clear_cache

echo -e "\n>>> [9/16] Running C8: Re-ranking + Prompt Caching (M2+M3)..."
prewarm_cache
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --modules M2,M3 \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$RUN_JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C8_M2_M3.jsonl"
clear_cache

echo -e "\n>>> [10/16] Running C9: Re-ranking + Model Routing (M2+M4)..."
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --modules M2,M4 \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$RUN_JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C9_M2_M4.jsonl"
clear_cache

echo -e "\n>>> [11/16] Running C10: Prompt Caching + Model Routing (M3+M4)..."
prewarm_cache
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --modules M3,M4 \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$RUN_JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C10_M3_M4.jsonl"
clear_cache

# ------------------------------------------------------------------------------
# Stage 2: Multi-Module Triplets (k=3)
# ------------------------------------------------------------------------------
echo -e "\n>>> [12/16] Running C11: S2B + Re-ranking + Prompt Caching (M1+M2+M3)..."
prewarm_cache
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --modules M1,M2,M3 \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$RUN_JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C11_M1_M2_M3.jsonl"
clear_cache

echo -e "\n>>> [13/16] Running C12: S2B + Re-ranking + Model Routing (M1+M2+M4)..."
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --modules M1,M2,M4 \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$RUN_JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C12_M1_M2_M4.jsonl"
clear_cache

echo -e "\n>>> [14/16] Running C13: S2B + Prompt Caching + Model Routing (M1+M3+M4)..."
prewarm_cache
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --modules M1,M3,M4 \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$RUN_JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C13_M1_M3_M4.jsonl"
clear_cache

echo -e "\n>>> [15/16] Running C14: Re-ranking + Prompt Caching + Model Routing (M2+M3+M4)..."
prewarm_cache
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --modules M2,M3,M4 \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$RUN_JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C14_M2_M3_M4.jsonl"
clear_cache

# ------------------------------------------------------------------------------
# Stage 2: Full-Stack Integration (k=4)
# ------------------------------------------------------------------------------
echo -e "\n>>> [16/16] Running C15: Full Stack (M1+M2+M3+M4)..."
prewarm_cache
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --modules M1,M2,M3,M4 \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$RUN_JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C15_M1_M2_M3_M4.jsonl"
  clear_cache

  if [ "$STAGE" = "queries" ]; then
    echo -e "\n========================================================================"
    echo " Queries Complete (Stage: queries)"
    echo " All 16 configurations successfully queried and saved to: $RUN_DIR"
    echo " To evaluate quality with RAGAS, run:"
    echo "   $0 --eval-only"
    echo " or:"
    echo "   ./scripts/evaluate_run.sh $RUN_DIR --judge $JUDGE --judge-model $JUDGE_MODEL --subfolder $SUBFOLDER"
    echo "========================================================================"
  fi
fi

# ------------------------------------------------------------------------------
# Stage: Offline Evaluation (RAGAS Metrics)
# ------------------------------------------------------------------------------
if [ "$STAGE" = "eval" ]; then
  echo "========================================================================"
  echo " Evaluating RAG Quality Metrics (RAGAS) in $RUN_DIR with $JUDGE"
  echo "========================================================================"
  "$PYTHON" src/cli.py evaluate \
    --input "$RUN_DIR" \
    --judge "$JUDGE" \
    --judge-model "$JUDGE_MODEL" \
    --inplace
fi

if [ "$STAGE" = "eval" ] || [ "$STAGE" = "report" ] || [ "$STAGE" = "all" ]; then
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

echo -e "\n✓ Full 16-configuration benchmark run and export completed successfully!"
fi
