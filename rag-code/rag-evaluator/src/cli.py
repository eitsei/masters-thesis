"""Unified Typer CLI for RAG optimization benchmarking, evaluation, and reporting."""

from __future__ import annotations

import warnings

# Clean terminal output: suppress third-party deprecation & future warnings
warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", category=UserWarning)
warnings.filterwarnings("ignore", category=DeprecationWarning)

import json
import os
import sys
import time
from datetime import datetime
from pathlib import Path
from typing import Any, Optional

# Ensure project root is in sys.path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import typer
from dotenv import load_dotenv
from rich.console import Console
from rich.panel import Panel

from src.client import RagBenchmarkClient
from src.export_latex import (
    export_benchmark_summary_latex,
    export_hop_breakdown_latex,
    export_wilcoxon_tests_latex,
)
from src.judge import get_judge_components, test_judge_connection
from src.metrics import (
    RagasEvaluatorWrapper,
    compute_composite_quality,
    compute_cost,
    compute_qpd,
    compute_qps,
    exact_match,
    token_f1,
)
from src.plot_results import (
    plot_latency_breakdown,
    plot_pareto_frontier,
    plot_ragas_metrics_radar,
)
from src.reporter import (
    display_comparison_table,
    display_hop_breakdown_table,
    display_summary_table,
)
from src.stats import batch_compare_configurations, compare_paired_runs

load_dotenv()

app = typer.Typer(
    name="rag-evaluator",
    help="RAG Efficiency & Quality Benchmarking Suite (Master's Thesis §4)",
    add_completion=False,
)
console = Console()


def _load_jsonl(file_path: Path | str) -> list[dict[str, Any]]:
    """Reads records from a JSONL file."""
    path = Path(file_path)
    if not path.exists():
        raise typer.BadParameter(f"File not found: {file_path}")
    records = []
    with path.open("r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                records.append(json.loads(line))
    return records


def _save_jsonl(records: list[dict[str, Any]], file_path: Path | str) -> Path:
    """Saves records to a JSONL file."""
    path = Path(file_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        for r in records:
            f.write(json.dumps(r) + "\n")
    return path


def _process_item_metrics(
    item: dict[str, Any],
    ragas_evaluator: RagasEvaluatorWrapper | None = None,
    pricing_tier: str = "gemini-2.5-flash",
) -> dict[str, Any]:
    """Enriches a benchmark result item with EM, F1, Cost, RAGAS scores, and Composite Q."""
    question = item.get("question") or item.get("query") or ""
    gt = item.get("groundTruthAnswer") or item.get("ground_truth") or item.get("reference") or ""
    aliases = item.get("groundTruthAliases") or item.get("aliases") or []
    pred = item.get("generatedAnswer") or item.get("answer") or ""

    item["question"] = question
    item["generatedAnswer"] = pred
    item["groundTruthAnswer"] = gt

    em = exact_match(pred, gt, aliases) if gt else 0.0
    f1 = token_f1(pred, gt, aliases) if gt else 0.0
    item["exact_match"] = em
    item["token_f1"] = f1

    # Token and cost accounting
    tu = item.get("tokenUsage", {})
    prompt_tokens = tu.get("promptTokens", 0)
    completion_tokens = tu.get("completionTokens", 0)
    cached_tokens = tu.get("cachedTokens", 0)
    total_tokens = tu.get("totalTokens", prompt_tokens + completion_tokens)
    item["total_tokens"] = total_tokens

    cost = compute_cost(
        prompt_tokens=prompt_tokens,
        completion_tokens=completion_tokens,
        cached_tokens=cached_tokens,
        tier=pricing_tier,
        prompt_is_uncached=True,
    )
    item["cost_usd"] = cost

    # Latency parsing
    timings = item.get("timings") or item.get("timingsMs") or {}
    ret_ms = timings.get("retrievalMs", item.get("retrieval_ms", 0.0))
    gen_ms = timings.get("generationMs", item.get("generation_ms", 0.0))
    tot_ms = item.get("totalLatencyMs", item.get("total_latency_ms", ret_ms + gen_ms))
    item["total_latency_ms"] = tot_ms
    item["retrieval_ms"] = ret_ms
    item["generation_ms"] = gen_ms

    # RAGAS evaluation
    if ragas_evaluator and ragas_evaluator.llm is not None:
        chunks = item.get("retrievedChunks", [])
        contexts = [c.get("snippet", c.get("text", "")) for c in chunks] if chunks else []
        ragas_scores = ragas_evaluator.evaluate_sample(
            question=question,
            response=pred,
            retrieved_contexts=contexts,
            ground_truth=gt,
        )
        item["faithfulness"] = ragas_scores.get("faithfulness", 0.0)
        item["answer_relevancy"] = ragas_scores.get("answer_relevancy", 0.0)
        item["context_precision"] = ragas_scores.get("context_precision", 0.0)
        item["context_recall"] = ragas_scores.get("context_recall", 0.0)
    else:
        # Default or preserve existing scores
        item.setdefault("faithfulness", 1.0 if em == 1.0 else f1)
        item.setdefault("answer_relevancy", f1)
        item.setdefault("context_precision", 1.0 if item.get("supportingEvidenceFound") else 0.5)
        item.setdefault("context_recall", 1.0 if item.get("supportingEvidenceFound") else 0.5)

    comp_q = compute_composite_quality(
        item["context_precision"],
        item["context_recall"],
        item["faithfulness"],
        item["answer_relevancy"],
    )
    item["composite_quality"] = comp_q
    item["qpd"] = compute_qpd(comp_q, cost)
    item["qps"] = compute_qps(comp_q, tot_ms)

    return item


@app.command()
def run(
    url: Optional[str] = typer.Option(None, "--url", help="RAG service URL (default: RAG_APP_URL or http://localhost:8080)"),
    limit: Optional[int] = typer.Option(None, "--limit", "-l", help="Number of benchmark questions to evaluate"),
    hops: Optional[int] = typer.Option(None, "--hops", "-h", help="Filter by hop count (e.g. 2, 3, 4)"),
    sample: bool = typer.Option(False, "--sample", "-s", help="Use canonical 100-question sample (Thesis §4.2.1)"),
    modules: str = typer.Option("", "--modules", "-m", help="Comma-separated optimization modules (e.g. M1,M2 or S2B,SR)"),
    table_name: Optional[str] = typer.Option(None, "--table-name", help="Vector store table name override"),
    judge: str = typer.Option("none", "--judge", "-j", help="Judge provider (ollama, gemini, claude, none)"),
    judge_model: Optional[str] = typer.Option(None, "--judge-model", help="Judge model identifier"),
    warmup: int = typer.Option(2, "--warmup", "-w", help="Number of unmeasured warm-up queries to eliminate JVM JIT / TLS jitter"),
    debug: bool = typer.Option(False, "--debug", "-d", help="Enable verbose debug logging"),
    output: Optional[str] = typer.Option(None, "--output", "-o", help="Path to save evaluation results JSONL"),
    top_k: Optional[int] = typer.Option(None, "--top-k", help="Retrieval top-k override"),
    stream: bool = typer.Option(False, "--stream", help="Use client-side streaming query collector with real-time JSONL checkpointing"),
) -> None:
    """Executes a benchmark evaluation run against the Spring Boot RAG service."""
    rag_url = url or os.getenv("RAG_APP_URL", "http://localhost:8080")
    client = RagBenchmarkClient(base_url=rag_url, debug=debug, timeout=180.0)

    module_list = [m.strip() for m in modules.split(",") if m.strip()]
    module_label = "+".join(module_list) if module_list else "C0_Baseline"

    console.print(Panel.fit(
        f"[bold cyan]RAG Benchmark Evaluator[/bold cyan]\n"
        f"Service URL: [green]{rag_url}[/green]\n"
        f"Modules: [yellow]{module_label}[/yellow] | Limit: {limit or 'All'} | Sample: {sample} | Hops: {hops or 'All'}\n"
        f"Judge: [magenta]{judge}[/magenta] ({judge_model or 'default'}) | Warm-up Queries: {warmup}",
        title="Configuration",
    ))

    # Health check
    if not client.health_check():
        console.print(f"[bold yellow]Warning:[/bold yellow] RAG service at {rag_url} not responding to health check. Attempting query anyway...")

    # Warm-up phase (Thesis §4.1)
    if warmup > 0:
        console.print(f"[dim]Executing {warmup} unmeasured warm-up queries to eliminate cold-start / JVM JIT distortions...[/dim]")
        for i in range(warmup):
            try:
                # Issue lightweight single question query or small batch
                sample_qs = client.get_questions(offset=0, limit=1)
                if sample_qs:
                    client.query_question(sample_qs[0]["id"], modules=module_list)
            except Exception as e:
                if debug:
                    console.print(f"[dim yellow]Warm-up query {i+1} failed: {e}[/dim yellow]")

    # Query execution (Batch or Streaming Collector)
    raw_results = []
    if stream:
        console.print("[bold green]Executing streaming benchmark query collection...[/bold green]")
        from src.collector import BenchmarkCollector
        run_name = Path(output).stem if output else f"run_{datetime.now().strftime('%Y%m%d_%H%M%S')}"
        collector = BenchmarkCollector(client=client, output_dir=Path("runs"), debug=debug, console=console)
        if sample:
            questions = client.get_canonical_sample()
            if limit is not None and limit > 0:
                questions = questions[:limit]
        else:
            questions = client.get_questions(limit=limit or 20, hops=hops)
        raw_results = collector.collect(
            run_name=run_name,
            questions=questions,
            modules=module_list,
            table_name=table_name,
            top_k=top_k,
            warmup_queries=0,  # already warmed up above
        )
    else:
        console.print("[bold green]Executing benchmark query evaluation...[/bold green]")
        t0 = time.perf_counter()
        try:
            batch_resp = client.batch_query(
                limit=limit,
                hops=hops,
                sample=sample,
                modules=module_list,
                table_name=table_name,
                top_k=top_k,
            )
            raw_results = batch_resp.get("results", [])
            elapsed_total = (time.perf_counter() - t0)
            console.print(f"Received [bold]{len(raw_results)}[/bold] query results in {elapsed_total:.2f}s.")
        except Exception as batch_err:
            console.print(f"[yellow]Batch query encountered an issue ({batch_err}); falling back to streaming collection...[/yellow]")
            from src.collector import BenchmarkCollector
            run_name = Path(output).stem if output else f"run_{datetime.now().strftime('%Y%m%d_%H%M%S')}"
            collector = BenchmarkCollector(client=client, output_dir=Path("runs"), debug=debug, console=console)
            if sample:
                questions = client.get_canonical_sample()
                if limit is not None and limit > 0:
                    questions = questions[:limit]
            else:
                questions = client.get_questions(limit=limit or 20, hops=hops)
            raw_results = collector.collect(
                run_name=run_name,
                questions=questions,
                modules=module_list,
                table_name=table_name,
                top_k=top_k,
                warmup_queries=0,
            )

    if not raw_results:
        console.print("[red]No query results returned from RAG service.[/red]")
        return

    # Initialize RAGAS judge if requested
    ragas_evaluator = None
    if judge.lower() not in ("none", "off", "disabled"):
        console.print(f"Initializing evaluation judge with provider [magenta]{judge}[/magenta]...")
        llm, emb = get_judge_components(provider=judge, model=judge_model)
        ragas_evaluator = RagasEvaluatorWrapper(llm=llm, embeddings=emb)

    # Process metrics
    processed_records: list[dict[str, Any]] = []
    for item in raw_results:
        processed = _process_item_metrics(item, ragas_evaluator=ragas_evaluator)
        processed_records.append(processed)

    # Display Rich Tables
    console.print()
    display_summary_table({module_label: processed_records}, console=console)
    console.print()
    display_hop_breakdown_table(processed_records, console=console)

    # Save output
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    out_path = Path(output) if output else Path(f"runs/run_{module_label}_{timestamp}.jsonl")
    _save_jsonl(processed_records, out_path)
    console.print(f"\n[bold green]✓ Benchmark run saved to:[/bold green] [cyan]{out_path}[/cyan]")


@app.command("test-judge")
def test_judge(
    judge: str = typer.Option("ollama", "--judge", "-j", help="Judge provider: ollama, gemini, claude, none"),
    judge_model: Optional[str] = typer.Option(None, "--judge-model", help="Judge model identifier"),
) -> None:
    """Tests connectivity, responsiveness, and latency of the configured evaluation judge."""
    with console.status(
        f"[bold cyan]Connecting to judge provider '{judge}' ({judge_model or 'default'})...[/bold cyan]\n"
        f"[dim]Pinging LLM & embeddings (Ollama may take 10-25s on first run to load weights into memory)...[/dim]"
    ):
        res = test_judge_connection(provider=judge, model=judge_model)

    status = res.get("status")
    if status == "success":
        llm_lat = res.get("llm_latency_ms", res.get("query_latency_ms", 0.0))
        emb_lat = res.get("embedding_latency_ms", 0.0)
        emb_stat = res.get("embedding_status", "N/A")
        console.print(Panel(
            f"[bold green]Connection Successful![/bold green]\n"
            f"Provider: {res.get('provider')}\n"
            f"Model: {res.get('model')}\n"
            f"Judge Response: {res.get('response')}\n"
            f"LLM Latency: {llm_lat:.1f} ms\n"
            f"Embeddings: {emb_stat} ({emb_lat:.1f} ms)",
            title="Judge Status",
            border_style="green",
        ))
    elif status == "disabled":
        console.print(f"[yellow]Judge is currently disabled ('{judge}').[/yellow]")
    else:
        console.print(Panel(
            f"[bold red]Connection Failed![/bold red]\n"
            f"Provider: {res.get('provider')}\n"
            f"Error: {res.get('error')}",
            title="Judge Error",
            border_style="red",
        ))
        raise typer.Exit(code=1)


@app.command()
def evaluate(
    input_file: Path = typer.Option(..., "--input", "-i", help="Input JSONL file containing raw benchmark results"),
    judge: str = typer.Option("ollama", "--judge", "-j", help="Judge provider: ollama, gemini, claude, none"),
    judge_model: Optional[str] = typer.Option(None, "--judge-model", help="Judge model identifier"),
    output: Optional[Path] = typer.Option(None, "--output", "-o", help="Output JSONL path for evaluated results"),
) -> None:
    """Computes EM, F1, RAGAS metrics, and Composite Q on previously gathered run records."""
    records = _load_jsonl(input_file)
    console.print(f"Loaded [bold]{len(records)}[/bold] records from [cyan]{input_file}[/cyan].")

    ragas_evaluator = None
    if judge.lower() not in ("none", "off", "disabled"):
        console.print(f"Initializing evaluation judge: [magenta]{judge}[/magenta]...")
        llm, emb = get_judge_components(provider=judge, model=judge_model)
        ragas_evaluator = RagasEvaluatorWrapper(llm=llm, embeddings=emb)

    evaluated_records: list[dict[str, Any]] = []
    for item in records:
        processed = _process_item_metrics(item, ragas_evaluator=ragas_evaluator)
        evaluated_records.append(processed)

    config_name = input_file.stem
    display_summary_table({config_name: evaluated_records}, console=console)
    display_hop_breakdown_table(evaluated_records, console=console)

    out_path = output or input_file.with_name(f"{input_file.stem}_evaluated.jsonl")
    _save_jsonl(evaluated_records, out_path)
    console.print(f"\n[bold green]✓ Evaluated results saved to:[/bold green] [cyan]{out_path}[/cyan]")


@app.command()
def compare(
    baseline: Path = typer.Option(..., "--baseline", "-b", help="Baseline run JSONL (C0)"),
    treatment: Path = typer.Option(..., "--treatment", "-t", help="Treatment run JSONL (Ck)"),
    output_latex: Optional[Path] = typer.Option(None, "--output-latex", help="Optional LaTeX output file (.tex)"),
    config_name: str = typer.Option("Treatment (Ck)", "--config-name", help="Display name for treatment configuration"),
) -> None:
    """Performs paired Wilcoxon signed-rank tests with Holm-Bonferroni correction (Table 4.2)."""
    base_records = _load_jsonl(baseline)
    treat_records = _load_jsonl(treatment)

    console.print(f"Comparing [cyan]{treatment.name}[/cyan] against baseline [cyan]{baseline.name}[/cyan]...")
    comps = compare_paired_runs(base_records, treat_records, config_name=config_name)

    display_comparison_table(comps, console=console)

    if output_latex:
        tex_content = export_wilcoxon_tests_latex(comps, output_latex)
        console.print(f"\n[bold green]✓ LaTeX comparison table exported to:[/bold green] [cyan]{output_latex}[/cyan]")


@app.command("export-thesis")
def export_thesis(
    input_target: str = typer.Option(..., "--input", "-i", help="Run JSONL file or directory containing run JSONL files"),
    tables_dir: Path = typer.Option(Path("thesis/tables"), "--tables-dir", help="Directory for thesis LaTeX tables"),
    figures_dir: Path = typer.Option(Path("thesis/figures"), "--figures-dir", help="Directory for publication PDF figures"),
) -> None:
    """Exports publication-ready LaTeX tables and vector PDF figures to the thesis directory."""
    target_path = Path(input_target)
    run_dict: dict[str, list[dict[str, Any]]] = {}

    if target_path.is_dir():
        for jsonl_file in sorted(target_path.glob("*.jsonl")):
            stem = jsonl_file.stem.replace("run_", "").replace("_evaluated", "")
            run_dict[stem] = _load_jsonl(jsonl_file)
    elif target_path.is_file():
        stem = target_path.stem.replace("run_", "").replace("_evaluated", "")
        run_dict[stem] = _load_jsonl(target_path)
    else:
        raise typer.BadParameter(f"Input path not found: {input_target}")

    if not run_dict:
        console.print("[red]No records found to export.[/red]")
        return

    console.print(f"Exporting thesis artifacts for [bold]{len(run_dict)}[/bold] configurations...")

    # 1. Export LaTeX Tables
    t_summary = export_benchmark_summary_latex(run_dict, tables_dir / "benchmark_summary.tex")
    console.print(f"  [green]✓[/green] Table: [cyan]{tables_dir / 'benchmark_summary.tex'}[/cyan]")

    # Hop breakdown for the first or combined run
    primary_records = next(iter(run_dict.values()))
    t_hops = export_hop_breakdown_latex(primary_records, tables_dir / "hop_breakdown.tex")
    console.print(f"  [green]✓[/green] Table: [cyan]{tables_dir / 'hop_breakdown.tex'}[/cyan]")

    # Wilcoxon hypothesis tests if baseline C0 is present
    baseline_key = next((k for k in run_dict.keys() if "c0" in k.lower() or "base" in k.lower()), None)
    if baseline_key and len(run_dict) > 1:
        base_recs = run_dict[baseline_key]
        treatments = {k: v for k, v in run_dict.items() if k != baseline_key}
        comparisons = batch_compare_configurations(base_recs, treatments)
        export_wilcoxon_tests_latex(comparisons, tables_dir / "wilcoxon_tests.tex")
        console.print(f"  [green]✓[/green] Table: [cyan]{tables_dir / 'wilcoxon_tests.tex'}[/cyan]")

    # 2. Export Publication Vector Figures
    p1 = plot_pareto_frontier(run_dict, figures_dir / "pareto_frontier.pdf")
    console.print(f"  [green]✓[/green] Figure: [cyan]{p1}[/cyan]")

    p2 = plot_latency_breakdown(run_dict, figures_dir / "latency_breakdown.pdf")
    console.print(f"  [green]✓[/green] Figure: [cyan]{p2}[/cyan]")

    p3 = plot_ragas_metrics_radar(run_dict, figures_dir / "ragas_metrics_radar.pdf")
    console.print(f"  [green]✓[/green] Figure: [cyan]{p3}[/cyan]")

    console.print("\n[bold green]All thesis tables and figures exported successfully![/bold green]")


@app.command("query")
def query_cmd(
    query_text: str = typer.Argument(..., help="Query or question to ask the RAG pipeline"),
    table_name: str = typer.Option("vector_store_nordea", "--table-name", "-t", help="Target vector store table"),
    modules: str = typer.Option("", "--modules", "-m", help="Comma-separated optimization modules"),
    judge: str = typer.Option("ollama", "--judge", "-j", help="Judge provider: ollama, gemini, claude, none"),
    judge_model: Optional[str] = typer.Option(None, "--judge-model", help="Judge model identifier"),
    ground_truth: Optional[str] = typer.Option(None, "--ground-truth", "-g", help="Optional reference answer"),
    output: Optional[Path] = typer.Option(None, "--output", "-o", help="Optional path to save evaluation result JSONL"),
    url: Optional[str] = typer.Option(None, "--url", help="RAG service URL"),
    debug: bool = typer.Option(False, "--debug", "-d", help="Enable verbose debug logging"),
) -> None:
    """Executes a single RAG query against any vector table and evaluates it with RAGAS."""
    rag_url = url or os.getenv("RAG_APP_URL", "http://localhost:8080")
    client = RagBenchmarkClient(base_url=rag_url, debug=debug, timeout=180.0)

    module_list = [m.strip() for m in modules.split(",") if m.strip()]
    console.print(Panel.fit(
        f"[bold cyan]Querying RAG Service[/bold cyan]\n"
        f"Query: [white]{query_text}[/white]\n"
        f"Table: [yellow]{table_name}[/yellow] | Modules: {module_list or 'C0_Baseline'}\n"
        f"Judge: [magenta]{judge}[/magenta] ({judge_model or 'default'})",
        title="Configuration",
    ))

    with console.status("[bold green]Executing RAG pipeline...[/bold green]"):
        t0 = time.perf_counter()
        res = client.query(query=query_text, modules=module_list, table_name=table_name)
        elapsed = (time.perf_counter() - t0) * 1000.0

    ans = res.get("answer", "")
    chunks = res.get("retrievedChunks", [])
    timings = res.get("timingsMs", {})
    tokens = res.get("tokenUsage", {})

    console.print(f"\n[bold green]Answer ({len(ans)} chars):[/bold green]\n{ans}\n")

    console.print(f"[bold cyan]Retrieved Chunks ({len(chunks)}):[/bold cyan]")
    for i, c in enumerate(chunks, 1):
        snip = (c.get("snippet") or c.get("text") or "")[:200].replace("\n", " ")
        console.print(f"  [dim]{i}. [{c.get('id', 'N/A')[:8]}][/dim] {snip}...")

    # Enrich and evaluate
    item = {
        "question": query_text,
        "generatedAnswer": ans,
        "groundTruthAnswer": ground_truth or "",
        "retrievedChunks": chunks,
        "timings": timings,
        "tokenUsage": tokens,
        "totalLatencyMs": res.get("totalLatencyMs", elapsed),
    }

    ragas_evaluator = None
    if judge.lower() not in ("none", "off", "disabled"):
        with console.status(f"[bold magenta]Evaluating with RAGAS ({judge})...[/bold magenta]"):
            llm, emb = get_judge_components(provider=judge, model=judge_model)
            ragas_evaluator = RagasEvaluatorWrapper(llm=llm, embeddings=emb)
            item = _process_item_metrics(item, ragas_evaluator=ragas_evaluator)
    else:
        item = _process_item_metrics(item, ragas_evaluator=None)

    # Display Metrics Panel
    metrics_str = (
        f"[bold]Latency:[/bold] Total {item.get('total_latency_ms', 0):.0f} ms "
        f"(Ret: {item.get('retrieval_ms', 0):.0f} ms, Gen: {item.get('generation_ms', 0):.0f} ms)\n"
        f"[bold]Tokens:[/bold] Total {item.get('total_tokens', 0)} "
        f"(Prompt: {tokens.get('promptTokens', 0)}, Comp: {tokens.get('completionTokens', 0)})\n"
        f"[bold]RAGAS Faithfulness:[/bold] [green]{item.get('faithfulness', 0.0):.3f}[/green]\n"
        f"[bold]RAGAS Answer Relevancy:[/bold] [green]{item.get('answer_relevancy', 0.0):.3f}[/green]"
    )
    if ground_truth:
        metrics_str += (
            f"\n[bold]RAGAS Context Precision:[/bold] {item.get('context_precision', 0.0):.3f}\n"
            f"[bold]RAGAS Context Recall:[/bold] {item.get('context_recall', 0.0):.3f}\n"
            f"[bold]Composite Quality (Q):[/bold] [yellow]{item.get('composite_quality', 0.0):.3f}[/yellow]\n"
            f"[bold]Exact Match / F1:[/bold] {item.get('exact_match', 0.0):.2f} / {item.get('token_f1', 0.0):.2f}"
        )
    console.print(Panel(metrics_str, title="[bold]Evaluation Summary[/bold]", border_style="cyan"))

    if output:
        _save_jsonl([item], output)
        console.print(f"\n[bold green]✓ Query evaluation saved to:[/bold green] [cyan]{output}[/cyan]")


@app.command("run-questions")
def run_questions(
    input_file: Path = typer.Option(..., "--input", "-i", help="JSON or JSONL file containing questions"),
    table_name: str = typer.Option("vector_store_nordea", "--table-name", "-t", help="Target vector store table"),
    modules: str = typer.Option("", "--modules", "-m", help="Comma-separated optimization modules"),
    judge: str = typer.Option("ollama", "--judge", "-j", help="Judge provider: ollama, gemini, claude, none"),
    judge_model: Optional[str] = typer.Option(None, "--judge-model", help="Judge model identifier"),
    output: Optional[Path] = typer.Option(None, "--output", "-o", help="Output JSONL path for evaluated results"),
    url: Optional[str] = typer.Option(None, "--url", help="RAG service URL"),
    debug: bool = typer.Option(False, "--debug", "-d", help="Enable verbose debug logging"),
) -> None:
    """Runs a batch of questions from a file against a vector store table and evaluates with RAGAS."""
    rag_url = url or os.getenv("RAG_APP_URL", "http://localhost:8080")
    client = RagBenchmarkClient(base_url=rag_url, debug=debug, timeout=180.0)

    # Load input file (supports JSON array or JSONL)
    records: list[dict[str, Any]] = []
    text = input_file.read_text(encoding="utf-8").strip()
    if text.startswith("["):
        records = json.loads(text)
    else:
        for line in text.splitlines():
            line = line.strip()
            if line:
                records.append(json.loads(line))

    console.print(f"Loaded [bold]{len(records)}[/bold] questions from [cyan]{input_file}[/cyan].")
    module_list = [m.strip() for m in modules.split(",") if m.strip()]
    module_label = "+".join(module_list) if module_list else "C0_Baseline"

    # Initialize judge if requested
    ragas_evaluator = None
    if judge.lower() not in ("none", "off", "disabled"):
        console.print(f"Initializing judge: [magenta]{judge}[/magenta]...")
        llm, emb = get_judge_components(provider=judge, model=judge_model)
        ragas_evaluator = RagasEvaluatorWrapper(llm=llm, embeddings=emb)

    evaluated_records: list[dict[str, Any]] = []
    for i, rec in enumerate(records, 1):
        q = rec.get("question") or rec.get("query") or ""
        gt = rec.get("ground_truth") or rec.get("reference") or rec.get("groundTruthAnswer") or ""
        aliases = rec.get("aliases") or rec.get("groundTruthAliases") or []
        qid = rec.get("id", f"q{i}")

        console.print(f"[{i}/{len(records)}] Querying: [cyan]{q}[/cyan]")
        t0 = time.perf_counter()
        resp = client.query(query=q, modules=module_list, table_name=table_name)
        elapsed = (time.perf_counter() - t0) * 1000.0

        item = {
            "questionId": qid,
            "question": q,
            "generatedAnswer": resp.get("answer", ""),
            "groundTruthAnswer": gt,
            "groundTruthAliases": aliases,
            "retrievedChunks": resp.get("retrievedChunks", []),
            "timings": resp.get("timingsMs", {}),
            "tokenUsage": resp.get("tokenUsage", {}),
            "totalLatencyMs": resp.get("totalLatencyMs", elapsed),
            "tableNameUsed": table_name,
        }
        processed = _process_item_metrics(item, ragas_evaluator=ragas_evaluator)
        evaluated_records.append(processed)

    # Display Rich Summary Table
    display_summary_table({table_name: evaluated_records}, console=console)

    # Save output
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    out_path = output or Path(f"runs/run_{table_name}_{timestamp}.jsonl")
    _save_jsonl(evaluated_records, out_path)
    console.print(f"\n[bold green]✓ Evaluated results saved to:[/bold green] [cyan]{out_path}[/cyan]")


if __name__ == "__main__":
    app()

