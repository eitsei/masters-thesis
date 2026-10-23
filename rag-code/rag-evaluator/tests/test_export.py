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
    export_cost_latex,
    export_hop_breakdown_latex,
    export_latency_latex,
    export_pareto_latex,
    export_quality_enterprise_latex,
    export_quality_musique_latex,
    export_wilcoxon_decomposed_latex,
    export_wilcoxon_rq1_ttft_latex,
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
    assert "\\begin{landscape}" in content
    assert "\\begin{table}[p]" in content
    assert "\\end{landscape}" in content
    assert "\\toprule" in content
    assert "\\bottomrule" in content
    assert "Naive Baseline" in content
    assert "\\label{tab:benchmark_summary}" in content
    assert "per 1k q" in content
    assert "\\$0.390" in content
    assert "EM / F1" in content
    assert "Ref / FRR" in content


def test_export_hop_breakdown_latex(tmp_path: Path, sample_benchmark_records):
    out_tex = tmp_path / "hop_breakdown.tex"
    content = export_hop_breakdown_latex(sample_benchmark_records, output_path=out_tex)

    assert out_tex.exists()
    assert "2-Hop Reasoning" in content
    assert "3-Hop Reasoning" in content
    assert "4-Hop Reasoning" in content
    assert "Combined Corpus" in content
    assert "\\begin{table}[!htbp]" in content
    assert "\\footnotesize" in content
    assert "\\makecell" in content
    assert "(\\$/1k)" in content
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


def test_export_wilcoxon_decomposed_latex(tmp_path: Path):
    comparisons = [
        WilcoxonComparison("RQ1", "End-to-End Latency", "C1: S2B", 1400.0, 1200.0, -200.0, 0.0, 0.001, 0.002, "**"),
        WilcoxonComparison("RQ2", "Cost ($)", "C1: S2B", 0.0004, 0.0003, -0.0001, 2.0, 0.004, 0.008, "**"),
        WilcoxonComparison("RQ3", "Composite Quality", "C1: S2B", 0.75, 0.85, 0.10, 1.0, 0.002, 0.006, "**"),
    ]

    results = export_wilcoxon_decomposed_latex(comparisons, output_dir=tmp_path)
    assert (tmp_path / "wilcoxon_rq1_latency.tex").exists()
    assert (tmp_path / "wilcoxon_rq2_cost.tex").exists()
    assert (tmp_path / "wilcoxon_rq3_quality.tex").exists()
    assert "\\label{tab:wilcoxon_rq1}" in results["rq1"]
    assert "\\label{tab:wilcoxon_rq2}" in results["rq2"]
    assert "\\label{tab:wilcoxon_rq3}" in results["rq3"]


def test_export_wilcoxon_rq1_ttft_latex(tmp_path: Path):
    out_tex = tmp_path / "wilcoxon_rq1_ttft.tex"
    comparisons = [
        WilcoxonComparison(
            target_rq="RQ1",
            metric_name="Time to First Token (ms)",
            config_name="C1: S2B",
            baseline_median=850.0,
            treatment_median=650.0,
            median_diff=-200.0,
            w_statistic=10.0,
            p_raw=0.001,
            p_adjusted=0.002,
            significance="**",
        ),
        WilcoxonComparison(
            target_rq="RQ1",
            metric_name="End-to-End Latency (ms)",
            config_name="C1: S2B",
            baseline_median=1400.0,
            treatment_median=1200.0,
            median_diff=-200.0,
            w_statistic=0.0,
            p_raw=0.001,
            p_adjusted=0.002,
            significance="**",
        ),
        WilcoxonComparison(
            target_rq="RQ2",
            metric_name="Total Token Usage",
            config_name="C1: S2B",
            baseline_median=1500.0,
            treatment_median=1000.0,
            median_diff=-500.0,
            w_statistic=5.0,
            p_raw=0.005,
            p_adjusted=0.010,
            significance="*",
        ),
        WilcoxonComparison(
            target_rq="RQ2",
            metric_name="Operational Cost ($)",
            config_name="C1: S2B",
            baseline_median=0.0004,
            treatment_median=0.0003,
            median_diff=-0.0001,
            w_statistic=2.0,
            p_raw=0.004,
            p_adjusted=0.008,
            significance="**",
        ),
    ]

    content = export_wilcoxon_rq1_ttft_latex(comparisons, output_path=out_tex)

    assert out_tex.exists()
    assert "\\label{tab:wilcoxon_rq1_ttft}" in content
    # Only TTFT metric should be exported
    assert "850.0" in content
    assert "650.0" in content
    assert "C1: S2B" in content

    # Verify that general latency (1400.0) and total token usage (1500.0) or cost are NOT selected
    assert "1400.0" not in content
    assert "1200.0" not in content
    assert "1500.0" not in content
    assert "1000.0" not in content
    assert "End-to-End Latency" not in content
    assert "Total Token Usage" not in content


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


def test_export_latency_latex(tmp_path: Path, sample_benchmark_records):
    out_tex = tmp_path / "tab_latency_musique.tex"
    run_dict = {
        "C0": sample_benchmark_records,
        "C1": [
            {**r, "total_latency_ms": r["total_latency_ms"] * 0.85, "ttft_ms": 120.0}
            for r in sample_benchmark_records
        ],
    }
    comparisons = [
        WilcoxonComparison("RQ1", "End-to-End Latency (ms)", "C1", 1312.5, 1115.6, -196.9, 0.0, 0.001, 0.002, "**"),
    ]

    content = export_latency_latex(
        run_dict,
        output_path=out_tex,
        comparisons=comparisons,
        label="tab:latency_musique",
        dataset_name="MuSiQue",
    )

    assert out_tex.exists()
    assert "\\label{tab:latency_musique}" in content
    assert "\\begin{table}[htbp]" in content
    assert "\\footnotesize" in content
    assert "\\renewcommand{\\arraystretch}{0.93}" in content
    assert "\\vspace{0.25em}" in content
    assert "\\parbox{\\linewidth}{\\scriptsize" in content
    assert "TTFT $P_{50}$" in content
    assert "$T_{\\text{total}} P_{50}$" in content
    assert "$\\Delta T_{\\text{total}}$" in content
    assert "Baseline ($k=0$)" in content
    assert "Single-Factor Isolated ($k=1$)" in content
    assert "\\textsuperscript{**}" in content


def test_export_latency_latex_batch_mode_note(tmp_path: Path, sample_benchmark_records):
    out_tex = tmp_path / "tab_latency_musique_batch.tex"
    run_dict = {
        "C0": sample_benchmark_records,  # All records have ttft_ms = 0 by default
    }
    content = export_latency_latex(run_dict, output_path=out_tex)
    assert "batch non-streaming mode" in content


def test_export_cost_latex(tmp_path: Path, sample_benchmark_records):
    out_tex = tmp_path / "tab_cost_musique.tex"
    run_dict = {
        "C0": [
            {**r, "tokenUsage": {"promptTokens": 1000, "completionTokens": 50, "cachedTokens": 0}}
            for r in sample_benchmark_records
        ],
        "C3": [
            {**r, "tokenUsage": {"promptTokens": 1000, "completionTokens": 50, "cachedTokens": 800}, "cost_usd": 0.00015}
            for r in sample_benchmark_records
        ],
    }
    comparisons = [
        WilcoxonComparison("RQ2", "Operational Cost ($)", "C3", 0.00039, 0.00015, -0.00024, 0.0, 0.001, 0.002, "***"),
    ]

    content = export_cost_latex(
        run_dict,
        output_path=out_tex,
        comparisons=comparisons,
        label="tab:cost_musique",
        dataset_name="MuSiQue",
    )

    assert out_tex.exists()
    assert "\\label{tab:cost_musique}" in content
    assert "\\footnotesize" in content
    assert "\\renewcommand{\\arraystretch}{0.93}" in content
    assert "\\vspace{0.25em}" in content
    assert "\\parbox{\\linewidth}{\\scriptsize" in content
    assert "Uncached" in content
    assert "Cached" in content
    assert "Output" in content
    assert "Cost / 1k" in content
    assert "\\textsuperscript{***}" in content
    # For C3: promptTokens is 1000, cachedTokens is 800 -> uncached should be 200
    assert "200" in content
    assert "800" in content


def test_export_quality_musique_latex(tmp_path: Path, sample_benchmark_records):
    out_tex = tmp_path / "tab_quality_musique.tex"
    run_dict = {
        "C0": sample_benchmark_records,
        "C1": [
            {**r, "composite_quality": 0.92, "token_f1": 0.85, "exact_match": 0.75}
            for r in sample_benchmark_records
        ],
    }
    comparisons = [
        WilcoxonComparison("RQ3", "Composite Quality Q", "C1", 0.86, 0.92, 0.06, 0.0, 0.002, 0.004, "**"),
    ]

    content = export_quality_musique_latex(
        run_dict,
        output_path=out_tex,
        comparisons=comparisons,
        label="tab:quality_musique",
        dataset_name="MuSiQue",
    )

    assert out_tex.exists()
    assert "\\label{tab:quality_musique}" in content
    assert "\\scriptsize" in content
    assert "\\renewcommand{\\arraystretch}{0.93}" in content
    assert "\\vspace{0.25em}" in content
    assert "\\parbox{\\linewidth}{\\scriptsize" in content
    assert "Context" in content and "Precision" in content
    assert "Context" in content and "Recall" in content
    assert "Faithful" in content
    assert "Answer" in content and "Relevancy" in content
    assert "Token" in content and "F1" in content
    assert "Exact" in content and "Match" in content
    assert "Refusal Acc." in content
    assert "FRR" in content
    assert "Composite" in content and "Quality" in content
    # Dynamic N in caption
    assert "($N = 10$)" in content
    # Highest Composite Quality Q is in bold
    assert "\\textbf{0.9200}" in content
    assert "\\textsuperscript{**}" in content


def test_export_quality_enterprise_latex_dynamic_n(tmp_path: Path, sample_benchmark_records):
    out_tex = tmp_path / "tab_quality_enterprise.tex"
    # Create 5 records
    records_5 = sample_benchmark_records[:5]
    run_dict = {"C0": records_5}

    content = export_quality_enterprise_latex(
        run_dict,
        output_path=out_tex,
        label="tab:quality_enterprise_raw",
    )

    assert out_tex.exists()
    assert "\\label{tab:quality_enterprise_raw}" in content
    assert "\\scriptsize" in content
    assert "\\renewcommand{\\arraystretch}{0.93}" in content
    assert "\\vspace{0.25em}" in content
    assert "\\parbox{\\linewidth}{\\scriptsize" in content
    assert "($N = 5$)" in content
    assert "($N = 100$)" not in content


def test_export_pareto_latex(tmp_path: Path, sample_benchmark_records):
    out_tex = tmp_path / "tab_pareto_musique.tex"
    run_dict = {
        "C0": sample_benchmark_records,
        "C1": [
            # Better Q, lower cost, lower latency -> dominates C0
            {**r, "composite_quality": 0.95, "cost_usd": 0.0001, "total_latency_ms": 500.0}
            for r in sample_benchmark_records
        ],
    }

    content = export_pareto_latex(
        run_dict,
        output_path=out_tex,
        label="tab:pareto_musique",
        dataset_name="MuSiQue",
    )

    assert out_tex.exists()
    assert "\\label{tab:pareto_musique}" in content
    assert "\\footnotesize" in content
    assert "\\renewcommand{\\arraystretch}{0.93}" in content
    assert "\\vspace{0.25em}" in content
    assert "\\parbox{\\linewidth}{\\scriptsize" in content
    assert "Composite" in content and "Quality" in content
    assert "Cost / 1k" in content
    assert "$T_{\\text{total}} P_{50}$" in content
    assert "QPD" in content
    assert "QPS" in content
    assert "Pareto" in content and "Optimal" in content
    # C1 dominates C0: C1 should be Pareto optimal ("Yes"), C0 dominated ("No")
    assert "Yes" in content
    assert "No" in content


def test_export_hop_breakdown_latex_dataset_name(tmp_path: Path, sample_benchmark_records):
    out_tex = tmp_path / "hop_breakdown_enterprise.tex"
    content = export_hop_breakdown_latex(
        sample_benchmark_records,
        output_path=out_tex,
        dataset_name="Enterprise",
    )

    assert out_tex.exists()
    assert "stratified by Enterprise reasoning depth tiers." in content
    assert "Evaluated over the Enterprise multi-hop benchmark sample" in content

