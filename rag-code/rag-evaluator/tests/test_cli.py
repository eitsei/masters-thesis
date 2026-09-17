"""Unit tests for the Typer CLI (src/cli.py).

Tests:
- CLI command discovery and help outputs for all commands
- 'compare' command with synthetic baseline and treatment JSONL runs
- 'evaluate' command adding metrics to raw benchmark responses
- 'export-thesis' command producing LaTeX tables and PDF figures
- 'test-judge' command with disabled/mocked judge
- 'run' command with mocked Spring Boot HTTP responses
"""

import json
from pathlib import Path
import pytest
from typer.testing import CliRunner
from unittest.mock import MagicMock, patch

from src.cli import app

runner = CliRunner()


@pytest.fixture
def synthetic_run_data():
    return [
        {
            "questionId": f"q_{i}",
            "question": f"Question {i}?",
            "groundTruthAnswer": "Target answer",
            "groundTruthAliases": ["Target alias"],
            "generatedAnswer": "Target answer" if i % 2 == 0 else "Wrong answer",
            "hopCount": 2 if i < 3 else 3,
            "timings": {"retrievalMs": 150.0, "generationMs": 650.0},
            "totalLatencyMs": 800.0 + i * 20,
            "tokenUsage": {"promptTokens": 1000, "completionTokens": 150, "cachedTokens": 0},
            "supportingEvidenceFound": True,
        }
        for i in range(6)
    ]


def test_cli_help():
    res = runner.invoke(app, ["--help"])
    assert res.exit_code == 0
    assert "RAG Efficiency & Quality Benchmarking Suite" in res.stdout
    assert "run" in res.stdout
    assert "test-judge" in res.stdout
    assert "evaluate" in res.stdout
    assert "compare" in res.stdout
    assert "export-thesis" in res.stdout


def test_cli_run_help():
    res = runner.invoke(app, ["run", "--help"])
    assert res.exit_code == 0
    assert "--url" in res.stdout
    assert "--limit" in res.stdout
    assert "--hops" in res.stdout
    assert "--sample" in res.stdout
    assert "--modules" in res.stdout
    assert "--judge" in res.stdout
    assert "--warmup" in res.stdout
    assert "--debug" in res.stdout
    assert "--output" in res.stdout


def test_cli_test_judge_disabled():
    res = runner.invoke(app, ["test-judge", "--judge", "none"])
    assert res.exit_code == 0
    assert "Judge is currently disabled" in res.stdout


def test_cli_evaluate(tmp_path: Path, synthetic_run_data):
    in_file = tmp_path / "raw_run.jsonl"
    out_file = tmp_path / "evaluated_run.jsonl"

    with in_file.open("w", encoding="utf-8") as f:
        for r in synthetic_run_data:
            f.write(json.dumps(r) + "\n")

    res = runner.invoke(app, [
        "evaluate",
        "--input", str(in_file),
        "--judge", "none",
        "--output", str(out_file),
    ])

    assert res.exit_code == 0
    assert out_file.exists()

    with out_file.open("r", encoding="utf-8") as f:
        lines = [json.loads(l) for l in f]
    assert len(lines) == 6
    assert "composite_quality" in lines[0]
    assert "exact_match" in lines[0]
    assert "token_f1" in lines[0]


def test_cli_compare(tmp_path: Path, synthetic_run_data):
    base_file = tmp_path / "base.jsonl"
    treat_file = tmp_path / "treat.jsonl"
    out_tex = tmp_path / "wilcoxon.tex"

    with base_file.open("w", encoding="utf-8") as f:
        for r in synthetic_run_data:
            f.write(json.dumps(r) + "\n")

    treat_data = [
        {**r, "totalLatencyMs": r["totalLatencyMs"] - 100, "generatedAnswer": "Target answer"}
        for r in synthetic_run_data
    ]
    with treat_file.open("w", encoding="utf-8") as f:
        for r in treat_data:
            f.write(json.dumps(r) + "\n")

    res = runner.invoke(app, [
        "compare",
        "--baseline", str(base_file),
        "--treatment", str(treat_file),
        "--output-latex", str(out_tex),
        "--config-name", "C1: S2B",
    ])

    assert res.exit_code == 0
    assert out_tex.exists()
    assert "\\begin{table*}" in out_tex.read_text(encoding="utf-8")


def test_cli_export_thesis(tmp_path: Path, synthetic_run_data):
    runs_dir = tmp_path / "runs"
    runs_dir.mkdir()
    tables_dir = tmp_path / "tables"
    figures_dir = tmp_path / "figures"

    base_file = runs_dir / "run_C0_baseline.jsonl"
    c1_file = runs_dir / "run_C1_S2B.jsonl"

    with base_file.open("w", encoding="utf-8") as f:
        for r in synthetic_run_data:
            f.write(json.dumps(r) + "\n")

    with c1_file.open("w", encoding="utf-8") as f:
        for r in synthetic_run_data:
            f.write(json.dumps({**r, "totalLatencyMs": r["totalLatencyMs"] - 50}) + "\n")

    res = runner.invoke(app, [
        "export-thesis",
        "--input", str(runs_dir),
        "--tables-dir", str(tables_dir),
        "--figures-dir", str(figures_dir),
    ])

    assert res.exit_code == 0
    assert (tables_dir / "benchmark_summary.tex").exists()
    assert (tables_dir / "hop_breakdown.tex").exists()
    assert (tables_dir / "wilcoxon_tests.tex").exists()
    assert (tables_dir / "wilcoxon_rq1_latency.tex").exists()
    assert (tables_dir / "wilcoxon_rq2_cost.tex").exists()
    assert (tables_dir / "wilcoxon_rq3_quality.tex").exists()
    assert (figures_dir / "pareto_frontier.pdf").exists()
    assert (figures_dir / "pareto_frontier_3d.pdf").exists()
    assert (figures_dir / "latency_breakdown.pdf").exists()
    assert (figures_dir / "cost_breakdown.pdf").exists()
    assert (figures_dir / "ragas_metrics_radar.pdf").exists()


def test_cli_export_thesis_with_subfolder(tmp_path: Path, synthetic_run_data):
    runs_dir = tmp_path / "runs"
    runs_dir.mkdir()
    c0_file = runs_dir / "run_C0_baseline.jsonl"
    with c0_file.open("w", encoding="utf-8") as f:
        for r in synthetic_run_data:
            f.write(json.dumps(r) + "\n")

    tables_dir = tmp_path / "thesis" / "tables"
    figures_dir = tmp_path / "thesis" / "figures"

    res = runner.invoke(app, [
        "export-thesis",
        "--input", str(runs_dir),
        "--tables-dir", str(tables_dir),
        "--figures-dir", str(figures_dir),
        "--subfolder", "sample_runs",
    ])

    assert res.exit_code == 0
    assert (tables_dir / "sample_runs" / "benchmark_summary.tex").exists()
    assert (tables_dir / "sample_runs" / "hop_breakdown.tex").exists()
    assert (figures_dir / "sample_runs" / "pareto_frontier.pdf").exists()
    assert (figures_dir / "sample_runs" / "pareto_frontier_3d.pdf").exists()
    assert (figures_dir / "sample_runs" / "cost_breakdown.pdf").exists()


@patch("src.cli.RagBenchmarkClient")
def test_cli_run_mocked(mock_client_cls, tmp_path: Path, synthetic_run_data):
    mock_instance = MagicMock()
    mock_client_cls.return_value = mock_instance
    mock_instance.health_check.return_value = True
    mock_instance.get_questions.return_value = [{"id": "q_0"}]
    mock_instance.query_question.return_value = synthetic_run_data[0]
    mock_instance.batch_query.return_value = {
        "totalQuestions": 6,
        "results": synthetic_run_data,
    }

    out_file = tmp_path / "run_output.jsonl"
    res = runner.invoke(app, [
        "run",
        "--url", "http://localhost:8080",
        "--limit", "6",
        "--modules", "M1,M2",
        "--judge", "none",
        "--warmup", "1",
        "--output", str(out_file),
    ])

    assert res.exit_code == 0
    assert out_file.exists()
    assert mock_instance.batch_query.called


@patch("src.cli.RagBenchmarkClient")
def test_cli_run_streaming_mocked(mock_client_cls, tmp_path: Path):
    mock_instance = MagicMock()
    mock_client_cls.return_value = mock_instance
    mock_instance.health_check.return_value = True
    mock_instance.get_canonical_sample.return_value = [
        {"id": "q1", "question": "What is M3?"},
    ]
    mock_instance.query_question.return_value = {
        "questionId": "q1",
        "question": "What is M3?",
        "groundTruthAnswer": "Prompt caching",
        "generatedAnswer": "Prompt caching is M3.",
        "retrievedChunks": [],
        "timings": {"retrievalMs": 10.0, "generationMs": 20.0},
        "totalLatencyMs": 30.0,
        "tokenUsage": {"promptTokens": 100, "completionTokens": 10, "cachedTokens": 0},
    }

    out_file = tmp_path / "stream_run.jsonl"
    res = runner.invoke(app, [
        "run",
        "--url", "http://localhost:8080",
        "--sample",
        "--limit", "1",
        "--stream",
        "--judge", "none",
        "--warmup", "0",
        "--output", str(out_file),
    ])

    assert res.exit_code == 0
    assert out_file.exists()
    assert mock_instance.query_question.called




@patch("src.cli.RagBenchmarkClient")
def test_cli_query_mocked(mock_client_cls, tmp_path: Path):
    mock_instance = MagicMock()
    mock_client_cls.return_value = mock_instance
    mock_instance.query.return_value = {
        "answer": "Nordea Platinum offers lounge access.",
        "modulesApplied": [],
        "retrievedChunks": [{"id": "c1", "snippet": "Nordea Platinum lounge"}],
        "timingsMs": {"retrievalMs": 10.0, "generationMs": 20.0},
        "tokenUsage": {"promptTokens": 100, "completionTokens": 20, "cachedTokens": 0},
        "totalLatencyMs": 30.0,
    }

    out_file = tmp_path / "single_query_eval.jsonl"
    res = runner.invoke(app, [
        "query",
        "Mitä etuja Platinum tarjoaa?",
        "--table-name", "vector_store_nordea",
        "--judge", "none",
        "--ground-truth", "Lounge access",
        "--output", str(out_file),
    ])

    assert res.exit_code == 0
    assert "Nordea Platinum offers lounge access" in res.stdout
    assert "vector_store_nordea" in res.stdout
    assert mock_instance.query.called
    assert out_file.exists()
    saved = json.loads(out_file.read_text(encoding="utf-8").strip())
    assert saved["question"] == "Mitä etuja Platinum tarjoaa?"
    assert saved["generatedAnswer"] == "Nordea Platinum offers lounge access."


@patch("src.cli.RagBenchmarkClient")
def test_cli_run_questions_mocked(mock_client_cls, tmp_path: Path):
    mock_instance = MagicMock()
    mock_client_cls.return_value = mock_instance
    mock_instance.query.return_value = {
        "answer": "Mocked banking answer",
        "modulesApplied": [],
        "retrievedChunks": [{"id": "c1", "snippet": "Banking snippet"}],
        "timingsMs": {"retrievalMs": 12.0, "generationMs": 25.0},
        "tokenUsage": {"promptTokens": 80, "completionTokens": 15, "cachedTokens": 0},
        "totalLatencyMs": 37.0,
    }

    q_file = tmp_path / "test_questions.jsonl"
    q_file.write_text(
        json.dumps({"question": "Mikä on PB?", "ground_truth": "Private Banking"}) + "\n",
        encoding="utf-8",
    )
    out_file = tmp_path / "eval_out.jsonl"

    res = runner.invoke(app, [
        "run-questions",
        "--input", str(q_file),
        "--table-name", "vector_store_nordea",
        "--judge", "none",
        "--output", str(out_file),
    ])

    assert res.exit_code == 0
    assert out_file.exists()
    assert mock_instance.query.called


@patch("src.cli.RagBenchmarkClient")
def test_cli_run_with_judge_delay(mock_client_cls, tmp_path: Path, synthetic_run_data):
    mock_instance = MagicMock()
    mock_client_cls.return_value = mock_instance
    mock_instance.health_check.return_value = True
    mock_instance.batch_query.return_value = {
        "totalQuestions": 2,
        "results": synthetic_run_data[:2],
    }

    out_file = tmp_path / "run_delay_output.jsonl"
    res = runner.invoke(app, [
        "run",
        "--url", "http://localhost:8080",
        "--limit", "2",
        "--judge", "none",
        "--judge-delay", "0.1",
        "--warmup", "0",
        "--output", str(out_file),
    ])

    assert res.exit_code == 0
    assert out_file.exists()


def test_process_item_metrics_caching_attribution():
    from src.cli import _process_item_metrics

    # 1. Non-M3 item with implicit server cache: cached_tokens must be ignored (0)
    # Default pricing_tier is gemini-3.8-flash ($0.75 prompt, $3.75 comp, $0.075 cached - 90% discount)
    non_m3_item = {
        "question": "What is RAG?",
        "generatedAnswer": "Retrieval Augmented Generation",
        "groundTruthAnswer": "Retrieval Augmented Generation",
        "modulesApplied": [],
        "tokenUsage": {"promptTokens": 1000, "completionTokens": 50, "cachedTokens": 800},
    }
    res_non_m3 = _process_item_metrics(non_m3_item)
    # 1000 uncached prompt * 0.75/1M + 50 comp * 3.75/1M + 3 embed * 0.025/1M = 0.000937575
    assert res_non_m3["cost_usd"] == pytest.approx(0.000937575, rel=1e-5)

    # 2. M3 item with cache miss (cachedTokens 0, but cacheWriteTokens 4000)
    m3_item = {
        "question": "What is RAG?",
        "generatedAnswer": "Retrieval Augmented Generation",
        "groundTruthAnswer": "Retrieval Augmented Generation",
        "modulesApplied": ["M3"],
        "tokenUsage": {"promptTokens": 25, "completionTokens": 50, "cachedTokens": 0, "cacheWriteTokens": 4000},
    }
    res_m3 = _process_item_metrics(m3_item)
    # 25 prompt * 0.75/1M + 4000 cached * 0.075/1M (90% discount) + 50 comp * 3.75/1M + 3 embed * 0.025/1M
    # = 0.00001875 + 0.000300 + 0.0001875 + 0.000000075 = 0.000506325
    assert res_m3["cost_usd"] == pytest.approx(0.000506325, rel=1e-5)

    # 3. Explicit legacy tier check (gemini-2.5-flash)
    # 1000 prompt * 0.15/1M + 50 comp * 0.60/1M + 3 embed * 0.025/1M = 0.000180075
    res_legacy = _process_item_metrics(non_m3_item, pricing_tier="gemini-2.5-flash")
    assert res_legacy["cost_usd"] == pytest.approx(0.000180075, rel=1e-5)


def test_process_item_metrics_model_routing_attribution():
    from src.cli import _process_item_metrics

    # M4 routed item targeting gemini-3.5-flash-lite
    m4_item = {
        "question": "What is RAG?",
        "generatedAnswer": "Retrieval Augmented Generation",
        "groundTruthAnswer": "Retrieval Augmented Generation",
        "modulesApplied": ["M4"],
        "modelUsed": "gemini-3.5-flash-lite",
        "timings": {
            "retrievalMs": 100.0,
            "routeMs": 25.0,
            "generationMs": 200.0,
        },
        "tokenUsage": {
            "promptTokens": 500,
            "completionTokens": 30,
            "cachedTokens": 0,
            "routingTokens": 80,
        },
    }

    res = _process_item_metrics(m4_item, pricing_tier="gemini-3.8-flash")

    assert res["model_used"] == "gemini-3.5-flash-lite"
    assert res["route_ms"] == 25.0
    assert res["retrieval_ms"] == 100.0
    assert res["generation_ms"] == 200.0
    assert res["total_latency_ms"] == 325.0
    assert res["routing_tokens"] == 80

    # Pricing calculation check:
    # generation tier: gemini-3.5-flash-lite (prompt $0.075/1M, comp $0.30/1M, embed $0.025/1M)
    # 500 prompt * 0.075/1M = 0.0000375
    # 30 comp * 0.30/1M = 0.000009
    # 3 embed * 0.025/1M = 0.000000075
    # routing overhead: 80 * 0.075/1M = 0.000006
    # expected total = 0.0000375 + 0.000009 + 0.000000075 + 0.000006 = 0.000052575
    assert res["cost_usd"] == pytest.approx(0.000052575, rel=1e-5)




