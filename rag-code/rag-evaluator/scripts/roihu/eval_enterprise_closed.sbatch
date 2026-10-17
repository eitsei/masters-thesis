#!/usr/bin/env bash
# ==============================================================================
# Slurm Batch Evaluation Job for CSC Roihu (NVIDIA GH200 Grace Hopper 96GB)
# Dataset: Enterprise Closed Corpus (16 configurations, N=100)
#
# Runs dual vLLM instances (BGE-M3 Embeddings + Qwen3.8-27B Judge) sequentially on 1 GPU,
# executes parallel RAGAS evaluation on offline JSONL runs, and generates publication tables/figures.
# ==============================================================================
#SBATCH --job-name=eval-ent-closed
#SBATCH --account=project_2021509
#SBATCH --partition=gpumedium
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=16
#SBATCH --mem=64G
#SBATCH --gres=gpu:gh200:1
#SBATCH --time=04:00:00
#SBATCH --output=logs/eval_ent_closed_%j.out
#SBATCH --error=logs/eval_ent_closed_%j.err

# 1. Environment & Module Setup (run before set -e so CSC profile scripts initialize safely)
export CSC_ENV_INIT_NON_INTERACTIVE=yes
[ -f /etc/profile ] && source /etc/profile
module load python-vllm/0.29.0

set -euo pipefail

# 2. Project Directory Setup
if [ -d "${SLURM_SUBMIT_DIR:-}/src" ]; then
    PROJECT_DIR="$SLURM_SUBMIT_DIR"
elif [ -d "$(pwd)/src" ]; then
    PROJECT_DIR="$(pwd)"
elif [ -d "$(dirname "${BASH_SOURCE[0]}")/../../src" ]; then
    PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
else
    PROJECT_DIR="/scratch/project_2021509/asunmaa/rag-evaluator"
fi
cd "$PROJECT_DIR"
mkdir -p logs

echo "========================================================================"
echo " Starting Roihu RAGAS Evaluation Job: Enterprise Closed (Job ID: ${SLURM_JOB_ID:-manual})"
echo " Host:        $(hostname)"
echo " Date:        $(date)"
echo " Working Dir: $(pwd)"
echo " Walltime:    4 hours (--time=04:00:00)"
echo "========================================================================"

# Prevent telemetry, offline stalls, and ignore conflicting user site-packages (~/.local)
export HF_HUB_OFFLINE=1
export TRANSFORMERS_OFFLINE=1
export HF_HOME="${HF_HOME:-/scratch/project_2021509/asunmaa/huggingface_cache}"
export RAGAS_DO_NOT_TRACK=true
export LANGCHAIN_TRACING_V2=false
export LANGSMITH_TRACING=false
export TOKENIZERS_PARALLELISM=false
export PYTHONNOUSERSITE=1

# Isolate compute node from orphaned host MPS daemon pipes (e.g. /tmp/nvidia-mps)
mkdir -p "/tmp/${USER}/no_mps"
export CUDA_MPS_PIPE_DIRECTORY="/tmp/${USER}/no_mps"

# Isolate compute node from any inherited environment proxies
export NO_PROXY="localhost,127.0.0.1"
export no_proxy="localhost,127.0.0.1"
unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY

# Activate virtual environment
VENV_PATH="${VENV_PATH:-/scratch/project_2021509/asunmaa/venv-roihu}"
if [ -f "${VENV_PATH}/bin/activate" ]; then
    echo "Activating Python virtual environment: ${VENV_PATH}"
    # shellcheck disable=SC1090
    source "${VENV_PATH}/bin/activate"
else
    echo "ERROR: Virtual environment not found at ${VENV_PATH}" >&2
    exit 1
fi

# 3. Configuration & Paths
SCRATCH_DIR="${SCRATCH_DIR:-/scratch/project_2021509/asunmaa}"
MODEL_DIR="${MODEL_DIR:-${SCRATCH_DIR}/models/Qwen3.8-27B}"
EMBED_DIR="${EMBED_DIR:-${SCRATCH_DIR}/models/bge-m3}"

# Input runs: prioritize scratch runs if present, else repo-relative
if [ -d "${SCRATCH_DIR}/runs/raw/enterprise_closed" ]; then
    DEFAULT_INPUT="${SCRATCH_DIR}/runs/raw/enterprise_closed"
elif [ -d "runs/raw/enterprise_closed" ]; then
    DEFAULT_INPUT="runs/raw/enterprise_closed"
elif [ -d "${SCRATCH_DIR}/runs/enterprise_closed" ]; then
    DEFAULT_INPUT="${SCRATCH_DIR}/runs/enterprise_closed"
elif [ -d "runs/enterprise_closed" ]; then
    DEFAULT_INPUT="runs/enterprise_closed"
else
    DEFAULT_INPUT="${SCRATCH_DIR}/runs"
fi
INPUT_RUNS="${INPUT_RUNS:-$DEFAULT_INPUT}"
OUTPUT_RUNS="${OUTPUT_RUNS:-${SCRATCH_DIR}/runs/evaluated/enterprise_closed}"
WORKERS="${WORKERS:-16}"
SUBFOLDER="enterprise_closed"

echo "Configuration:"
echo "  HF_HOME:     $HF_HOME"
echo "  MODEL_DIR:   $MODEL_DIR"
echo "  EMBED_DIR:   $EMBED_DIR"
echo "  INPUT_RUNS:  $INPUT_RUNS"
echo "  OUTPUT_RUNS: $OUTPUT_RUNS"
echo "  WORKERS:     $WORKERS"
echo "  SUBFOLDER:   $SUBFOLDER"

export VLLM_BASE_URL="http://127.0.0.1:8000/v1"
export VLLM_EMBEDDING_BASE_URL="http://127.0.0.1:8001/v1"
export JUDGE_PROVIDER="vllm"
export JUDGE_MODEL="qwen3.8-27b"
export JUDGE_EMBEDDING_MODEL="bge-m3"
export JUDGE_ENABLE_THINKING="0"
export JUDGE_MAX_TOKENS="2048"
export JUDGE_TIMEOUT="180.0"

# Verify model directories exist
if [ ! -d "$MODEL_DIR" ]; then
    echo "ERROR: Judge model directory not found: $MODEL_DIR" >&2
    exit 1
fi
if [ ! -d "$EMBED_DIR" ]; then
    echo "ERROR: Embedding model directory not found: $EMBED_DIR" >&2
    exit 1
fi

# Cleanup handler for background vLLM servers
PID_LLM=""
PID_EMB=""
cleanup() {
    echo -e "\nStopping background vLLM servers..."
    if [ -n "$PID_LLM" ] && kill -0 "$PID_LLM" 2>/dev/null; then
        kill "$PID_LLM" 2>/dev/null || true
    fi
    if [ -n "$PID_EMB" ] && kill -0 "$PID_EMB" 2>/dev/null; then
        kill "$PID_EMB" 2>/dev/null || true
    fi
    wait "$PID_LLM" 2>/dev/null || true
    wait "$PID_EMB" 2>/dev/null || true
    echo "Cleanup complete."
}
trap cleanup EXIT SIGINT SIGTERM

# 4. Start vLLM Servers Sequentially (prevents VRAM profiling race conditions)
# GH200 has 96 GB VRAM. Allocate 8% (~7.6 GB) to BGE-M3 and 82% (~78.7 GB) to Qwen3.8-27B.

echo -e "\n>>> 1/2 Launching vLLM Embeddings Server (Port 8001)..."
vllm serve "$EMBED_DIR" \
    --host 127.0.0.1 \
    --port 8001 \
    --served-model-name bge-m3 \
    --runner pooling \
    --gpu-memory-utilization 0.08 \
    --trust-remote-code \
    > logs/vllm_embed.log 2>&1 &
PID_EMB=$!

echo "Waiting for Embeddings server to initialize and lock VRAM..."
EMBED_READY=false
for i in {1..60}; do
    if ! kill -0 "$PID_EMB" 2>/dev/null; then
        echo "ERROR: vLLM Embed process died prematurely!" >&2
        cat logs/vllm_embed.log >&2
        exit 1
    fi
    if curl -s http://127.0.0.1:8001/health > /dev/null 2>&1; then
        EMBED_READY=true
        echo "Embeddings server ready! (waited ~$((i * 5))s)"
        break
    fi
    sleep 5
done

if [ "$EMBED_READY" != true ]; then
    echo "ERROR: Embeddings server timed out." >&2
    tail -n 30 logs/vllm_embed.log >&2
    exit 1
fi

echo -e "\n>>> 2/2 Launching vLLM Judge LLM Server (Port 8000)..."
vllm serve "$MODEL_DIR" \
    --host 127.0.0.1 \
    --port 8000 \
    --served-model-name qwen3.8-27b \
    --gpu-memory-utilization 0.82 \
    --max-model-len 8192 \
    --max-num-seqs 256 \
    --gdn-prefill-backend triton \
    --trust-remote-code \
    --default-chat-template-kwargs '{"enable_thinking": false}' \
    > logs/vllm_judge.log 2>&1 &
PID_LLM=$!

echo "Waiting for Judge LLM server to initialize and lock VRAM..."
LLM_READY=false
for i in {1..90}; do
    if ! kill -0 "$PID_LLM" 2>/dev/null; then
        echo "ERROR: vLLM Judge process died prematurely!" >&2
        tail -n 50 logs/vllm_judge.log >&2
        exit 1
    fi
    if curl -s http://127.0.0.1:8000/health > /dev/null 2>&1; then
        LLM_READY=true
        echo "Judge LLM server ready! (waited ~$((i * 5))s)"
        break
    fi
    sleep 5
done

if [ "$LLM_READY" != true ]; then
    echo "ERROR: Judge LLM server timed out." >&2
    tail -n 30 logs/vllm_judge.log >&2
    exit 1
fi

# 5. Connectivity & Smoke Test
echo -e "\n>>> Testing Judge Connectivity via CLI..."
python src/cli.py test-judge --judge vllm

# 6. Execute Evaluation
echo -e "\n>>> Running parallel RAGAS evaluation on Enterprise Closed (workers: ${WORKERS})..."
mkdir -p "$OUTPUT_RUNS"
python src/cli.py evaluate \
    --input "$INPUT_RUNS" \
    --output "$OUTPUT_RUNS" \
    --judge vllm \
    --workers "$WORKERS" \
    --resume

# 7. Generate Publication Tables & Figures
echo -e "\n>>> Generating LaTeX Tables and Publication Figures for Enterprise Closed..."
python src/cli.py export-thesis \
    --input "$OUTPUT_RUNS" \
    --subfolder "$SUBFOLDER"

echo -e "\n========================================================================"
echo " Enterprise Closed evaluation and thesis export completed successfully at $(date)"
echo " Evaluated runs: $OUTPUT_RUNS"
echo "========================================================================"
