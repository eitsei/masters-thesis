# Graph-Based Approaches and Functionalities in Retrieval-Augmented Generation: A Comprehensive Survey
**Authors:** Zulun Zhu, Tiancheng Huang, Kai Wang, Junda Ye, Xinghe Chen, Siqiang Luo | **Year:** 2026
**Category:** Survey

### 📝 Abstract & Relevance Summary
This comprehensive survey broadens the definition of graph-based RAG by systematically investigating how graph data management enhances every stage of the RAG pipeline—from knowledge base construction to topological prompting. It categorizes graph techniques across databases (existing KGs vs. text-generated), algorithms (deterministic, probabilistic, learning-based), and pipeline topologies (sequential, loop, tree). This is highly relevant to the thesis as it provides a detailed analysis of the accuracy-latency trade-offs (RQ1) and token budget constraints (RQ2) inherent in structured vs. unstructured retrieval. The paper identifies that "Topological RAG" can significantly mitigate the "noisy context" problem, leading to more efficient and explainable multi-hop reasoning.

### 🔍 Research Context (Surveys & Background)
- **Foundational Concepts:** Knowledge Graphs (KGs), Topological RAG, Open Information Extraction (OpenIE), and Graph-Structure Prompting.
- **Taxonomy:** Categorizes retrieval algorithms into **Deterministic** (e.g., Dijkstra), **Probabilistic** (e.g., PageRank), and **Heuristic-based** (e.g., K-hop sampling). Pipeline types include Sequential, Loop, and Tree.
- **Snowballing Leads:** GraphRAG [49], G-Retriever [74], HippoRAG [68], Graph-COT [94], and MedGraphRAG [193].

### 📊 Evaluation Data & Bias Note
- **Datasets Used:** 38 benchmark datasets organized by task (KGQA, Graph Tasks, Domain-specific).
- **AI-Evaluation:** Discusses the use of instruction-tuned LMs as "KG builders" and controllers for multi-step graph exploration.
- **Bias Assessment:** Uses **Self-Consistency Rate (SCR)** and **Rationale Consistency** to detect hallucination risks, noting that structured grounding inherently reduces the model's vulnerability to noise-induced bias.

### 💡 Key Takeaway for Thesis
Graph-enhanced RAG offers a structured alternative to "naive" vector RAG that optimizes the information density of context windows, directly supporting the goal of high-precision, token-efficient generation (RQ2).

### 🔗 BibTeX Reference (Target: /bib)
```bibtex
@article{zhu2026graph,
  title={Graph-Based Approaches and Functionalities in Retrieval-Augmented Generation: A Comprehensive Survey},
  author={Zhu, Zulun and Huang, Tiancheng and Wang, Kai and Ye, Junda and Chen, Xinghe and Luo, Siqiang},
  journal={ACM Computing Surveys},
  volume={58},
  number={10},
  pages={Article 261},
  year={2026},
  publisher={ACM}
}
```
