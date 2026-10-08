#!/usr/bin/env bash
# ==============================================================================
# Model Routing Benchmark Suite (C0 vs C4 / M4)
# Evaluates 5-sample benchmark against vector_store_musique with Gemini judge
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

RUN_DIR="${RUN_DIR:-runs/sample5_m4}"
TABLES_DIR="${TABLES_DIR:-../../thesis/tables}"
FIGURES_DIR="${FIGURES_DIR:-../../thesis/figures}"
SUBFOLDER="${SUBFOLDER:-sample5_m4}"

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
echo " Starting RAG Benchmark Suite: Model Routing (C0 Baseline vs C4: M4)"
echo " Service URL:    $URL"
echo " Run Directory:  $RUN_DIR"
echo " Stage:          $STAGE"
echo " Judge:          $JUDGE ($JUDGE_MODEL)"
echo " Target Table:   $TABLE_NAME | Limit: $LIMIT | Warmup: $WARMUP"
echo " Export Folder:  $SUBFOLDER"
echo "========================================================================"

# 0. Check Judge Connectivity
if [ "$STAGE" = "eval" ] || [ "$STAGE" = "all" ]; then
  echo -e "\n>>> Verifying Judge Connectivity..."
  "$PYTHON" src/cli.py test-judge --judge "$JUDGE" --judge-model "$JUDGE_MODEL"
fi

if [ "$STAGE" = "queries" ] || [ "$STAGE" = "all" ]; then
  # 1. M0: Baseline (C0)
  echo -e "\n>>> [1/2] Running C0: Baseline..."
  "$PYTHON" src/cli.py run \
    --url "$URL" \
    --sample --limit "$LIMIT" --stream \
    --table-name "$TABLE_NAME" \
    --warmup "$WARMUP" --judge "$RUN_JUDGE" --judge-model "$JUDGE_MODEL" \
    --output "${RUN_DIR}/run_C0_baseline.jsonl"

  # 2. M4: Model Routing (C4)
  echo -e "\n>>> [2/2] Running C4: Model Routing (M4)..."
  "$PYTHON" src/cli.py run \
    --url "$URL" \
    --sample --limit "$LIMIT" --stream \
    --modules M4 \
    --table-name "$TABLE_NAME" \
    --warmup "$WARMUP" --judge "$RUN_JUDGE" --judge-model "$JUDGE_MODEL" \
    --output "${RUN_DIR}/run_C4_M4.jsonl"

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

  # Terminal Wilcoxon table: C4 vs C0
  "$PYTHON" src/cli.py compare \
    --baseline "${RUN_DIR}/run_C0_baseline.jsonl" \
    --treatment "${RUN_DIR}/run_C4_M4.jsonl" \
    --config-name "C4: Model Routing (M4)"

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
