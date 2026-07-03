# Enhancing Q&A Text Retrieval with Ranking Models: Benchmarking, fine-tuning and deploying Rerankers for RAG
**Authors:** Gabriel de Souza P. Moreira, Ronay Ak, Benedikt Schifferer, Mengyao Xu, Radek Osmulski, Even Oldridge | **Year:** 2024
**Category:** Semantic-Re-ranking

### 📝 Abstract & Relevance Summary
Text retrieval systems for open-domain question-answering typically use a multi-stage pipeline: a first-stage dense embedding or sparse index retrieves candidate passages, followed by a second-stage reranking model that refines the results. This paper benchmarks various publicly available ranking models and introduces a state-of-the-art reranker, NV-RerankQA-Mistral-4B-v3, which is pruned from Mistral 7B and fine-tuned for QA tasks. The authors demonstrate that neural rerankers significantly improve retrieval accuracy. Crucially, the paper addresses practical engineering considerations such as indexing throughput, serving latency, and memory footprint when deploying these pipelines in enterprise environments.

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** First-stage query embedding latency is extremely low (e.g., 5.1 ms for NV-EmbedQA-E5-v5 and 19.8 ms for Mistral7B-v2). Running the second-stage NV-RerankQA-Mistral-4B-v3 reranker on 64 candidate passages of 512 tokens adds approximately 266 ms of latency.
- **Cost & Token Usage:** Pruning the Mistral 7B backbone down to 16 layers (creating the 4B parameter model) yields a 50% reduction in parameter count. This significantly decreases GPU memory requirements and serving costs while incurring only a marginal 1% accuracy penalty.
- **Throughput:** Indexing throughput is high for small embedding models (e.g., 558.4 passages/sec for E5-v5) but drops for large 7B models (68.7 passages/sec), validating the multi-stage approach where a small model does the initial retrieval and a larger reranker refines only the top 64 candidates.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Retrieval quality is measured using Normalized Discounted Cumulative Gain (NDCG@10). NV-RerankQA-Mistral-4B-v3 achieves a 14% absolute increase in average NDCG@10 compared to pipelines using the second-best reranker (BGE-Reranker-v2-M3).
- **Benchmark Data:** Question-answering datasets from the BEIR benchmark: Natural Questions (NQ), HotpotQA, and FiQA.
- **Judge Setup:** The reranker is trained using pointwise and pairwise classification losses on query-passage relevance datasets, using a score threshold representing 95% of positive scores to filter out noise.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** Multi-stage retrieval delivers a balanced Pareto frontier: it avoids the high computational latency and cost of running large embedding models on the entire database by restricting the large 4B model to a small candidate set (top-64), adding 266 ms to query response times but boosting accuracy by 14%.
- **Pareto Frontier & Combinations:** Comparing point-wise vs. pair-wise training losses shows that pair-wise loss paired with bi-directional attention mechanisms in the decoder-only transformer backbone yields the highest accuracy ceiling.
- **Chunking/Routing Strategy:** Candidate passages are truncated to a maximum of 512 tokens.
- **Enterprise Suitability:** The paper focuses on commercially viable models, deliberately avoiding models trained on research-only public datasets like MS-MARCO to ensure compliance in corporate settings.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Standard BEIR information retrieval benchmarking framework.
- **Bias Detection:** A score threshold based on 95% of positive scores was implemented to prune noisy positive targets during training.

### 🔍 Technical Implementation
- **Portability:** The embedding and reranking models are converted to ONNX and TensorRT-LLM for deployment on NVIDIA hardware. In a Java/Spring AI stack, these models can be served via Triton Inference Server or local Hugging Face endpoints.
- **Key References:**
  - Bajaj et al. (2018) MS-MARCO: A Human Generated MAchine Reading COmprehension Dataset
  - Thakur et al. (2021) BEIR: A Heterogeneous Benchmark for Systematic Evaluation of Diverse Information Retrieval Models

### 💡 Key Takeaway for Thesis
- This benchmarking study provides concrete data on the latency-accuracy trade-offs of deploying large language model rerankers in production, demonstrating that pruning a decoder model to 4B parameters achieves a 50% cost reduction with only a 1% drop in accuracy.

### 🔗 BibTeX Reference (Target: /LLM_wiki/bib)
```bibtex
@article{moreira2024enhancing,
  title={Enhancing Q\&A Text Retrieval with Ranking Models: Benchmarking, fine-tuning and deploying Rerankers for RAG},
  author={Moreira, Gabriel de Souza P. and Ak, Ronay and Schifferer, Benedikt and Xu, Mengyao and Osmulski, Radek and Oldridge, Even},
  journal={arXiv preprint arXiv:2409.07691},
  year={2024}
}
```
