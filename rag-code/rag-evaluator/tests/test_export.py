"""Unit tests for LaTeX export (export_latex.py) and figure plotting (plot_results.py).

Tests:
- Publication-ready LaTeX tables with booktabs
- Generation of benchmark_summary.tex, hop_breakdown.tex, wilcoxon_tests.tex
- Publication vector PDF figures (pareto_frontier.pdf, latency_breakdown.pdf, ragas_metrics_radar.pdf)
"""

from pathlib import Path
import pytest
from src.export_latex import (
    export_benchmark_summary_latex,
    export_hop_breakdown_latex,
    export_wilcoxon_tests_latex,
)
from src.plot_results import (
    plot_cost_breakdown,
    plot_latency_breakdown,
    plot_pareto_frontier,
    plot_pareto_frontier_3d,
    plot_ragas_metrics_radar,
)
from src.stats import WilcoxonComparison


@pytest.fixture
def sample_benchmark_records():
    return [
        {
            "questionId": f"q_{i}",
            "hopCount": 2 if i < 4 else (3 if i < 7 else 4),
            "total_latency_ms": 1200.0 + i * 25.0,
            "cost_usd": 0.0003 + i * 0.00002,
            "total_tokens": 1400 + i * 10,
            "exact_match": 1.0 if i % 2 == 0 else 0.0,
            "token_f1": 0.80,
            "faithfulness": 0.88,
            "answer_relevancy": 0.90,
            "context_precision": 0.84,
            "context_recall": 0.82,
            "composite_quality": 0.86,
            "retrieval_ms": 350.0,
            "generation_ms": 850.0,
        }
        for i in range(10)
    ]


def test_export_benchmark_summary_latex(tmp_path: Path, sample_benchmark_records):
    out_tex = tmp_path / "benchmark_summary.tex"
    content = export_benchmark_summary_latex(
        {"$C_0$: Naive Baseline": sample_benchmark_records},
        output_path=out_tex,
    )

    assert out_tex.exists()
    assert "\\begin{table*}" in content
    assert "\\toprule" in content
    assert "\\bottomrule" in content
    assert "Naive Baseline" in content
    assert "\\label{tab:benchmark_summary}" in content
    assert "per 1k q" in content
    assert "\\$0.390" in content


def test_export_hop_breakdown_latex(tmp_path: Path, sample_benchmark_records):
    out_tex = tmp_path / "hop_breakdown.tex"
    content = export_hop_breakdown_latex(sample_benchmark_records, output_path=out_tex)

    assert out_tex.exists()
    assert "2-Hop Reasoning" in content
    assert "3-Hop Reasoning" in content
    assert "4-Hop Reasoning" in content
    assert "Combined Corpus" in content
    assert "\\begin{table*}" in content
    assert "per 1k q" in content
    assert "\\$0.390" in content


def test_export_wilcoxon_tests_latex(tmp_path: Path):
    out_tex = tmp_path / "wilcoxon_tests.tex"
    comparisons = [
        WilcoxonComparison("RQ1", "End-to-End Latency", "C1: S2B", 1400.0, 1200.0, -200.0, 0.0, 0.001, 0.002, "**"),
        WilcoxonComparison("RQ2", "Cost ($)", "C1: S2B", 0.0004, 0.0003, -0.0001, 2.0, 0.004, 0.008, "**"),
        WilcoxonComparison("RQ3", "Composite Quality", "C1: S2B", 0.75, 0.85, 0.10, 1.0, 0.002, 0.006, "**"),
    ]

    content = export_wilcoxon_tests_latex(comparisons, output_path=out_tex)
    assert out_tex.exists()
    assert "RQ1" in content
    assert "RQ2" in content
    assert "RQ3" in content
    assert "C1: S2B" in content
    assert "\\textbf{**}" in content


def test_plot_pareto_frontier(tmp_path: Path, sample_benchmark_records):
    out_pdf = tmp_path / "pareto_frontier.pdf"
    data = {
        "C0: Baseline": sample_benchmark_records,
        "C1: S2B": [
            {**r, "total_latency_ms": r["total_latency_ms"] * 0.9, "composite_quality": 0.89}
            for r in sample_benchmark_records
        ],
    }

    result_path = plot_pareto_frontier(data, output_path=out_pdf)
    assert result_path.exists()
    assert result_path.stat().st_size > 1000  # Non-empty valid PDF


def test_plot_latency_breakdown(tmp_path: Path, sample_benchmark_records):
    out_pdf = tmp_path / "latency_breakdown.pdf"
    data = {
        "C0: Baseline": sample_benchmark_records,
        "C1: S2B": sample_benchmark_records,
    }

    result_path = plot_latency_breakdown(data, output_path=out_pdf)
    assert result_path.exists()
    assert result_path.stat().st_size > 1000


def test_plot_latency_breakdown_multicomponent(tmp_path: Path):
    out_pdf = tmp_path / "latency_breakdown_multi.pdf"
    data = {
        "C0: Baseline": [
            {"timings": {"retrievalMs": 80.0, "generationMs": 1500.0}, "totalLatencyMs": 1580.0}
        ],
        "C1: S2B": [
            {"timings": {"retrievalMs": 85.0, "expansionMs": 15.0, "generationMs": 1450.0}, "totalLatencyMs": 1550.0}
        ],
        "C2: Re-rank": [
            {"timings": {"retrievalMs": 80.0, "rerankMs": 220.0, "generationMs": 1300.0}, "totalLatencyMs": 1600.0}
        ],
        "C5: S2B+Re-rank": [
            {"timings": {"retrievalMs": 85.0, "rerankMs": 210.0, "expansionMs": 12.0, "generationMs": 1280.0}, "totalLatencyMs": 1587.0}
        ],
    }

    result_path = plot_latency_breakdown(data, output_path=out_pdf)
    assert result_path.exists()
    assert result_path.stat().st_size > 1000


def test_plot_ragas_metrics_radar(tmp_path: Path, sample_benchmark_records):
    out_pdf = tmp_path / "ragas_metrics_radar.pdf"
    data = {
        "C0: Baseline": sample_benchmark_records,
        "C1: S2B": sample_benchmark_records,
    }

    result_path = plot_ragas_metrics_radar(data, output_path=out_pdf)
    assert result_path.exists()
    assert result_path.stat().st_size > 1000


def test_plot_pareto_frontier_3d(tmp_path: Path, sample_benchmark_records):
    out_pdf = tmp_path / "pareto_frontier_3d.pdf"
    data = {
        "C0: Baseline": sample_benchmark_records,
        "C1: S2B": [
            {**r, "total_latency_ms": r["total_latency_ms"] * 1.2, "composite_quality": 0.88, "cost_usd": 0.0004}
            for r in sample_benchmark_records
        ],
        "C2: Re-rank": [
            {**r, "total_latency_ms": r["total_latency_ms"] * 1.1, "composite_quality": 0.91, "cost_usd": 0.00035}
            for r in sample_benchmark_records
        ],
        "C3: Prompt Caching": [
            {**r, "total_latency_ms": r["total_latency_ms"] * 0.7, "composite_quality": 0.86, "cost_usd": 0.00005}
            for r in sample_benchmark_records
        ],
    }

    result_path = plot_pareto_frontier_3d(data, output_path=out_pdf, subtitle="Unit Test Surface")
    assert result_path.exists()
    assert result_path.stat().st_size > 1000


def test_plot_pareto_frontier_3d_small_sample(tmp_path: Path, sample_benchmark_records):
    out_pdf = tmp_path / "pareto_frontier_3d_small.pdf"
    data = {
        "C0: Baseline": sample_benchmark_records[:2],
        "C1: S2B": sample_benchmark_records[:2],
    }

    # Should not crash when len < 3
    result_path = plot_pareto_frontier_3d(data, output_path=out_pdf)
    assert result_path.exists()
    assert result_path.stat().st_size > 1000


def test_plot_cost_breakdown(tmp_path: Path, sample_benchmark_records):
    out_pdf = tmp_path / "cost_breakdown.pdf"
    data = {
        "C0: Baseline": sample_benchmark_records,
        "C1: S2B": sample_benchmark_records,
    }

    result_path = plot_cost_breakdown(data, output_path=out_pdf)
    assert result_path.exists()
    assert result_path.stat().st_size > 1000


def test_plot_cost_breakdown_multicomponent(tmp_path: Path):
    out_pdf = tmp_path / "cost_breakdown_multi.pdf"
    data = {
        "C0: Baseline": [
            {
                # Server prefix cache reports 500 cachedTokens, but without M3 it must be ignored
                "tokenUsage": {"promptTokens": 1000, "completionTokens": 50, "cachedTokens": 500},
                "timings": {"retrievalMs": 80.0, "generationMs": 1500.0},
                "cost_usd": 0.00018,
                "modulesApplied": [],
            }
        ],
        "C2: Re-rank": [
            {
                "tokenUsage": {"promptTokens": 1000, "completionTokens": 50, "cachedTokens": 0},
                "timings": {"retrievalMs": 80.0, "rerankMs": 220.0, "generationMs": 1300.0},
                "modulesApplied": ["M2"],
                "rerankDocs": 10,
                "cost_usd": 0.00118,
            }
        ],
        "C3: Prompt Caching": [
            {
                # Cache miss/creation: cachedTokens is 0, but cacheWriteTokens has 4000 tokens
                "tokenUsage": {"promptTokens": 50, "completionTokens": 50, "cachedTokens": 0, "cacheWriteTokens": 4000},
                "timings": {"retrievalMs": 80.0, "cacheMs": 10.0, "generationMs": 600.0},
                "modulesApplied": ["M3"],
                "cost_usd": 0.0001875,
            }
        ],
    }

    result_path = plot_cost_breakdown(data, output_path=out_pdf)
    assert result_path.exists()
    assert result_path.stat().st_size > 1000

