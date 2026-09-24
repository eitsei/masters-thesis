"""Unified Typer CLI for RAG optimization benchmarking, evaluation, and reporting."""

from __future__ import annotations

import warnings

# Clean terminal output: suppress third-party deprecation & future warnings
warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", category=UserWarning)
warnings.filterwarnings("ignore", category=DeprecationWarning)

import json
import os
import re
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
from src.collector import BenchmarkCollector, StreamingBenchmarkCollector
from src.export_latex import (
    export_benchmark_summary_latex,
    export_hop_breakdown_latex,
    export_wilcoxon_decomposed_latex,
    export_wilcoxon_tests_latex,
)
from src.judge import get_judge_components, test_judge_connection
from src.metrics import (
    RagasEvaluatorWrapper,
    compute_composite_quality,
    compute_cost,
    compute_qpd,
    compute_qps,
    compute_ttft,
    exact_match,
    get_pricing_tier,
    token_f1,
)
from src.plot_results import (
    plot_cost_breakdown,
    plot_latency_breakdown,
    plot_pareto_frontier,
    plot_pareto_frontier_3d,
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
    pricing_tier: str = "gemini-3.8-flash",
    progress_callback: Any = None,
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
    modules = item.get("modulesApplied") or item.get("modulesRequested") or []
    has_m3 = (
        "M3" in modules
        or "PC" in modules
        or item.get("cacheStatus") in ("EXPLICIT_HIT", "EXPLICIT_MISS")
        or (isinstance(item.get("runName"), str) and "m3" in item["runName"].lower())
    )

    tu = item.get("tokenUsage", {}) or {}
    prompt_tokens = int(tu.get("promptTokens", 0) or 0)
    completion_tokens = int(tu.get("completionTokens", 0) or 0)

    cached_read_tokens = 0
    cache_write_tokens = 0
    if has_m3:
        cached_read_tokens = int(tu.get("cachedTokens", 0) or 0)
        cache_write_tokens = int(tu.get("cacheWriteTokens", 0) or 0)
        if cached_read_tokens == 0 and cache_write_tokens == 0:
            cache_meta = item.get("cacheMetadata", {}) or {}
            token_count = int(cache_meta.get("tokenCount", 0) or 0)
            status = str(item.get("cacheStatus", "") or "").upper()
            if "HIT" in status:
                cached_read_tokens = token_count
            elif "MISS" in status:
                cache_write_tokens = token_count
    else:
        cached_read_tokens = 0
        cache_write_tokens = 0

    item["cached_tokens"] = cached_read_tokens
    item["cache_write_tokens"] = cache_write_tokens

    routing_prompt_tokens = int(
        tu.get("routingPromptTokens")
        or tu.get("routing_prompt_tokens")
        or item.get("routingPromptTokens")
        or item.get("routing_prompt_tokens")
        or 0
    )
    routing_completion_tokens = int(
        tu.get("routingCompletionTokens")
        or tu.get("routing_completion_tokens")
        or item.get("routingCompletionTokens")
        or item.get("routing_completion_tokens")
        or 0
    )
    route_tokens = int(
        item.get("routingTokens")
        or item.get("route_tokens")
        or tu.get("routingTokens")
        or tu.get("route_tokens")
        or (routing_prompt_tokens + routing_completion_tokens)
        or 0
    )

    api_total = int(tu.get("totalTokens", 0) or 0)
    if api_total > 0:
        total_tokens = api_total + route_tokens
    else:
        total_tokens = prompt_tokens + completion_tokens + route_tokens
    item["total_tokens"] = total_tokens

    rerank_docs = int(
        item.get("rerankDocs")
        or item.get("rerank_docs")
        or tu.get("rerankDocs")
        or tu.get("rerank_docs")
        or 0
    )
    if rerank_docs == 0 and ("M2" in modules or "SR" in modules or any("m2" in str(m).lower() for m in modules)):
        rerank_docs = 50  # Default candidate pool size per Thesis Table 4.1

    embed_tokens = int(
        item.get("embedTokens")
        or item.get("embed_tokens")
        or tu.get("embedTokens")
        or tu.get("embed_tokens")
        or 0
    )
    if embed_tokens == 0:
        embed_tokens = max(1, len(question.split())) if question else 20

    model_used = item.get("modelUsed") or item.get("model_used")
    if model_used:
        item["model_used"] = model_used
    effective_tier = model_used if model_used else pricing_tier

    item["rerank_docs"] = rerank_docs
    item["embed_tokens"] = embed_tokens
    item["routing_tokens"] = route_tokens
    item["routing_prompt_tokens"] = routing_prompt_tokens
    item["routing_completion_tokens"] = routing_completion_tokens

    cost = compute_cost(
        prompt_tokens=prompt_tokens,
        completion_tokens=completion_tokens,
        cached_tokens=cached_read_tokens,
        cache_write_tokens=cache_write_tokens,
        routing_prompt_tokens=routing_prompt_tokens,
        routing_completion_tokens=routing_completion_tokens,
        routing_tokens=route_tokens,
        rerank_docs=rerank_docs,
        embed_tokens=embed_tokens,
        tier=effective_tier,
        prompt_is_uncached=True,
    )
    item["cost_usd"] = cost

    # Latency parsing
    timings = item.get("timings") or item.get("timingsMs") or {}
    ret_ms = float(timings.get("retrievalMs", item.get("retrieval_ms", 0.0)) or 0.0)
    gen_ms = float(timings.get("generationMs", item.get("generation_ms", 0.0)) or 0.0)
    route_ms = float(timings.get("routeMs", item.get("route_ms", 0.0)) or 0.0)
    rerank_ms = float(timings.get("rerankMs", item.get("rerank_ms", 0.0)) or 0.0)
    expand_ms = float(timings.get("expansionMs", item.get("expansion_ms", 0.0)) or 0.0)
    cache_ms = float(timings.get("cacheMs", item.get("cache_ms", 0.0)) or 0.0)

    item["retrieval_ms"] = ret_ms
    item["generation_ms"] = gen_ms
    item["route_ms"] = route_ms
    item["rerank_ms"] = rerank_ms
    item["expansion_ms"] = expand_ms
    item["cache_ms"] = cache_ms

    tot_ms = float(item.get(
        "totalLatencyMs",
        item.get(
            "total_latency_ms",
            ret_ms + gen_ms + route_ms + rerank_ms + expand_ms + cache_ms,
        ),
    ) or 0.0)
    item["total_latency_ms"] = tot_ms

    prefill_ms = float(timings.get("prefillMs", item.get("prefill_ms", 0.0)) or 0.0)
    stream_ms = float(timings.get("streamMs", item.get("stream_ms", 0.0)) or 0.0)
    if stream_ms == 0.0 and prefill_ms > 0.0 and gen_ms >= prefill_ms:
        stream_ms = max(0.0, gen_ms - prefill_ms)

    raw_ttft = timings.get("ttftMs")
    if raw_ttft is None:
        raw_ttft = item.get("ttft_ms")
    ttft_ms = float(raw_ttft) if raw_ttft is not None else compute_ttft(tot_ms, stream_ms)

    item["prefill_ms"] = prefill_ms
    item["stream_ms"] = stream_ms
    item["ttft_ms"] = ttft_ms

    # RAGAS evaluation
    if ragas_evaluator and ragas_evaluator.llm is not None:
        chunks = item.get("retrievedChunks", [])
        contexts = [c.get("snippet", c.get("text", "")) for c in chunks] if chunks else []
        ragas_scores = ragas_evaluator.evaluate_sample(
            question=question,
            response=pred,
            retrieved_contexts=contexts,
            ground_truth=gt,
            progress_callback=progress_callback,
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


METRIC_DISPLAY_NAMES: dict[str, str] = {
    "faithfulness": "Faithfulness",
    "answer_relevancy": "Answer Relevancy",
    "context_precision": "Context Precision",
    "context_recall": "Context Recall",
}


def _pacing_delay_with_spinner(
    seconds: float,
    current_idx: int,
    total_items: int,
    console: Console = console,
) -> None:
    """Displays an active spinner while pausing between judge requests to prevent rate limits."""
    if seconds <= 0 or current_idx >= total_items:
        return
    with console.status(
        f"[dim yellow]Pacing judge ({seconds:.1f}s rate-limit pause before item [{current_idx + 1}/{total_items}])...[/dim yellow]",
        spinner="clock",
    ):
        time.sleep(seconds)


def _evaluate_item_with_feedback(
    item: dict[str, Any],
    ragas_evaluator: RagasEvaluatorWrapper | None,
    judge: str,
    item_idx: int,
    total_items: int,
    pricing_tier: str = "gemini-3.8-flash",
    console: Console = console,
) -> dict[str, Any]:
    """Evaluates a single item with active spinner and metric-by-metric progress feedback."""
    if not ragas_evaluator or ragas_evaluator.llm is None:
        processed = _process_item_metrics(item, ragas_evaluator=None, pricing_tier=pricing_tier)
        em = processed.get("exact_match", 0.0)
        f1 = processed.get("token_f1", 0.0)
        tot_ms = processed.get("total_latency_ms", 0.0)
        cost = processed.get("cost_usd", 0.0)
        console.print(
            f"  [bold green]✓[/bold green] [{item_idx}/{total_items}] Evaluated: "
            f"EM=[green]{em:.2f}[/green] | F1=[green]{f1:.2f}[/green] | "
            f"Latency=[dim]{tot_ms:.0f}ms[/dim] | Cost=[cyan]${cost:.5f}[/cyan]"
        )
        return processed

    with console.status(
        f"[bold magenta][{item_idx}/{total_items}][/bold magenta] Judge [{judge}]: Preparing evaluation...",
        spinner="dots",
    ) as status:
        def _on_progress(metric_name: str, phase: str, detail: str = "") -> None:
            clean = METRIC_DISPLAY_NAMES.get(metric_name.lower(), metric_name)
            if phase == "start":
                status.update(
                    f"[bold magenta][{item_idx}/{total_items}][/bold magenta] Judge [{judge}]: Evaluating [cyan]{clean}[/cyan] ({detail})..."
                )
            elif phase == "done":
                status.update(
                    f"[bold magenta][{item_idx}/{total_items}][/bold magenta] Judge [{judge}]: Finished [cyan]{clean}[/cyan] (score: {detail})"
                )
            elif phase == "delay":
                status.update(
                    f"[dim yellow][{item_idx}/{total_items}][/dim yellow] Judge [{judge}]: Inter-metric pause ({detail})..."
                )
            elif phase == "error":
                status.update(
                    f"[bold red][{item_idx}/{total_items}][/bold red] Judge [{judge}]: Error on [cyan]{clean}[/cyan] ({detail})"
                )

        processed = _process_item_metrics(
            item,
            ragas_evaluator=ragas_evaluator,
            pricing_tier=pricing_tier,
            progress_callback=_on_progress,
        )

    f_score = processed.get("faithfulness", 0.0)
    ar_score = processed.get("answer_relevancy", 0.0)
    cp_score = processed.get("context_precision", 0.0)
    cr_score = processed.get("context_recall", 0.0)
    q_comp = processed.get("composite_quality", 0.0)
    tot_ms = processed.get("total_latency_ms", 0.0)
    cost = processed.get("cost_usd", 0.0)
    console.print(
        f"  [bold green]✓[/bold green] [{item_idx}/{total_items}] Evaluated: "
        f"Faith=[green]{f_score:.2f}[/green] | Rel=[green]{ar_score:.2f}[/green] | "
        f"Prec=[green]{cp_score:.2f}[/green] | Rec=[green]{cr_score:.2f}[/green] | "
        f"Q=[yellow]{q_comp:.3f}[/yellow] | Latency=[dim]{tot_ms:.0f}ms[/dim] | Cost=[cyan]${cost:.5f}[/cyan]"
    )
    return processed


@app.command()
def run(
    url: Optional[str] = typer.Option(None, "--url", help="RAG service URL (default: RAG_APP_URL or http://localhost:8080)"),
    limit: Optional[int] = typer.Option(None, "--limit", "-l", help="Number of benchmark questions to evaluate"),
    hops: Optional[int] = typer.Option(None, "--hops", "-h", help="Filter by hop count (e.g. 2, 3, 4)"),
    sample: bool = typer.Option(False, "--sample", "-s", help="Use canonical 100-question sample (Thesis §4.2.1)"),
    modules: str = typer.Option("", "--modules", "-m", help="Comma-separated optimization modules (e.g. M1,M2 or S2B,SR)"),
    table_name: Optional[str] = typer.Option(None, "--table-name", help="Vector store table name override"),
    judge: str = typer.Option("none", "--judge", "-j", help="Judge provider (claude, gemini, vertex, ollama, none)"),
    judge_model: Optional[str] = typer.Option(None, "--judge-model", help="Judge model identifier"),
    warmup: int = typer.Option(2, "--warmup", "-w", help="Number of unmeasured warm-up queries to eliminate JVM JIT / TLS jitter"),
    debug: bool = typer.Option(False, "--debug", "-d", help="Enable verbose debug logging"),
    output: Optional[str] = typer.Option(None, "--output", "-o", help="Path to save evaluation results JSONL"),
    top_k: Optional[int] = typer.Option(None, "--top-k", help="Retrieval top-k override"),
    stream: bool = typer.Option(False, "--stream", help="Use client-side streaming query collector with real-time JSONL checkpointing"),
    judge_delay: Optional[float] = typer.Option(
        None,
        "--judge-delay",
        help="Pacing delay in seconds between judge calls to prevent 429 rate limits (default: 1.5s for cloud judges like Claude/Gemini, 0s for Ollama)",
    ),
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
                    console.print(f"[yellow]Warm-up query failed: {e}[/yellow]")

    # Query execution (Batch or Streaming Collector)
    raw_results: list[dict[str, Any]] = []
    stream_output_dir = Path(output).parent if output else Path("runs")
    stream_run_name = Path(output).stem if output else f"run_{module_label}"

    if stream:
        console.print("[bold green]Executing streaming benchmark query collection...[/bold green]")
        collector = StreamingBenchmarkCollector(
            client=client,
            output_dir=stream_output_dir,
            debug=debug,
            console=console,
        )
        try:
            checkpoint_path = collector.run_benchmark_stream(
                run_name=stream_run_name,
                modules=module_list,
                limit=limit,
                hops=hops,
                sample=sample,
                table_name=table_name,
                top_k=top_k,
                warmup_queries=0,
            )
            raw_results = _load_jsonl(checkpoint_path)
        except Exception as exc:
            console.print(f"[bold red]Streaming collection error: {exc}[/bold red]")
            return
    else:
        try:
            with console.status("[bold green]Executing batch queries against RAG service...[/bold green]"):
                batch_res = client.batch_query(
                    limit=limit,
                    hops=hops,
                    sample=sample,
                    modules=module_list,
                    table_name=table_name,
                    top_k=top_k,
                    warmup_queries=0,
                )
                raw_results = batch_res.get("results", [])
        except Exception as batch_err:
            console.print(f"[yellow]Batch query encountered an issue ({batch_err}); falling back to streaming collection...[/yellow]")
            collector = StreamingBenchmarkCollector(
                client=client,
                output_dir=stream_output_dir,
                debug=debug,
                console=console,
            )
            checkpoint_path = collector.run_benchmark_stream(
                run_name=stream_run_name,
                modules=module_list,
                limit=limit,
                hops=hops,
                sample=sample,
                table_name=table_name,
                top_k=top_k,
                warmup_queries=0,
            )
            raw_results = _load_jsonl(checkpoint_path)

    if not raw_results:
        console.print("[red]No query results returned from RAG service.[/red]")
        return

    # Determine effective pacing delay between judge requests to prevent 429 quota exhaustion
    effective_delay: float
    if judge_delay is not None:
        effective_delay = max(0.0, judge_delay)
    elif judge.lower() in ("gemini", "claude", "vertex", "vertexai"):
        effective_delay = 1.5
    else:
        effective_delay = 0.0

    # Initialize RAGAS judge if requested
    ragas_evaluator = None
    if judge.lower() not in ("none", "off", "disabled"):
        with console.status(
            f"[bold magenta]Initializing judge [{judge}] ({judge_model or 'default'})...[/bold magenta]",
            spinner="dots",
        ):
            llm, emb = get_judge_components(provider=judge, model=judge_model)
            inter_metric_delay = min(0.8, effective_delay / 2) if effective_delay > 0 else 0.0
            ragas_evaluator = RagasEvaluatorWrapper(llm=llm, embeddings=emb, delay_seconds=inter_metric_delay)
        console.print(f"[bold green]✓[/bold green] Judge [magenta]{judge}[/magenta] initialized.")
        if effective_delay > 0:
            console.print(f"[dim]Pacing judge requests ({effective_delay:.1f}s delay) to avoid 429 rate limit errors...[/dim]")

    # Process metrics
    processed_records: list[dict[str, Any]] = []
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    out_path = Path(output) if output else Path(f"runs/run_{module_label}_{timestamp}.jsonl")
    out_path.parent.mkdir(parents=True, exist_ok=True)

    for idx, item in enumerate(raw_results, 1):
        processed = _evaluate_item_with_feedback(
            item=item,
            ragas_evaluator=ragas_evaluator,
            judge=judge,
            item_idx=idx,
            total_items=len(raw_results),
            console=console,
        )
        processed_records.append(processed)
        if ragas_evaluator and effective_delay > 0 and idx < len(raw_results):
            _pacing_delay_with_spinner(effective_delay, current_idx=idx, total_items=len(raw_results), console=console)

    # Display Rich Tables
    console.print()
    display_summary_table({module_label: processed_records}, console=console)
    console.print()
    display_hop_breakdown_table(processed_records, console=console)

    # Save output
    _save_jsonl(processed_records, out_path)
    console.print(f"\n[bold green]✓ Benchmark run saved to:[/bold green] [cyan]{out_path}[/cyan]")


@app.command("test-judge")
def test_judge(
    judge: str = typer.Option("claude", "--judge", "-j", help="Judge provider: claude, gemini, vertex, ollama, none"),
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
    judge: str = typer.Option("claude", "--judge", "-j", help="Judge provider: claude, gemini, vertex, ollama, none"),
    judge_model: Optional[str] = typer.Option(None, "--judge-model", help="Judge model identifier"),
    output: Optional[Path] = typer.Option(None, "--output", "-o", help="Output JSONL path for evaluated results"),
    judge_delay: Optional[float] = typer.Option(
        None,
        "--judge-delay",
        help="Pacing delay in seconds between judge calls to prevent 429 rate limits (default: 1.5s for cloud judges like Claude/Gemini, 0s for Ollama)",
    ),
) -> None:
    """Computes EM, F1, RAGAS metrics, and Composite Q on previously gathered run records."""
    records = _load_jsonl(input_file)
    console.print(f"Loaded [bold]{len(records)}[/bold] records from [cyan]{input_file}[/cyan].")

    effective_delay: float
    if judge_delay is not None:
        effective_delay = max(0.0, judge_delay)
    elif judge.lower() in ("gemini", "claude", "vertex", "vertexai"):
        effective_delay = 1.5
    else:
        effective_delay = 0.0

    ragas_evaluator = None
    if judge.lower() not in ("none", "off", "disabled"):
        with console.status(
            f"[bold magenta]Initializing judge [{judge}] ({judge_model or 'default'})...[/bold magenta]",
            spinner="dots",
        ):
            llm, emb = get_judge_components(provider=judge, model=judge_model)
            inter_metric_delay = min(0.8, effective_delay / 2) if effective_delay > 0 else 0.0
            ragas_evaluator = RagasEvaluatorWrapper(llm=llm, embeddings=emb, delay_seconds=inter_metric_delay)
        console.print(f"[bold green]✓[/bold green] Judge [magenta]{judge}[/magenta] initialized.")
        if effective_delay > 0:
            console.print(f"[dim]Pacing judge requests ({effective_delay:.1f}s delay) to avoid 429 rate limit errors...[/dim]")

    evaluated_records: list[dict[str, Any]] = []
    out_path = output or input_file.with_name(f"{input_file.stem}_evaluated.jsonl")
    out_path.parent.mkdir(parents=True, exist_ok=True)

    for idx, item in enumerate(records, 1):
        processed = _evaluate_item_with_feedback(
            item=item,
            ragas_evaluator=ragas_evaluator,
            judge=judge,
            item_idx=idx,
            total_items=len(records),
            console=console,
        )
        evaluated_records.append(processed)
        if ragas_evaluator and effective_delay > 0 and idx < len(records):
            _pacing_delay_with_spinner(effective_delay, current_idx=idx, total_items=len(records), console=console)

    config_name = input_file.stem
    display_summary_table({config_name: evaluated_records}, console=console)
    display_hop_breakdown_table(evaluated_records, console=console)

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
        if output_latex.is_dir() or output_latex.suffix != ".tex":
            output_latex = output_latex / "wilcoxon_tests.tex"
        tex_content = export_wilcoxon_tests_latex(comps, output_latex)
        console.print(f"\n[bold green]✓ LaTeX comparison table exported to:[/bold green] [cyan]{output_latex}[/cyan]")


def _resolve_thesis_path(p: Path) -> Path:
    if p.is_absolute():
        return p
    if p.parts and p.parts[0] == "thesis":
        for base in [Path.cwd()] + list(Path(__file__).resolve().parents):
            if (base / "thesis" / "_main.tex").exists():
                return base / p
    return p


@app.command("export-thesis")
def export_thesis(
    input_target: str = typer.Option(..., "--input", "-i", help="Run JSONL file or directory containing run JSONL files"),
    tables_dir: Path = typer.Option(Path("thesis/tables"), "--tables-dir", help="Directory for thesis LaTeX tables"),
    figures_dir: Path = typer.Option(Path("thesis/figures"), "--figures-dir", help="Directory for publication PDF figures"),
    subfolder: Optional[str] = typer.Option(
        None,
        "--subfolder",
        "-s",
        help="Optional subfolder inside tables-dir and figures-dir to isolate run outputs (e.g., 'sample_runs', 'musique', 'nordea', 'comparisons', 'runs/<run_name>')",
    ),
    family_scope: str = typer.Option(
        "metric",
        "--family-scope",
        help="Holm-Bonferroni family scope: 'metric' (default, m=15 per metric), 'rq', 'config', 'global'",
    ),
) -> None:
    """Exports publication-ready LaTeX tables and vector PDF figures to the thesis directory."""
    tables_dir = _resolve_thesis_path(tables_dir)
    figures_dir = _resolve_thesis_path(figures_dir)

    if subfolder:
        tables_dir = tables_dir / subfolder
        figures_dir = figures_dir / subfolder

    tables_dir.mkdir(parents=True, exist_ok=True)
    figures_dir.mkdir(parents=True, exist_ok=True)

    target_path = Path(input_target)
    run_dict: dict[str, list[dict[str, Any]]] = {}

    def _sort_key(p: Path) -> tuple[int, str]:
        m = re.search(r"[Cc](\d+)", p.stem)
        return (int(m.group(1)) if m else 999, p.name)

    if target_path.is_dir():
        for jsonl_file in sorted(target_path.glob("*.jsonl"), key=_sort_key):
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
        comparisons = batch_compare_configurations(base_recs, treatments, family_scope=family_scope)
        export_wilcoxon_tests_latex(comparisons, tables_dir / "wilcoxon_tests.tex")
        console.print(f"  [green]✓[/green] Table: [cyan]{tables_dir / 'wilcoxon_tests.tex'}[/cyan]")
        export_wilcoxon_decomposed_latex(comparisons, tables_dir)
        console.print(f"  [green]✓[/green] Tables (Decomposed): [cyan]{tables_dir / 'wilcoxon_rq*.tex'}[/cyan]")

    # 2. Export Publication Vector Figures
    p1 = plot_pareto_frontier(run_dict, figures_dir / "pareto_frontier.pdf")
    console.print(f"  [green]✓[/green] Figure: [cyan]{p1}[/cyan]")

    p2 = plot_pareto_frontier_3d(run_dict, figures_dir / "pareto_frontier_3d.pdf")
    console.print(f"  [green]✓[/green] Figure: [cyan]{p2}[/cyan]")

    p3 = plot_latency_breakdown(run_dict, figures_dir / "latency_breakdown.pdf")
    console.print(f"  [green]✓[/green] Figure: [cyan]{p3}[/cyan]")

    p4 = plot_cost_breakdown(run_dict, figures_dir / "cost_breakdown.pdf")
    console.print(f"  [green]✓[/green] Figure: [cyan]{p4}[/cyan]")

    p5 = plot_ragas_metrics_radar(run_dict, figures_dir / "ragas_metrics_radar.pdf")
    console.print(f"  [green]✓[/green] Figure: [cyan]{p5}[/cyan]")

    console.print("\n[bold green]All thesis tables and figures exported successfully![/bold green]")


@app.command("query")
def query_cmd(
    query_text: str = typer.Argument(..., help="Query or question to ask the RAG pipeline"),
    table_name: str = typer.Option("vector_store_nordea", "--table-name", "-t", help="Target vector store table"),
    modules: str = typer.Option("", "--modules", "-m", help="Comma-separated optimization modules"),
    judge: str = typer.Option("claude", "--judge", "-j", help="Judge provider: claude, gemini, vertex, ollama, none"),
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
        with console.status(
            f"[bold magenta]Initializing judge [{judge}] ({judge_model or 'default'})...[/bold magenta]",
            spinner="dots",
        ):
            llm, emb = get_judge_components(provider=judge, model=judge_model)
            ragas_evaluator = RagasEvaluatorWrapper(llm=llm, embeddings=emb)

    item = _evaluate_item_with_feedback(
        item=item,
        ragas_evaluator=ragas_evaluator,
        judge=judge,
        item_idx=1,
        total_items=1,
        console=console,
    )

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
    limit: Optional[int] = typer.Option(None, "--limit", "-l", help="Limit number of questions to evaluate"),
    judge: str = typer.Option("claude", "--judge", "-j", help="Judge provider: claude, gemini, vertex, ollama, none"),
    judge_model: Optional[str] = typer.Option(None, "--judge-model", help="Judge model identifier"),
    output: Optional[Path] = typer.Option(None, "--output", "-o", help="Output JSONL path for evaluated results"),
    url: Optional[str] = typer.Option(None, "--url", help="RAG service URL"),
    debug: bool = typer.Option(False, "--debug", "-d", help="Enable verbose debug logging"),
    judge_delay: Optional[float] = typer.Option(
        None,
        "--judge-delay",
        help="Pacing delay in seconds between judge calls to prevent 429 rate limits (default: 1.5s for cloud judges like Claude/Gemini, 0s for Ollama)",
    ),
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

    if limit is not None and limit > 0:
        records = records[:limit]

    console.print(f"Loaded [bold]{len(records)}[/bold] questions from [cyan]{input_file}[/cyan].")
    module_list = [m.strip() for m in modules.split(",") if m.strip()]
    module_label = "+".join(module_list) if module_list else "C0_Baseline"

    effective_delay: float
    if judge_delay is not None:
        effective_delay = max(0.0, judge_delay)
    elif judge.lower() in ("gemini", "claude", "vertex", "vertexai", "vertex-claude", "claude-vertex"):
        effective_delay = 1.5
    else:
        effective_delay = 0.0

    # Initialize judge if requested
    ragas_evaluator = None
    if judge.lower() not in ("none", "off", "disabled"):
        with console.status(
            f"[bold magenta]Initializing judge [{judge}] ({judge_model or 'default'})...[/bold magenta]",
            spinner="dots",
        ):
            llm, emb = get_judge_components(provider=judge, model=judge_model)
            inter_metric_delay = min(0.8, effective_delay / 2) if effective_delay > 0 else 0.0
            ragas_evaluator = RagasEvaluatorWrapper(llm=llm, embeddings=emb, delay_seconds=inter_metric_delay)
        console.print(f"[bold green]✓[/bold green] Judge [magenta]{judge}[/magenta] initialized.")
        if effective_delay > 0:
            console.print(f"[dim]Pacing judge requests ({effective_delay:.1f}s delay) to avoid 429 rate limit errors...[/dim]")

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    out_path = output or Path(f"runs/run_{table_name}_{timestamp}.jsonl")
    out_path.parent.mkdir(parents=True, exist_ok=True)

    evaluated_records: list[dict[str, Any]] = []
    for i, rec in enumerate(records, 1):
        q = rec.get("question") or rec.get("query") or ""
        gt = rec.get("ground_truth") or rec.get("reference") or rec.get("groundTruthAnswer") or ""
        aliases = rec.get("aliases") or rec.get("groundTruthAliases") or []
        qid = rec.get("id", f"q{i}")

        q_disp = (q[:90] + "...") if len(q) > 90 else q
        console.print(f"\n[bold cyan]─── Question [{i}/{len(records)}] ───[/bold cyan] [bold white]{q_disp}[/bold white]")

        try:
            with console.status(
                f"[bold cyan][{i}/{len(records)}][/bold cyan] Querying RAG service ({table_name})...",
                spinner="dots",
            ):
                t0 = time.perf_counter()
                resp = client.query(query=q, modules=module_list, table_name=table_name)
                elapsed = (time.perf_counter() - t0) * 1000.0
        except Exception as q_err:
            console.print(f"  [bold red]✗ Query failed for question [{i}/{len(records)}]: {q_err}[/bold red]")
            continue

        ret_chunks = len(resp.get("retrievedChunks", []))
        ans_raw = (resp.get("answer") or "").replace("\n", " ").strip()
        ans_preview = (ans_raw[:80] + "...") if len(ans_raw) > 80 else ans_raw
        console.print(f"  [dim]↳ RAG responded in {elapsed:.0f}ms ({ret_chunks} chunks): \"{ans_preview}\"[/dim]")

        item = {
            "questionId": qid,
            "question": q,
            "generatedAnswer": resp.get("answer", ""),
            "groundTruthAnswer": gt,
            "groundTruthAliases": aliases,
            "modulesApplied": resp.get("modulesApplied", module_list),
            "retrievedChunks": resp.get("retrievedChunks", []),
            "timings": resp.get("timingsMs", {}),
            "tokenUsage": resp.get("tokenUsage", {}),
            "totalLatencyMs": resp.get("totalLatencyMs", elapsed),
            "tableNameUsed": table_name,
            "modelUsed": resp.get("modelUsed"),
            "cacheStatus": resp.get("cacheStatus"),
            "cacheMetadata": resp.get("cacheMetadata"),
        }

        processed = _evaluate_item_with_feedback(
            item=item,
            ragas_evaluator=ragas_evaluator,
            judge=judge,
            item_idx=i,
            total_items=len(records),
            console=console,
        )
        evaluated_records.append(processed)

        # Incrementally checkpoint to disk so progress is preserved
        with out_path.open("a", encoding="utf-8") as f:
            f.write(json.dumps(processed) + "\n")

        if ragas_evaluator and effective_delay > 0 and i < len(records):
            _pacing_delay_with_spinner(effective_delay, current_idx=i, total_items=len(records), console=console)

    if evaluated_records:
        # Display Rich Summary Table
        display_summary_table({table_name: evaluated_records}, console=console)
        # Ensure final file is cleanly written
        _save_jsonl(evaluated_records, out_path)
        console.print(f"\n[bold green]✓ Evaluated results saved to:[/bold green] [cyan]{out_path}[/cyan]")
    else:
        console.print("[yellow]No questions were successfully evaluated.[/yellow]")


@app.command("generate-nordea-testset")
def generate_nordea_testset(
    md_dir: Path = typer.Option(
        Path("../rag-benchmark/data/nordea/md"),
        "--md-dir",
        "-d",
        help="Path to directory containing crawled Nordea markdown documents (pages/ and pdfs/)",
    ),
    output: Path = typer.Option(
        Path("data/nordea/nordea_benchmark_100.jsonl"),
        "--output",
        "-o",
        help="Target JSONL file path for generated questions",
    ),
    size: int = typer.Option(
        100,
        "--size",
        "-s",
        help="Total number of benchmark questions to generate (Thesis §4.4: 40% single-hop, 40% multi-hop, 20% out-of-domain)",
    ),
    judge_model: str = typer.Option(
        "gemini-3.8-flash",
        "--judge-model",
        "-m",
        help="Generator LLM model on Vertex AI global endpoint",
    ),
    limit_docs: Optional[int] = typer.Option(
        None,
        "--limit-docs",
        help="Limit number of canonical documents to ingest (useful for fast smoke runs)",
    ),
) -> None:
    """Generates a stratified synthetic benchmark dataset (Thesis §4.4) for Nordea Private Banking."""
    from src.generator import generate_nordea_dataset

    # Resolve relative md_dir if needed
    resolved_md = md_dir if md_dir.is_absolute() else (Path.cwd() / md_dir).resolve()
    if not resolved_md.exists():
        # Fallback check relative to project root
        alt_path = Path(__file__).resolve().parent.parent.parent / "rag-benchmark" / "data" / "nordea" / "md"
        if alt_path.exists():
            resolved_md = alt_path

    resolved_out = output if output.is_absolute() else (Path.cwd() / output).resolve()

    generate_nordea_dataset(
        md_dir=resolved_md,
        output_file=resolved_out,
        total_size=size,
        judge_model=judge_model,
        limit_docs=limit_docs,
        console=console,
    )


if __name__ == "__main__":
    app()

