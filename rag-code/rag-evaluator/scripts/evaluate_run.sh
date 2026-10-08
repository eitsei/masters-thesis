#!/usr/bin/env bash
# ==============================================================================
# Standalone Offline RAGAS Evaluator & Thesis Report Generator
# Evaluates previously collected JSONL runs without requiring Spring Boot.
#
# Usage:
#   ./scripts/evaluate_run.sh <RUN_DIR> [OPTIONS]
#
# Examples:
#   # Evaluate Nordea 16-configuration sweep with Llama:
#   ./scripts/evaluate_run.sh runs/nordea_all16_final --judge llama --judge-model meta/llama-4-scout-17b-16e-instruct-maas --subfolder enterprise
#
#   # Evaluate MuSiQue 16-configuration sweep with Gemini:
#   ./scripts/evaluate_run.sh runs/musique_all16_final --judge gemini --judge-model gemini-3.5-flash-lite --subfolder musique
#
#   # Evaluate Sample 5 run with in-place updates:
#   ./scripts/evaluate_run.sh runs/sample5_m3 --judge gemini --judge-model gemini-3.5-flash-lite
# ==============================================================================
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

PYTHON="${PYTHON:-venv/bin/python}"
TABLES_DIR="${TABLES_DIR:-../../thesis/tables}"
FIGURES_DIR="${FIGURES_DIR:-../../thesis/figures}"

RUN_DIR=""
JUDGE="${JUDGE:-gemini}"
JUDGE_MODEL="${JUDGE_MODEL:-gemini-3.5-flash-lite}"
SUBFOLDER=""
DATASET_NAME=""
RESUME="--resume"

# Argument parsing
while [[ $# -gt 0 ]]; do
  case "$1" in
    --judge|-j)
      JUDGE="$2"
      shift 2
      ;;
    --judge-model|-m)
      JUDGE_MODEL="$2"
      shift 2
      ;;
    --subfolder|-s)
      SUBFOLDER="$2"
      shift 2
      ;;
    --dataset|-d|--dataset-name)
      DATASET_NAME="$2"
      shift 2
      ;;
    --tables-dir)
      TABLES_DIR="$2"
      shift 2
      ;;
    --figures-dir)
      FIGURES_DIR="$2"
      shift 2
      ;;
    --no-resume)
      RESUME="--no-resume"
      shift
      ;;
    --force|-f)
      RESUME="--force"
      shift
      ;;
    -*)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
    *)
      if [ -z "$RUN_DIR" ]; then
        RUN_DIR="$1"
      else
        echo "Unexpected argument: $1" >&2
        exit 1
      fi
      shift
      ;;
  esac
done

if [ -z "$RUN_DIR" ]; then
  echo "Usage: $0 <RUN_DIR> [--judge <provider>] [--judge-model <model>] [--subfolder <subfolder>]" >&2
  exit 1
fi

if [ ! -d "$RUN_DIR" ] && [ ! -f "$RUN_DIR" ]; then
  echo "Error: Target path '$RUN_DIR' not found!" >&2
  exit 1
fi

# Auto-infer SUBFOLDER if not explicitly provided
if [ -z "$SUBFOLDER" ]; then
  SUBFOLDER="$(basename "$RUN_DIR" | sed 's/runs\///')"
fi

echo "========================================================================"
echo " Offline RAG Quality Evaluator & Report Generator"
echo " Run Target:    $RUN_DIR"
echo " Judge:         $JUDGE ($JUDGE_MODEL)"
echo " Subfolder:     $SUBFOLDER"
echo " Tables Dir:    $TABLES_DIR"
echo " Figures Dir:   $FIGURES_DIR"
echo "========================================================================"

# 1. Verify Judge Connectivity
echo -e "\n>>> Verifying Judge Connectivity..."
"$PYTHON" src/cli.py test-judge --judge "$JUDGE" --judge-model "$JUDGE_MODEL"

# 2. Evaluate target files in-place
echo -e "\n>>> Evaluating benchmark JSONL runs in-place..."
EVAL_ARGS=(
  "--input" "$RUN_DIR"
  "--judge" "$JUDGE"
  "--judge-model" "$JUDGE_MODEL"
  "--inplace"
)
if [ "$RESUME" = "--force" ]; then
  EVAL_ARGS+=("--force")
elif [ "$RESUME" = "--no-resume" ]; then
  EVAL_ARGS+=("--no-resume")
else
  EVAL_ARGS+=("--resume")
fi

"$PYTHON" src/cli.py evaluate "${EVAL_ARGS[@]}"

# 3. Paired Wilcoxon hypothesis tests against C0 baseline (if present)
BASELINE=""
if [ -d "$RUN_DIR" ]; then
  for cand in "${RUN_DIR}"/*C0*.jsonl; do
    if [ -f "$cand" ]; then
      BASELINE="$cand"
      break
    fi
  done

  if [ -n "$BASELINE" ]; then
    echo -e "\n========================================================================"
    echo " Statistical Hypothesis Testing (Wilcoxon Signed-Rank Tests vs Baseline)"
    echo " Baseline: $(basename "$BASELINE")"
    echo "========================================================================"

    for treat in "${RUN_DIR}"/run_C*.jsonl; do
      [ -f "$treat" ] || continue
      if [ "$treat" != "$BASELINE" ]; then
        cfg_name="$(basename "$treat" .jsonl | sed 's/run_//' | tr '_' ' ')"
        "$PYTHON" src/cli.py compare \
          --baseline "$BASELINE" \
          --treatment "$treat" \
          --config-name "$cfg_name" || true
      fi
    done
  fi
fi

# 4. Consolidated LaTeX tables & publication figures export
echo -e "\n========================================================================"
echo " Consolidated LaTeX Tables & Publication Figures Export"
echo "========================================================================"

EXPORT_ARGS=(
  "--input" "$RUN_DIR"
  "--tables-dir" "$TABLES_DIR"
  "--figures-dir" "$FIGURES_DIR"
  "--subfolder" "$SUBFOLDER"
)
if [ -n "$DATASET_NAME" ]; then
  EXPORT_ARGS+=("--dataset-name" "$DATASET_NAME")
fi

"$PYTHON" src/cli.py export-thesis "${EXPORT_ARGS[@]}"

echo -e "\n✓ Offline evaluation and thesis export completed successfully!"
