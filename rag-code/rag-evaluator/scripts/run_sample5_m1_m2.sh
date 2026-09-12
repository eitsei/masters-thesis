#!/usr/bin/env bash
# ==============================================================================
# Baseline & M1/M2 Benchmark Suite (C0, C1, C2, C5)
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

RUN_DIR="${RUN_DIR:-runs/sample5}"
TABLES_DIR="${TABLES_DIR:-../../thesis/tables}"
FIGURES_DIR="${FIGURES_DIR:-../../thesis/figures}"
SUBFOLDER="${SUBFOLDER:-sample5}"

mkdir -p "$RUN_DIR"

echo "========================================================================"
echo " Starting RAG Benchmark Suite (C0, C1, C2, C5)"
echo " Service URL:    $URL"
echo " Run Directory:  $RUN_DIR"
echo " Judge:          $JUDGE ($JUDGE_MODEL)"
echo " Target Table:   $TABLE_NAME | Limit: $LIMIT | Warmup: $WARMUP"
echo " Export Folder:  $SUBFOLDER"
echo "========================================================================"

# 1. M0: Baseline (C0)
echo -e "\n>>> [1/4] Running C0: Baseline..."
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C0_baseline.jsonl"

# 2. M1: Small-to-Big (C1)
echo -e "\n>>> [2/4] Running C1: Small-to-Big (M1)..."
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --modules M1 \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C1_M1.jsonl"

# 3. M2: Semantic Re-ranking (C2)
echo -e "\n>>> [3/4] Running C2: Semantic Re-ranking (M2)..."
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --modules M2 \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C2_M2.jsonl"

# 4. M1 & M2: Small-to-Big + Re-ranking (C5)
echo -e "\n>>> [4/4] Running C5: Small-to-Big + Re-ranking (M1+M2)..."
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --modules M1,M2 \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C5_M1_M2.jsonl"

echo "========================================================================"
echo " Statistical Hypothesis Testing (Wilcoxon Signed-Rank Tests vs Baseline)"
echo "========================================================================"

# Terminal Wilcoxon table: C1 vs C0
"$PYTHON" src/cli.py compare \
  --baseline "${RUN_DIR}/run_C0_baseline.jsonl" \
  --treatment "${RUN_DIR}/run_C1_M1.jsonl" \
  --config-name "C1: Small-to-Big (M1)"

# Terminal Wilcoxon table: C2 vs C0
"$PYTHON" src/cli.py compare \
  --baseline "${RUN_DIR}/run_C0_baseline.jsonl" \
  --treatment "${RUN_DIR}/run_C2_M2.jsonl" \
  --config-name "C2: Semantic Re-ranking (M2)"

# Terminal Wilcoxon table: C5 vs C0
"$PYTHON" src/cli.py compare \
  --baseline "${RUN_DIR}/run_C0_baseline.jsonl" \
  --treatment "${RUN_DIR}/run_C5_M1_M2.jsonl" \
  --config-name "C5: S2B + Re-ranking (M1+M2)"

echo "========================================================================"
echo " Consolidated LaTeX Tables & Publication Figures Export"
echo "========================================================================"

"$PYTHON" src/cli.py export-thesis \
  --input "$RUN_DIR" \
  --tables-dir "$TABLES_DIR" \
  --figures-dir "$FIGURES_DIR" \
  --subfolder "$SUBFOLDER"

echo -e "\n✓ Benchmark run and export completed successfully!"
