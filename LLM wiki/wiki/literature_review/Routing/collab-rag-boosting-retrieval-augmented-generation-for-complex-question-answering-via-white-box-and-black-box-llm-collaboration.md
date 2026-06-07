# Collab-RAG: Boosting Retrieval-Augmented Generation for Complex Question Answering via White-Box and Black-Box LLM Collaboration
**Authors:** Ran Xu, Wenqi Shi, Yuchen Zhuang, et al. | **Year:** 2025
**Category:** Routing

### 📝 Abstract & Relevance Summary
Collab-RAG addresses the challenge of multi-hop question answering by fostering collaboration between a white-box Small Language Model (SLM) and a black-box Large Language Model (LLM). The SLM serves as an optimized query decomposer, breaking complex queries into atomic sub-questions to enhance retrieval precision, while the LLM acts as a reader for intermediate answers and final synthesis. This framework balances **Operational Efficiency** by delegating expensive planning and decomposition to small, fine-tuned models (e.g., 3B or 8B) and **Output Quality** by leveraging the robust extraction and reasoning of larger proprietary models on focused sub-contexts.

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** A fine-tuned 3B SLM decomposer achieves better results than a frozen 32B model, significantly reducing the computational cost of the planning stage.
- **Token Usage:** By decomposing queries into specific sub-queries, the system reduces the injection of irrelevant context, thereby minimizing the total tokens processed by the more expensive black-box LLM.
- **Throughput:** Utilizing SLMs for the iterative decomposition loop frees up Large Model capacity for high-value reasoning and synthesis.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Exact Match (EM), Accuracy, and F1 Score.
- **Benchmark Data:** Evaluated on five multi-hop datasets: HotpotQA, 2WikiMQA, MusiQue, StrategyQA, and Bamboogle.
- **Judge Setup:** Iterative Preference Optimization (DPO) guided by outcome feedback from GPT-4o-mini to refine the SLM's decomposition strategy.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** Collab-RAG outperforms black-box only and standard SLM fine-tuning baselines by 1.8% to 14.2% on average. It demonstrates that investing in an SLM-based planning stage yields significant accuracy gains without the massive cost of multi-agent large model calls.
- **Chunking Strategy:** Leverages sub-questions to guide the retriever (Dragon-Plus) in sequentially retrieving and synthesizing atomic facts.
- **Enterprise Suitability:** Highly suited for enterprise RAG pipelines that combine local hosting for planning/security with external API calls for high-reasoning tasks.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Employs a self-improving training strategy relying solely on outcome-based feedback from the black-box reader.
- **Bias Detection:** Avoids the need for intermediate distillation from frontier models, reducing reliance on potentially biased larger model "thoughts."

### 🔍 Technical Implementation
- **Portability:** The white-box SLM components (Qwen-2.5-3B or Llama-3.1-8B) are standard architectures compatible with local Spring AI deployment (e.g., via vLLM or Ollama).
- **Key References:** 
  - Trivedi et al., 2023 (IRCoT)
  - Khot et al., 2023 (Decomposed Prompting)

### 💡 Key Takeaway for Thesis
Collab-RAG proves that the efficiency-quality equilibrium for complex RAG can be optimized through a heterogeneous collaboration model, where small models handle query-side sieving to maximize the reasoning precision of large models.

### 🔗 BibTeX Reference (Target: /bib)
```bibtex
@article{xu2025collabrag,
  title={Collab-{RAG}: Boosting Retrieval-Augmented Generation for Complex Question Answering via White-Box and Black-Box {LLM} Collaboration},
  author={Xu, Ran and Shi, Wenqi and Zhuang, Yuchen and Yu, Yue and Ho, Joyce C. and Wang, Haoyu and Yang, Carl},
  journal={arXiv preprint arXiv:2504.04915},
  year={2025}
}
```