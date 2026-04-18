# LongLLMLingua: Accelerating and Enhancing LLMs in Long Context Scenarios via Prompt Compression
**Authors:** Huiqiang Jiang, Qianhui Wu, Xufang Luo, Dongsheng Li, Chin-Yew Lin, Yuqing Yang, Lili Qiu | **Year:** 2024
**Category:** Prompt Compression

### 📝 Abstract & Relevance Summary
LongLLMLingua addresses the challenges of computational cost, performance reduction, and position bias in long-context LLM scenarios. It introduces a question-aware coarse-to-fine compression method, document reordering, and dynamic compression ratios to improve information density. The approach also features a subsequence recovery strategy to ensure the integrity of key entities. Evaluation across multiple benchmarks shows significant improvements in performance while drastically reducing costs and latency. This paper is highly relevant for RAG efficiency as it provides a robust method for managing large context windows without sacrificing accuracy.

### 🚀 Latency Optimization (Speed)
- **Method:** LongLLMLingua prompt compression.
- **Technical Implementation:** Question-aware coarse-to-fine compression, document reordering, and dynamic compression ratios.
- **Results:** Accelerates end-to-end latency by 1.4x-2.6x when compressing prompts of about 10k tokens at ratios of 2x-6x.

### 📉 Token Efficiency (Cost & Context)
- **Method:** Prompt compression using a small language model (LLaMA-2-7B-Chat) for perplexity evaluation.
- **Token Reduction:** Boosts performance with around 4x fewer tokens in GPT-3.5-Turbo; achieves 94.0% cost reduction in the LooGLE benchmark.
- **Impact on Accuracy:** Enhances performance by up to 21.4% in NaturalQuestions; outperforms original prompts in terms of accuracy despite compression.

### 🛠 Architecture Overview
- **Pipeline Stage:** Pre-processing (Prompt Compression)
- **Models Used:** GPT-3.5-Turbo, LongChat-13B, LLaMA-2-7B-Chat (small model for compression)
- **Hardware Context:** Tested on NVIDIA V100-32G.

### 💡 Key Takeaway for Thesis
LongLLMLingua demonstrates that prompt compression not only reduces costs but can actually improve RAG accuracy by filtering noise and mitigating position bias, making it a critical technique for efficient long-context processing.

### 🔗 BibTeX Reference (Target: /bib)
```bibtex
@inproceedings{jiang-etal-2024-longllmlingua,
    title = "{L}ong{LLML}ingua: Accelerating and Enhancing {LLM}s in Long Context Scenarios via Prompt Compression",
    author = "Jiang, Huiqiang  and
      Wu, Qianhui  and
      Luo, Xufang  and
      Li, Dongsheng  and
      Lin, Chin-Yew  and
      Yang, Yuqing  and
      Qiu, Lili",
    booktitle = "Proceedings of the 62nd Annual Meeting of the Association for Computational Linguistics (Volume 1: Long Papers)",
    month = aug,
    year = "2024",
    address = "Bangkok, Thailand",
    publisher = "Association for Computational Linguistics",
    url = "https://aclanthology.org/2024.acl-long.91",
    pages = "1658--1677",
}
```
