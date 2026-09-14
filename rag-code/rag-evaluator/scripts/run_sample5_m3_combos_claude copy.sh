#!/usr/bin/env bash
# ==============================================================================
# Full Multi-Module Benchmark Suite (C0, C1, C2, C3, C5, C6, C8, C11)
# Evaluates all combinations of M1, M2, and M3 against vector_store_musique with Claude Sonnet 5 judge (Vertex AI)
# ==============================================================================
set -euo pipefail

# Ensure working directory is rag-code/rag-evaluator
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

PYTHON="${PYTHON:-venv/bin/python}"
URL="${RAG_APP_URL:-http://localhost:8080}"
LIMIT="${LIMIT:-5}"
TABLE_NAME="${TABLE_NAME:-vector_store_musique}"
JUDGE="${JUDGE:-claude}"
JUDGE_MODEL="${JUDGE_MODEL:-claude-sonnet-5}"
WARMUP="${WARMUP:-0}"

RUN_DIR="${RUN_DIR:-runs/sample5_m3_combos}"
TABLES_DIR="${TABLES_DIR:-../../thesis/tables}"
FIGURES_DIR="${FIGURES_DIR:-../../thesis/figures}"
SUBFOLDER="${SUBFOLDER:-sample5_m3_combos}"

mkdir -p "$RUN_DIR"

echo "========================================================================"
echo " Starting Full Factorial Benchmark Suite (C0 - C11: M1, M2, M3)"
echo " Service URL:    $URL"
echo " Run Directory:  $RUN_DIR"
echo " Judge:          $JUDGE ($JUDGE_MODEL)"
echo " Target Table:   $TABLE_NAME | Limit: $LIMIT | Warmup: $WARMUP"
echo " Export Folder:  $SUBFOLDER"
echo "========================================================================"

# 1. M0: Baseline (C0)
echo -e "\n>>> [1/8] Running C0: Baseline..."
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C0_baseline.jsonl"

# 2. M1: Small-to-Big (C1)
echo -e "\n>>> [2/8] Running C1: Small-to-Big (M1)..."
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --modules M1 \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C1_M1.jsonl"

# 3. M2: Semantic Re-ranking (C2)
echo -e "\n>>> [3/8] Running C2: Semantic Re-ranking (M2)..."
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --modules M2 \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C2_M2.jsonl"

# 4. M3: Prompt Caching (C3)
echo -e "\n>>> [4/8] Running C3: Prompt Caching (M3)..."
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --modules M3 \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C3_M3.jsonl"

# 5. M1+M2: Small-to-Big + Re-ranking (C5)
echo -e "\n>>> [5/8] Running C5: Small-to-Big + Re-ranking (M1+M2)..."
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --modules M1,M2 \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C5_M1_M2.jsonl"

# 6. M1+M3: Small-to-Big + Prompt Caching (C6)
echo -e "\n>>> [6/8] Running C6: Small-to-Big + Prompt Caching (M1+M3)..."
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --modules M1,M3 \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C6_M1_M3.jsonl"

# 7. M2+M3: Re-ranking + Prompt Caching (C8)
echo -e "\n>>> [7/8] Running C8: Re-ranking + Prompt Caching (M2+M3)..."
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --modules M2,M3 \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C8_M2_M3.jsonl"

# 8. M1+M2+M3: Full Multi-Module Stack (C11)
echo -e "\n>>> [8/8] Running C11: S2B + Re-ranking + Prompt Caching (M1+M2+M3)..."
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --modules M1,M2,M3 \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "${RUN_DIR}/run_C11_M1_M2_M3.jsonl"

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
  --treatment "${RUN_DIR}/run_C5_M1_M2.jsonl" \
  --config-name "C5: S2B + Re-ranking (M1+M2)"

"$PYTHON" src/cli.py compare \
  --baseline "${RUN_DIR}/run_C0_baseline.jsonl" \
  --treatment "${RUN_DIR}/run_C6_M1_M3.jsonl" \
  --config-name "C6: S2B + Caching (M1+M3)"

"$PYTHON" src/cli.py compare \
  --baseline "${RUN_DIR}/run_C0_baseline.jsonl" \
  --treatment "${RUN_DIR}/run_C8_M2_M3.jsonl" \
  --config-name "C8: Re-ranking + Caching (M2+M3)"

"$PYTHON" src/cli.py compare \
  --baseline "${RUN_DIR}/run_C0_baseline.jsonl" \
  --treatment "${RUN_DIR}/run_C11_M1_M2_M3.jsonl" \
  --config-name "C11: Full Stack (M1+M2+M3)"

echo "========================================================================"
echo " Consolidated LaTeX Tables & Publication Figures Export"
echo "========================================================================"

"$PYTHON" src/cli.py export-thesis \
  --input "$RUN_DIR" \
  --tables-dir "$TABLES_DIR" \
  --figures-dir "$FIGURES_DIR" \
  --subfolder "$SUBFOLDER"

echo -e "\n✓ Benchmark run and export completed successfully!"
