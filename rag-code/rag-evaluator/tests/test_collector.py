"""Unit tests for streaming benchmark collector."""

import json
from unittest.mock import MagicMock
import pytest
from rich.console import Console

from src.client import RagClient
from src.collector import BenchmarkCollector, collect_run


@pytest.fixture
def mock_client():
    client = MagicMock(spec=RagClient)
    client.query_question.side_effect = lambda question_id, **kwargs: {
        "questionId": question_id,
        "question": f"Question text for {question_id}?",
        "groundTruthAnswer": "Ground truth",
        "generatedAnswer": f"Answer for {question_id}",
        "modulesApplied": ["M0"],
        "retrievedChunks": [
            {"id": f"chunk-{question_id}-1", "snippet": "snippet 1", "text": "full text 1"}
        ],
        "timings": {"retrievalMs": 45, "generationMs": 120},
        "totalLatencyMs": 165,
        "tokenUsage": {
            "promptTokens": 500,
            "completionTokens": 50,
            "cachedTokens": 200,
            "totalTokens": 550,
        },
        "tableNameUsed": "vector_store_test",
        "expectedSupportingTitles": ["Title A"],
        "supportingEvidenceFound": True,
    }
    client.get_canonical_sample.return_value = [
        {"id": "canon-1", "question": "Canonical 1?"},
        {"id": "canon-2", "question": "Canonical 2?"},
    ]
    return client


class TestWarmupQueries:
    def test_warmup_queries_executed_unmeasured(self, tmp_path, mock_client):
        collector = BenchmarkCollector(
            client=mock_client,
            output_dir=tmp_path,
            debug=False,
            console=Console(quiet=True),
        )
        questions = [{"id": "q1"}, {"id": "q2"}]

        records = collector.collect(
            run_name="warmup_test",
            questions=questions,
            warmup_queries=2,
            resume=False,
        )

        # 2 warmups + 2 real questions = 4 calls total
        assert mock_client.query_question.call_count == 4

        # Output JSONL should ONLY contain the 2 collected records, not the warmups
        run_file = tmp_path / "warmup_test.jsonl"
        assert run_file.exists()
        with open(run_file) as f:
            lines = [json.loads(l) for l in f if l.strip()]
        assert len(lines) == 2
        assert len(records) == 2

    def test_zero_warmup_queries(self, tmp_path, mock_client):
        collector = BenchmarkCollector(
            client=mock_client,
            output_dir=tmp_path,
            debug=False,
            console=Console(quiet=True),
        )
        questions = [{"id": "q1"}]

        collector.collect(
            run_name="zero_warmup_test",
            questions=questions,
            warmup_queries=0,
            resume=False,
        )

        assert mock_client.query_question.call_count == 1


class TestStreamingJsonlCheckpointing:
    def test_checkpoint_fields_and_streaming(self, tmp_path, mock_client):
        collector = BenchmarkCollector(
            client=mock_client,
            output_dir=tmp_path,
            debug=False,
            console=Console(quiet=True),
        )
        questions = [{"id": "q10"}, {"id": "q20"}]

        records = collector.collect(
            run_name="stream_run",
            questions=questions,
            modules=["M1", "M2"],
            table_name="test_tbl",
            top_k=5,
            warmup_queries=0,
            resume=False,
        )

        run_file = tmp_path / "stream_run.jsonl"
        assert run_file.exists()

        with open(run_file) as f:
            lines = [json.loads(l) for l in f if l.strip()]

        assert len(lines) == 2
        for line in lines:
            assert line["runName"] == "stream_run"
            assert "timestamp" in line
            assert line["modulesRequested"] == ["M1", "M2"]
            assert line["timings"]["retrievalMs"] == 45
            assert line["timings"]["generationMs"] == 120
            assert line["tokenUsage"]["cachedTokens"] == 200
            assert len(line["retrievedChunks"]) == 1
            assert "clientRoundtripMs" in line

    def test_resume_skips_existing_records(self, tmp_path, mock_client):
        run_file = tmp_path / "resume_run.jsonl"
        existing_record = {
            "questionId": "q1",
            "question": "Already done",
            "generatedAnswer": "Done answer",
        }
        with open(run_file, "w") as f:
            f.write(json.dumps(existing_record) + "\n")

        collector = BenchmarkCollector(
            client=mock_client,
            output_dir=tmp_path,
            debug=False,
            console=Console(quiet=True),
        )
        questions = [{"id": "q1"}, {"id": "q2"}]

        records = collector.collect(
            run_name="resume_run",
            questions=questions,
            warmup_queries=0,
            resume=True,
        )

        # q1 was already completed, so query_question should only be called once for q2
        assert mock_client.query_question.call_count == 1
        assert len(records) == 2
        with open(run_file) as f:
            lines = [json.loads(l) for l in f if l.strip()]
        assert len(lines) == 2
        assert lines[0]["questionId"] == "q1"
        assert lines[1]["questionId"] == "q2"


class TestCollectorModes:
    def test_debug_mode_execution(self, tmp_path, mock_client):
        test_console = Console(record=True, width=120)
        collector = BenchmarkCollector(
            client=mock_client,
            output_dir=tmp_path,
            debug=True,
            console=test_console,
        )
        questions = [{"id": "debug-q1"}]

        collector.collect(
            run_name="debug_run",
            questions=questions,
            warmup_queries=1,
            resume=False,
        )

        output = test_console.export_text()
        assert "DEBUG" in output
        assert "Timers:" in output
        assert "Tokens:" in output
        assert "Chunks (1):" in output

    def test_convenience_collect_run_with_canonical_sample(self, tmp_path, mock_client):
        records = collect_run(
            client=mock_client,
            run_name="canon_run",
            questions=None,  # triggers get_canonical_sample
            warmup_queries=0,
            output_dir=tmp_path,
            debug=False,
        )

        mock_client.get_canonical_sample.assert_called_once()
        assert len(records) == 2
        assert mock_client.query_question.call_count == 2

    def test_run_benchmark_stream_sample_filtering(self, tmp_path, mock_client):
        from src.collector import StreamingBenchmarkCollector

        collector = StreamingBenchmarkCollector(
            client=mock_client,
            output_dir=tmp_path,
            debug=False,
            console=Console(quiet=True),
        )
        checkpoint_path = collector.run_benchmark_stream(
            run_name="stream_test",
            sample=True,
            limit=1,
            warmup_queries=0,
        )

        assert checkpoint_path.exists()
        assert checkpoint_path == tmp_path / "stream_test.jsonl"
        with open(checkpoint_path) as f:
            lines = [json.loads(l) for l in f if l.strip()]
        assert len(lines) == 1
