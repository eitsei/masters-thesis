# DeepSieve: Information Sieving via LLM-as-a-Knowledge-Router
**Authors:** Minghao Guo, Qingcheng Zeng, Xujiang Zhao, Yanchi Liu, Wenchao Yu, Mengnan Du, Haifeng Chen, Wei Cheng | **Year:** 2026
**Category:** Routing

### 📝 Abstract & Relevance Summary
Large Language Models (LLMs) often struggle with knowledge-intensive queries due to static parameter limitations and noisy retrieval. While Retrieval-Augmented Generation (RAG) mitigates this, existing methods lack fine-grained control over both the query and source sides, resulting in shallow reasoning and poor adaptability to heterogeneous, unmergeable knowledge sources. To solve this, the authors introduce DeepSieve, a modular RAG framework that incorporates a multi-stage information sieving process via an LLM-as-a-knowledge-router. DeepSieve decomposes complex queries into structured sub-queries (DAG of subgoals), routes each sub-query to its most appropriate (Tool, Corpus) pair based on domain-specific profiles, refines answers iteratively through a reflection loop, and fuses the answers. DeepSieve addresses output quality (by isolating reasoning steps and routing to specialized sources) and operational efficiency (by dramatically reducing token usage compared to conventional agentic RAG models).

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** DeepSieve reduces overall latency by executing sub-queries along the DAG path structure and resolving them with minimal tokens. Because it routes queries directly to appropriate specialized databases (such as SQL tables or specific search APIs) rather than searching a flat, massive text index, it avoids the retrieval latency and post-processing delay of reading irrelevant documents.
- **Cost & Token Usage:** DeepSieve achieves high accuracy while using significantly fewer tokens. On HotpotQA, it averages only **3.9K tokens** per query, compared to ReAct's **9.8K tokens** (a **~60% reduction**) and Reflexion's **37.9K tokens** (a **~90% reduction**). The routing step adds negligible token overhead, while the decomposition step consumes 550-780 tokens.
- **Throughput:** By reducing LLM token generation requirements by 60% to 90% compared to agentic alternatives, DeepSieve enables much higher request throughput and lowers execution costs.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Evaluated using Exact Match (EM) and F1 Score.
- **Benchmark Data:** Evaluated on three multi-hop QA benchmarks: MuSiQue, 2WikiMultiHopQA, and HotpotQA.
- **Judge Setup:** Evaluated directly using ground truth answers (EM/F1) from benchmarks rather than using an LLM-as-a-judge.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** On average, DeepSieve (Naive RAG) achieves the highest F1 score (58.9) with DeepSeek-V3 and 51.2 with GPT-4o, outperforming all pure RAG and agentic RAG baselines. On HotpotQA, DeepSieve achieves 49.3 EM and 61.7 F1 using only 3.9K tokens, showing that structuring the reasoning pipeline with decomposition and routing yields superior quality *and* efficiency compared to expensive recursive self-reflection methods.
- **Pareto Frontier & Combinations:** Demonstrates a highly optimized trade-off (largest enclosed area on F1-EM-Efficiency radar plots). Combining Decomposition, Routing, and Reflexion (D+Rt+Rf) consistently yields the best results across heterogeneous datasets.
- **Chunking/Routing Strategy:** Input queries are decomposed into a DAG of sub-queries. The LLM router dispatches each sub-query to the most appropriate source profile (e.g. Wiki, SQL Database, API) and retrieves documents using specialized tools.
- **Enterprise Suitability:** Highly suitable for enterprises. DeepSieve avoids the requirement to merge incompatible or private database indexes (which is often impossible due to privacy, cost, or format constraints) by dynamically routing sub-queries to separate isolated data silos (SQL, RAG, Web Search) and fusing answers at the end.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Standard EM/F1 metrics on multi-hop QA datasets.
- **Bias Detection:** Evaluated against objective dataset labels, avoiding the subjective bias of LLM judges.

### 🔍 Technical Implementation
- **Portability:** High portability to Java/Spring AI. The system is modular and relies on orchestration logic (decomposition, routing via LLM, reflexion check, and final fusion). It can be implemented in Spring AI using multiple distinct `VectorStore` instances and routing query strings based on semantic router classifications.
- **Key References:**
  1. *ReAct*: Yao, S., et al. (2023). ReAct: Synergizing Reasoning and Acting in Language Models.
  2. *Reflexion*: Shinn, N., et al. (2023). Reflexion: Language Agents with Active Reinforcement Learning.
  3. *HippoRAG*: Gutierrez, B., et al. (2024). HippoRAG: Collaboratively recalling knowledge from databases.

### 💡 Key Takeaway for Thesis
- DeepSieve shows that using an LLM router to dispatch sub-queries to specialized heterogeneous sources achieves state-of-the-art accuracy on multi-hop QA while reducing token consumption by up to 90% compared to typical agentic architectures.

### 🔗 BibTeX Reference (Target: /LLM_wiki/bib)
```bibtex
@inproceedings{guo2026deepsieve,
  title={DeepSieve: Information Sieving via {LLM}-as-a-Knowledge-Router},
  author={Guo, Minghao and Zeng, Qingcheng and Zhao, Xujiang and Liu, Yanchi and Yu, Wenchao and Du, Mengnan and Chen, Haifeng and Cheng, Wei},
  booktitle={Findings of the Association for Computational Linguistics: EACL 2026},
  pages={3054--3077},
  year={2026}
}
```
