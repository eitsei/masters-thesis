# RAGRouter: Learning to Route Queries to Multiple Retrieval-Augmented Language Models
**Authors:** Jiarui Zhang, Xiangyu Liu, Yong Hu, Chaoyue Niu, Fan Wu, Guihai Chen | **Year:** 2025
**Category:** Routing

### 📝 Abstract & Relevance Summary
Retrieval-Augmented Generation (RAG) significantly improves LLM performance on knowledge-intensive tasks, but the heterogeneous response qualities of different LLMs under RAG demand intelligent query routing. This paper introduces RAGRouter, the first routing mechanism specifically designed for RAG scenarios. The authors observe that external document injection dynamically shifts LLMs' capabilities—making some queries answerable that were previously unanswerable, or vice versa—rendering static non-RAG routers obsolete. RAGRouter addresses this by representing candidate LLMs using both static parametric knowledge embeddings and dynamic RAG capability embeddings, alongside document and query encoders. Trained via contrastive learning (Cross-Setting and Intra-Setting Contrast), RAGRouter models how document injection alters model performance, thus balancing **Operational Efficiency** (by routing queries to smaller, more efficient RAG-enabled models) and **Output Quality** (outperforming the best individual LLM on knowledge-intensive benchmarks).

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** Under a score-threshold-based routing mechanism, RAGRouter can trade a small amount of accuracy for lower latency. By sorting candidate models from most efficient to least and routing to the first model whose score is within a threshold margin `theta` of the performance-optimal model, it achieves superior accuracy-latency trade-offs. The router's own inference latency is only 0.011 seconds per query on an NVIDIA RTX 4090D GPU.
- **Cost & Token Usage:** RAGRouter significantly reduces expensive model calls. In experiments containing proprietary models (GPT-4o, Qwen2.5-Max, DeepSeek-R1), RAGRouter outperforms the strongest model, GPT-4o, while invoking it in only 44.79% of samples, leading to substantial API token cost savings.
- **Throughput:** The router's small footprint (136M parameters) allows high-throughput processing (approx. 90 requests/second).

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Accuracy (Exact Match or Cover Exact Match), Area under the accuracy-latency curve (Area), Peak Accuracy (PA), and Latency Gap-to-Match (G).
- **Benchmark Data:** PopQA, MedMCQA, Natural Questions (NQ), WebQuestions (WebQ), and TriviaQA (TQA) evaluated under local (Wikipedia dump + BGE-large) and online (DuckDuckGo Search API) retrieval settings with synthetic noise.
- **Judge Setup:** Evaluated against ground-truth labels. The candidate pool consists of 15 open-source LLMs (0.5B to 72B parameter models from Llama, Qwen, Yi, Gemma, Ministral series) and 3 closed-source LLMs (GPT-4o, Qwen2.5-Max, DeepSeek-R1).

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** RAGRouter achieves an average accuracy of 64.46% across open-source models, outperforming the strongest individual model Llama-3.3-70B-Instruct (60.85%) by +3.61%, and the Oracle Single Best (61.22%) by +3.24%. Under low-latency constraints, its accuracy-latency Pareto curve consistently dominates all baselines.
- **Pareto Frontier & Combinations:** A threshold margin `theta` is used to navigate the Pareto frontier between accuracy and latency.
- **Chunking/Routing Strategy:** RAGRouter incorporates query, document, and cross-interaction encoders (based on `all-mpnet-base-v2` and `ms-marco-MiniLM-L12-v2`). The dynamic RAG-aware model representation is calculated as `v_k' = v_k + Attention(v_r, v_d, v_c)`, where `v_r` is the model's RAG capability embedding, `v_d` is the document embedding, and `v_c` is the query-document interaction embedding.
- **Enterprise Suitability:** Highly suitable for enterprise multi-model architectures. It allows seamless integration of both local open-source models and proprietary APIs, with the flexibility to prioritize either low latency/cost or maximum output accuracy.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Employs Cover Exact Match and exact match accuracy, alongside latency profiling.
- **Bias Detection:** Mitigates routing bias via a contrastive loss that incorporates Cross-Setting Contrast (CSC) and Intra-Setting Contrast (ISC) to distinguish how document injection shifts model behavior relative to a non-RAG baseline.

### 🔍 Technical Implementation
- **Portability:** Fully portable. The router's architecture (query/document encoders, cross-encoder, attention layers, and linear layers) can be exported to the ONNX format and run directly in Java/Spring AI using the ONNX Runtime library.
- **Key References:**
  - Ding, D., et al. (2024). *Hybrid LLM: Cost-efficient and quality-aware query routing.* ICLR 2024.
  - Ong, I., et al. (2025). *RouteLLM: Learning to route LLMs with preference data.* ICLR 2025.
  - Chen, L., et al. (2023). *FrugalGPT: How to use large language models while reducing cost and improving performance.* arXiv preprint arXiv:2305.05176.

### 💡 Key Takeaway for Thesis
- RAGRouter demonstrates that because external document injection dynamically alters LLM capabilities, query routers in RAG architectures must be "RAG-aware"—integrating document context and RAG capability embeddings—to achieve optimal routing performance and cost-efficiency trade-offs.

### 🔗 BibTeX Reference (Target: /LLM_wiki/bib)
```bibtex
@inproceedings{zhang2025ragrouter,
  title={RAGRouter: Learning to Route Queries to Multiple Retrieval-Augmented Language Models},
  author={Zhang, Jiarui and Liu, Xiangyu and Hu, Yong and Niu, Chaoyue and Wu, Fan and Chen, Guihai},
  booktitle={39th Conference on Neural Information Processing Systems (NeurIPS 2025)},
  year={2025}
}
```
