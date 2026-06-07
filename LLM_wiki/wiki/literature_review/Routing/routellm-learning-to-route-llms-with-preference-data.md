# RouteLLM: Learning to Route LLMs with Preference Data
**Authors:** Isaac Ong, Amjad Almahairi, Vincent Wu, Wei-Lin Chiang, Joseph E. Gonzalez, M Waleed Kadous, Ion Stoica, Tianhao Wu | **Year:** 2025
**Category:** Routing

### 📝 Abstract & Relevance Summary
Deploying LLMs in enterprise systems involves a fundamental trade-off: larger models (e.g., GPT-4) provide high-quality outputs but are expensive, while smaller models (e.g., Mixtral-8x7B) are cost-effective but less capable. To address this, the authors present RouteLLM, a training framework to learn efficient router models that dynamically choose between a stronger and a weaker LLM on a query-by-query basis. RouteLLM leverages human preference data (e.g., Chatbot Arena) alongside targeted data augmentation (golden-labeled and LLM-judge-labeled data) to train routers. By routing simpler queries to the weaker model and reserving complex queries for the stronger model, RouteLLM achieves over 2x cost savings with negligible impact on response quality. This framework prioritizes both **Operational Efficiency** (optimizing cost and keeping router latency overhead minimal) and **Output Quality** (ensuring complex reasoning queries still receive strong model responses).

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** Direct query routing to a single model avoids multi-model query cascades or self-verification loops, preserving fast Time to First Token (TTFT). The router's inference overhead is tiny: the Matrix Factorization router processes 155.16 requests/second on a single NVIDIA L4 GPU, representing a negligible latency addition.
- **Cost & Token Usage:** RouteLLM achieves cost savings of over 2x (and up to 3.66x) depending on the target performance. Specifically:
  - **MT Bench:** Saves up to 3.66x cost at CPT(50%) (which retains 95% of GPT-4's quality) and 2.49x at CPT(80%).
  - **MMLU:** Saves up to 1.41x cost at CPT(50%) and 1.14x at CPT(80%).
  - **GSM8K:** Saves up to 1.49x cost at CPT(50%) and 1.27x at CPT(80%).
- **Throughput:** Throughput varies by router capacity:
  - *Matrix Factorization:* 155.16 requests/sec (Google Cloud L4 GPU)
  - *BERT Classifier:* 69.62 requests/sec
  - *Causal LLM:* 42.46 requests/sec
  - *Similarity-Weighted (SW) Ranking:* 2.9 requests/sec (CPU-only)

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Performance Gap Recovered (PGR) - normalizes the router's score between the weak and strong models. Average PGR (APGR) - measures overall performance across all cost thresholds. Call-Performance Threshold (CPT(x%)) - minimum percentage of strong model calls needed to achieve x% PGR.
- **Benchmark Data:** MT Bench (160 open-ended queries), MMLU (14,042 multi-subject queries), and GSM8K (1,000+ math word problems).
- **Judge Setup:** GPT-4 is used as a judge for open-ended MT Bench evaluations. It also acts as the LLM judge to label the Djudge training set (Nectar dataset).

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** On MT Bench, the Matrix Factorization router (trained on Arena + Djudge) achieves 50% PGR (recovering 95% of GPT-4's performance) with only 13.4% of queries routed to GPT-4, and 80% PGR with 31.3% of queries. This represents an enormous efficiency gain with minimal quality degradation.
- **Pareto Frontier & Combinations:** A win-probability model `P_theta(wins | q)` combined with a cost threshold `alpha` allows developers to set a cost target and map the cost-quality Pareto frontier.
- **Chunking/Routing Strategy:** Binary model routing (Strong vs Weak). Crucially, the router generalizes to out-of-distribution pairs (e.g., Claude 3 Opus/Sonnet or Llama 3.1 70B/8B) without retraining, indicating it learns general query-complexity features.
- **Enterprise Suitability:** Highly suitable for enterprise setups due to zero retraining requirements when upgrading underlying models and an overhead cost of less than 0.4% compared to GPT-4 generation.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Uses Chatbot Arena preference battles (80K battles) clustered into 10 classes to handle label sparsity.
- **Bias Detection:** Implements embedding similarity search with a 0.95 threshold to check for and remove contaminated examples between training and evaluation datasets.

### 🔍 Technical Implementation
- **Portability:** The Matrix Factorization router relies on projecting text embeddings (e.g., from OpenAI's text-embedding-3-small) and doing simple matrix multiplication. This is highly portable to Java/Spring AI using standard matrix libraries or an ONNX runtime.
- **Key References:**
  - Ding, D., et al. (2024). *Hybrid LLM: Cost-efficient and quality-aware query routing.* ICLR 2024.
  - Chen, L., et al. (2023). *FrugalGPT: How to use large language models while reducing cost and improving performance.* arXiv preprint arXiv:2305.05176.
  - Chiang, W.-L., et al. (2024). *Chatbot Arena: An open platform for evaluating LLMs by human preference.*

### 💡 Key Takeaway for Thesis
- RouteLLM demonstrates that training a query router using preference data and targeted dataset augmentation allows systems to dynamically allocate queries to strong or weak models, cutting costs by over 2x with negligible impact on quality.

### 🔗 BibTeX Reference (Target: /LLM_wiki/bib)
```bibtex
@inproceedings{ong2025routellm,
  title={{RouteLLM}: Learning to Route {LLMs} with Preference Data},
  author={Ong, Isaac and Almahairi, Amjad and Wu, Vincent and Chiang, Wei-Lin and Wu, Tianhao and Gonzalez, Joseph E. and Kadous, M Waleed and Stoica, Ion},
  booktitle={International Conference on Learning Representations (ICLR 2025)},
  year={2025}
}
```
