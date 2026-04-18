# Don’t Break the Cache: An Evaluation of Prompt Caching for Long-Horizon Agentic Tasks
**Authors:** Elias Lumer, Faheem Nizar, Akshaya Jangiti, Kevin Frank, Anmol Gulati, Mandar Phadate, Vamse Kumar Subbiah | **Year:** 2026
**Category:** Prompt Compression / Caching

### 📝 Abstract & Relevance Summary
This paper presents a comprehensive evaluation of prompt caching for long-horizon agentic tasks across three major LLM providers: OpenAI, Anthropic, and Google. It compares three caching strategies—full context caching, system prompt only caching, and caching that excludes dynamic tool results—using the DeepResearch Bench. The study demonstrates that strategic prompt cache block control significantly reduces API costs by 41-80% and improves time to first token (TTFT) by 13-31%. The authors highlight that naive full context caching can paradoxically increase latency, and they provide guidance on maintaining stable system prompt prefixes to maximize efficiency in production agentic systems. This is an essential read for anyone implementing RAG at scale.

### 🚀 Latency Optimization (Speed)
- **Method:** Strategic Prompt Cache Block Control.
- **Technical Implementation:** Reusing previously computed Key-Value (KV) tensors from attention layers to skip prefill computation on repeated prompt prefixes.
- **Results:** Improves Time to First Token (TTFT) by 13-31% across providers; GPT-4o showed up to 60% improvement (4,290ms to 1,699ms) at 50,000 tokens.

### 📉 Token Efficiency (Cost & Context)
- **Method:** Prefix Caching and Boundary Control.
- **Token Reduction:** Reduces API costs by 41-80% through reduced pricing for cached input tokens; universal linear cost benefits observed for prompts up to 50,000 tokens.
- **Impact on Accuracy:** Accuracy is maintained as the full prompt is still processed; however, the study emphasizes that stable prefixes are required to prevent cache misses.

### 🛠 Architecture Overview
- **Pipeline Stage:** Generation (Inference optimization)
- **Models Used:** GPT-5.2, Claude Sonnet 4.5, Gemini 2.5 Pro, GPT-4o.
- **Hardware Context:** Benchmarked against enterprise cloud APIs using models with context windows up to 50k tokens.

### 💡 Key Takeaway for Thesis
This paper provides empirical proof that prompt caching is the single most effective way to reduce operational costs in RAG systems, provided that prompt engineering is used to keep dynamic content (like search results) at the end of the context window to preserve the cached prefix.

### 🔗 BibTeX Reference (Target: /bib)
```bibtex
@article{lumer2026dont,
  title={Don't Break the Cache: An Evaluation of Prompt Caching for Long-Horizon Agentic Tasks},
  author={Lumer, Elias and Nizar, Faheem and Jangiti, Akshaya and Frank, Kevin and Gulati, Anmol and Phadate, Mandar and Subbiah, Vamse Kumar},
  journal={arXiv preprint arXiv:2601.06007},
  year={2026}
}
```
