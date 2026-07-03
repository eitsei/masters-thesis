# Retrieval-Augmented Generation (RAG) and Large Language Models (LLMs) for Enterprise Knowledge Management and Document Automation: A Systematic Literature Review
**Authors:** Ehlullah Karakurt, Akhan Akbulut | **Year:** 2025
**Category:** Survey

### 📝 Abstract & Relevance Summary
This systematic literature review (SLR) analyzes 63 primary studies evaluating the deployment of Retrieval-Augmented Generation (RAG) and Large Language Models (LLMs) in enterprise knowledge management and document automation. The authors identify a significant "lab-to-market" gap, noting that while 93.6% of studies employ academic k-fold cross-validation for retrieval evaluation, only 13% conduct real-world case studies or field trials. Additionally, 63.6% of implementations rely on GPT-based models and 80.5% use standard retrieval frameworks like FAISS or Elasticsearch. The review outlines key enterprise challenges including data privacy, latency under load, and factual consistency. This paper is highly relevant to establishing the baseline of enterprise adoption trends and identifying standard evaluation metrics.

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** Latency and scalability are highlighted as major deployment hurdles in 31.7% of the reviewed papers. A practical guideline is identified where real-world production environments target end-to-end execution latencies below 100 ms, forcing a reliance on lighter, less accurate bi-encoders or hybrid sparse-dense indexes over heavy cross-encoders.
- **Cost & Token Usage:** Cloud-native deployments represent 66.7% of configurations, leveraging API-based services. On-premises deployments (19.0%) avoid API costs but encounter high local infrastructure and GPU hosting overhead. Custom industrial datasets (17.4% of studies) require heavy manual data cleaning.
- **Throughput:** Throughput limitations are noted under high concurrency, making asynchronous processing, model quantization, and prompt caching vital directions for scaling.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** The review categorizes metrics as follows: Classic retrieval/classification metrics (81.0%), Recall@K/Precision@K (73.0%), ROUGE/BLEU generation scores (44.4%), Human Evaluation (19.0%), and Business Impact Metrics (15.9%).
- **Benchmark Data:** 54.0% of studies utilize open-source GitHub datasets, 17.4% use custom industrial corpora, 15.9% use proprietary repositories, and 12.7% use standard academic benchmarks (such as BEIR).
- **Judge Setup:** Studies increasingly incorporate automated frameworks such as RAGAS and ARES, alongside human judges to grade factuality and business utility.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** Enterprise systems face a trade-off where complex rerankers improve quality but degrade latency. The survey reports that bi-encoders or sparse filters are preferred for sub-200 ms query boundaries.
- **Pareto Frontier & Combinations:** A combination of RAG Sequence architecture (46.8% of cases) or RAG Token (36.4%) with hybrid dense-sparse indexing yields the strongest quality-efficiency balance.
- **Chunking/Routing Strategy:** Dense vector indexing (80.5%) is frequently combined with sparse filters like BM25 (55%) or Knowledge Graphs (24%) to boost context precision.
- **Enterprise Suitability:** Real-world case studies show RAG systems can reduce manual document editing times by 30%–50% and increase customer service issue resolution rates by 25%.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Standard RAG evaluation frameworks like RAGAS and ARES are highlighted.
- **Bias Detection:** The authors warn of data leakage and concept drift in static evaluation sets. Live operational testing remains critical to identify synthetic-to-real performance gaps.

### 🔍 Technical Implementation
- **Portability:** Language-agnostic principles. Platforms are predominantly cloud-native (66.7%) or on-premises (19.0%). The design patterns (hybrid retrieval, caching, and model cascading) are fully implementable in Java/Spring AI.
- **Key References:**
  - Lewis et al. (2020) Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks
  - Es et al. (2024) RAGAS: Automated Evaluation of Retrieval Augmented Generation
  - Chen et al. (2024) Benchmarking LLMs in RAG

### 💡 Key Takeaway for Thesis
- This survey maps the practical "lab-to-market" landscape of RAG, highlighting that while cloud-based GPT models dominate, the core challenge lies in matching the quality of complex academic architectures under strict E2E latency constraints (<100 ms) and security rules.

### 🔗 BibTeX Reference (Target: /LLM_wiki/bib)
```bibtex
@article{karakurt2025retrieval,
  title={Retrieval-Augmented Generation (RAG) and Large Language Models (LLMs) for Enterprise Knowledge Management and Document Automation: A Systematic Literature Review},
  author={Karakurt, Ehlullah and Akbulut, Akhan},
  journal={Applied Sciences},
  volume={16},
  number={1},
  pages={368},
  year={2025},
  publisher={MDPI}
}
```
