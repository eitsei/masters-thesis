#!/usr/bin/env bash
# ==============================================================================
# 16-Configuration RAG Benchmark Query Runner: Enterprise Closed Corpus
#
# Executes all 2^4 = 16 factorial pipeline configurations (C0 - C15)
# against local Spring Boot RAG service (http://localhost:8080) and
# vector_store_enterprise_closed using data/enterprise/enterprise_100_final.jsonl (N=100).
#
# Pure query collection mode:
# - Disables online LLM judge (--judge none) for maximum speed and zero cloud drift.
# - Records exact TTFT, total latency, token usage, and retrieved chunks to JSONL.
# - Clears cache between configurations and pre-warms cache before M3 configs.
#
# Quality evaluation is executed separately offline via CSC Roihu (eval_enterprise_closed.sbatch).
# ==============================================================================
set -euo pipefail

# Ensure working directory is rag-code/rag-evaluator
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_DIR"

PYTHON="${PYTHON:-venv/bin/python}"
URL="${RAG_APP_URL:-http://localhost:8080}"
LIMIT="${LIMIT:-100}"
TABLE_NAME="${TABLE_NAME:-vector_store_enterprise_closed}"
DEFAULT_QUESTIONS="data/enterprise/enterprise_100_final.jsonl"
TIMEOUT="${TIMEOUT:-300}"

QUESTIONS_FILE="${QUESTIONS_FILE:-$DEFAULT_QUESTIONS}"
RUN_DIR="${RUN_DIR:-runs/raw/enterprise_closed}"

echo "========================================================================"
echo " Starting Full 16-Configuration RAG Benchmark on Enterprise Closed (N=${LIMIT})"
echo " Target Service:    $URL"
echo " Vector Store:      $TABLE_NAME"
echo " Questions File:    $QUESTIONS_FILE"
echo " Output Directory:  $RUN_DIR"
echo " Query Timeout:     ${TIMEOUT}s"
echo " Judge Mode:        NONE (Pure local latency/cost query benchmark)"
echo "========================================================================"

mkdir -p "$RUN_DIR"

if [ ! -f "$QUESTIONS_FILE" ]; then
  echo "ERROR: Questions file not found at: $QUESTIONS_FILE" >&2
  exit 1
fi

# Helper: Pre-warm GCP Gemini explicit context cache before M3 configurations
prewarm_cache() {
  echo ">>> [Cache Pre-warm] Triggering context cache allocation on RAG service..."
  if curl -s -f -X POST "${URL}/api/benchmark/cache/prewarm" > /dev/null 2>&1; then
    echo "    Cache pre-warmed successfully (HTTP 200)."
  else
    echo "    Warning: Cache pre-warm endpoint returned non-200 status (service may be initializing)."
  fi
}

# Helper: Clear remote and local prompt cache on the RAG service between configs
clear_cache() {
  echo ">>> [Cache Invalidation] Clearing application & Vertex AI prompt caches..."
  if curl -s -f -X POST "${URL}/api/benchmark/cache/clear" > /dev/null 2>&1; then
    echo "    Cache invalidated successfully (HTTP 200)."
  else
    echo "    Warning: Cache clear endpoint returned non-200 status."
  fi
}

# Verify connectivity to Spring Boot RAG service
echo ">>> Checking Spring Boot service availability at ${URL}..."
if ! curl -s -f -m 5 "${URL}/actuator/health" > /dev/null 2>&1 && ! curl -s -f -m 5 "${URL}/api/benchmark/cache/clear" > /dev/null 2>&1; then
  echo "ERROR: Spring Boot RAG service is not reachable at ${URL}." >&2
  echo "Please start the backend (e.g. './gradlew bootRun' in rag-benchmark) before running queries." >&2
  exit 1
fi
echo "    Spring Boot service is active."

# ------------------------------------------------------------------------------
# Baseline Execution: C0
# ------------------------------------------------------------------------------
clear_cache
echo -e "\n>>> [1/16] Running C0: Naive RAG Baseline..."
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --table-name "$TABLE_NAME" \
  --judge none \
  --output "${RUN_DIR}/run_C0_baseline.jsonl"
clear_cache

# ------------------------------------------------------------------------------
# Stage 1: Single-Factor Isolated Optimizations (k=1)
# ------------------------------------------------------------------------------
echo -e "\n>>> [2/16] Running C1: Small-to-Big Retrieval (M1)..."
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --modules M1 \
  --table-name "$TABLE_NAME" \
  --judge none \
  --output "${RUN_DIR}/run_C1_M1.jsonl"
clear_cache

echo -e "\n>>> [3/16] Running C2: Two-Stage Semantic Re-ranking (M2)..."
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --modules M2 \
  --table-name "$TABLE_NAME" \
  --judge none \
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
  --judge none \
  --output "${RUN_DIR}/run_C3_M3.jsonl"
clear_cache

echo -e "\n>>> [5/16] Running C4: Model Cascading / Routing (M4)..."
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --modules M4 \
  --table-name "$TABLE_NAME" \
  --judge none \
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
  --judge none \
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
  --judge none \
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
  --judge none \
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
  --judge none \
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
  --judge none \
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
  --judge none \
  --output "${RUN_DIR}/run_C10_M3_M4.jsonl"
clear_cache

# ------------------------------------------------------------------------------
# Stage 3: Multi-Module Triplets (k=3)
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
  --judge none \
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
  --judge none \
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
  --judge none \
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
  --judge none \
  --output "${RUN_DIR}/run_C14_M2_M3_M4.jsonl"
clear_cache

# ------------------------------------------------------------------------------
# Stage 4: Full-Stack Integration (k=4)
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
  --judge none \
  --output "${RUN_DIR}/run_C15_M1_M2_M3_M4.jsonl"
clear_cache

echo -e "\n========================================================================"
echo " All 16 Enterprise Closed benchmark runs completed successfully!"
echo " Results saved to: $RUN_DIR"
echo ""
echo " Next step: Execute offline RAGAS evaluation on CSC Roihu:"
echo "   sbatch scripts/roihu/eval_enterprise_closed.sbatch"
echo "========================================================================"
