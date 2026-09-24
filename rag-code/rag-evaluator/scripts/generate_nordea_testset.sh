#!/usr/bin/env bash
# ==============================================================================
# Generate Synthetic Enterprise Test Set for Nordea Private Banking (Thesis §4.4)
# Generates 100 benchmark queries:
# - 40% Single-Hop Factual Queries
# - 40% Multi-Hop Relational Queries
# - 20% Out-of-Domain Refusal Queries with Standard Refusal Ground Truth
# ==============================================================================
set -euo pipefail

# Ensure working directory is rag-code/rag-evaluator
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

PYTHON="${PYTHON:-venv/bin/python}"
SIZE="${SIZE:-100}"
JUDGE_MODEL="${JUDGE_MODEL:-gemini-3.8-flash}"
MD_DIR="${MD_DIR:-../rag-benchmark/data/nordea/md}"
OUTPUT_FILE="${OUTPUT_FILE:-data/nordea/nordea_benchmark_100.jsonl}"
LIMIT_DOCS="${LIMIT_DOCS:-}"

echo "========================================================================"
echo " Generating Nordea Synthetic Benchmark Test Set (Thesis §4.4)"
echo " Generator Model: $JUDGE_MODEL (Vertex AI global)"
echo " Target Size:     $SIZE questions (40% single-hop, 40% multi-hop, 20% OOD)"
echo " Source Corpus:   $MD_DIR"
echo " Output File:     $OUTPUT_FILE"
if [ -n "$LIMIT_DOCS" ]; then
  echo " Doc Limit:       $LIMIT_DOCS canonical documents"
fi
echo "========================================================================"

mkdir -p "$(dirname "$OUTPUT_FILE")"

EXTRA_ARGS=()
if [ -n "$LIMIT_DOCS" ]; then
  EXTRA_ARGS+=(--limit-docs "$LIMIT_DOCS")
fi

"$PYTHON" src/cli.py generate-nordea-testset \
  --md-dir "$MD_DIR" \
  --output "$OUTPUT_FILE" \
  --size "$SIZE" \
  --judge-model "$JUDGE_MODEL" \
  "${EXTRA_ARGS[@]}"

echo -e "\n========================================================================"
echo " Generation completed successfully!"
echo " Questions saved to: $OUTPUT_FILE"
echo " You can now evaluate configurations using:"
echo "   QUESTIONS_FILE=$OUTPUT_FILE ./scripts/run_nordea_all16.sh"
echo "========================================================================"
