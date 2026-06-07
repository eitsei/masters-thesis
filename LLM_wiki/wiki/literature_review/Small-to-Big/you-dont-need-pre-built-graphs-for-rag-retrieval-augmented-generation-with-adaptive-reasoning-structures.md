# You Don't Need Pre-built Graphs for RAG: Retrieval Augmented Generation with Adaptive Reasoning Structures
**Authors:** Shengyuan Chen, Chuang Zhou, Zheng Yuan, Qinggang Zhang, Zeyang Cui, Hao Chen, Yilin Xiao, Jiannong Cao, and Xiao Huang | **Year:** 2026
**Category:** Small-to-Big

### 📝 Abstract & Relevance Summary
Graph-based RAG (GraphRAG) systems model connections between documents to answer complex queries, but they suffer from high token costs and update latencies due to expensive offline graph construction. Furthermore, pre-constructed graphs are static and may not align with the reasoning structures required for varied, real-world queries. The authors propose **LogicRAG**, a framework that dynamically extracts query reasoning structures at inference time without any pre-built graphs. LogicRAG decomposes queries into subproblems, represents their logical dependencies as a directed acyclic graph (DAG), linearizes the DAG via topological sorting, and executes retrieval sequentially. It balances **Operational Efficiency** (saving token cost and update time) and **Output Quality** (accuracy in multi-hop QA) through two-dimensional pruning (graph pruning and context pruning via rolling memory).

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** LogicRAG achieves an average query execution time of **9.83 seconds** on the 2WikiMQA dataset. While flat retrieval methods (like VanillaRAG) are faster at query time (**4.28s**), LogicRAG requires **0 seconds** of offline pre-indexing or graph construction. In contrast, baseline GraphRAG methods require tens to hundreds of minutes of offline preprocessing before any query can be run.
- **Cost & Token Usage:** LogicRAG consumes an average of **1,777.9 tokens** per query on 2WikiMQA. This is a **62.2% reduction** compared to Microsoft GraphRAG (4,699.8 tokens) and an **84.0% reduction** compared to KGP (11,097.8 tokens). 
- **Throughput:** Eliminates the computational bottleneck of offline graph index building, enabling instantaneous updates in dynamic, large-scale database environments.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Evaluated using **String-based accuracy** (checking if the normalized gold answer is in the generation) and **LLM-based accuracy** (using a model to judge answer equivalence).
- **Benchmark Data:** 1,000 validation questions extracted from three multi-hop QA benchmarks: **MuSiQue**, **2WikiMultiHopQA (2WikiMQA)**, and **HotpotQA**.
- **Judge Setup:** **GPT-4o-mini** was used as the generator, reader, and LLM judge for evaluation.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** On 2WikiMQA, LogicRAG achieves **64.7% string accuracy** (outperforming the next best graph baseline, HippoRAG2, which achieves 50.0%). On HotpotQA, it scores **54.8% string accuracy** and **62.6% LLM-judged accuracy**. LogicRAG trades off a slight query-time latency increase (5.5s slower than flat retrieval) for a +14.7% absolute gain in accuracy and massive offline token savings.
- **Pareto Frontier & Combinations:** A Pareto frontier of efficiency vs. accuracy is analyzed across different retrieval parameters ($k$). Sibling subproblems are merged using graph pruning to reduce retrieval rounds. Context pruning uses LLM-based summarization to build a rolling memory, preventing context bloat: `Mem(i) = Summarize(Mem(i-1) U R(p(i)))`.
- **Chunking/Routing Strategy:** Decomposes complex queries into subproblems. Resolves sibling subqueries at the same topological rank in parallel using a single unified query `q_uni = Merge(S_i)` to optimize the retrieval-to-generation ratio. Retrieves contexts with $k=3$ (the optimal trade-off).
- **Enterprise Suitability:** Extremely well-suited for enterprise production because it eliminates index update lag entirely, allowing the RAG system to reflect document changes instantly without rebuilding expensive knowledge graphs.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Implemented using standard LlamaIndex and custom agentic frameworks.
- **Bias Detection:** Analyzed agentic "hesitation bias" (where an LLM repeatedly generates similar subqueries when uncertain). LogicRAG mitigates this by using **sampling without replacement**, forcing progression to the next subproblem rank.

### 🔍 Technical Implementation
- **Portability:** Fully portable to a Java/Spring AI stack. The logic relies on query decomposition prompts, standard Topological Sort (DFS traversal), and context rolling memory, all of which are easily implemented in Java.
- **Key References:**
  - *GraphRAG:* Edge et al., 2024 (From Local to Global: A Graph RAG Approach to Query-Focused Summarization)
  - *HippoRAG:* Gutierrez et al., 2024 (Neurobiologically Inspired Long-Term Memory for LLMs)
  - *LightRAG:* Guo et al., 2024 (Simple and Fast Retrieval-Augmented Generation)

### 💡 Key Takeaway for Thesis
- LogicRAG proves that dynamically decomposing queries into logical DAGs at query-time matches the retrieval accuracy of graph-based RAG while reducing query token consumption by 62% and eliminating the massive offline graph-building bottleneck.

### 🔗 BibTeX Reference (Target: /LLM_wiki/bib)
```bibtex
@inproceedings{chen2026logicrag,
  title={{LogicRAG}: Retrieval Augmented Generation with Adaptive Reasoning Structures},
  author={Chen, Shengyuan and Zhou, Chuang and Yuan, Zheng and Zhang, Qinggang and Cui, Zeyang and Chen, Hao and Xiao, Yilin and Cao, Jiannong and Huang, Xiao},
  booktitle={The Fortieth AAAI Conference on Artificial Intelligence (AAAI-26)},
  year={2026}
}
```
