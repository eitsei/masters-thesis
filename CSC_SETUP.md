# High-Performance Computing (HPC) Evaluation Guide — CSC Roihu

This guide documents the environment setup, model weight provisioning, and Slurm batch job execution required to reproduce the Stage 2 LLM-as-a-Judge (RAGAS) evaluations on the **CSC Roihu** supercomputer (NVIDIA Grace Hopper GH200 96 GB GPU nodes).

---

## 1. Prerequisites & SSH Authentication

CSC Roihu uses short-lived (24-hour) SSH certificates issued via the MyCSC portal or the official CSC certificate CLI utility.

```bash
# Authenticate and fetch a 24h SSH certificate via CSC's certificate-helper-tool:
git clone https://github.com/CSCfi/certificate-helper-tool.git
python3 certificate-helper-tool/csc_cert.py -u <csc_username> ~/.ssh/id_ed25519.pub

# Connect to the Roihu GPU login node (ARM64 Grace Hopper environment):
ssh <csc_username>@roihu-gpu.csc.fi
```

---

## 2. Scratch Workspace & Environment Variables

Compute nodes operate on the shared Lustre parallel scratch filesystem (`/scratch/<project_id>`). Configure your project and user paths before provisioning models or environments:

```bash
export CSC_PROJECT="project_<id>"
export SCRATCH_BASE="/scratch/${CSC_PROJECT}/${USER}"

# Create directory structure for model weights, Hugging Face cache, and run logs
mkdir -p "${SCRATCH_BASE}"/{models,huggingface_cache,runs/raw,runs/evaluated}

# Check project billing units (BUs) and workspace quotas
csc-projects
csc-workspaces
```

---

## 3. Environment & Model Weight Provisioning

Compute nodes on Roihu do not have outbound internet access during batch execution. All Python dependencies and Hugging Face model weights must be pre-downloaded on the login node.

### 3.1 Download Local Judge & Embedding Weights
```bash
module load python-vllm/0.29.0
export HF_HOME="${SCRATCH_BASE}/huggingface_cache"

# 1. Judge LLM (Qwen3.8-27B)
hf download Qwen/Qwen3.8-27B --local-dir "${SCRATCH_BASE}/models/Qwen3.8-27B"

# 2. Embedding Model (BAAI/bge-m3 for RAGAS Answer Relevance)
hf download BAAI/bge-m3 --local-dir "${SCRATCH_BASE}/models/bge-m3"
```

### 3.2 Initialize Python Virtual Environment
Create a virtual environment that inherits the system-optimized ARM64 PyTorch and vLLM packages from the `python-vllm` module:

```bash
python3 -m venv --system-site-packages "${SCRATCH_BASE}/venv-roihu"
source "${SCRATCH_BASE}/venv-roihu/bin/activate"

cd "${SCRATCH_BASE}/rag-evaluator"
pip install -r requirements-roihu.txt
```

---

## 4. Data Synchronization (`rsync`)

Run these commands from your local workstation to synchronize the evaluation harness and raw Stage 1 benchmark outputs (`--judge none`) with the Roihu scratch workspace:

```bash
# 1. Sync the evaluation harness to Roihu
rsync -avz --exclude 'venv' --exclude '__pycache__' --exclude '.git' \
  rag-code/rag-evaluator "<csc_username>@roihu-gpu.csc.fi:/scratch/<project_id>/<csc_username>/"

# 2. Upload local raw benchmark runs (Stage 1 outputs) for GPU evaluation
rsync -avz rag-code/rag-evaluator/runs/raw/ \
  "<csc_username>@roihu-gpu.csc.fi:/scratch/<project_id>/<csc_username>/rag-evaluator/runs/raw/"

# 3. Download evaluated runs, statistical tables, and figures after Slurm completion
rsync -avz "<csc_username>@roihu-gpu.csc.fi:/scratch/<project_id>/<csc_username>/rag-evaluator/runs/evaluated/" \
  rag-code/rag-evaluator/runs/evaluated/
```

---

## 5. Slurm Batch Execution

Each Slurm batch script in `rag-code/rag-evaluator/scripts/roihu/` provisions a single NVIDIA GH200 (96 GB) GPU on the `gpumedium` partition, launches co-located vLLM servers for `BAAI/bge-m3` (port 8001) and `Qwen/Qwen3.8-27B` (port 8000), evaluates all $2^4 = 16$ factorial configurations in parallel, and executes paired Wilcoxon signed-rank tests.

```bash
cd "${SCRATCH_BASE}/rag-evaluator"
mkdir -p logs

# Submit evaluation jobs to the Slurm queue
sbatch scripts/roihu/eval_musique.sbatch
sbatch scripts/roihu/eval_enterprise_closed.sbatch
sbatch scripts/roihu/eval_enterprise_raw.sbatch
```

### Job Monitoring & Diagnostics
```bash
# View active and queued jobs
squeue --me

# Stream job output logs
tail -f logs/eval_musique_<job_id>.out

# Inspect CPU/GPU memory utilization and efficiency after completion
seff <job_id>

# Cancel a running or pending job
scancel <job_id>
```
