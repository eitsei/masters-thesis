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
        assert op["latency"]["p50TotalLatencyMs"] == 150.0
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


class TestUnanswerableAndAbstentionHandling:
    @pytest.fixture
    def mixed_records(self):
        return [
            {
                "questionId": "ans_1",
                "question": "What is the capital of Finland?",
                "generatedAnswer": "Helsinki is the capital of Finland.",
                "groundTruthAnswer": "Helsinki",
                "retrievedChunks": [{"id": "c1", "text": "Helsinki is Finland's capital."}],
                "totalLatencyMs": 120,
                "answerable": True,
            },
            {
                "questionId": "ans_2",
                "question": "What is the capital of Sweden?",
                "generatedAnswer": "Stockholm is the capital of Sweden.",
                "groundTruthAnswer": "Stockholm",
                "retrievedChunks": [{"id": "c2", "text": "Stockholm is Sweden's capital."}],
                "totalLatencyMs": 180,
                "answerable": True,
            },
            {
                "questionId": "ood__nordea_001",
                "question": "How do I build a rocket to Mars?",
                "generatedAnswer": "The provided context does not contain sufficient information to answer this query.",
                "groundTruthAnswer": "The provided context does not contain sufficient information to answer this query.",
                "retrievedChunks": [{"id": "c3", "text": "Nordea banking information."}],
                "totalLatencyMs": 80,
                "answerable": False,
            },
            {
                "questionId": "musique_unans_002",
                "question": "Who was the first Martian astronaut?",
                "generatedAnswer": "Neil Armstrong was on Mars in 1969.",
                "groundTruthAnswer": "I am sorry, but the context does not provide sufficient information.",
                "retrievedChunks": [{"id": "c4", "text": "Space exploration notes."}],
                "totalLatencyMs": 90,
                "answerable": False,
            },
        ]

    def test_unanswerable_samples_isolated_from_ragas(self, tmp_path, mixed_records):
        mock_eval_result = MagicMock()
        mock_eval_result.to_pandas.return_value = pd.DataFrame({
            "faithfulness": [1.0, 0.8],
            "answer_relevancy": [0.9, 0.85],
            "context_precision": [0.95, 0.90],
            "context_recall": [1.0, 0.80],
        })

        evaluator = RagEvaluator(llm=MagicMock(), embeddings=MagicMock())

        with patch("src.evaluator.evaluate", return_value=mock_eval_result) as mock_evaluate:
            summary = evaluator.evaluate_records(
                records=mixed_records,
                run_name="mixed_run",
                output_dir=tmp_path,
                save_output=True,
            )

        # 1. RAGAS evaluate should have been called ONCE, and ONLY with the 2 answerable samples
        assert mock_evaluate.call_count == 1
        call_kwargs = mock_evaluate.call_args[1] if mock_evaluate.call_args[1] else {}
        if not call_kwargs and mock_evaluate.call_args[0]:
            dataset_arg = mock_evaluate.call_args[0][0]
        else:
            dataset_arg = call_kwargs.get("dataset")

        assert len(dataset_arg) == 2
        assert dataset_arg[0].user_input == "What is the capital of Finland?"
        assert dataset_arg[1].user_input == "What is the capital of Sweden?"

        # 2. Overall counts in summary
        assert summary["totalSamples"] == 4
        assert summary["answerableSamples"] == 2
        assert summary["unanswerableSamples"] == 2

        # 3. Quality scores: composite quality computed only from answerable, refusal accuracy and FRR computed
        scores = summary["qualityScores"]
        assert scores["faithfulness"] == 0.9  # (1.0 + 0.8) / 2
        assert scores["answer_relevancy"] == 0.875
        assert scores["context_precision"] == 0.925
        assert scores["context_recall"] == 0.9
        assert scores["composite_quality"] > 0.0

        # Abstention quality metrics:
        # unanswerable count = 2. 1 refused ("ood__nordea_001"), 1 hallucinated ("musique_unans_002") => 50%
        assert scores["refusal_accuracy"] == 0.5
        # answerable count = 2. Neither refused => 0%
        assert scores["false_refusal_rate"] == 0.0

        # 4. Abstention in operationalMetrics
        abstention = summary["operationalMetrics"]["abstention"]
        assert abstention["answerableCount"] == 2
        assert abstention["unanswerableCount"] == 2
        assert abstention["refusalAccuracy"] == 0.5
        assert abstention["falseRefusalRate"] == 0.0

        # 5. Median P50 latency in operationalMetrics
        # Latencies: [120, 180, 80, 90] -> sorted: [80, 90, 120, 180] -> median = 105.0
        assert summary["operationalMetrics"]["latency"]["p50TotalLatencyMs"] == 105.0

        # 6. Per-sample inspection
        samples = summary["perSample"]
        assert len(samples) == 4

        # Sample 0 (answerable)
        assert samples[0]["isUnanswerable"] is False
        assert samples[0]["isRefusal"] is False
        assert samples[0]["compositeQuality"] is not None
        assert "faithfulness" in samples[0]["scores"]

        # Sample 1 (answerable)
        assert samples[1]["isUnanswerable"] is False
        assert samples[1]["isRefusal"] is False
        assert samples[1]["compositeQuality"] is not None

        # Sample 2 (unanswerable, correctly refused)
        assert samples[2]["isUnanswerable"] is True
        assert samples[2]["isRefusal"] is True
        assert samples[2]["scores"] == {}
        assert samples[2]["compositeQuality"] is None

        # Sample 3 (unanswerable, failed refusal)
        assert samples[3]["isUnanswerable"] is True
        assert samples[3]["isRefusal"] is False
        assert samples[3]["scores"] == {}
        assert samples[3]["compositeQuality"] is None

    def test_all_unanswerable_records_skips_ragas(self, tmp_path):
        records = [
            {
                "questionId": "ood__1",
                "question": "What is Foo?",
                "generatedAnswer": "The provided context does not contain sufficient information to answer this query.",
                "groundTruthAnswer": "The provided context does not contain sufficient information to answer this query.",
                "totalLatencyMs": 50,
                "answerable": False,
            },
            {
                "questionId": "ood__2",
                "question": "What is Bar?",
                "generatedAnswer": "The provided context does not contain sufficient information to answer this query.",
                "groundTruthAnswer": "The provided context does not contain sufficient information to answer this query.",
                "totalLatencyMs": 60,
                "answerable": False,
            },
        ]

        evaluator = RagEvaluator(llm=MagicMock(), embeddings=MagicMock())

        with patch("src.evaluator.evaluate") as mock_evaluate:
            summary = evaluator.evaluate_records(
                records=records,
                run_name="all_unans_run",
                output_dir=tmp_path,
                save_output=False,
            )

        # evaluate should never be called when answerable count is 0
        mock_evaluate.assert_not_called()

        assert summary["totalSamples"] == 2
        assert summary["answerableSamples"] == 0
        assert summary["unanswerableSamples"] == 2
        assert summary["qualityScores"]["composite_quality"] == 0.0
        assert summary["qualityScores"]["refusal_accuracy"] == 1.0
        assert summary["qualityScores"]["false_refusal_rate"] == 0.0
        assert summary["operationalMetrics"]["latency"]["p50TotalLatencyMs"] == 55.0

    def test_false_refusal_rate_when_answerable_incorrectly_refuses(self, tmp_path):
        records = [
            {
                "questionId": "ans_1",
                "question": "Legitimate question?",
                "generatedAnswer": "I am sorry, but the provided context does not contain sufficient information to answer this query.",
                "groundTruthAnswer": "Actual factual answer",
                "totalLatencyMs": 100,
                "answerable": True,
            },
            {
                "questionId": "ans_2",
                "question": "Another legitimate question?",
                "generatedAnswer": "Legitimate answer.",
                "groundTruthAnswer": "Legitimate answer.",
                "totalLatencyMs": 110,
                "answerable": True,
            },
        ]

        mock_eval_result = MagicMock()
        mock_eval_result.to_pandas.return_value = pd.DataFrame({
            "faithfulness": [0.0, 1.0],
            "answer_relevancy": [0.0, 1.0],
            "context_precision": [0.0, 1.0],
            "context_recall": [0.0, 1.0],
        })

        evaluator = RagEvaluator(llm=MagicMock(), embeddings=MagicMock())
        with patch("src.evaluator.evaluate", return_value=mock_eval_result):
            summary = evaluator.evaluate_records(records=records, save_output=False)

        # 1 out of 2 answerable queries gave a refusal => false refusal rate = 0.5
        assert summary["operationalMetrics"]["abstention"]["falseRefusalRate"] == 0.5
        assert summary["perSample"][0]["isRefusal"] is True
        assert summary["perSample"][1]["isRefusal"] is False

