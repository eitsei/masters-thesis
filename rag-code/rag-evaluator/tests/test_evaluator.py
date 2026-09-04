"""Unit tests for the RAGAS evaluator and quality aggregator."""

import json
from unittest.mock import MagicMock, patch
import numpy as np
import pandas as pd
import pytest
from ragas import EvaluationDataset

from src.evaluator import RagEvaluator, evaluate_run, jsonl_to_ragas_dataset


class TestDatasetConversion:
    def test_jsonl_to_ragas_dataset_standard_fields(self):
        records = [
            {
                "questionId": "q1",
                "question": "What is M1?",
                "generatedAnswer": "Small-to-big retrieval.",
                "groundTruthAnswer": "Small-to-big hierarchical chunking.",
                "retrievedChunks": [
                    {"id": "c1", "text": "M1 is small to big chunking.", "snippet": "M1 chunking"}
                ],
            },
            {
                "questionId": "q2",
                "question": "What is M3?",
                "generatedAnswer": "Prompt caching.",
                "groundTruthAnswer": "Context caching.",
                "retrievedChunks": [
                    {"id": "c2", "snippet": "Snippet without text field"}
                ],
            },
        ]

        dataset = jsonl_to_ragas_dataset(records)
        assert isinstance(dataset, EvaluationDataset)
        assert len(dataset) == 2

        # Check sample 0
        s0 = dataset[0]
        assert s0.user_input == "What is M1?"
        assert s0.response == "Small-to-big retrieval."
        assert s0.reference == "Small-to-big hierarchical chunking."
        assert s0.retrieved_contexts == ["M1 is small to big chunking."]

        # Check sample 1 (fallback to snippet if text missing)
        s1 = dataset[1]
        assert s1.retrieved_contexts == ["Snippet without text field"]

    def test_jsonl_to_ragas_dataset_string_contexts_and_alternatives(self):
        records = [
            {
                "user_input": "Alternative key question",
                "response": "Alternative key answer",
                "reference": "Alternative key reference",
                "retrieved_contexts": ["Context 1", "Context 2"],
            }
        ]
        dataset = jsonl_to_ragas_dataset(records)
        assert len(dataset) == 1
        s0 = dataset[0]
        assert s0.user_input == "Alternative key question"
        assert s0.response == "Alternative key answer"
        assert s0.retrieved_contexts == ["Context 1", "Context 2"]


class TestEvaluatorPipeline:
    @pytest.fixture
    def mock_eval_result(self):
        res = MagicMock()
        df = pd.DataFrame({
            "faithfulness": [0.8, 1.0],
            "answer_relevancy": [0.9, 0.7],
            "context_precision": [0.85, 0.95],
            "context_recall": [0.75, 0.85],
        })
        res.to_pandas.return_value = df
        return res

    @pytest.fixture
    def sample_records(self):
        return [
            {
                "questionId": "q1",
                "question": "Question 1",
                "generatedAnswer": "Answer 1",
                "groundTruthAnswer": "Truth 1",
                "retrievedChunks": [{"id": "c1", "text": "chunk text 1"}],
                "totalLatencyMs": 100,
                "timings": {"retrievalMs": 30, "generationMs": 70},
                "tokenUsage": {
                    "promptTokens": 1000,
                    "completionTokens": 100,
                    "cachedTokens": 400,
                    "totalTokens": 1100,
                },
                "supportingEvidenceFound": True,
            },
            {
                "questionId": "q2",
                "question": "Question 2",
                "generatedAnswer": "Answer 2",
                "groundTruthAnswer": "Truth 2",
                "retrievedChunks": [{"id": "c2", "text": "chunk text 2"}],
                "totalLatencyMs": 200,
                "timings": {"retrievalMs": 50, "generationMs": 150},
                "tokenUsage": {
                    "promptTokens": 1000,
                    "completionTokens": 150,
                    "cachedTokens": 0,
                    "totalTokens": 1150,
                },
                "supportingEvidenceFound": False,
            },
        ]

    def test_composite_quality_and_aggregates_computation(
        self, tmp_path, mock_eval_result, sample_records
    ):
        mock_llm = MagicMock()
        mock_emb = MagicMock()

        evaluator = RagEvaluator(llm=mock_llm, embeddings=mock_emb)

        with patch("src.evaluator.evaluate", return_value=mock_eval_result):
            summary = evaluator.evaluate_records(
                records=sample_records,
                run_name="pipeline_test",
                output_dir=tmp_path,
                save_output=True,
            )

        assert summary["runName"] == "pipeline_test"
        assert summary["totalSamples"] == 2

        # Check quality scores
        scores = summary["qualityScores"]
        assert scores["faithfulness"] == 0.9  # (0.8 + 1.0) / 2
        assert scores["answer_relevancy"] == 0.8  # (0.9 + 0.7) / 2
        assert scores["context_precision"] == 0.9  # (0.85 + 0.95) / 2
        assert round(scores["composite_quality"], 2) == 0.84  # Regularized harmonic mean per Thesis Eq. 238

        # Check aggregates
        assert "faithfulness" in summary["qualityAggregates"]
        assert summary["qualityAggregates"]["faithfulness"]["min"] == 0.8
        assert summary["qualityAggregates"]["faithfulness"]["max"] == 1.0

        # Check operational metrics
        op = summary["operationalMetrics"]
        assert op["latency"]["meanTotalLatencyMs"] == 150.0  # (100 + 200) / 2
        assert op["latency"]["meanRetrievalMs"] == 40.0
        assert op["latency"]["meanGenerationMs"] == 110.0

        assert op["tokenUsage"]["totalPromptTokens"] == 2000
        assert op["tokenUsage"]["totalCompletionTokens"] == 250
        assert op["tokenUsage"]["totalCachedTokens"] == 400
        # cacheHitRate: 400 / (2000 + 400) = 400 / 2400 ≈ 0.1667
        assert round(op["tokenUsage"]["cacheHitRate"], 4) == 0.1667

        assert op["supportingEvidence"]["evidenceFoundCount"] == 1
        assert op["supportingEvidence"]["evidenceFoundRate"] == 0.5

        # Check JSON output persisted
        saved_file = tmp_path / "pipeline_test_evaluation.json"
        assert saved_file.exists()
        with open(saved_file) as f:
            persisted = json.load(f)
        assert persisted["runName"] == "pipeline_test"

    def test_evaluate_file_integration(
        self, tmp_path, mock_eval_result, sample_records
    ):
        jsonl_path = tmp_path / "test_file_run.jsonl"
        with open(jsonl_path, "w") as f:
            for r in sample_records:
                f.write(json.dumps(r) + "\n")

        mock_llm = MagicMock()
        mock_emb = MagicMock()

        with patch("src.evaluator.evaluate", return_value=mock_eval_result):
            summary = evaluate_run(
                jsonl_path=jsonl_path,
                output_dir=tmp_path,
                llm=mock_llm,
                embeddings=mock_emb,
                save_output=True,
            )

        assert summary["runName"] == "test_file_run"
        assert summary["totalSamples"] == 2
        assert (tmp_path / "test_file_run_evaluation.json").exists()

    def test_empty_records_raises_error(self):
        evaluator = RagEvaluator(llm=MagicMock(), embeddings=MagicMock())
        with pytest.raises(ValueError, match="Cannot evaluate empty records"):
            evaluator.evaluate_records([])
