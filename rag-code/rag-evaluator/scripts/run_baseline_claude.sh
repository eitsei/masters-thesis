#!/usr/bin/env bash
# ==============================================================================
# Baseline Test Run with Claude Sonnet 5 Judge (Vertex AI)
# ==============================================================================
# Purpose:
#   1. Verifies connectivity to Anthropic Claude Sonnet 5 and Google Vertex AI
#      Model Garden (global endpoint) + text-embedding-004 (regional endpoint).
#   2. Executes a baseline (C0) benchmark query run against the Spring Boot RAG
#      service (default: 1 question for fast connectivity validation).
# ==============================================================================
set -euo pipefail

# Ensure working directory is rag-code/rag-evaluator
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

PYTHON="${PYTHON:-venv/bin/python}"
URL="${RAG_APP_URL:-http://localhost:8080}"
LIMIT="${LIMIT:-1}"
TABLE_NAME="${TABLE_NAME:-vector_store_musique}"
JUDGE="${JUDGE:-claude}"
JUDGE_MODEL="${JUDGE_MODEL:-claude-sonnet-5}"
WARMUP="${WARMUP:-0}"
CHECK_ONLY="${CHECK_ONLY:-0}"

# Google Cloud & Vertex AI configuration
GCP_PROJECT_DETECTED="$(gcloud config get-value project 2>/dev/null || echo "dippatyo")"
export GOOGLE_CLOUD_PROJECT_ID="${GOOGLE_CLOUD_PROJECT_ID:-${GCP_PROJECT_DETECTED:-dippatyo}}"
export GOOGLE_CLOUD_LOCATION="${GOOGLE_CLOUD_LOCATION:-global}"
export ANTHROPIC_VERTEX_LOCATION="${ANTHROPIC_VERTEX_LOCATION:-global}"
export GOOGLE_CLOUD_EMBEDDING_LOCATION="${GOOGLE_CLOUD_EMBEDDING_LOCATION:-us-central1}"

RUN_DIR="${RUN_DIR:-runs/baseline_claude}"
mkdir -p "$RUN_DIR"

echo "========================================================================"
echo " Baseline Test Suite — Claude Sonnet 5 Judge (Vertex AI)"
echo "========================================================================"
echo " Service URL:         $URL"
echo " Judge Provider:      $JUDGE"
echo " Judge Model:         $JUDGE_MODEL"
echo " GCP Project ID:      $GOOGLE_CLOUD_PROJECT_ID"
echo " Claude Endpoint:     $ANTHROPIC_VERTEX_LOCATION (Vertex Model Garden Global)"
echo " Embedding Endpoint:  $GOOGLE_CLOUD_EMBEDDING_LOCATION"
echo " Target Table:        $TABLE_NAME"
echo " Limit:               $LIMIT questions"
echo " Output Directory:    $RUN_DIR"
echo "========================================================================"

# Step 1: Judge Connectivity Check
echo -e "\n>>> [1/2] Testing Claude Sonnet 5 judge connectivity..."
set +e
"$PYTHON" src/cli.py test-judge --judge "$JUDGE" --judge-model "$JUDGE_MODEL"
JUDGE_STATUS=$?
set -e

if [ $JUDGE_STATUS -ne 0 ]; then
  echo -e "\n[!] Judge connectivity check encountered an issue (exit code $JUDGE_STATUS)."
  echo "    Note: If you received a 429 Resource Exhausted / Quota Exceeded error from Google Cloud,"
  echo "    ensure that the quota 'global_online_prediction_requests_per_base_model' for 'anthropic-claude-sonnet'"
  echo "    is requested and active in GCP Project '$GOOGLE_CLOUD_PROJECT_ID':"
  echo "    https://console.cloud.google.com/iam-admin/quotas?project=${GOOGLE_CLOUD_PROJECT_ID}"
  
  if [ "$CHECK_ONLY" = "1" ] || [ "${1:-}" = "--check-only" ]; then
    echo -e "\nCheck-only mode completed with status $JUDGE_STATUS."
    exit $JUDGE_STATUS
  fi
fi

if [ "$CHECK_ONLY" = "1" ] || [ "${1:-}" = "--check-only" ]; then
  echo -e "\n✓ Check-only mode: Judge connection test finished successfully."
  exit 0
fi

# Step 2: Run Baseline (C0) against Claude Sonnet 5
OUTPUT_FILE="${RUN_DIR}/run_C0_baseline.jsonl"
echo -e "\n>>> [2/2] Running Baseline (C0) with Claude Sonnet 5 judge..."
"$PYTHON" src/cli.py run \
  --url "$URL" \
  --sample --limit "$LIMIT" --stream \
  --table-name "$TABLE_NAME" \
  --warmup "$WARMUP" \
  --judge "$JUDGE" --judge-model "$JUDGE_MODEL" \
  --output "$OUTPUT_FILE"

echo -e "\n========================================================================"
echo "✓ Baseline test run complete! Results saved to: $OUTPUT_FILE"
echo "========================================================================"
