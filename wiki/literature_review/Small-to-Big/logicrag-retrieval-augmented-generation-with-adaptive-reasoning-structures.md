# LogicRAG: Retrieval Augmented Generation with Adaptive Reasoning Structures
**Authors:** Shengyuan Chen, Chuang Zhou, Zheng Yuan, et al. | **Year:** 2026
**Category:** Small-to-Big

### 📝 Abstract & Relevance Summary
LogicRAG is an inference-time framework that dynamically extracts reasoning structures to guide RAG without requiring pre-built, costly graphs. It decomposes complex queries into interdependent sub-problems and models their dependencies as a Directed Acyclic Graph (DAG), which is then linearized via topological sort for logically consistent execution. This approach addresses the high token cost and update latency of GraphRAG while offering better flexibility for varied logic structures. It is highly relevant to **Operational Efficiency** (by avoiding the massive pre-construction costs of graphs) and **Output Quality** (by ensuring logically sound, step-by-step multi-hop reasoning).

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** Eliminates the hundreds of minutes of offline graph construction time common in methods like Microsoft GraphRAG. Inference latency remains competitive with flat RAG models (average 9.83s on complex 2WikiMQA queries).
- **Token Usage:** Reduces token costs through context pruning (via a compressed rolling memory) and graph pruning (merging similar sub-problems), significantly lowering the total generation overhead.
- **Throughput:** Maintains high throughput in dynamically evolving knowledge bases, as it requires no static indexing of relationships.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** String-based Accuracy and LLM-based Accuracy.
- **Benchmark Data:** MuSiQue, 2WikiMultiHopQA, and HotpotQA.
- **Judge Setup:** Uses GPT-4o-mini to decide whether the generated answer correctly matches the gold answer.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** LogicRAG outperforms all baselines by a significant margin (e.g., achieving 64.7% accuracy on 2WikiMQA, a +14.7% absolute improvement over HippoRAG2). It provides the precision of graph-based reasoning at a fraction of the total token and time cost.
- **Chunking Strategy:** Dynamically adapts based on previously retrieved and inferred sub-answers, enabling context-aware retrieval with linear complexity.
- **Enterprise Suitability:** Ideal for production environments with large-scale or rapidly changing corpora where pre-building a knowledge graph is computationally or logistically prohibitive.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Extensive comparison against state-of-the-art graph-based RAG models (RAPTOR, HippoRAG, GraphRAG).
- **Bias Detection:** Investigates the "hesitation" issue and human cognitive bias in LLM-driven reasoning, using self-reflection to correct early retrieval failures.

### 🔍 Technical Implementation
- **Portability:** Fully prompt-based structure-aware inference. Highly compatible with Java/Spring AI as an orchestrator managing sub-task execution and context rolling memory.
- **Key References:** 
  - Edge et al., 2024 (Microsoft GraphRAG)
  - Gutierrez et al., 2024 (HippoRAG)
  - Sarthi et al., 2024 (RAPTOR)

### 💡 Key Takeaway for Thesis
LogicRAG demonstrates that the "Graph" in GraphRAG should be a dynamic reasoning structure extracted at inference time, not a static index, to achieve the optimal equilibrium of flexibility, quality, and efficiency.

### 🔗 BibTeX Reference (Target: /bib)
```bibtex
@inproceedings{chen2026logicrag,
  title={{LogicRAG}: Retrieval Augmented Generation with Adaptive Reasoning Structures},
  author={Chen, Shengyuan and Zhou, Chuang and Yuan, Zheng and Zhang, Qinggang and Cui, Zeyang and Chen, Hao and Xiao, Yilin and Cao, Jiannong and Huang, Xiao},
  booktitle={The Fortieth AAAI Conference on Artificial Intelligence (AAAI-26)},
  year={2026}
}
```