# Searching for Best Practices in Retrieval-Augmented Generation
**Authors:** Xiaohua Wang, Zhenghua Wang, Xuan Gao, Feiran Zhang, Yixin Wu, Zhibo Xu, Tianyuan Shi, Zhengyuan Wang, Shizheng Li, Qi Qian, Ruicheng Yin, Changze Lv, Xiaoqing Zheng, Xuanjing Huang | **Year:** 2024
**Category:** Survey

### 📝 Abstract & Relevance Summary
This paper systematically investigates existing Retrieval-Augmented Generation (RAG) approaches and their potential combinations to identify optimal practices. By exploring a modular workflow consisting of query classification, chunking, vector databases, retrieval, reranking, repacking, summarization, and generator fine-tuning, the authors perform extensive experiments to map the efficiency and performance of RAG configurations. The findings are highly relevant to the balance of operational efficiency and output quality, showing that strategic selection of components can significantly reduce processing latency while maintaining or even improving factual generation performance.

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** The introduction of a Query Classification module leads to a substantial latency reduction, lowering average execution times from 16.41 seconds to 11.58 seconds per query by avoiding retrieval for "sufficient" queries. In retrieval, while "Hybrid Search with HyDE" yields high quality, it takes 11.71 seconds per query; switching to a standard "Hybrid" search (original embedding + BM25) cuts retrieval latency to 1.45 seconds with minimal loss in accuracy.
- **Cost & Token Usage:** Prompt compression via Recomp reduces context length, directly cutting down token costs and generation time. Generator fine-tuning with a mixed context (gold and random documents) makes the model robust to irrelevant context, allowing it to ignore noise without needing expensive pre-filtering steps.
- **Throughput:** Not explicitly quantified in terms of TPS, but the optimized pipeline paths directly enhance overall system throughput by reducing compute-heavy LLM calls and context lengths.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Evaluated using RAGAS capabilities (Faithfulness, Context Relevancy, Answer Relevancy, Answer Correctness), Retrieval Similarity (cosine similarity to gold documents), token-level F1, Exact Match (EM), and task-specific Accuracy (Acc).
- **Benchmark Data:** Evaluated on Commonsense Reasoning, Fact Checking (FEVER), Open-Domain QA (NQ, TriviaQA), MultiHop QA (HotpotQA, MuSiQue), and Medical QA (PubMedQA).
- **Judge Setup:** GPT-3.5-turbo was used to score the open-ended QA datasets (ODQA and MultiHop) concurrently.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** The Query Classification module achieves a win-win, raising the average RAG score from 0.428 to 0.443 while cutting latency by ~30%. However, using HyDE in Hybrid retrieval represents a strict trade-off: it increases quality (RAG score 0.58 vs 0.49 for hybrid without HyDE) but increases retrieval time by nearly 8x.
- **Pareto Frontier & Combinations:** The authors propose two main recipes:
  1. *Max Performance Recipe:* Query Classification + Hybrid Search with HyDE + monoT5 reranking + Reverse repacking + Recomp summarization (Avg Score: 0.483, High Latency).
  2. *Balanced Recipe:* Query Classification + Hybrid Search (no HyDE) + TILDEv2 reranking + Reverse repacking + Recomp summarization (Comparable quality with dramatically lower latency).
- **Chunking/Routing Strategy:** Evaluated token/sentence/semantic chunking. The study utilizes sentence-level chunking with a child chunk size of 175 tokens and a parent chunk size of 512 tokens (overlap of 20 tokens). Sliding window techniques achieved 97.41% faithfulness and 96.85% relevancy.
- **Enterprise Suitability:** The modularity and availability of the balanced recipe make this highly suitable for enterprise systems requiring real-time responses under 2 seconds.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Uses RAGAS framework and custom sub-sampling (up to 500 examples per dataset) to measure retrieval and generation capabilities.
- **Bias Detection:** RAG metrics were calculated using LLM judges, which may inherit model-specific grading biases.

### 🔍 Technical Implementation
- **Portability:** Highly portable to a Java/Spring AI stack since it defines a modular architecture using standard databases (like Milvus) and APIs.
- **Key References:** 
  - Gao et al., 2022 (HyDE)
  - Xu et al., 2023 (Recomp)
  - Jiang et al., 2023 (LongLLMLingua)

### 💡 Key Takeaway for Thesis
- This survey establishes a clear Pareto frontier for RAG optimization, proving that query classification and hybrid retrieval without query expansion (HyDE) are essential for achieving sub-second latency in production without compromising accuracy.

### 🔗 BibTeX Reference (Target: /LLM_wiki/bib)
```bibtex
@inproceedings{wang2024searching,
  author    = {Xiaohua Wang and Zhenghua Wang and Xuan Gao and Feiran Zhang and Yixin Wu and Zhibo Xu and Tianyuan Shi and Zhengyuan Wang and Shizheng Li and Qi Qian and Ruicheng Yin and Changze Lv and Xiaoqing Zheng and Xuanjing Huang},
  title     = {Searching for Best Practices in Retrieval-Augmented Generation},
  booktitle = {Proceedings of the 2024 Conference on Empirical Methods in Natural Language Processing},
  pages     = {17716--17736},
  year      = {2024},
  publisher = {Association for Computational Linguistics}
}
```
