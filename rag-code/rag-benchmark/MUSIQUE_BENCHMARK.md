# MuSiQue Benchmark Guide

This guide describes how to explore the MuSiQue multi-hop question-answering dataset, ingest its corpus into pgvector, and evaluate baseline/optimized RAG pipeline performance.

---

## 📋 Overview

The [MuSiQue](https://arxiv.org/abs/2108.01073) dataset is designed for multi-hop reasoning (2-hop, 3-hop, and 4-hop questions) where disconnected evidence paragraphs must be retrieved and synthesized to arrive at the final answer.

In `rag-benchmark`, MuSiQue is integrated to evaluate retrieval quality and reasoning capabilities across the optimization modules (M0–M4). All modules M0–M4 are executable across the full factorial experiment matrix ($C_0$–$C_{15}$).

---

## 🚀 Quickstart Workflow

### 1. Check Dataset Statistics
Verify the local dataset status and question counts by hop depth:

```bash
curl -s http://localhost:8080/api/benchmark/musique/stats | jq .
```

*Example Response:*
```json
{
  "totalQuestions": 2417,
  "twoHopCount": 1252,
  "threeHopCount": 760,
  "fourHopCount": 405,
  "canonicalSampleSize": 100,
  "datasetPath": "data/musique/musique_ans_v1.0_dev.jsonl"
}
```

---

### 2. Ingest Dataset Paragraphs into pgvector

Before running queries, populate the vector store tables with paragraphs. The ingestion pipeline supports composite ingestion (`mode: original` runs both standard baseline and Small-to-Big hierarchical chunking) as well as table clearing (`clearDatabase=true`):

#### Method A: Using the CLI Helper Script (Recommended)
```bash
# Ingest canonical 100 questions into both baseline and S2B tables with database reset:
./scripts/ingest_musique.sh original true

# Ingest without resetting tables:
./scripts/ingest_musique.sh original false

# Ingest only baseline table or only S2B tables:
./scripts/ingest_musique.sh baseline true
./scripts/ingest_musique.sh s2b true
```

#### Method B: Triggering via REST API (`POST /api/ingest/musique` or `/api/benchmark/musique/ingest`)
```bash
# Reset database and ingest both baseline and S2B (canonical 100 questions):
curl -X POST "http://localhost:8080/api/ingest/musique?mode=original&clearDatabase=true" \
  -H "Content-Type: application/json"

# Ingest full dataset (~2,400 questions) into both baseline and S2B tables:
curl -X POST http://localhost:8080/api/ingest/musique \
  -H "Content-Type: application/json" \
  -d '{"mode": "original", "clearDatabase": true, "sampleOnly": false}'
```

**Target Tables Populated:**
- `vector_store_musique`: Standard baseline paragraphs (~1,600 deduplicated Wikipedia passages).
- `vector_store_musique_large`: Small-to-Big parent context chunks (1024 tokens).
- `vector_store_musique_small`: Small-to-Big child search chunks (128 tokens, linked to parent).

---

### 3. Browse Questions & Ground Truth

#### List Questions with Filtering & Pagination
```bash
# Get 5 questions from the 2-hop pool
curl -s "http://localhost:8080/api/benchmark/musique/questions?hops=2&limit=5" | jq .

# Get questions from the canonical 100-sample
curl -s "http://localhost:8080/api/benchmark/musique/questions?sample=true&limit=10" | jq .
```

#### Fetch the Canonical 100-Question Benchmark Sample (Thesis §4.2.1)
```bash
curl -s "http://localhost:8080/api/benchmark/musique/sample" | jq .
```

#### Get Full Question Details by ID
```bash
curl -s "http://localhost:8080/api/benchmark/musique/questions/2hop__390055_82341" | jq .
```

---

### 4. Run Benchmark Query & Evaluate Against Ground Truth

Execute a RAG pipeline query for a specific question ID:

```bash
curl -X POST http://localhost:8080/api/benchmark/musique/questions/2hop__390055_82341/query | jq .
```

#### Response Structure:
```json
{
  "questionId": "2hop__390055_82341",
  "question": "Where in florida did Rosalie King die?",
  "groundTruthAnswer": "in Northern Florida",
  "groundTruthAliases": [
    "Northern Florida"
  ],
  "hopCount": 2,
  "generatedAnswer": "Ocala, Florida",
  "modulesApplied": [],
  "retrievedChunks": [
    {
      "id": "fd15e7ea-a247-3eed-9692-e8e90e11a364",
      "snippet": "Rosalie King\n\nRosalie King (born Rosa Lea Frances Stephens... died June 11, 1997, Ocala, Florida)..."
    },
    {
      "id": "8462abe6-650f-3a57-b958-b90015cc2007",
      "snippet": "Ocala, Florida\n\nOcala (/ oʊ ˈkælə / oh - KAL - ə) is a city located in Northern Florida..."
    }
  ],
  "timings": {
    "retrievalMs": 110,
    "generationMs": 3500
  },
  "expectedSupportingTitles": [
    "Rosalie King",
    "Ocala, Florida"
  ],
  "supportingEvidenceFound": true
}
```

---

### 5. Ad-Hoc Freeform Queries

You can also pass arbitrary question text to the general query endpoint:

```bash
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{"query": "Where in florida did Rosalie King die?"}'
```

---

## 🛠 REST API Reference

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/benchmark/musique/stats` | Dataset hop breakdown, sample sizes, and file paths. |
| `GET` | `/api/benchmark/musique/sample` | 100-question canonical evaluation sample (stratified 40% 2-hop, 20% 3-hop, 20% 4-hop, 20% unanswerable). |
| `GET` | `/api/benchmark/musique/questions` | List questions with query parameters (`offset`, `limit`, `hops`, `sample`). |
| `GET` | `/api/benchmark/musique/questions/{id}` | Full detail for a single question including decomposition steps and paragraphs. |
| `POST` | `/api/benchmark/musique/ingest` | Ingest corpus into pgvector (`mode`: `original`/`baseline`/`s2b`, `clearDatabase`: `true`/`false`). |
| `POST` | `/api/ingest/musique` | Alias endpoint matching `/api/ingest/enterprise-closed` convention. |
| `POST` | `/api/benchmark/musique/questions/{id}/query` | Evaluate pipeline on question `{id}`, comparing generated answer to ground truth. |
| `POST` | `/api/query` | Standard RAG pipeline execution for arbitrary text queries. |

---

## ⚙️ Configuration Properties

The following properties can be tuned in `src/main/resources/application.yml`:

```yaml
rag:
  dataset:
    musique:
      file-path: data/musique/musique_ans_v1.0_dev.jsonl
      sample-size: 100
      two-hop-ratio: 0.40
      three-hop-ratio: 0.30
      four-hop-ratio: 0.30
      random-seed: 42
      batch-size: 100
      include-title-in-chunk-text: true
```
