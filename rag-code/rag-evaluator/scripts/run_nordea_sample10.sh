#!/usr/bin/env bash
# ==============================================================================
# Enterprise Closed (Nordea) Benchmark Query Runner: Multi-Module Sample Suite (N=10)
#
# Evaluates key optimization configurations against vector_store_nordea_closed using
# data/nordea/nordea_10_sample.jsonl (stratified across 1-hop, 2-hop & out-of-domain unanswerable):
#   - C0:  Naive RAG Baseline
#   - C1:  Small-to-Big Retrieval (M1)
#   - C3:  Prompt Caching (M3)
#   - C4:  Model Routing (M4)
#   - C10: Prompt Caching + Model Routing (M3+M4)
#   - C13: Small-to-Big + Prompt Caching + Model Routing (M1+M3+M4)
#
# Adheres strictly to official benchmark pipeline standards:
# - Pure query collection by default (--judge none) for reproducible latency & costs
# - Spring Boot service health check before execution
# - Explicit pre-warming of GCP Vertex AI prompt cache before M3 configs
# - Invalidation/clearing of application & remote prompt caches between configs
#
# Usage:
#   ./scripts/run_nordea_sample10.sh [OPTIONS]
#
# Options:
#   -l, --limit <N>         Number of questions to evaluate (default: 10)
#   -j, --judge <PROVIDER>  Judge provider: none, gemini, claude, vllm (default: none)
#   -m, --judge-model <MDL> Judge model identifier
#   -u, --url <URL>         Target Spring Boot service URL (default: http://localhost:8080)
#   -o, --run-dir <DIR>     Output directory (default: runs/raw/nordea_sample10)
#   --skip-baseline         Skip baseline C0 run
#   --eval                  Evaluate runs offline after query collection
#   -h, --help              Show this help message
# ==============================================================================
set -euo pipefail

# Ensure working directory is rag-code/rag-evaluator
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$SCRIPT_DIR/../src/cli.py" ]; then
  PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
elif [ -f "$SCRIPT_DIR/../../src/cli.py" ]; then
  PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
else
  PROJECT_DIR="$(pwd)"
fi
cd "$PROJECT_DIR"

PYTHON="${PYTHON:-venv/bin/python}"
URL="${RAG_APP_URL:-http://localhost:8080}"
LIMIT="${LIMIT:-10}"
TABLE_NAME="${TABLE_NAME:-vector_store_nordea_closed}"
DEFAULT_QUESTIONS="data/nordea/nordea_10_sample.jsonl"
TIMEOUT="${TIMEOUT:-300}"
QUESTIONS_FILE="${QUESTIONS_FILE:-$DEFAULT_QUESTIONS}"
RUN_DIR="${RUN_DIR:-runs/raw/nordea_sample10}"
JUDGE="${JUDGE:-none}"
JUDGE_MODEL="${JUDGE_MODEL:-}"
RUN_BASELINE=true
DO_EVAL=false

# Argument parsing
while [[ $# -gt 0 ]]; do
  case "$1" in
    -l|--limit)
      LIMIT="$2"
      shift 2
      ;;
    -j|--judge)
      JUDGE="$2"
      shift 2
      ;;
    -m|--judge-model)
      JUDGE_MODEL="$2"
      shift 2
      ;;
    -u|--url)
      URL="$2"
      shift 2
      ;;
    -o|--run-dir)
      RUN_DIR="$2"
      shift 2
      ;;
    --skip-baseline|--no-baseline)
      RUN_BASELINE=false
      shift
      ;;
    --eval|--evaluate)
      DO_EVAL=true
      shift
      ;;
    -h|--help)
      sed -n '2,/^set -euo/p' "$0" | head -n -1 | sed 's/^# \?//'
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

echo "========================================================================"
echo " Starting Nordea Enterprise Benchmark Sample Suite (N=${LIMIT})"
echo " Target Service:    $URL"
echo " Vector Store:      $TABLE_NAME"
echo " Questions File:    $QUESTIONS_FILE"
echo " Output Directory:  $RUN_DIR"
echo " Query Timeout:     ${TIMEOUT}s"
echo " Judge Mode:        $JUDGE ${JUDGE_MODEL:+($JUDGE_MODEL)}"
echo " Baseline Included: $RUN_BASELINE"
echo " Configurations:"
if [ "$RUN_BASELINE" = true ]; then
  echo "   - C0:  Naive RAG Baseline"
fi
echo "   - C1:  Small-to-Big Retrieval (M1)"
echo "   - C3:  Prompt Caching (M3)"
echo "   - C4:  Model Routing (M4)"
echo "   - C10: Prompt Caching + Model Routing (M3+M4)"
echo "   - C13: Small-to-Big + Prompt Caching + Model Routing (M1+M3+M4)"
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
  echo "Please start the backend (e.g. './gradlew bootRun' or 'mvn spring-boot:run' in rag-benchmark) before running queries." >&2
  exit 1
fi
echo "    Spring Boot service is active."

JUDGE_ARGS=()
if [ "$JUDGE" != "none" ]; then
  JUDGE_ARGS+=(--judge "$JUDGE")
  if [ -n "$JUDGE_MODEL" ]; then
    JUDGE_ARGS+=(--judge-model "$JUDGE_MODEL")
  fi
else
  JUDGE_ARGS+=(--judge none)
fi

# ------------------------------------------------------------------------------
# 1. Baseline: C0 (Naive RAG)
# ------------------------------------------------------------------------------
if [ "$RUN_BASELINE" = true ]; then
  clear_cache
  echo -e "\n>>> Running C0: Naive RAG Baseline..."
  "$PYTHON" src/cli.py run-questions \
    --input "$QUESTIONS_FILE" \
    --url "$URL" \
    --limit "$LIMIT" \
    --timeout "$TIMEOUT" \
    --table-name "$TABLE_NAME" \
    "${JUDGE_ARGS[@]}" \
    --output "${RUN_DIR}/run_C0_baseline.jsonl"
  clear_cache
fi

# ------------------------------------------------------------------------------
# 2. Setup 1: C1: Small-to-Big Retrieval (M1)
# ------------------------------------------------------------------------------
echo -e "\n>>> Running C1: Small-to-Big Retrieval (M1)..."
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --modules M1 \
  --table-name "$TABLE_NAME" \
  "${JUDGE_ARGS[@]}" \
  --output "${RUN_DIR}/run_C1_M1.jsonl"
clear_cache

# ------------------------------------------------------------------------------
# 3. Setup 2: C3: Prompt Caching (M3)
# ------------------------------------------------------------------------------
echo -e "\n>>> Running C3: Prompt Caching (M3)..."
prewarm_cache
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --modules M3 \
  --table-name "$TABLE_NAME" \
  "${JUDGE_ARGS[@]}" \
  --output "${RUN_DIR}/run_C3_M3.jsonl"
clear_cache

# ------------------------------------------------------------------------------
# 4. Setup 3: C4: Model Routing / Cascading (M4)
# ------------------------------------------------------------------------------
echo -e "\n>>> Running C4: Model Cascading / Routing (M4)..."
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --modules M4 \
  --table-name "$TABLE_NAME" \
  "${JUDGE_ARGS[@]}" \
  --output "${RUN_DIR}/run_C4_M4.jsonl"
clear_cache

# ------------------------------------------------------------------------------
# 5. Setup 4: C10: Prompt Caching + Model Routing (M3+M4)
# ------------------------------------------------------------------------------
echo -e "\n>>> Running C10: Prompt Caching + Model Routing (M3+M4)..."
prewarm_cache
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --modules M3,M4 \
  --table-name "$TABLE_NAME" \
  "${JUDGE_ARGS[@]}" \
  --output "${RUN_DIR}/run_C10_M3_M4.jsonl"
clear_cache

# ------------------------------------------------------------------------------
# 6. Setup 5: C13: S2B + Prompt Caching + Model Routing (M1+M3+M4)
# ------------------------------------------------------------------------------
echo -e "\n>>> Running C13: S2B + Prompt Caching + Model Routing (M1+M3+M4)..."
prewarm_cache
"$PYTHON" src/cli.py run-questions \
  --input "$QUESTIONS_FILE" \
  --url "$URL" \
  --limit "$LIMIT" \
  --timeout "$TIMEOUT" \
  --modules M1,M3,M4 \
  --table-name "$TABLE_NAME" \
  "${JUDGE_ARGS[@]}" \
  --output "${RUN_DIR}/run_C13_M1_M3_M4.jsonl"
clear_cache

echo -e "\n========================================================================"
echo " Nordea sample benchmark runs completed successfully!"
echo " Results saved to: $RUN_DIR"
echo "========================================================================"

# Optional offline evaluation
if [ "$DO_EVAL" = true ]; then
  EVAL_JUDGE="${JUDGE}"
  if [ "$EVAL_JUDGE" = "none" ]; then
    EVAL_JUDGE="gemini"
  fi
  echo -e "\n>>> Executing offline evaluation with judge: $EVAL_JUDGE..."
  if [ -f "./scripts/utils/evaluate_run.sh" ]; then
    ./scripts/utils/evaluate_run.sh "$RUN_DIR" --judge "$EVAL_JUDGE" ${JUDGE_MODEL:+--judge-model "$JUDGE_MODEL"}
  else
    "$PYTHON" src/cli.py evaluate --input "$RUN_DIR" --judge "$EVAL_JUDGE" ${JUDGE_MODEL:+--judge-model "$JUDGE_MODEL"} --inplace
  fi
else
  echo ""
  echo " Next steps (offline quality evaluation / comparisons):"
  echo " 1. Evaluate with offline judge:"
  echo "    ./scripts/utils/evaluate_run.sh $RUN_DIR --judge gemini"
  if [ "$RUN_BASELINE" = true ]; then
    echo " 2. Quick Wilcoxon comparison vs baseline:"
    echo "    $PYTHON src/cli.py compare --baseline ${RUN_DIR}/run_C0_baseline.jsonl --treatment ${RUN_DIR}/run_C1_M1.jsonl"
    echo "    $PYTHON src/cli.py compare --baseline ${RUN_DIR}/run_C0_baseline.jsonl --treatment ${RUN_DIR}/run_C3_M3.jsonl"
    echo "    $PYTHON src/cli.py compare --baseline ${RUN_DIR}/run_C0_baseline.jsonl --treatment ${RUN_DIR}/run_C10_M3_M4.jsonl"
  fi
fi
