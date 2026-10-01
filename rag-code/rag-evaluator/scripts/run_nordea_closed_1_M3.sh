#!/usr/bin/env bash
# ==============================================================================
# Nordea Closed Corpus Benchmark: Baseline (C0) & Prompt Caching (M3)
# Evaluates Baseline and M3 against vector_store_nordea_closed.
# Console-only execution (no artifact or run directories created).
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

echo "========================================================================"
echo " Nordea Closed-Corpus: Baseline (C0) & Prompt Caching (M3)"
echo " Service URL:     $URL"
echo " Questions File:  $QUESTIONS_FILE"
echo " Judge:           $JUDGE ($JUDGE_MODEL)"
echo " Target Table:    $TABLE_NAME | Limit: $LIMIT | Timeout: ${TIMEOUT}s"
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
echo -e "\n>>> Verifying Judge Connectivity..."
"$PYTHON" src/cli.py test-judge --judge "$JUDGE" --judge-model "$JUDGE_MODEL"

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
  --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output /dev/null
clear_cache

# ------------------------------------------------------------------------------
# Stage 1: Prompt caching (M3 / C1)
# ------------------------------------------------------------------------------
echo -e "\n>>> [2/2] Running Prompt Caching (M3)..."
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --modules M3 \
  --table-name "$TABLE_NAME" \
  --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output /dev/null
clear_cache

echo -e "\n✓ Finished Baseline (C0) and Prompt Caching (M3) runs."
