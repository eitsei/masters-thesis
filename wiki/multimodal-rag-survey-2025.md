# A Survey on Multimodal Retrieval-Augmented Generation
**Authors:** Lang Mei, Siyu Mo, Zhihan Yang, Chong Chen | **Year:** 2025
**Category:** Indexing / Pipeline Survey

### 📝 Abstract & Relevance Summary
This survey provides a comprehensive review of Multimodal Retrieval-Augmented Generation (MRAG), which integrates text, images, and videos into the RAG framework. It categorizes the evolution of MRAG from 1.0 (pseudo-MRAG using captions) to 3.0 (true end-to-end multimodality with document screenshots). The paper details essential components like multimodal parsing, search planning, retrieval, and generation while highlighting technologies that improve accuracy and reduce hallucinations. It also systematically reviews datasets and evaluation metrics, identifying current limitations and proposing future research directions for efficient multimodal information acquisition. This paper is a foundational reference for understanding the scalability and efficiency trade-offs in multimodal RAG pipelines.

### 🚀 Latency Optimization (Speed)
- **Method:** Multimodal Search Planning and Adaptive Planning.
- **Technical Implementation:** Dynamic retrieval strategy selection, query reformulation, and atomic sub-query decomposition to minimize unnecessary searches.
- **Results:** Optimization of resource utilization and response time by avoiding compulsive multimodal retrieval when textual information suffices.

### 📉 Token Efficiency (Cost & Context)
- **Method:** Prompt Refinement (Hard and Soft Prompting), Visual Token Compression (e.g., PyramidDrop, G-Search).
- **Token Reduction:** Visual token reduction via pruning redundant image tokens; specific techniques like PyramidDrop partition MLLMs to drop image tokens at various stages.
- **Impact on Accuracy:** Enhances accuracy by filtering noise and ensuring cross-modal consistency; supports higher-quality responses by utilizing refined textual representations alongside original data.

### 🛠 Architecture Overview
- **Pipeline Stage:** Entire Pipeline (Parsing, Indexing, Search Planning, Retrieval, Generation)
- **Models Used:** Various MLLMs (e.g., GPT-4o, Gemini 1.5 Pro, LLaVA, BLIP-2), CLIP, Vision Transformers (ViTs).
- **Hardware Context:** Discusses architectures applicable to high-performance compute environments (e.g., NVIDIA A100).

### 💡 Key Takeaway for Thesis
This survey establishes MRAG as the next frontier for RAG efficiency, proving that intelligent search planning and visual token compression are essential for scaling RAG to multimodal contexts without linear cost increases.

### 🔗 BibTeX Reference (Target: /bib)
```bibtex
@article{mei2025survey,
  title={A Survey on Multimodal Retrieval-Augmented Generation},
  author={Mei, Lang and Mo, Siyu and Yang, Zhihan and Chen, Chong},
  journal={arXiv preprint arXiv:2504.08748},
  year={2025}
}
```
