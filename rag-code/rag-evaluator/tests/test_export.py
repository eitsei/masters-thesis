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
    plot_latency_breakdown,
    plot_pareto_frontier,
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


def test_export_hop_breakdown_latex(tmp_path: Path, sample_benchmark_records):
    out_tex = tmp_path / "hop_breakdown.tex"
    content = export_hop_breakdown_latex(sample_benchmark_records, output_path=out_tex)

    assert out_tex.exists()
    assert "2-Hop Reasoning" in content
    assert "3-Hop Reasoning" in content
    assert "4-Hop Reasoning" in content
    assert "Combined Corpus" in content
    assert "\\begin{table*}" in content


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


def test_plot_ragas_metrics_radar(tmp_path: Path, sample_benchmark_records):
    out_pdf = tmp_path / "ragas_metrics_radar.pdf"
    data = {
        "C0: Baseline": sample_benchmark_records,
        "C1: S2B": sample_benchmark_records,
    }

    result_path = plot_ragas_metrics_radar(data, output_path=out_pdf)
    assert result_path.exists()
    assert result_path.stat().st_size > 1000
