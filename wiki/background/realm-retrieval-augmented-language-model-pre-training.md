# REALM: Retrieval-Augmented Language Model Pre-Training
**Authors:** Kelvin Guu, Kenton Lee, Zora Tung, Panupong Pasupat, Ming-Wei Chang | **Year:** 2020
**Category:** Background

### 📝 Abstract & Relevance Summary
REALM is a pioneering framework that integrates a latent knowledge retriever directly into the language model pre-training process. Unlike models that store knowledge implicitly in parameters, REALM retrieves documents from a large corpus (e.g., Wikipedia) to assist in predicting masked tokens during pre-training. The authors demonstrate that this retriever can be trained in an unsupervised manner using the masked language modeling objective, with gradients backpropagating through a MIPS-based retrieval step. This fits the thesis by establishing how "Modular Knowledge" improves performance on knowledge-intensive tasks while being more interpretable and parameter-efficient than monolithic LLMs, directly addressing the trade-offs between parameter scale and retrieval efficiency (RQ1, RQ2).

### 🔍 Research Context (Surveys & Background)
- **Foundational Concepts:** Latent knowledge retriever, Maximum Inner Product Search (MIPS), and Asynchronous Index Refreshing.
- **Taxonomy:** Modular vs. Implicit knowledge storage. Categorizes the approach as a "retrieve-then-predict" generative process.
- **Snowballing Leads:** BERT [Devlin et al., 2018], ORQA [Lee et al., 2019], kNN-LM [Khandelwal et al., 2019], and T5 [Raffel et al., 2019].

### 📊 Evaluation Data & Bias Note
- **Datasets Used:** Natural Questions-Open, WebQuestions, and CuratedTrec.
- **AI-Evaluation:** Uses "Retrieval Utility" (RU) as a metric to measure how much a retrieved document improves the log-likelihood of correct token prediction.
- **Bias Assessment:** Identifies "Trivial Retrievals" (where the model finds the exact source string) as a source of bias that must be filtered to ensure semantic learning.

### 💡 Key Takeaway for Thesis
REALM proves that retrievers can be optimized during the pre-training phase using standard MLM signals, offering a scalable and modular way to keep LLMs up-to-date without full re-training.

### 🔗 BibTeX Reference (Target: /bib)
```bibtex
@inproceedings{guu2020realm,
  title={REALM: Retrieval-augmented language model pre-training},
  author={Guu, Kelvin and Lee, Kenton and Tung, Zora and Pasupat, Panupong and Chang, Ming-Wei},
  booktitle={International Conference on Machine Learning},
  pages={3929--3938},
  year={2020},
  organization={PMLR}
}
```
