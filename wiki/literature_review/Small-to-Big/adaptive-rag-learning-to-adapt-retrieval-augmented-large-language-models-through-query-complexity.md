# Adaptive-RAG: Learning to Adapt Retrieval-Augmented Large Language Models through Question Complexity
**Authors:** Soyeong Jeong, Jinheon Baek, Sukmin Cho, Sung Ju Hwang, Jong C. Park | **Year:** 2024
**Category:** Small-to-Big

### 📝 Abstract & Relevance Summary
Adaptive-RAG is a framework that dynamically selects the most suitable RAG strategy (from no retrieval to single-step or multi-step) based on the detected complexity of an incoming query. It uses a small language model as a classifier to route queries, avoiding the computational overhead of complex multi-step reasoning for simple questions while ensuring sufficient depth for difficult ones. This is a primary optimization for **Operational Efficiency** (by reducing average Time-per-Query) and **Output Quality** (by matching retrieval depth to the question's reasoning requirements).

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** Significantly improves Time-per-Query efficiency by bypassing retrieval for straightforward questions. Average classification time is only 0.35s (for 'No Retrieval' labels).
- **Token Usage:** Reduces token consumption by avoiding unnecessary retrieval-augmented prompts for simple queries, thereby lowering API costs and resource usage.
- **Throughput:** By allocating resources dynamically, the framework enhances the overall concurrency and scalability of RAG systems in production.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** F1 Score, Exact Match (EM), and Accuracy.
- **Benchmark Data:** Evaluated on SQuAD v1.1, Natural Questions (NQ), TriviaQA, MuSiQue, HotpotQA, and 2WikiMultiHopQA.
- **Judge Setup:** Uses a T5-Large model as a query-complexity classifier, trained on automatically collected silver labels from model outcomes and dataset inductive biases.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** Adaptive-RAG provides a balanced strategy that matches the accuracy of expensive multi-step approaches while maintaining the speed of single-step or no-retrieval models on simpler tasks. It effectively eliminates the "one-size-fits-all" bottleneck of static RAG pipelines.
- **Chunking Strategy:** N/A (Focuses on dynamic orchestration between single and multi-step retrieval loops).
- **Enterprise Suitability:** Highly suitable for industrial RAG systems that encounter a high volume of varied user requests, offering a principled way to manage the performance-efficiency trade-off.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Extensive testing across LLM sizes (FLAN-T5-XL to XXL and GPT-3.5) and varied retrieval datasets.
- **Bias Detection:** Addresses the "overconfidence" bias in standard retrievers by correctly identifying when parametric knowledge is sufficient or when deeper non-parametric evidence is required.

### 🔍 Technical Implementation
- **Portability:** The classifier is a small standalone model (e.g., T5 or BERT-based). The routing logic is model-agnostic and can be integrated into any Spring AI workflow as a pre-processing router.
- **Key References:** 
  - Mallen et al., 2023 (Frequency-based adaptive retrieval)
  - Trivedi et al., 2023 (IRCoT)
  - Asai et al., 2024 (Self-RAG)

### 💡 Key Takeaway for Thesis
Adaptive-RAG proves that query complexity is the critical lever for optimizing RAG systems, demonstrating that a multi-tiered pipeline is essential for achieving both industrial scale and reasoning depth.

### 🔗 BibTeX Reference (Target: /bib)
```bibtex
@inproceedings{jeong2024adaptiverag,
  title={Adaptive-{RAG}: Learning to Adapt Retrieval-Augmented Large Language Models through Question Complexity},
  author={Jeong, Soyeong and Baek, Jinheon and Cho, Sukmin and Hwang, Sung Ju and Park, Jong C.},
  booktitle={Proceedings of the 2024 Conference of the North American Chapter of the Association for Computational Linguistics: Human Language Technologies (Volume 1: Long Papers)},
  pages={7036--7050},
  year={2024}
}
```