# RAGRouter: Learning to Route Queries to Multiple Retrieval-Augmented Language Models
**Authors:** Jiarui Zhang, Xiangyu Liu, Yong Hu, Chaoyue Niu, Fan Wu, Guihai Chen | **Year:** 2025
**Category:** Routing

### 📝 Abstract & Relevance Summary
RAGRouter proposes a contrastive learning-based routing framework designed to dynamically select the most suitable LLM from a pool of candidate models for each query in a RAG setting. It addresses the limitation of traditional routers that rely on static parametric knowledge by explicitly modeling the dynamic knowledge shifts induced by retrieved documents. This approach is highly relevant to both **Operational Efficiency** and **Output Quality**. It enhances efficiency by avoiding the invocation of all models and using a score-threshold mechanism to favor faster models, while simultaneously improving quality by leveraging the complementary strengths of different LLMs to surpass the performance of any single model.

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** The routing model is lightweight, processing 270 instances in 3 seconds (average inference time of 0.011 seconds per instance) on an RTX 4090D, adding negligible overhead.
- **Token Usage:** By pre-selecting a single, optimal model for each query, it avoids the redundant token generation costs associated with ensemble or multi-LLM querying strategies.
- **Throughput:** Incorporates a latency-aware, score-threshold-based mechanism that sacrifices minimal accuracy to route to faster models, effectively increasing the overall system throughput and reducing average query latency.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Exact Match (EM) and Accuracy.
- **Benchmark Data:** Evaluated on five knowledge-intensive tasks: PopQA, MedMCQA, Natural Questions (NQ), WebQuestions (WebQ), and TriviaQA.
- **Judge Setup:** Deterministic evaluation against ground-truth answers.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** RAGRouter achieves an average accuracy of 64.46%, outperforming the best single LLM (60.85%) and existing non-RAG-aware routing methods. Its latency-aware routing matches the accuracy of larger models using smaller ones, demonstrating a superior trade-off.
- **Chunking Strategy:** Context is provided via dense retrieval (BGE-large-en-v1.5) over Wikipedia and online search (DuckDuckGo), simulating realistic and noisy RAG scenarios.
- **Enterprise Suitability:** Extremely suitable for enterprise applications that utilize a fleet of heterogeneous models (e.g., small local models and large proprietary APIs), optimizing both cost and latency dynamically.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Compared against strong baselines including RouterDC, GraphRouter, and Prompt LLM across 15 candidate LLMs ranging from 0.5B to 72B parameters.
- **Bias Detection:** The study explicitly tests against artificially injected retrieval noise (e.g., irrelevant and counterfactual noise) to ensure the router remains robust and unbiased by misleading contexts.

### 🔍 Technical Implementation
- **Portability:** The router uses small pre-trained encoders (`all-mpnet-base-v2` and `ms-marco-MiniLM-L12-v2`). This architecture can be easily ported to Java/Spring AI using ONNX Runtime for local execution.
- **Key References:** 
  - Chen et al., 2024 (RouterDC)
  - Feng et al., 2024 (GraphRouter)

### 💡 Key Takeaway for Thesis
RAGRouter proves that explicitly modeling the interaction between retrieved documents and a model's intrinsic RAG capability enables highly effective routing, allowing enterprise systems to utilize smaller, cheaper models without sacrificing the quality of large, expensive ones.

### 🔗 BibTeX Reference (Target: /bib)
```bibtex
@inproceedings{zhang2025ragrouter,
  title={RAGRouter: Learning to Route Queries to Multiple Retrieval-Augmented Language Models},
  author={Zhang, Jiarui and Liu, Xiangyu and Hu, Yong and Niu, Chaoyue and Wu, Fan and Chen, Guihai},
  booktitle={39th Conference on Neural Information Processing Systems (NeurIPS 2025)},
  year={2025}
}
```