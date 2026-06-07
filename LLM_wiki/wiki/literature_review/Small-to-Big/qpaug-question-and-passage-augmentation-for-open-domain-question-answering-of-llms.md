# QPaug: Question and Passage Augmentation for Open-Domain Question Answering of LLMs
**Authors:** Minsang Kim, Cheoneum Park, and Seungjun Baek | **Year:** 2024
**Category:** Small-to-Big

### 📝 Abstract & Relevance Summary
Traditional Retrieval-Augmented Generation (RAG) readers rely heavily on the quality of retrieved contexts, which can degrade if search queries are ambiguous or if the corpus is noisy. The authors propose **QPaug** (Question and Passage Augmentation), a simple yet effective prompting framework that integrates parametric and non-parametric knowledge. QPaug first decomposes the query into sub-questions and plans using zero-shot Chain of Thought (CoT) to retrieve more specific contexts. It then generates a fact-based passage using the LLM's own parametric knowledge to augment the retrieved documents. This approach addresses **Output Quality** by providing richer context for answer extraction, while maintaining **Operational Efficiency** by keeping the total context window size consistent during generation.

### 🚀 Efficiency & Cost Metrics (RQ1 & RQ2)
- **Latency Impact:** QPaug adds two pre-generation LLM inference steps (one for question decomposition/planning, and one for passage self-generation). This increases Time to First Token (TTFT) and total query latency compared to direct retrieval pipelines.
- **Cost & Token Usage:** To control reader prompt token overhead, QPaug replaces one retrieved passage with the self-generated passage, setting retrieval size to **K=9** retrieved documents + **1** self-generated passage (totaling 10 documents, matching the baseline). Token consumption increases only by the length of the decomposition plan and the self-generated passage.
- **Throughput:** Requires multiple LLM calls per query, which can reduce throughput on high-concurrency servers.

### 🏆 Quality & Accuracy Metrics (RQ3)
- **Quality Metrics:** Evaluated using **F1 Score** and **ROUGE-L** to compare predictions against target answers. Answer **Recall@10** of retrieved passages is used to evaluate retrieval quality.
- **Benchmark Data:** Evaluated on three open-domain QA (ODQA) datasets: **Natural Questions (NQ)** (4,289 samples), **2wiki** (12,576 samples), and **HotpotQA** (7,405 samples). The external retrieval database consists of the **21-million passage** Wikipedia dump from DPR.
- **Judge Setup:** Standard evaluation on gold target answers using GPT-3.5, GPT-4, and LLaMA-2-7b-chat.

### ⚖️ The Trade-off Analysis
- **Efficiency-Quality Equilibrium:** On Contriever and GPT-3.5, QPaug improves the F1 score to **44.6%** on NQ (vs. 40.4% for SuRE), **35.5%** on 2wiki (vs. 32.6% for SuRE), and **45.1%** on HotpotQA (vs. 33.6% for SuRE). This represents an increase of up to +34% in accuracy at the cost of two extra LLM prompt calls.
- **Pareto Frontier & Combinations:** Question augmentation (Qaug) achieves the highest relative recall gains at smaller retrieval budgets (e.g. $K=10$ vs. $K=100$), as precise query formulations are critical when context limits are tight.
- **Chunking/Routing Strategy:** Appends a zero-shot CoT ("Let's think step-by-step") plan to the original question for retrieval: $\hat{q} = q \oplus q_{aug}$. For generation, retrieved passages are augmented with the generated passage labeled as `"Your knowledge: [Generated Passage]"`.
- **Enterprise Suitability:** Suitable for handling complex, multi-hop queries. Features a safety fallback mechanism: the LLM is explicitly instructed to generate `[NONE]` if it lacks factual knowledge, preventing hallucinations from contaminating the context.

### 📊 Methodology & Bias Note
- **Evaluation Framework:** Evaluated using the LangChain library and FAISS indexing for Approximate Maximum Inner Product Search (MIPS).
- **Bias Detection:** Grounding the generated passage with `"Your knowledge:"` prevents hallucinated content from overriding the retrieved context, mitigating the LLM's self-preference bias.

### 🔍 Technical Implementation
- **Portability:** High portability. The method is fully language-agnostic and easily implemented in Java/Spring AI since it only requires standard prompt templates and string concatenation.
- **Key References:**
  - *Zero-Shot CoT:* Kojima et al., 2022 (Large Language Models are Zero-Shot Reasoners)
  - *DPR:* Karpukhin et al., 2020 (Dense Passage Retrieval for Open-Domain Question Answering)
  - *SuRE:* Kim et al., 2023b (Improving Open-Domain QA of LLMs via Summarized Retrieval)

### 💡 Key Takeaway for Thesis
- QPaug demonstrates that combining query decomposition (CoT) and parametric passage self-generation (with fallback) boosts ODQA retrieval precision and answer F1 score by up to 34% within a constant token budget.

### 🔗 BibTeX Reference (Target: /LLM_wiki/bib)
```bibtex
@article{kim2024qpaug,
  title={QPaug: Question and Passage Augmentation for Open-Domain Question Answering of LLMs},
  author={Kim, Minsang and Park, Cheoneum and Baek, Seungjun},
  journal={arXiv preprint arXiv:2406.14277v2},
  year={2024}
}
```
