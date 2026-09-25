#!/usr/bin/env bash
# ==============================================================================
# Build Nordea Personal Banking Benchmark Dataset in MuSiQue Schema (Thesis §4.4)
#
# Transforms Finnish customer questions (qs_fi.csv) and crawled Markdown pages
# into a structured 100-question benchmark dataset:
# - 40% Single-Hop Factual Queries (1 supporting paragraph + 19 distractors)
# - 40% Multi-Hop Relational Queries (2 supporting paragraphs + 18 distractors + decomposition)
# - 20% Out-of-Domain Refusal Queries (0 supporting paragraphs + 20 distractors)
#
# Generates 100% MuSiQue-schema JSONL compatible with Java Spring AI
# (MusiqueDatasetReader) and rag-evaluator CLI runner.
# ==============================================================================
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

PYTHON="${PYTHON:-venv/bin/python}"
SIZE="${SIZE:-100}"
JUDGE_MODEL="${JUDGE_MODEL:-gemini-3.5-flash}"
CSV_FILE="${CSV_FILE:-data/nordea/qs_fi.csv}"
MULTIHOP_CSV="${MULTIHOP_CSV:-data/nordea/qs_multihop_fi.csv}"
CORPUS_DIR="${CORPUS_DIR:-../rag-benchmark/data/nordea/md}"
OUTPUT_FILE="${OUTPUT_FILE:-data/nordea/nordea_ans_v1.0_dev.jsonl}"
LIMIT_INPUT="${LIMIT_INPUT:-}"
DELAY="${DELAY:-2.0}"
RESUME="${RESUME:-true}"

echo "========================================================================"
echo " Building Nordea MuSiQue-Schema Benchmark Dataset (Thesis §4.4)"
echo " LLM Verifier:  $JUDGE_MODEL (Vertex AI global)"
echo " Target Size:   $SIZE questions (40% single-hop, 40% multi-hop, 20% OOD)"
echo " Resume Build:  $RESUME"
echo " Single-Hop:    $CSV_FILE"
echo " Multi-Hop:     $MULTIHOP_CSV"
echo " Source Corpus: $CORPUS_DIR"
echo " Output File:   $OUTPUT_FILE"
echo " Pacing Delay:  ${DELAY}s per LLM call"
if [ -n "$LIMIT_INPUT" ]; then
  echo " Input Limit:   $LIMIT_INPUT candidate questions"
fi
echo "========================================================================"

if [[ " $* " =~ " --help " ]] || [[ " $* " =~ " -h " ]]; then
  "$PYTHON" src/cli.py build-nordea-musique-dataset --help
  exit 0
fi

if [ ! -f "$CSV_FILE" ]; then
  echo "Error: Single-hop Questions CSV '$CSV_FILE' not found!" >&2
  exit 1
fi

if [ ! -f "$MULTIHOP_CSV" ]; then
  echo "Warning: Multi-hop Questions CSV '$MULTIHOP_CSV' not found. Multi-hop pool will be empty." >&2
fi

if [ ! -d "$CORPUS_DIR" ]; then
  echo "Warning: Corpus directory '$CORPUS_DIR' does not exist or has not been crawled yet."
  echo "Make sure to run the Nordea ingestion/crawler first to populate Markdown files."
fi

mkdir -p "$(dirname "$OUTPUT_FILE")"

EXTRA_ARGS=()
if [ -n "$LIMIT_INPUT" ]; then
  EXTRA_ARGS+=(--limit-input "$LIMIT_INPUT")
fi
if [ "$RESUME" = "true" ]; then
  EXTRA_ARGS+=(--resume)
else
  EXTRA_ARGS+=(--no-resume)
fi

"$PYTHON" src/cli.py build-nordea-musique-dataset \
  --csv-file "$CSV_FILE" \
  --multihop-csv "$MULTIHOP_CSV" \
  --corpus-dir "$CORPUS_DIR" \
  --output "$OUTPUT_FILE" \
  --size "$SIZE" \
  --judge-model "$JUDGE_MODEL" \
  --delay "$DELAY" \
  "${EXTRA_ARGS[@]}" \
  "$@"

echo -e "\n========================================================================"
echo " Dataset construction completed successfully!"
echo " Questions saved to: $OUTPUT_FILE"
echo " Format: MuSiQue-schema JSONL (paragraphs with is_supporting flags)"
echo "========================================================================"
