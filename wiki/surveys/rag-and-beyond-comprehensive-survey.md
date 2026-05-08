# Retrieval-Augmented Generation (RAG) and Beyond: A Comprehensive Survey on How to Make your LLMs use External Data More Wisely
**Authors:** Siyun Zhao, Yuqing Yang, Zilong Wang, Zhiyuan He, Luna K. Qiu, Lili Qiu | **Year:** 2024
**Category:** Survey

### 📝 Abstract & Relevance Summary
This comprehensive survey categorizes RAG tasks into four levels of complexity: explicit facts, implicit facts, interpretable rationales, and hidden rationales. It provides a "bird's-eye view" of data-augmented LLM applications, identifying unique challenges and effective techniques for each level. The paper discusses three main forms of integrating external data: context (RAG), small models, and fine-tuning, highlighting their respective strengths and limitations. This fits the thesis scope by providing a foundational taxonomy to evaluate RAG efficiency, specifically how different architectures impact latency (RQ1) and token costs (RQ2) based on the query complexity level.

### 🔍 Research Context (Surveys & Background)
- **Foundational Concepts:** RAG (Retrieval-Augmented Generation), Fine-tuning, and Data-augmented LLM applications. Defines the task as mapping Q to A based on external data D.
- **Taxonomy:** Stratifies queries into four levels: Level-1 Explicit Facts, Level-2 Implicit Facts, Level-3 Interpretable Rationales, and Level-4 Hidden Rationales.
- **Snowballing Leads:** NQ (Natural Questions) [15], MS MARCO [16], TriviaQA [17], SQuAD [18], HotPotQA [21], 2WikiMultiHopQA [22], MuSiQue [23], StrategyQA [25].

### 📊 Evaluation Data & Bias Note
- **Datasets Used:** Extensive list including Natural Questions, MS MARCO, TriviaQA, SQuAD, HotPotQA, StrategyQA, and many others (Table 1).
- **AI-Evaluation:** Discusses LLM-based evaluation of retrieved blocks (e.g., Self-RAG [92]) and using LLMs to evaluate credibility.
- **Bias Assessment:** Highlights that fine-tuning on new factual data can lead to "mechanical memorization" and a "reversal curse," where models fail to generalize learned facts, potentially increasing hallucinations.

### 💡 Key Takeaway for Thesis
This paper provides the essential Level 1-4 taxonomy used to structure the comparative analysis of RAG efficiency methods, helping to determine the optimal balance between retrieval complexity and generation quality for different RAG pipeline stages.

### 🔗 BibTeX Reference (Target: /bib)
```bibtex
@article{zhao2024rag,
  title={Retrieval-Augmented Generation (RAG) and Beyond: A Comprehensive Survey on How to Make your LLMs use External Data More Wisely},
  author={Zhao, Siyun and Yang, Yuqing and Wang, Zilong and He, Zhiyuan and Qiu, Luna K. and Qiu, Lili},
  journal={arXiv preprint arXiv:2409.14924},
  year={2024}
}
```
