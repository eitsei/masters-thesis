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
import shutil
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
from rich.table import Table

from src.client import RagBenchmarkClient, RagClient
from src.collector import BenchmarkCollector, StreamingBenchmarkCollector
from src.judge import get_judge_components, test_judge_connection
from src.metrics import (
    RagasEvaluatorWrapper,
    compute_composite_quality,
    compute_cost,
    compute_false_refusal_rate,
    compute_qpd,
    compute_qps,
    compute_refusal_accuracy,
    compute_ttft,
    exact_match,
    get_pricing_tier,
    is_refusal_answer,
    is_refusal_ground_truth,
    is_sample_unanswerable,
    token_f1,
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
    gt = (
        item.get("groundTruthAnswer")
        or item.get("ground_truth")
        or item.get("reference")
        or (item.get("answer") if "generatedAnswer" in item else "")
        or ""
    )
    aliases = (
        item.get("groundTruthAliases")
        or item.get("aliases")
        or item.get("answer_aliases")
        or item.get("answerAliases")
        or []
    )
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
        prompt_is_uncached=False if prompt_tokens >= cached_read_tokens else True,
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

    # Check if item is unanswerable / out-of-domain (true negative)
    is_unans = is_sample_unanswerable(item, ground_truth=gt)
    refused = is_refusal_answer(pred)
    item["is_refusal"] = refused

    if is_unans:
        # Refusal evaluation: do NOT invoke RAGAS for unanswerable queries
        if progress_callback:
            try:
                progress_callback("ragas", "skip", "unanswerable query (refusal evaluated)")
            except Exception:
                pass
        item["faithfulness"] = None
        item["answer_relevancy"] = None
        item["context_precision"] = None
        item["context_recall"] = None
        item["composite_quality"] = None
        item["qpd"] = None
        item["qps"] = None
        item["ragas_evaluated"] = True
    else:
        # RAGAS evaluation on answerable queries
        if ragas_evaluator and ragas_evaluator.llm is not None:
            chunks = item.get("retrievedChunks", [])
            contexts = [c.get("text") or c.get("snippet") or "" for c in chunks] if chunks else []
            ragas_scores = ragas_evaluator.evaluate_sample(
                question=question,
                response=pred,
                retrieved_contexts=contexts,
                ground_truth=gt,
                progress_callback=progress_callback,
            )
            faith = ragas_scores.get("faithfulness")
            ar = ragas_scores.get("answer_relevancy")
            cp = ragas_scores.get("context_precision")
            cr = ragas_scores.get("context_recall")
            item["faithfulness"] = faith
            item["answer_relevancy"] = ar
            item["context_precision"] = cp
            item["context_recall"] = cr
            all_succeeded = all(v is not None for v in (faith, ar, cp, cr))
            item["ragas_evaluated"] = all_succeeded
            if "_errors" in ragas_scores:
                item["ragas_errors"] = ragas_scores["_errors"]
        else:
            # Default or preserve existing scores
            item.setdefault("faithfulness", 1.0 if em == 1.0 else f1)
            item.setdefault("answer_relevancy", f1)
            item.setdefault("context_precision", 1.0 if item.get("supportingEvidenceFound") else 0.5)
            item.setdefault("context_recall", 1.0 if item.get("supportingEvidenceFound") else 0.5)
            item.setdefault("ragas_evaluated", False)

        comp_q = compute_composite_quality(
            item.get("context_precision"),
            item.get("context_recall"),
            item.get("faithfulness"),
            item.get("answer_relevancy"),
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


def _resolve_judge_delay(judge: str, judge_delay: float | None = None) -> float:
    """Determine effective pacing delay between judge requests to prevent 429 quota exhaustion.

    Cloud API providers (Gemini, Claude, Vertex) default to 1.5s delay.
    Local providers (vllm, ollama) and explicit override default to 0.0s.
    """
    if judge_delay is not None:
        return max(0.0, judge_delay)
    if judge.lower() in (
        "gemini", "claude", "vertex", "vertexai", "llama", "meta", "maas",
        "vertex-maas", "vertex-claude", "claude-vertex"
    ):
        return 1.5
    return 0.0


def _format_metric_val(val: float | None, precision: int = 2) -> str:
    """Formats a floating-point metric value or returns 'None'."""
    return f"{val:.{precision}f}" if val is not None else "None"


def _evaluate_item_with_feedback(
    item: dict[str, Any],
    ragas_evaluator: RagasEvaluatorWrapper | None,
    judge: str,
    item_idx: int,
    total_items: int,
    pricing_tier: str = "gemini-3.8-flash",
    console: Console = console,
    resume: bool = True,
    force: bool = False,
    show_spinner: bool = True,
) -> dict[str, Any]:
    """Evaluates a single item with active spinner (or plain print in worker mode) and metric-by-metric progress feedback."""
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

    # If resuming and item was already evaluated by a judge, skip LLM calls
    if resume and not force and item.get("ragas_evaluated") is True:
        f_score = item.get("faithfulness")
        ar_score = item.get("answer_relevancy")
        cp_score = item.get("context_precision")
        cr_score = item.get("context_recall")
        q_comp = item.get("composite_quality")
        tot_ms = item.get("total_latency_ms", 0.0)
        cost = item.get("cost_usd", 0.0)
        if f_score is None:
            ref_status = (
                "[green]Refused (Correct)[/green]"
                if item.get("is_refusal")
                else "[bold red]False Positive (Failed to Refuse)[/bold red]"
            )
            console.print(
                f"  [dim green]✓[/dim green] [{item_idx}/{total_items}] Resumed: "
                f"{ref_status} | Latency=[dim]{tot_ms:.0f}ms[/dim] | Cost=[cyan]${cost:.5f}[/cyan]"
            )
        else:
            q_str = _format_metric_val(q_comp, 3)
            f_str = _format_metric_val(f_score, 2)
            ar_str = _format_metric_val(ar_score, 2)
            cp_str = _format_metric_val(cp_score, 2)
            cr_str = _format_metric_val(cr_score, 2)
            console.print(
                f"  [dim green]✓[/dim green] [{item_idx}/{total_items}] Resumed: "
                f"Faith=[green]{f_str}[/green] | Rel=[green]{ar_str}[/green] | "
                f"Prec=[green]{cp_str}[/green] | Rec=[green]{cr_str}[/green] | "
                f"Q=[yellow]{q_str}[/yellow] | Latency=[dim]{tot_ms:.0f}ms[/dim] | Cost=[cyan]${cost:.5f}[/cyan]"
            )
        return item

    if show_spinner:
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
    else:
        processed = _process_item_metrics(
            item,
            ragas_evaluator=ragas_evaluator,
            pricing_tier=pricing_tier,
            progress_callback=None,
        )

    # Record provenance
    if judge:
        processed["judge_provider"] = judge
    if ragas_evaluator and ragas_evaluator.llm:
        raw = getattr(ragas_evaluator.llm, "langchain_llm", ragas_evaluator.llm)
        m_name = getattr(raw, "model_name", getattr(raw, "model", None))
        if m_name:
            processed["judge_model"] = m_name
    if os.getenv("JUDGE_ENABLE_THINKING"):
        processed["judge_thinking"] = os.getenv("JUDGE_ENABLE_THINKING", "0").strip().lower() in ("1", "true", "yes")

    f_score = processed.get("faithfulness")
    ar_score = processed.get("answer_relevancy")
    cp_score = processed.get("context_precision")
    cr_score = processed.get("context_recall")
    q_comp = processed.get("composite_quality")
    tot_ms = processed.get("total_latency_ms", 0.0)
    cost = processed.get("cost_usd", 0.0)

    if f_score is None:
        ref_status = (
            "[green]Refused (Correct)[/green]"
            if processed.get("is_refusal")
            else "[bold red]False Positive (Failed to Refuse)[/bold red]"
        )
        console.print(
            f"  [bold green]✓[/bold green] [{item_idx}/{total_items}] Evaluated: "
            f"{ref_status} | Latency=[dim]{tot_ms:.0f}ms[/dim] | Cost=[cyan]${cost:.5f}[/cyan]"
        )
    else:
        ref_warn = " [bold red][False Refusal][/bold red]" if processed.get("is_refusal") else ""
        q_str = _format_metric_val(q_comp, 3)
        f_str = _format_metric_val(f_score, 2)
        ar_str = _format_metric_val(ar_score, 2)
        cp_str = _format_metric_val(cp_score, 2)
        cr_str = _format_metric_val(cr_score, 2)
        console.print(
            f"  [bold green]✓[/bold green] [{item_idx}/{total_items}] Evaluated: "
            f"Faith=[green]{f_str}[/green] | Rel=[green]{ar_str}[/green] | "
            f"Prec=[green]{cp_str}[/green] | Rec=[green]{cr_str}[/green] | "
            f"Q=[yellow]{q_str}[/yellow]{ref_warn} | Latency=[dim]{tot_ms:.0f}ms[/dim] | Cost=[cyan]${cost:.5f}[/cyan]"
        )
    return processed


def _verify_cache_preflight(
    client: RagClient,
    modules: list[str],
    table_name: str | None = None,
    console: Console | None = None,
) -> None:
    """Verify prompt cache pre-flight behavior before benchmark query execution."""
    c = console or globals().get("console") or Console()
    has_m3 = "M3" in modules or "PC" in modules or any(m.upper() == "M3" for m in modules)

    if has_m3:
        c.print("Verifying M3 explicit prompt cache pre-flight integrity...")
        try:
            res = client.verify_cache_behavior(expect_cached=True, table_name=table_name)
            if isinstance(res, (tuple, list)) and len(res) >= 2:
                success, msg = bool(res[0]), str(res[1])
            elif isinstance(res, bool):
                success, msg = res, "OK" if res else "Verification failed"
            else:
                success, msg = True, "Verified (mock)"
        except Exception as exc:
            success, msg = False, str(exc)

        if not success:
            c.print(
                f"[bold red]CRITICAL: M3 explicit cache pre-flight verification failed: {msg}. Aborting run to prevent un-cached benchmark execution.[/bold red]"
            )
            raise typer.Exit(code=1)
        c.print(f"[bold green]✓[/bold green] M3 prompt cache verified: {msg}")
    else:
        c.print("Verifying baseline cache isolation (implicit cache must be disabled)...")
        try:
            res = client.verify_cache_behavior(expect_cached=False, table_name=table_name)
            if isinstance(res, (tuple, list)) and len(res) >= 2:
                success, msg = bool(res[0]), str(res[1])
            elif isinstance(res, bool):
                success, msg = res, "OK" if res else "Verification failed"
            else:
                success, msg = True, "Verified (mock)"
        except Exception as exc:
            success, msg = False, str(exc)

        if not success:
            c.print(
                f"[bold red]CRITICAL: Baseline cache isolation failed: {msg}. Aborting run to prevent invalid benchmark results.[/bold red]"
            )
            raise typer.Exit(code=1)
        c.print(f"[bold green]✓[/bold green] Baseline cache isolation verified: {msg}")


@app.command()
def run(
    url: Optional[str] = typer.Option(None, "--url", help="RAG service URL (default: RAG_APP_URL or http://localhost:8080)"),
    limit: Optional[int] = typer.Option(None, "--limit", "-l", help="Number of benchmark questions to evaluate"),
    hops: Optional[int] = typer.Option(None, "--hops", "-h", help="Filter by hop count (e.g. 2, 3, 4)"),
    sample: bool = typer.Option(False, "--sample", "-s", help="Use canonical 100-question sample (Thesis §4.2.1)"),
    modules: str = typer.Option("", "--modules", "-m", help="Comma-separated optimization modules (e.g. M1,M2 or S2B,SR)"),
    table_name: Optional[str] = typer.Option(None, "--table-name", help="Vector store table name override"),
    judge: str = typer.Option("none", "--judge", "-j", envvar="JUDGE_PROVIDER", help="Judge provider (claude, gemini, vertex, ollama, vllm, none)"),
    judge_model: Optional[str] = typer.Option(None, "--judge-model", envvar="JUDGE_MODEL", help="Judge model identifier"),
    warmup: int = typer.Option(2, "--warmup", "-w", help="Number of unmeasured warm-up queries to eliminate JVM JIT / TLS jitter"),
    debug: bool = typer.Option(False, "--debug", "-d", help="Enable verbose debug logging"),
    output: Optional[str] = typer.Option(None, "--output", "-o", help="Path to save evaluation results JSONL"),
    top_k: Optional[int] = typer.Option(None, "--top-k", help="Retrieval top-k override"),
    stream: bool = typer.Option(False, "--stream", help="Use client-side streaming query collector with real-time JSONL checkpointing"),
    judge_delay: Optional[float] = typer.Option(
        None,
        "--judge-delay",
        help="Pacing delay in seconds between judge calls to prevent 429 rate limits (default: 1.5s for cloud judges like Claude/Gemini, 0s for vLLM/Ollama)",
    ),
    timeout: float = typer.Option(300.0, "--timeout", help="Timeout in seconds for RAG service requests (default: 300s)"),
    skip_cache_check: bool = typer.Option(False, "--skip-cache-check", help="Skip pre-flight prompt cache verification"),
) -> None:
    """Executes a benchmark evaluation run against the Spring Boot RAG service."""
    rag_url = url or os.getenv("RAG_APP_URL", "http://localhost:8080")
    client = RagBenchmarkClient(base_url=rag_url, debug=debug, timeout=timeout)

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

    # Pre-flight prompt cache check
    if not skip_cache_check:
        _verify_cache_preflight(
            client=client,
            modules=module_list,
            table_name=table_name,
            console=console,
        )

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
    effective_delay: float = _resolve_judge_delay(judge, judge_delay)

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
    if str(out_path) not in ("/dev/null", "nul"):
        _save_jsonl(processed_records, out_path)
        console.print(f"\n[bold green]✓ Benchmark run saved to:[/bold green] [cyan]{out_path}[/cyan]")


@app.command("test-judge")
def test_judge(
    judge: str = typer.Option("claude", "--judge", "-j", envvar="JUDGE_PROVIDER", help="Judge provider: claude, gemini, vertex, ollama, vllm, none"),
    judge_model: Optional[str] = typer.Option(None, "--judge-model", envvar="JUDGE_MODEL", help="Judge model identifier"),
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


@app.command("test-cache")
def test_cache(
    url: Optional[str] = typer.Option(None, "--url", help="RAG service URL (default: RAG_APP_URL or http://localhost:8080)"),
    models: Optional[str] = typer.Option(None, "--models", help="Comma-separated models to prewarm"),
    table_name: Optional[str] = typer.Option(None, "--table-name", help="Vector store table name override"),
    timeout: float = typer.Option(300.0, "--timeout", help="Timeout in seconds for RAG service requests"),
    debug: bool = typer.Option(False, "--debug", "-d", help="Enable verbose debug logging"),
) -> None:
    """Verifies prompt cache isolation, explicit prewarming, and cache hit integrity."""
    rag_url = url or os.getenv("RAG_APP_URL", "http://localhost:8080")
    client = RagBenchmarkClient(base_url=rag_url, debug=debug, timeout=timeout)

    console.print(Panel.fit(
        f"[bold cyan]Prompt Cache Verification & Prewarming Suite[/bold cyan]\n"
        f"Service URL: [green]{rag_url}[/green]\n"
        f"Models: [yellow]{models or 'Default (server configured)'}[/yellow]\n"
        f"Table: [yellow]{table_name or 'Default'}[/yellow] | Timeout: {timeout}s",
        title="Cache Test Configuration",
    ))

    # Health check
    if not client.health_check():
        console.print(f"[bold red]CRITICAL: RAG service at {rag_url} failed health check. Aborting.[/bold red]")
        raise typer.Exit(code=1)
    console.print("[bold green]✓[/bold green] Service health check passed.")

    model_list = [m.strip() for m in models.split(",") if m.strip()] if models else None

    # Phase 1: Baseline cache isolation
    console.print("\n[bold]Phase 1:[/bold] Verifying baseline cache isolation (implicit cache must be disabled)...")
    try:
        res1 = client.verify_cache_behavior(expect_cached=False, table_name=table_name)
        if isinstance(res1, (tuple, list)) and len(res1) >= 2:
            ok1, msg1 = bool(res1[0]), str(res1[1])
        elif isinstance(res1, bool):
            ok1, msg1 = res1, "OK" if res1 else "Baseline cache isolation failed"
        else:
            ok1, msg1 = True, "Verified (mock)"
    except Exception as exc:
        ok1, msg1 = False, str(exc)
    if ok1:
        console.print(f"  [bold green]✓[/bold green] {msg1}")
    else:
        console.print(f"  [bold red]✗[/bold red] {msg1}")

    # Phase 2: Prewarm prompt cache
    console.print("\n[bold]Phase 2:[/bold] Prewarming prompt cache on server...")
    try:
        prewarm_res = client.prewarm_cache(models=model_list)
        ok2 = True
        msg2 = f"Prewarm successful: {prewarm_res}"
        console.print("  [bold green]✓[/bold green] Prompt cache prewarmed successfully.")
    except Exception as exc:
        ok2 = False
        msg2 = f"Prewarm failed: {exc}"
        console.print(f"  [bold red]✗[/bold red] {msg2}")

    # Phase 3: M3 explicit cache hit
    console.print("\n[bold]Phase 3:[/bold] Verifying M3 explicit prompt cache hit...")
    try:
        res3 = client.verify_cache_behavior(expect_cached=True, table_name=table_name)
        if isinstance(res3, (tuple, list)) and len(res3) >= 2:
            ok3, msg3 = bool(res3[0]), str(res3[1])
        elif isinstance(res3, bool):
            ok3, msg3 = res3, "OK" if res3 else "Explicit cache hit verification failed"
        else:
            ok3, msg3 = True, "Verified (mock)"
    except Exception as exc:
        ok3, msg3 = False, str(exc)
    if ok3:
        console.print(f"  [bold green]✓[/bold green] {msg3}")
    else:
        console.print(f"  [bold red]✗[/bold red] {msg3}")

    # Phase 4: Clear prompt cache
    console.print("\n[bold]Phase 4:[/bold] Clearing prompt cache resources...")
    try:
        clear_res = client.clear_cache()
        ok4 = True
        msg4 = f"Cache cleared: {clear_res}"
        console.print("  [bold green]✓[/bold green] Prompt cache cleared successfully.")
    except Exception as exc:
        ok4 = False
        msg4 = f"Clear cache failed: {exc}"
        console.print(f"  [bold red]✗[/bold red] {msg4}")

    # Rich summary table
    summary_table = Table(title="Prompt Cache Verification Summary")
    summary_table.add_column("Phase", style="bold")
    summary_table.add_column("Status", justify="center")
    summary_table.add_column("Details")

    summary_table.add_row("Phase 1: Baseline Isolation", "[green]PASS[/green]" if ok1 else "[red]FAIL[/red]", str(msg1))
    summary_table.add_row("Phase 2: Cache Prewarm", "[green]PASS[/green]" if ok2 else "[red]FAIL[/red]", str(msg2))
    summary_table.add_row("Phase 3: M3 Explicit Cache Hit", "[green]PASS[/green]" if ok3 else "[red]FAIL[/red]", str(msg3))
    summary_table.add_row("Phase 4: Cache Cleanup", "[green]PASS[/green]" if ok4 else "[red]FAIL[/red]", str(msg4))

    console.print("\n")
    console.print(summary_table)

    if not (ok1 and ok2 and ok3 and ok4):
        console.print("\n[bold red]Prompt cache verification suite failed.[/bold red]")
        raise typer.Exit(code=1)

    console.print("\n[bold green]All prompt cache verification stages passed successfully![/bold green]")


@app.command()
def evaluate(
    input_file: Path = typer.Option(..., "--input", "-i", help="Input JSONL file or directory containing run JSONL files"),
    judge: str = typer.Option("claude", "--judge", "-j", envvar="JUDGE_PROVIDER", help="Judge provider: claude, gemini, vertex, ollama, vllm, none"),
    judge_model: Optional[str] = typer.Option(None, "--judge-model", envvar="JUDGE_MODEL", help="Judge model identifier"),
    output: Optional[Path] = typer.Option(None, "--output", "-o", help="Output JSONL path (or target directory if input is directory; ignored if --inplace is set)"),
    inplace: bool = typer.Option(False, "--inplace", help="Update input file(s) in place with evaluated metrics"),
    resume: bool = typer.Option(True, "--resume/--no-resume", help="Skip samples that already have non-surrogate RAGAS scores (default: True)"),
    force: bool = typer.Option(False, "--force", "-f", help="Force re-evaluation of all samples even if already evaluated"),
    judge_delay: Optional[float] = typer.Option(
        None,
        "--judge-delay",
        help="Pacing delay in seconds between judge calls to prevent 429 rate limits (default: 1.5s for cloud judges like Claude/Gemini, 0s for vLLM/Ollama)",
    ),
    pricing_tier: str = typer.Option("gemini-3.8-flash", "--pricing-tier", help="Default pricing tier for operational cost calculations"),
    workers: int = typer.Option(1, "--workers", "-w", help="Number of concurrent worker threads for parallel evaluation (default: 1)"),
) -> None:
    """Computes EM, F1, RAGAS metrics, and Composite Q on previously gathered run records."""
    target_path = Path(input_file)
    if target_path.is_dir():
        def _sort_key(p: Path) -> tuple[int, str]:
            m = re.search(r"[Cc](\d+)", p.stem)
            return (int(m.group(1)) if m else 999, p.name)

        files_to_process = sorted(target_path.glob("run_*.jsonl"), key=_sort_key)
        if not files_to_process:
            files_to_process = sorted(target_path.glob("*.jsonl"), key=_sort_key)
        if not files_to_process:
            console.print(f"[red]No JSONL run files found in directory: {target_path}[/red]")
            return
        console.print(f"Found [bold]{len(files_to_process)}[/bold] run files to evaluate in [cyan]{target_path}[/cyan].")
    elif target_path.is_file():
        files_to_process = [target_path]
    else:
        raise typer.BadParameter(f"Input path not found: {input_file}")

    effective_delay: float = _resolve_judge_delay(judge, judge_delay)
    if workers > 1:
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
        if workers > 1:
            console.print(f"[bold cyan]Parallel evaluation enabled with {workers} worker threads.[/bold cyan]")

    all_evaluated_summary: dict[str, list[dict[str, Any]]] = {}

    for file_idx, cur_file in enumerate(files_to_process, 1):
        records = _load_jsonl(cur_file)
        console.print(f"\n[bold cyan]=== [{file_idx}/{len(files_to_process)}] Processing: {cur_file.name} ({len(records)} records) ===[/bold cyan]")

        # Determine destination path
        if inplace or (target_path.is_dir() and output is None):
            out_path = cur_file
        elif output is not None and target_path.is_dir():
            out_path = output / cur_file.name
        elif output is not None:
            out_path = output
        else:
            out_path = cur_file.with_name(f"{cur_file.stem}_evaluated.jsonl")

        out_path.parent.mkdir(parents=True, exist_ok=True)
        tmp_path = out_path.with_name(f".{out_path.name}.eval_tmp")

        # Check for existing partial checkpoint if resuming
        evaluated_records: list[dict[str, Any]] = []
        if resume and not force and tmp_path.exists():
            try:
                evaluated_records = _load_jsonl(tmp_path)
                console.print(f"  [dim green]Found checkpoint with {len(evaluated_records)} evaluated records in {tmp_path.name}. Resuming...[/dim green]")
            except Exception:
                evaluated_records = []

        start_idx = len(evaluated_records)
        # If tmp checkpoint wasn't found or was reset, start fresh
        if start_idx == 0 and tmp_path.exists():
            tmp_path.unlink()

        items_to_process = list(enumerate(records))[start_idx:]
        if workers > 1 and ragas_evaluator is not None and len(items_to_process) > 0:
            import threading
            from concurrent.futures import ThreadPoolExecutor

            thread_local = threading.local()

            def _get_worker_evaluator():
                if not hasattr(thread_local, "evaluator"):
                    w_llm, w_emb = get_judge_components(provider=judge, model=judge_model)
                    thread_local.evaluator = RagasEvaluatorWrapper(llm=w_llm, embeddings=w_emb, delay_seconds=0.0)
                return thread_local.evaluator

            def _worker_task(idx_and_item):
                idx, itm = idx_and_item
                evaluator = _get_worker_evaluator()
                return _evaluate_item_with_feedback(
                    item=itm,
                    ragas_evaluator=evaluator,
                    judge=judge,
                    item_idx=idx + 1,
                    total_items=len(records),
                    pricing_tier=pricing_tier,
                    console=console,
                    resume=resume,
                    force=force,
                    show_spinner=False,
                )

            with ThreadPoolExecutor(max_workers=workers) as executor:
                for processed in executor.map(_worker_task, items_to_process):
                    evaluated_records.append(processed)
                    with tmp_path.open("a", encoding="utf-8") as f_tmp:
                        f_tmp.write(json.dumps(processed) + "\n")
        else:
            for idx in range(start_idx, len(records)):
                item = records[idx]
                processed = _evaluate_item_with_feedback(
                    item=item,
                    ragas_evaluator=ragas_evaluator,
                    judge=judge,
                    item_idx=idx + 1,
                    total_items=len(records),
                    pricing_tier=pricing_tier,
                    console=console,
                    resume=resume,
                    force=force,
                    show_spinner=True,
                )
                evaluated_records.append(processed)

                # Incremental checkpointing per item to safeguard against interrupts
                with tmp_path.open("a", encoding="utf-8") as f_tmp:
                    f_tmp.write(json.dumps(processed) + "\n")

                if ragas_evaluator and effective_delay > 0 and (idx + 1) < len(records):
                    _pacing_delay_with_spinner(effective_delay, current_idx=idx + 1, total_items=len(records), console=console)

        # File completed: atomically replace destination with checkpoint
        if tmp_path.exists():
            tmp_path.replace(out_path)

        config_name = cur_file.stem.replace("run_", "").replace("_evaluated", "")
        all_evaluated_summary[config_name] = evaluated_records

        display_summary_table({config_name: evaluated_records}, console=console)
        display_hop_breakdown_table(evaluated_records, console=console)
        console.print(f"[bold green]✓ Saved evaluated results to:[/bold green] [cyan]{out_path}[/cyan]")

    if len(files_to_process) > 1:
        console.print("\n[bold green]========================================================================[/bold green]")
        console.print("[bold green] Consolidated Evaluation Summary Across All Configurations[/bold green]")
        console.print("[bold green]========================================================================[/bold green]")
        display_summary_table(all_evaluated_summary, console=console)


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
        from src.export_latex import export_wilcoxon_tests_latex
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
        help="Optional subfolder inside tables-dir and figures-dir to isolate run outputs (e.g., 'sample_runs', 'musique', 'nordea', 'enterprise', 'comparisons', 'runs/<run_name>')",
    ),
    dataset_name: Optional[str] = typer.Option(
        None,
        "--dataset-name",
        "-d",
        help="Dataset name for figure titles and filenames (e.g. 'MuSiQue', 'Enterprise'). If not provided, inferred from subfolder or input.",
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

    if not dataset_name:
        sub_str = str(subfolder).lower() if subfolder else ""
        inp_str = str(input_target).lower()
        if "musique" in sub_str or "musique" in inp_str or "sample10" in sub_str or "sample10" in inp_str:
            dataset_name = "MuSiQue"
        elif "enterprise_closed" in sub_str or "enterprise_closed" in inp_str or "nordea_closed" in sub_str or "nordea_closed" in inp_str:
            dataset_name = "Enterprise Closed"
        elif "enterprise" in sub_str or "enterprise" in inp_str or "nordea" in sub_str or "nordea" in inp_str:
            dataset_name = "Enterprise"
        elif subfolder:
            dataset_name = subfolder.capitalize()

    slug = dataset_name.lower().replace(" ", "_") if dataset_name else (subfolder.lower() if subfolder else "")

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

    # Dynamic recalculation of query costs on loaded records using prompt_is_uncached=False
    for config_name, records in run_dict.items():
        for r in records:
            tu = r.get("tokenUsage") if isinstance(r.get("tokenUsage"), dict) else {}
            prompt_tokens = int(
                tu.get("promptTokens")
                or tu.get("prompt_tokens")
                or r.get("prompt_tokens")
                or 0
            )
            completion_tokens = int(
                tu.get("completionTokens")
                or tu.get("completion_tokens")
                or r.get("completion_tokens")
                or 0
            )
            modules = r.get("modulesApplied") or r.get("modulesRequested") or []
            has_m3 = (
                "M3" in modules
                or "PC" in modules
                or r.get("cacheStatus") in ("EXPLICIT_HIT", "EXPLICIT_MISS")
                or "m3" in config_name.lower()
                or (isinstance(r.get("runName"), str) and "m3" in r["runName"].lower())
            )

            cached_read_tokens = 0
            cache_write_tokens = 0
            if has_m3:
                cached_read_tokens = int(
                    tu.get("cachedTokens")
                    or tu.get("cached_tokens")
                    or r.get("cached_tokens")
                    or r.get("cachedTokens")
                    or 0
                )
                cache_write_tokens = int(
                    tu.get("cacheWriteTokens")
                    or tu.get("cache_write_tokens")
                    or r.get("cache_write_tokens")
                    or r.get("cacheWriteTokens")
                    or 0
                )
                if cached_read_tokens == 0 and cache_write_tokens == 0:
                    cache_meta = (
                        (r.get("cacheMetadata") or tu.get("cacheMetadata"))
                        if isinstance(r.get("cacheMetadata") or tu.get("cacheMetadata"), dict)
                        else {}
                    )
                    fallback_count = int(cache_meta.get("tokenCount") or cache_meta.get("token_count") or 0)
                    cache_status = str(
                        r.get("cacheStatus")
                        or r.get("cache_status")
                        or tu.get("cacheStatus")
                        or tu.get("cache_status")
                        or ""
                    ).upper()
                    if "HIT" in cache_status:
                        cached_read_tokens = fallback_count
                    elif "MISS" in cache_status:
                        cache_write_tokens = fallback_count

            routing_prompt_tokens = int(
                tu.get("routingPromptTokens")
                or tu.get("routing_prompt_tokens")
                or r.get("routingPromptTokens")
                or r.get("routing_prompt_tokens")
                or 0
            )
            routing_completion_tokens = int(
                tu.get("routingCompletionTokens")
                or tu.get("routing_completion_tokens")
                or r.get("routingCompletionTokens")
                or r.get("routing_completion_tokens")
                or 0
            )
            route_tokens = int(
                r.get("routingTokens")
                or r.get("routing_tokens")
                or r.get("route_tokens")
                or tu.get("routingTokens")
                or tu.get("routing_tokens")
                or tu.get("route_tokens")
                or (routing_prompt_tokens + routing_completion_tokens)
                or 0
            )

            rerank_docs = int(
                r.get("rerankDocs")
                or r.get("rerank_docs")
                or tu.get("rerankDocs")
                or tu.get("rerank_docs")
                or 0
            )
            if rerank_docs == 0 and (
                "M2" in modules
                or "SR" in modules
                or "m2" in config_name.lower()
                or any("m2" in str(m).lower() for m in modules)
                or (isinstance(r.get("runName"), str) and "m2" in r["runName"].lower())
            ):
                rerank_docs = 50

            embed_tokens = int(
                r.get("embedTokens")
                or r.get("embed_tokens")
                or tu.get("embedTokens")
                or tu.get("embed_tokens")
                or 0
            )
            if embed_tokens == 0:
                q_text = str(r.get("question") or r.get("query") or "")
                embed_tokens = max(1, len(q_text.split())) if q_text else 20

            model_used = r.get("modelUsed") or r.get("model_used")
            tier = model_used if model_used else "gemini-3.8-flash"

            r["cost_usd"] = compute_cost(
                prompt_tokens=prompt_tokens,
                completion_tokens=completion_tokens,
                cached_tokens=cached_read_tokens,
                cache_write_tokens=cache_write_tokens,
                routing_prompt_tokens=routing_prompt_tokens,
                routing_completion_tokens=routing_completion_tokens,
                routing_tokens=route_tokens,
                rerank_docs=rerank_docs,
                embed_tokens=embed_tokens,
                tier=tier,
                prompt_is_uncached=False if prompt_tokens >= cached_read_tokens else True,
            )
            r["prompt_tokens"] = prompt_tokens
            r["completion_tokens"] = completion_tokens
            r["cached_tokens"] = cached_read_tokens
            r["cache_write_tokens"] = cache_write_tokens

    console.print(f"Exporting thesis artifacts for [bold]{len(run_dict)}[/bold] configurations (Dataset: [yellow]{dataset_name or 'Default'}[/yellow])...")

    from src.export_latex import (
        export_benchmark_summary_latex,
        export_cost_latex,
        export_hop_breakdown_latex,
        export_latency_latex,
        export_pareto_latex,
        export_quality_enterprise_latex,
        export_quality_musique_latex,
        export_wilcoxon_decomposed_latex,
        export_wilcoxon_tests_latex,
    )
    from src.plot_results import (
        plot_cost_breakdown,
        plot_latency_breakdown,
        plot_pareto_frontier,
        plot_pareto_frontier_3d,
        plot_quality_delta_matrix,
        plot_quality_lollipop,
        plot_ragas_metrics_radar,
    )

    # 1. Export LaTeX Tables
    t_summary = export_benchmark_summary_latex(run_dict, tables_dir / "benchmark_summary.tex")
    console.print(f"  [green]✓[/green] Table: [cyan]{tables_dir / 'benchmark_summary.tex'}[/cyan]")

    # Hop breakdown for the first or combined run
    primary_records = next(iter(run_dict.values()))
    t_hops = export_hop_breakdown_latex(
        primary_records,
        tables_dir / "hop_breakdown.tex",
        dataset_name=dataset_name or "MuSiQue",
    )
    console.print(f"  [green]✓[/green] Table: [cyan]{tables_dir / 'hop_breakdown.tex'}[/cyan]")

    # Wilcoxon hypothesis tests if baseline C0 is present
    baseline_key = next((k for k in run_dict.keys() if "c0" in k.lower() or "base" in k.lower()), None)
    comparisons = None
    if baseline_key and len(run_dict) > 1:
        base_recs = run_dict[baseline_key]
        treatments = {k: v for k, v in run_dict.items() if k != baseline_key}
        comparisons = batch_compare_configurations(base_recs, treatments, family_scope=family_scope)
        export_wilcoxon_tests_latex(comparisons, tables_dir / "wilcoxon_tests.tex")
        console.print(f"  [green]✓[/green] Table: [cyan]{tables_dir / 'wilcoxon_tests.tex'}[/cyan]")
        export_wilcoxon_decomposed_latex(comparisons, tables_dir)
        console.print(f"  [green]✓[/green] Tables (Decomposed): [cyan]{tables_dir / 'wilcoxon_rq*.tex'}[/cyan]")

    # Modular thesis tables (Latency, Cost, Quality, Pareto)
    subfolder_str = str(subfolder).lower() if subfolder else ""
    if slug == "musique":
        export_latency_latex(
            run_dict,
            tables_dir / "tab_latency_musique.tex",
            comparisons=comparisons,
            label="tab:latency_musique",
            dataset_name=dataset_name or "MuSiQue",
        )
        console.print(f"  [green]✓[/green] Table: [cyan]{tables_dir / 'tab_latency_musique.tex'}[/cyan]")
        export_cost_latex(
            run_dict,
            tables_dir / "tab_cost_musique.tex",
            comparisons=comparisons,
            label="tab:cost_musique",
            dataset_name=dataset_name or "MuSiQue",
        )
        console.print(f"  [green]✓[/green] Table: [cyan]{tables_dir / 'tab_cost_musique.tex'}[/cyan]")
        export_quality_musique_latex(
            run_dict,
            tables_dir / "tab_quality_musique.tex",
            comparisons=comparisons,
            label="tab:quality_musique",
            dataset_name=dataset_name or "MuSiQue",
        )
        console.print(f"  [green]✓[/green] Table: [cyan]{tables_dir / 'tab_quality_musique.tex'}[/cyan]")
        export_pareto_latex(
            run_dict,
            tables_dir / "tab_pareto_musique.tex",
            label="tab:pareto_musique",
            dataset_name=dataset_name or "MuSiQue",
        )
        console.print(f"  [green]✓[/green] Table: [cyan]{tables_dir / 'tab_pareto_musique.tex'}[/cyan]")
    elif subfolder_str == "enterprise_closed" or "enterprise_closed" in slug:
        export_latency_latex(
            run_dict,
            tables_dir / "tab_latency_enterprise.tex",
            comparisons=comparisons,
            label="tab:latency_enterprise",
            dataset_name=dataset_name or "Enterprise Closed",
        )
        console.print(f"  [green]✓[/green] Table: [cyan]{tables_dir / 'tab_latency_enterprise.tex'}[/cyan]")
        export_cost_latex(
            run_dict,
            tables_dir / "tab_cost_enterprise.tex",
            comparisons=comparisons,
            label="tab:cost_enterprise",
            dataset_name=dataset_name or "Enterprise Closed",
        )
        console.print(f"  [green]✓[/green] Table: [cyan]{tables_dir / 'tab_cost_enterprise.tex'}[/cyan]")
        export_quality_enterprise_latex(
            run_dict,
            tables_dir / "tab_quality_enterprise.tex",
            comparisons=comparisons,
            label="tab:quality_enterprise",
        )
        console.print(f"  [green]✓[/green] Table: [cyan]{tables_dir / 'tab_quality_enterprise.tex'}[/cyan]")
        export_pareto_latex(
            run_dict,
            tables_dir / "tab_pareto_enterprise.tex",
            label="tab:pareto_enterprise",
            dataset_name=dataset_name or "Enterprise Closed",
        )
        console.print(f"  [green]✓[/green] Table: [cyan]{tables_dir / 'tab_pareto_enterprise.tex'}[/cyan]")
    elif subfolder_str == "enterprise" or slug == "enterprise":
        export_latency_latex(
            run_dict,
            tables_dir / "tab_latency_enterprise.tex",
            comparisons=comparisons,
            label="tab:latency_enterprise_raw",
            dataset_name=dataset_name or "Enterprise",
        )
        console.print(f"  [green]✓[/green] Table: [cyan]{tables_dir / 'tab_latency_enterprise.tex'}[/cyan]")
        export_cost_latex(
            run_dict,
            tables_dir / "tab_cost_enterprise.tex",
            comparisons=comparisons,
            label="tab:cost_enterprise_raw",
            dataset_name=dataset_name or "Enterprise",
        )
        console.print(f"  [green]✓[/green] Table: [cyan]{tables_dir / 'tab_cost_enterprise.tex'}[/cyan]")
        export_quality_enterprise_latex(
            run_dict,
            tables_dir / "tab_quality_enterprise.tex",
            comparisons=comparisons,
            label="tab:quality_enterprise_raw",
        )
        console.print(f"  [green]✓[/green] Table: [cyan]{tables_dir / 'tab_quality_enterprise.tex'}[/cyan]")
        export_pareto_latex(
            run_dict,
            tables_dir / "tab_pareto_enterprise.tex",
            label="tab:pareto_enterprise_raw",
            dataset_name=dataset_name or "Enterprise",
        )
        console.print(f"  [green]✓[/green] Table: [cyan]{tables_dir / 'tab_pareto_enterprise.tex'}[/cyan]")
    elif slug:
        export_latency_latex(
            run_dict,
            tables_dir / f"tab_latency_{slug}.tex",
            comparisons=comparisons,
            label=f"tab:latency_{slug}",
            dataset_name=dataset_name or "Default",
        )
        export_cost_latex(
            run_dict,
            tables_dir / f"tab_cost_{slug}.tex",
            comparisons=comparisons,
            label=f"tab:cost_{slug}",
            dataset_name=dataset_name or "Default",
        )
        export_quality_musique_latex(
            run_dict,
            tables_dir / f"tab_quality_{slug}.tex",
            comparisons=comparisons,
            label=f"tab:quality_{slug}",
            dataset_name=dataset_name or "Default",
        )
        export_pareto_latex(
            run_dict,
            tables_dir / f"tab_pareto_{slug}.tex",
            label=f"tab:pareto_{slug}",
            dataset_name=dataset_name or "Default",
        )

    # 2. Export Publication Vector Figures
    slug_suffix = f"_{slug}" if slug else ""

    p1 = plot_pareto_frontier(run_dict, figures_dir / f"pareto_frontier{slug_suffix}.pdf", dataset_name=dataset_name)
    console.print(f"  [green]✓[/green] Figure: [cyan]{p1}[/cyan]")

    p2 = plot_pareto_frontier_3d(run_dict, figures_dir / f"pareto_frontier_3d{slug_suffix}.pdf", dataset_name=dataset_name)
    console.print(f"  [green]✓[/green] Figure: [cyan]{p2}[/cyan]")

    p3 = plot_latency_breakdown(run_dict, figures_dir / f"latency_breakdown{slug_suffix}.pdf", dataset_name=dataset_name)
    console.print(f"  [green]✓[/green] Figure: [cyan]{p3}[/cyan]")

    p4 = plot_cost_breakdown(run_dict, figures_dir / f"cost_breakdown{slug_suffix}.pdf", dataset_name=dataset_name)
    console.print(f"  [green]✓[/green] Figure: [cyan]{p4}[/cyan]")

    p5 = plot_ragas_metrics_radar(run_dict, figures_dir / f"quality_radar{slug_suffix}.pdf", dataset_name=dataset_name)
    console.print(f"  [green]✓[/green] Figure (legacy radar): [cyan]{p5}[/cyan]")

    p6 = plot_quality_lollipop(run_dict, figures_dir / f"quality_lollipop{slug_suffix}.pdf", dataset_name=dataset_name)
    console.print(f"  [green]✓[/green] Figure [bold](PRIMARY quality)[/bold]: [cyan]{p6}[/cyan]")

    p7 = plot_quality_delta_matrix(run_dict, figures_dir / f"quality_delta_matrix{slug_suffix}.pdf", dataset_name=dataset_name)
    console.print(f"  [green]✓[/green] Figure (executive heatmap): [cyan]{p7}[/cyan]")

    # For un-slugged root export, provide backwards compatibility alias for legacy ragas_metrics_radar.pdf
    if not slug:
        if (figures_dir / "ragas_metrics_radar.pdf").resolve() != p5.resolve():
            shutil.copy2(p5, figures_dir / "ragas_metrics_radar.pdf")

    console.print("\n[bold green]All thesis tables and figures exported successfully![/bold green]")
    console.print("[dim]Quality figure standard: quality_lollipop (primary) + quality_radar (legacy).[/dim]")


@app.command("query")
def query_cmd(
    query_text: str = typer.Argument(..., help="Query or question to ask the RAG pipeline"),
    table_name: str = typer.Option("vector_store_nordea", "--table-name", "-t", help="Target vector store table"),
    modules: str = typer.Option("", "--modules", "-m", help="Comma-separated optimization modules"),
    judge: str = typer.Option("claude", "--judge", "-j", envvar="JUDGE_PROVIDER", help="Judge provider: claude, gemini, vertex, ollama, vllm, none"),
    judge_model: Optional[str] = typer.Option(None, "--judge-model", envvar="JUDGE_MODEL", help="Judge model identifier"),
    ground_truth: Optional[str] = typer.Option(None, "--ground-truth", "-g", help="Optional reference answer"),
    output: Optional[Path] = typer.Option(None, "--output", "-o", help="Optional path to save evaluation result JSONL"),
    url: Optional[str] = typer.Option(None, "--url", help="RAG service URL"),
    debug: bool = typer.Option(False, "--debug", "-d", help="Enable verbose debug logging"),
    timeout: float = typer.Option(300.0, "--timeout", help="Timeout in seconds for RAG service requests (default: 300s)"),
) -> None:
    """Executes a single RAG query against any vector table and evaluates it with RAGAS."""
    rag_url = url or os.getenv("RAG_APP_URL", "http://localhost:8080")
    client = RagBenchmarkClient(base_url=rag_url, debug=debug, timeout=timeout)

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
    judge: str = typer.Option("claude", "--judge", "-j", envvar="JUDGE_PROVIDER", help="Judge provider: claude, gemini, vertex, ollama, vllm, none"),
    judge_model: Optional[str] = typer.Option(None, "--judge-model", envvar="JUDGE_MODEL", help="Judge model identifier"),
    output: Optional[Path] = typer.Option(None, "--output", "-o", help="Output JSONL path for evaluated results"),
    url: Optional[str] = typer.Option(None, "--url", help="RAG service URL"),
    debug: bool = typer.Option(False, "--debug", "-d", help="Enable verbose debug logging"),
    judge_delay: Optional[float] = typer.Option(
        None,
        "--judge-delay",
        help="Pacing delay in seconds between judge calls to prevent 429 rate limits (default: 1.5s for cloud judges like Claude/Gemini, 0s for vLLM/Ollama)",
    ),
    timeout: float = typer.Option(300.0, "--timeout", help="Timeout in seconds for RAG service requests (default: 300s)"),
    skip_cache_check: bool = typer.Option(False, "--skip-cache-check", help="Skip pre-flight prompt cache verification"),
) -> None:
    """Runs a batch of questions from a file against a vector store table and evaluates with RAGAS."""
    rag_url = url or os.getenv("RAG_APP_URL", "http://localhost:8080")
    client = RagBenchmarkClient(base_url=rag_url, debug=debug, timeout=timeout)

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

    # Pre-flight prompt cache check
    if not skip_cache_check:
        _verify_cache_preflight(
            client=client,
            modules=module_list,
            table_name=table_name,
            console=console,
        )

    effective_delay: float = _resolve_judge_delay(judge, judge_delay)

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
        gt = (
            rec.get("ground_truth")
            or rec.get("reference")
            or rec.get("groundTruthAnswer")
            or rec.get("answer")
            or ""
        )
        aliases = (
            rec.get("aliases")
            or rec.get("groundTruthAliases")
            or rec.get("answer_aliases")
            or rec.get("answerAliases")
            or []
        )
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
        if "answerable" in rec:
            item["answerable"] = rec["answerable"]
        if "question_decomposition" in rec:
            item["question_decomposition"] = rec["question_decomposition"]
        if "hopCount" in rec and rec["hopCount"] is not None:
            item["hopCount"] = rec["hopCount"]
        elif "hop_count" in rec and rec["hop_count"] is not None:
            item["hopCount"] = rec["hop_count"]
        elif "id" in rec:
            rec_id = str(rec["id"]).lower()
            if rec_id.startswith("1hop"):
                item["hopCount"] = 1
            elif rec_id.startswith("2hop"):
                item["hopCount"] = 2
            elif rec_id.startswith("3hop"):
                item["hopCount"] = 3
            elif rec_id.startswith("4hop"):
                item["hopCount"] = 4
            elif rec_id.startswith("ood"):
                item["hopCount"] = 0
            elif "question_decomposition" in rec and rec["question_decomposition"]:
                item["hopCount"] = len(rec["question_decomposition"])
        elif "question_decomposition" in rec and rec["question_decomposition"]:
            item["hopCount"] = len(rec["question_decomposition"])

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
        # Display Rich Summary and Hop Breakdown Tables
        console.print()
        display_summary_table({f"{table_name} [{module_label}]": evaluated_records}, console=console)
        console.print()
        display_hop_breakdown_table(evaluated_records, console=console)

        # Ensure final file is cleanly written
        if str(out_path) not in ("/dev/null", "nul"):
            _save_jsonl(evaluated_records, out_path)
            console.print(f"\n[bold green]✓ Evaluated results saved to:[/bold green] [cyan]{out_path}[/cyan]")
        else:
            console.print("\n[dim]Console-only run complete (output discarded to /dev/null).[/dim]")
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


@app.command("build-nordea-musique-dataset")
def build_nordea_musique_dataset(
    csv_file: Path = typer.Option(
        Path("data/nordea/qs_fi.csv"),
        "--csv-file",
        "-c",
        help="Path to questions CSV file (e.g. qs_fi.csv)",
    ),
    corpus_dir: Path = typer.Option(
        Path("../rag-benchmark/data/nordea/md"),
        "--corpus-dir",
        "-d",
        help="Path to directory containing crawled personal banking markdown documents (pages/ and pdfs/)",
    ),
    output: Path = typer.Option(
        Path("data/nordea/nordea_ans_v1.0_dev.jsonl"),
        "--output",
        "-o",
        help="Target JSONL file path conforming to MuSiQue schema",
    ),
    size: int = typer.Option(
        100,
        "--size",
        "-s",
        help="Target number of benchmark questions (Thesis §4.4: 40% single-hop, 40% multi-hop, 20% out-of-domain)",
    ),
    judge_model: str = typer.Option(
        "gemini-3.5-flash",
        "--judge-model",
        "-m",
        help="Synthesizer/verifier LLM model on Vertex AI global endpoint (default: gemini-3.5-flash)",
    ),
    limit_input: Optional[int] = typer.Option(
        None,
        "--limit-input",
        help="Limit number of candidate input questions to process (useful for fast smoke runs)",
    ),
    delay: float = typer.Option(
        2.0,
        "--delay",
        help="Pacing delay in seconds between Vertex AI LLM calls to prevent 429 rate limit errors (default: 2.0s)",
    ),
    multihop_csv: Path = typer.Option(
        Path("data/nordea/qs_multihop_fi.csv"),
        "--multihop-csv",
        "-mhc",
        help="Path to composite multi-hop questions CSV (composite_question,sub_question_1,sub_question_2)",
    ),
    shuffle: bool = typer.Option(
        True,
        "--shuffle/--no-shuffle",
        help="Randomize candidate question order to sample diversely across banking topics (default: True)",
    ),
    seed: int = typer.Option(
        42,
        "--seed",
        help="Random seed for reproducible question shuffling (default: 42)",
    ),
    resume: bool = typer.Option(
        True,
        "--resume/--no-resume",
        help="Resume incrementally if the output file already contains partially generated questions (default: True)",
    ),
) -> None:
    """Builds a MuSiQue-schema enterprise benchmark dataset from Finnish questions and Nordea corpus."""
    from src.dataset_builder import (
        CorpusIndexer,
        NordeaMusiqueDatasetBuilder,
        load_multihop_questions_from_csv,
        load_questions_from_csv,
    )

    # Resolve paths relative to working directory / project root
    resolved_csv = csv_file if csv_file.is_absolute() else (Path.cwd() / csv_file).resolve()
    if not resolved_csv.exists():
        alt_csv = Path(__file__).resolve().parent.parent / "data" / "nordea" / "qs_fi.csv"
        if alt_csv.exists():
            resolved_csv = alt_csv

    resolved_corpus = corpus_dir if corpus_dir.is_absolute() else (Path.cwd() / corpus_dir).resolve()
    if not resolved_corpus.exists():
        alt_corpus = Path(__file__).resolve().parent.parent.parent / "rag-benchmark" / "data" / "nordea" / "md"
        if alt_corpus.exists():
            resolved_corpus = alt_corpus

    resolved_mh = multihop_csv if multihop_csv.is_absolute() else (Path.cwd() / multihop_csv).resolve()
    if not resolved_mh.exists():
        alt_mh = Path(__file__).resolve().parent.parent / "data" / "nordea" / "qs_multihop_fi.csv"
        if alt_mh.exists():
            resolved_mh = alt_mh

    resolved_out = output if output.is_absolute() else (Path.cwd() / output).resolve()

    console.print(f"Loading single-hop questions from: [cyan]{resolved_csv}[/cyan]")
    questions = load_questions_from_csv(resolved_csv)
    console.print(f"Loaded [bold]{len(questions)}[/bold] unique single-hop questions from CSV.")

    multihop_questions = []
    if resolved_mh.exists():
        console.print(f"Loading multi-hop questions from: [cyan]{resolved_mh}[/cyan]")
        multihop_questions = load_multihop_questions_from_csv(resolved_mh)
        console.print(f"Loaded [bold]{len(multihop_questions)}[/bold] composite multi-hop questions from CSV.")
    else:
        console.print(f"[yellow]Multi-hop CSV not found at {resolved_mh}. Multi-hop pool will be empty.[/yellow]")

    console.print(f"Indexing corpus from: [cyan]{resolved_corpus}[/cyan]")
    indexer = CorpusIndexer(resolved_corpus)
    console.print(f"Indexed [bold]{len(indexer.paragraphs)}[/bold] substantive paragraphs.")
    if delay > 0:
        console.print(f"Pacing delay: [yellow]{delay:.1f}s[/yellow] between Vertex AI LLM calls to prevent 429 quota exhaustion.")

    builder = NordeaMusiqueDatasetBuilder(
        corpus_indexer=indexer,
        judge_model=judge_model,
        delay_seconds=delay,
        console=console,
    )

    builder.build_dataset(
        questions=questions,
        output_file=resolved_out,
        target_size=size,
        limit_input=limit_input,
        multihop_questions=multihop_questions,
        shuffle=shuffle,
        seed=seed,
        resume=resume,
    )


if __name__ == "__main__":
    app()

