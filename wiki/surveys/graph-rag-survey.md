# Graph Retrieval-Augmented Generation for Large Language Models: A Survey
**Authors:** Tyler Thomas Procko, Omar Ochoa | **Year:** 2024
**Category:** Survey

### 📝 Abstract & Relevance Summary
This survey explores Graph Retrieval-Augmented Generation (Graph RAG), which integrates Knowledge Graphs (KGs) into the RAG pipeline to provide structured, noise-free context. It addresses the "noisy RAG" problem where relevant documents contain irrelevant information that obfuscates the desired snippet. The paper categorizes Graph RAG research into two perspectives: using extant KGs and generating task-specific KGs from corpora. This is relevant to the thesis as it suggests a more precise retrieval method that could potentially reduce token costs (RQ2) and improve reasoning accuracy compared to traditional vector-based RAG.

### 🔍 Research Context (Surveys & Background)
- **Foundational Concepts:** Knowledge Graphs (KGs), Textual Graphs, and KGQA (Knowledge Graph Question Answering).
- **Taxonomy:** Archetypes include KG-enhanced LLMs, LLM-augmented KGs, and Synergized LLMs+KGs. Distinguishes between Hard Prompts (explicit text) and Soft Prompts (implicit embeddings).
- **Snowballing Leads:** G-Retriever [26], GRAG [24], TRACE [8], GNN-RAG [32], and the seminal GraphRAG by Edge et al. [25].

### 📊 Evaluation Data & Bias Note
- **Datasets Used:** WebQuestionsSP, Complex WebQuestions, HotPotQA, CommonsenseQA, OpenBookQA.
- **AI-Evaluation:** Uses LLMs to generate KG nodes and edges, as well as to summarize graph communities.
- **Bias Assessment:** Notes that increased document noise significantly worsens RAG efficacy, advocating for structured knowledge as a bias mitigation strategy.

### 💡 Key Takeaway for Thesis
Graph RAG provides a pathway to high-precision retrieval that minimizes "noise-induced" token wastage, offering a structured alternative to improve both accuracy and token efficiency (RQ2).

### 🔗 BibTeX Reference (Target: /bib)
```bibtex
@inproceedings{procko2024graph,
  title={Graph Retrieval-Augmented Generation for Large Language Models: A Survey},
  author={Procko, Tyler Thomas and Ochoa, Omar},
  booktitle={2024 Conference on AI, Science, Engineering, and Technology (AIxSET)},
  pages={166--169},
  year={2024},
  organization={IEEE}
}
```
