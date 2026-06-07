# QPaug: Question and Passage Augmentation for Open-Domain Question Answering of LLMs
**Authors:** Minsang Kim, Cheoneum Park, Seungjun Baek | **Year:** 2024
**Category:** Small-to-Big

### 📝 Abstract & Relevance Summary
QPaug (Question and Passage Augmentation) leverages the parametric knowledge of LLMs to enhance the retrieval process for complex open-domain queries. The framework decomposes original questions into fine-grained sub-questions (Qaug) and self-generates relevant passages (Pgen) prior to or alongside the standard retrieval step. This approach harmonizes parametric memory with non-parametric retrieval to overcome ambiguous queries and noisy contexts. While it trades off some operational efficiency by requiring upfront LLM calls, it drastically improves context precision and final output quality.

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** Increases TTFT and overall latency due to the multi-step nature of the pipeline (LLM Question Augmentation $\rightarrow$ Retrieval $\rightarrow$ LLM Passage Generation $\rightarrow$ Final Generation).
- **Token Usage:** Consumes higher prompt and completion tokens upfront to expand the question and synthesize passages. 
- **Throughput:** Due to the chained LLM dependencies, raw query throughput is lower compared to standard single-shot RAG.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Exact Match (EM), F1 Score, and ROUGE-L.
- **Benchmark Data:** Evaluated on multi-hop and single-hop open-domain QA tasks: Natural Questions (NQ), 2WikiHop (2wiki), and HotpotQA.
- **Judge Setup:** Standard lexical overlap metrics (ROUGE-L and F1).

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** QPaug explicitly trades operational efficiency (increased latency and token cost from extra LLM calls) for massive leaps in output quality (e.g., up to 34.2% performance gain on HotpotQA). It is an optimization geared heavily towards context precision over speed.
- **Chunking Strategy:** Tested retrieval limits spanning $K=10$ to $K=100$ passages derived from 100-word Wikipedia chunks. Augmentation showed the most significant gains at larger $K$ sizes (50-100).
- **Enterprise Suitability:** Best suited for complex, high-stakes enterprise queries where simple retrieval fails. To optimize costs in production, it would be best deployed conditionally (e.g., routed only when a query is determined to be ambiguous or multi-hop).

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Extensive ablation studies combining multiple retrievers (SBERT, ANCE, Contriever) with varied LLMs (GPT-4, GPT-3.5, LLaMA-2).
- **Bias Detection:** Addressed the risk of LLM hallucination in self-generated passages by explicitly prompting the model to output `[NONE]` if it lacked factual certainty, ensuring false parametric data did not corrupt the retrieved context.

### 🔍 Technical Implementation
- **Portability:** The methodology is fully prompt-based (utilizing zero-shot Chain-of-Thought prompting), meaning it is entirely model- and language-agnostic. It can be effortlessly orchestrated using Spring AI's chat clients and prompt templates.
- **Key References:** 
  - Kojima et al., 2022 (Chain-of-Thoughts)
  - Asai et al., 2023 (Self-RAG)

### 💡 Key Takeaway for Thesis
QPaug demonstrates that strategically front-loading token expenditure and latency to augment questions and passages via LLMs yields massive dividends in context precision and output quality for complex RAG tasks.