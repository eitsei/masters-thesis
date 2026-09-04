"""Streaming benchmark query collector.

Executes benchmark questions against the Spring Boot RAG application,
handles warm-up queries, checkpoints results to streaming JSONL files,
and supports normal (Rich progress bar) and debug (verbose micro-step timers) modes.
"""

from __future__ import annotations

import json
import logging
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

from rich.console import Console
from rich.progress import (
    BarColumn,
    MofNCompleteColumn,
    Progress,
    SpinnerColumn,
    TaskProgressColumn,
    TextColumn,
    TimeElapsedColumn,
    TimeRemainingColumn,
)

from .client import RagClient

logger = logging.getLogger(__name__)


class BenchmarkCollector:
    """Orchestrates query collection against the RAG pipeline."""

    def __init__(
        self,
        client: RagClient,
        output_dir: str | Path = "runs",
        debug: bool = False,
        console: Console | None = None,
    ) -> None:
        self.client = client
        self.output_dir = Path(output_dir)
        self.debug = debug
        self.console = console or Console()

    def _get_question_id(self, item: Any) -> str:
        if isinstance(item, dict):
            return str(item.get("id") or item.get("questionId") or "")
        return str(item)

    def _load_existing_checkpoint(self, file_path: Path) -> list[dict[str, Any]]:
        records: list[dict[str, Any]] = []
        if not file_path.exists():
            return records
        with open(file_path, "r", encoding="utf-8") as f:
            for line in f:
                line_str = line.strip()
                if line_str:
                    try:
                        records.append(json.loads(line_str))
                    except json.JSONDecodeError:
                        continue
        return records

    def run_warmup(
        self,
        questions: list[Any],
        warmup_queries: int = 2,
        modules: Iterable[str] | None = None,
        table_name: str | None = None,
        top_k: int | None = None,
    ) -> None:
        """Execute unmeasured warm-up queries (Thesis §4.1) prior to benchmark data collection."""
        if warmup_queries <= 0 or not questions:
            return

        if not self.debug:
            self.console.print(
                f"[bold yellow]Warming up RAG pipeline ({warmup_queries} unmeasured queries)...[/bold yellow]"
            )

        for i in range(warmup_queries):
            target = questions[i % len(questions)]
            q_id = self._get_question_id(target)
            t0 = time.perf_counter()
            try:
                self.client.query_question(
                    question_id=q_id,
                    modules=modules,
                    table_name=table_name,
                    top_k=top_k,
                )
                duration_ms = (time.perf_counter() - t0) * 1000.0
                if self.debug:
                    self.console.print(
                        f"[dim yellow][DEBUG] Warmup {i + 1}/{warmup_queries} completed for question '{q_id}' in {duration_ms:.1f} ms (unmeasured)[/dim yellow]"
                    )
            except Exception as exc:
                logger.warning(f"Warm-up query {i + 1} failed for question '{q_id}': {exc}")
                if self.debug:
                    self.console.print(
                        f"[red][DEBUG] Warmup query {i + 1} encountered error: {exc}[/red]"
                    )

    def collect(
        self,
        run_name: str,
        questions: list[Any] | None = None,
        modules: Iterable[str] | None = None,
        table_name: str | None = None,
        top_k: int | None = None,
        warmup_queries: int = 2,
        resume: bool = True,
    ) -> list[dict[str, Any]]:
        """Collect benchmark question executions and stream checkpoints to JSONL.

        Args:
            run_name: Name of run; determines checkpoint output file (`runs/<run_name>.jsonl`).
            questions: List of question dicts or question IDs. If None, fetches canonical sample.
            modules: Optimization modules to activate (e.g. ['M1', 'M2']).
            table_name: Vector store table override.
            top_k: Retrieval top-k override.
            warmup_queries: Number of unmeasured warm-up queries (default: 2 per Thesis §4.1).
            resume: If True, skips question IDs already recorded in an existing checkpoint file.

        Returns:
            List of all collected question result records (including resumed records).
        """
        self.output_dir.mkdir(parents=True, exist_ok=True)
        run_file = self.output_dir / f"{run_name}.jsonl"

        if questions is None:
            if not self.debug:
                self.console.print("[cyan]Fetching canonical benchmark sample from RAG application...[/cyan]")
            questions = self.client.get_canonical_sample()

        # Check existing checkpoint
        existing_records = self._load_existing_checkpoint(run_file) if resume else []
        completed_ids = {
            self._get_question_id(r) for r in existing_records if self._get_question_id(r)
        }

        pending_questions = [
            q for q in questions if self._get_question_id(q) not in completed_ids
        ]

        if resume and completed_ids:
            self.console.print(
                f"[green]Resuming run '{run_name}': {len(completed_ids)} already completed, "
                f"{len(pending_questions)} remaining.[/green]"
            )

        if not pending_questions:
            self.console.print(f"[bold green]Run '{run_name}' is already fully completed ({len(existing_records)} items).[/bold green]")
            return existing_records

        # Execute warm-ups
        self.run_warmup(
            questions=questions,
            warmup_queries=warmup_queries,
            modules=modules,
            table_name=table_name,
            top_k=top_k,
        )

        records = list(existing_records)
        module_list = list(modules) if modules is not None else []

        # Stream collection
        with open(run_file, "a", encoding="utf-8") as f_out:
            if self.debug:
                self.console.print(
                    f"\n[bold magenta]─── Starting Debug Benchmark Collection for '{run_name}' ({len(pending_questions)} questions) ───[/bold magenta]"
                )
                for idx, q in enumerate(pending_questions, start=1):
                    q_id = self._get_question_id(q)
                    t0 = time.perf_counter()
                    try:
                        res = self.client.query_question(
                            question_id=q_id,
                            modules=module_list,
                            table_name=table_name,
                            top_k=top_k,
                        )
                        client_roundtrip_ms = (time.perf_counter() - t0) * 1000.0
                    except Exception as exc:
                        self.console.print(f"[bold red][DEBUG ERROR] Query failed for '{q_id}': {exc}[/bold red]")
                        continue

                    record = self._build_record(res, run_name, module_list, client_roundtrip_ms)
                    f_out.write(json.dumps(record, ensure_ascii=False) + "\n")
                    f_out.flush()
                    records.append(record)

                    self._log_debug_item(idx, len(pending_questions), record, client_roundtrip_ms)
            else:
                with Progress(
                    SpinnerColumn(),
                    TextColumn("[progress.description]{task.description}"),
                    BarColumn(),
                    MofNCompleteColumn(),
                    TaskProgressColumn(),
                    TimeElapsedColumn(),
                    TimeRemainingColumn(),
                    console=self.console,
                ) as progress:
                    task = progress.add_task(f"Collecting [{run_name}]", total=len(pending_questions))
                    for q in pending_questions:
                        q_id = self._get_question_id(q)
                        t0 = time.perf_counter()
                        try:
                            res = self.client.query_question(
                                question_id=q_id,
                                modules=module_list,
                                table_name=table_name,
                                top_k=top_k,
                            )
                            client_roundtrip_ms = (time.perf_counter() - t0) * 1000.0
                        except Exception as exc:
                            logger.error(f"Failed query for question {q_id}: {exc}")
                            progress.advance(task)
                            continue

                        record = self._build_record(res, run_name, module_list, client_roundtrip_ms)
                        f_out.write(json.dumps(record, ensure_ascii=False) + "\n")
                        f_out.flush()
                        records.append(record)
                        progress.advance(task)

        if not self.debug:
            self.console.print(
                f"[bold green]Collection complete! Saved {len(records)} records to {run_file}[/bold green]"
            )
        return records

    def _build_record(
        self,
        res: dict[str, Any],
        run_name: str,
        modules_requested: list[str],
        client_roundtrip_ms: float,
    ) -> dict[str, Any]:
        record = dict(res)
        record["timestamp"] = datetime.now(timezone.utc).isoformat()
        record["runName"] = run_name
        record["modulesRequested"] = modules_requested
        record["clientRoundtripMs"] = round(client_roundtrip_ms, 2)
        return record

    def _log_debug_item(
        self,
        idx: int,
        total: int,
        record: dict[str, Any],
        roundtrip_ms: float,
    ) -> None:
        q_id = record.get("questionId") or record.get("id") or "N/A"
        q_text = record.get("question", "")
        answer = record.get("generatedAnswer", "")
        timings = record.get("timings") or {}
        retrieval_ms = timings.get("retrievalMs", 0)
        generation_ms = timings.get("generationMs", 0)
        total_latency_ms = record.get("totalLatencyMs", roundtrip_ms)
        tokens = record.get("tokenUsage") or {}
        prompt_tokens = tokens.get("promptTokens", 0)
        completion_tokens = tokens.get("completionTokens", 0)
        cached_tokens = tokens.get("cachedTokens", 0)
        total_tokens = tokens.get("totalTokens", 0)
        chunks = record.get("retrievedChunks") or []
        chunk_ids = [
            c.get("id") if isinstance(c, dict) else str(c) for c in chunks
        ]
        evidence_found = record.get("supportingEvidenceFound", False)

        preview_q = (q_text[:75] + "...") if len(q_text) > 75 else q_text
        preview_ans = (answer[:90] + "...") if len(answer) > 90 else answer

        self.console.print(f"[bold cyan]─── [{idx}/{total}] Question ID: {q_id} ───[/bold cyan]")
        self.console.print(f"  [bold]Query:[/bold] {preview_q}")
        self.console.print(f"  [bold]Answer:[/bold] [italic]{preview_ans}[/italic]")
        self.console.print(
            f"  [bold]Timers:[/bold] Retrieval: [yellow]{retrieval_ms}ms[/yellow] | "
            f"Generation: [yellow]{generation_ms}ms[/yellow] | "
            f"Server Total: [bold yellow]{total_latency_ms}ms[/bold yellow] | "
            f"Client Roundtrip: [yellow]{roundtrip_ms:.1f}ms[/yellow]"
        )
        self.console.print(
            f"  [bold]Tokens:[/bold] Prompt: [blue]{prompt_tokens}[/blue] | "
            f"Completion: [blue]{completion_tokens}[/blue] | "
            f"Cached: [magenta]{cached_tokens}[/magenta] | "
            f"Total: [bold blue]{total_tokens}[/bold blue]"
        )
        self.console.print(
            f"  [bold]Chunks ({len(chunks)}):[/bold] IDs={chunk_ids} | Evidence Found: [green]{evidence_found}[/green]\n"
        )


def collect_run(
    client: RagClient,
    run_name: str,
    questions: list[Any] | None = None,
    modules: Iterable[str] | None = None,
    table_name: str | None = None,
    top_k: int | None = None,
    warmup_queries: int = 2,
    output_dir: str | Path = "runs",
    debug: bool = False,
    resume: bool = True,
) -> list[dict[str, Any]]:
    """Convenience function to run benchmark data collection."""
    collector = BenchmarkCollector(
        client=client,
        output_dir=output_dir,
        debug=debug,
    )
    return collector.collect(
        run_name=run_name,
        questions=questions,
        modules=modules,
        table_name=table_name,
        top_k=top_k,
        warmup_queries=warmup_queries,
        resume=resume,
    )
