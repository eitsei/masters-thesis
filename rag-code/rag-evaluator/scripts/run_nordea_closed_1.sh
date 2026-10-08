#!/usr/bin/env bash
# ==============================================================================
# Nordea Closed Corpus Benchmark: Baseline (C0) & Small-to-Big (M1)
# Evaluates Baseline and M1 (C1) against vector_store_nordea_closed
# using nordea_100_final.jsonl and Llama 4 Scout judge via Vertex AI MaaS.
# ==============================================================================
set -euo pipefail

# Ensure working directory is rag-code/rag-evaluator
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

PYTHON="${PYTHON:-venv/bin/python}"
URL="${RAG_APP_URL:-http://localhost:8080}"
LIMIT="${LIMIT:-10}"
TABLE_NAME="${TABLE_NAME:-vector_store_nordea_closed}"
DEFAULT_QUESTIONS="data/nordea/nordea_100_final.jsonl"
TIMEOUT="${TIMEOUT:-300}"

QUESTIONS_FILE="${QUESTIONS_FILE:-$DEFAULT_QUESTIONS}"
JUDGE="${JUDGE:-llama}"
JUDGE_MODEL="${JUDGE_MODEL:-meta/llama-4-scout-17b-16e-instruct-maas}"
WARMUP="${WARMUP:-2}"

RUN_DIR="${RUN_DIR:-runs/nordea_closed_1}"
TABLES_DIR="${TABLES_DIR:-../../thesis/tables}"
FIGURES_DIR="${FIGURES_DIR:-../../thesis/figures}"
SUBFOLDER="${SUBFOLDER:-nordea_closed_1}"

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
echo " Nordea Closed-Corpus: Baseline (C0) & Small-to-Big (M1)"
echo " Service URL:     $URL"
echo " Run Directory:   $RUN_DIR"
echo " Stage:           $STAGE"
echo " Questions File:  $QUESTIONS_FILE"
echo " Judge:           $JUDGE ($JUDGE_MODEL)"
echo " Target Table:    $TABLE_NAME | Limit: $LIMIT | Timeout: ${TIMEOUT}s"
echo " Export Folder:   $SUBFOLDER"
echo "========================================================================"

# Check questions file existence
if [ ! -f "$QUESTIONS_FILE" ]; then
  echo "Error: Questions file '$QUESTIONS_FILE' not found!" >&2
  exit 1
fi

# Helper: Clear remote and local prompt cache on the RAG service between configs
clear_cache() {
  echo -e "\n>>> Clearing prompt cache on service (${URL}/api/benchmark/cache/clear)..."
  curl -s -f -X POST "${URL}/api/benchmark/cache/clear" > /dev/null || echo "  Warning: Failed to clear prompt cache on ${URL}"
  sleep 2
}

# 0. Check Judge Connectivity
if [ "$STAGE" = "eval" ] || [ "$STAGE" = "all" ]; then
  echo -e "\n>>> Verifying Judge Connectivity..."
  "$PYTHON" src/cli.py test-judge --judge "$JUDGE" --judge-model "$JUDGE_MODEL"
fi

if [ "$STAGE" = "queries" ] || [ "$STAGE" = "all" ]; then
  # Warm-up queries to eliminate JVM JIT, connection pool, and TLS jitter
  if [ "$WARMUP" -gt 0 ]; then
    echo -e "\n>>> Executing $WARMUP unmeasured warm-up queries..."
    for i in $(seq 1 "$WARMUP"); do
      curl -s -o /dev/null -w "  Warm-up $i: HTTP %{http_code} (%{time_total}s)\n" \
        "${URL}/api/query" \
        -H "Content-Type: application/json" \
        -d "{\"query\": \"lämmittelykysely $i\", \"tableName\": \"$TABLE_NAME\"}"
    done
    clear_cache
  fi

  # ------------------------------------------------------------------------------
  # Stage 0: Naive Baseline (C0)
  # ------------------------------------------------------------------------------
  echo -e "\n>>> [1/2] Running Baseline (C0)..."
  "$PYTHON" src/cli.py run-questions \
    --input "$QUESTIONS_FILE" \
    --url "$URL" \
    --limit "$LIMIT" \
    --timeout "$TIMEOUT" \
    --table-name "$TABLE_NAME" \
    --judge "$RUN_JUDGE" --judge-model "$JUDGE_MODEL" \
    --output "${RUN_DIR}/run_C0_baseline.jsonl"
  clear_cache

  # ------------------------------------------------------------------------------
  # Stage 1: Small-to-Big (M1 / C1)
  # ------------------------------------------------------------------------------
  echo -e "\n>>> [2/2] Running Small-to-Big (M1)..."
  "$PYTHON" src/cli.py run-questions \
    --input "$QUESTIONS_FILE" \
    --url "$URL" \
    --limit "$LIMIT" \
    --timeout "$TIMEOUT" \
    --modules M1 \
    --table-name "$TABLE_NAME" \
    --judge "$RUN_JUDGE" --judge-model "$JUDGE_MODEL" \
    --output "${RUN_DIR}/run_C1_M1.jsonl"
  clear_cache

  if [ "$STAGE" = "queries" ]; then
    echo -e "\n========================================================================"
    echo " Queries Complete (Stage: queries)"
    echo " All configurations successfully queried and saved to: $RUN_DIR"
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
  echo "========================================================================"
  echo " Statistical Hypothesis Testing (Wilcoxon Signed-Rank Tests vs Baseline)"
  echo "========================================================================"

  # Terminal Wilcoxon table: C1 vs C0
  "$PYTHON" src/cli.py compare \
    --baseline "${RUN_DIR}/run_C0_baseline.jsonl" \
    --treatment "${RUN_DIR}/run_C1_M1.jsonl" \
    --config-name "C1: Small-to-Big (M1)"

  echo "========================================================================"
  echo " Consolidated LaTeX Tables & Publication Figures Export"
  echo "========================================================================"

  "$PYTHON" src/cli.py export-thesis \
    --input "$RUN_DIR" \
    --tables-dir "$TABLES_DIR" \
    --figures-dir "$FIGURES_DIR" \
    --subfolder "$SUBFOLDER"

  echo -e "\n✓ Benchmark run and export completed successfully!"
fi
