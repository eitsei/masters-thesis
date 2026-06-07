# Collab-RAG: Boosting Retrieval-Augmented Generation for Complex Question Answering via White-Box and Black-Box LLM Collaboration
**Authors:** Ran Xu, Wenqi Shi, Yuchen Zhuang, Yue Yu, Joyce C. Ho, Haoyu Wang, Carl Yang | **Year:** 2025
**Category:** Routing

### 📝 Abstract & Relevance Summary
Standard RAG pipelines often struggle with complex multi-hop question-answering tasks because they retrieve irrelevant context and lack structured multi-step reasoning. To address this, the authors introduce Collab-RAG, a collaborative training framework that pairs a lightweight, white-box Small Language Model (SLM) query decomposer with a powerful, black-box Large Language Model (LLM) context reader. The SLM breaks down complex queries into simpler sequential sub-questions to optimize external retrieval, while the black-box LLM reader processes the retrieved context to generate intermediate and final answers. To improve the SLM's decomposition capabilities without expensive human labeling or distillation from frontier models, Collab-RAG uses an iterative preference optimization (iterative DPO) loop guided solely by the black-box LLM's final answer accuracy. This framework directly targets both output quality (by reducing noise and misdirection in retrieval) and operational efficiency (by leveraging smaller local models for query decomposition rather than relying on expensive API calls to larger models).

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** Using local white-box SLMs (3B and 8B parameters) for question decomposition significantly reduces execution latency compared to running zero-shot question decomposition on proprietary black-box models. While there are no explicit millisecond latency measurements, the paper emphasizes that running decomposition locally on open-source SLMs avoids the heavy latency overhead associated with multiple round-trip API calls to frontier black-box LLMs during multi-hop QA execution.
- **Cost & Token Usage:** Collab-RAG avoids the high costs of proprietary distillation by training the SLM using feedback only from an affordable black-box model (GPT-4o-mini). A fine-tuned 3B Qwen-2.5 model achieves question decomposition that surpasses a frozen 32B model, providing substantial parameter and API cost savings. Furthermore, structured question decomposition prevents the retrieval of irrelevant documents, which reduces overall context window token consumption at the LLM reader.
- **Throughput:** Not explicitly measured, but the use of lightweight SLMs (3B/8B) locally enables much higher throughput (QPS) and lower token generation times for the decomposition stage than API-dependent alternatives.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Evaluated using Exact Match (EM), F1 Score, and Accuracy (Acc). Exact Match (EM) is used as the primary metric.
- **Benchmark Data:** Five representative multi-hop QA datasets: HotpotQA, 2WikiMQA, MusiQue, StrategyQA, and Bamboogle.
- **Judge Setup:** The framework uses a rule-based evaluation reward rather than an LLM judge. The reward $u(x_i, f_\theta(x_i))$ combines format validation (checking if the sub-questions correctly reference prior answers, e.g. using `#1` to reference answer 1) and exact matching/accuracy of the final output against ground-truth labels: $eval(x_i, \hat{y}_{i,T_i}, y_i) = 0.5 \times (EM(\hat{y}_{i,T_i} = y_i) + Acc(\hat{y}_{i,T_i} \subseteq y_i))$.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** A 3B SLM (Qwen-2.5-3B) trained via Collab-RAG achieves a 51.6% EM on HotpotQA, outperforming a standard RAG baseline with GPT-4o-mini decomposer by 0.7%, showing that small, specialized models can match or exceed larger frontier models in decomposition tasks while being far more cost-effective.
- **Pareto Frontier & Combinations:** Collab-RAG combines iterative retrieval-generation flows with query decomposition and preference optimization. It achieves competitive performance against RAG-Gym and RAG-Star while operating orthogonally, meaning it can be combined with search-time optimizations and re-rankers (e.g. COCO-DR, E5, and GTE retrievers were tested and showed robust performance).
- **Chunking/Routing Strategy:** Rather than simple routing, the model decomposes the original query $x_i$ into $T_i$ subquestions. It uses Dragon-Plus or other retrievers (E5, GTE) to retrieve $k=10$ passages per subquestion. The routing rule enforces sequential generation of sub-queries where the SLM references previous answers (e.g., "What is the birthplace of #1?").
- **Enterprise Suitability:** Highly suitable for enterprise RAG systems. It allows businesses to use smaller, secure, on-premise models (3B/8B SLMs) for query preprocessing and planning, whilst calling a larger black-box API only for final synthesis, mitigating privacy risks and operational API costs.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Standard QA metrics (EM/F1) against original dataset ground truth.
- **Bias Detection:** The framework mitigates self-preference and LLM judge bias by relying on objective ground-truth answer matching (EM/Acc) for its reward signal, rather than relying on GPT-4 prompt-based quality evaluations.

### 🔍 Technical Implementation
- **Portability:** Fully portable to Java/Spring AI. The sequential decomposition and iterative reader execution is orchestrated at the application level. An application can prompt a local model (via Ollama or vLLM) for the decomposition step, retrieve documents via a vector store (like pgvector), and then feed the context to the main LLM client.
- **Key References:**
  1. *IRCoT*: Trivedi, H., et al. (2023). Interleaving Retrieval with Chain-of-Thought Reasoning for Knowledge-Intensive Multi-Step Questions.
  2. *Self-RAG*: Asai, A., et al. (2023). Self-RAG: Learning to Retrieve, Generate, and Critique through Self-Reflection.
  3. *FLARE*: Jiang, Z., et al. (2023). Active Retrieval Augmented Generation.

### 💡 Key Takeaway for Thesis
- Collab-RAG demonstrates that a lightweight 3B/8B local model can be fine-tuned via iterative DPO to act as a high-quality query decomposer, enabling complex multi-hop reasoning in RAG without the operational costs and latency of frontier-model planning.

### 🔗 BibTeX Reference (Target: /LLM_wiki/bib)
```bibtex
@article{xu2025collabrag,
  title={Collab-{RAG}: Boosting Retrieval-Augmented Generation for Complex Question Answering via White-Box and Black-Box {LLM} Collaboration},
  author={Xu, Ran and Shi, Wenqi and Zhuang, Yuchen and Yu, Yue and Ho, Joyce C. and Wang, Haoyu and Yang, Carl},
  journal={arXiv preprint arXiv:2504.04915},
  year={2025}
}
```
