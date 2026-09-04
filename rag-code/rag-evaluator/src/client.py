"""HTTP client for communicating with the Spring Boot RAG application.

Supports local execution (e.g. http://localhost:8080) and Google Cloud Run deployments,
including automatic IAM bearer token retrieval for secured endpoints.
"""

from __future__ import annotations

import logging
import os
import subprocess
import time
from typing import Any, Iterable
from urllib.parse import urlparse

import requests
from rich.console import Console

logger = logging.getLogger(__name__)


def is_cloud_run_url(url: str) -> bool:
    """Detect whether a target URL points to a GCP Cloud Run deployment."""
    parsed = urlparse(url)
    hostname = (parsed.hostname or "").lower()
    return ".run.app" in hostname or "run.app" in hostname


def resolve_gcp_identity_token(target_url: str) -> str | None:
    """Attempt to resolve a GCP IAM identity token for the target URL.

    Checks:
      1. Environment variables (`GCP_ID_TOKEN`, `IDENTITY_TOKEN`, `GCP_TOKEN`)
      2. `google-auth` library (`google.oauth2.id_token.fetch_id_token`)
      3. `gcloud auth print-identity-token` CLI command
    """
    # 1. Environment variable override
    for env_var in ("GCP_ID_TOKEN", "IDENTITY_TOKEN", "GCP_TOKEN"):
        token = os.getenv(env_var)
        if token and token.strip():
            logger.debug(f"Using identity token from {env_var}")
            return token.strip()

    # 2. google-auth library
    try:
        from google.auth.transport.requests import Request
        from google.oauth2 import id_token

        auth_req = Request()
        token = id_token.fetch_id_token(auth_req, target_url)
        if token and token.strip():
            logger.debug("Successfully acquired identity token via google-auth")
            return token.strip()
    except Exception as exc:
        logger.debug(f"google-auth id_token fetch not available: {exc}")

    # 3. gcloud CLI fallback
    try:
        proc = subprocess.run(
            ["gcloud", "auth", "print-identity-token"],
            capture_output=True,
            text=True,
            check=True,
            timeout=10,
        )
        token = proc.stdout.strip()
        if token:
            logger.debug("Successfully acquired identity token via gcloud CLI")
            return token
    except Exception as exc:
        logger.debug(f"gcloud identity token fallback failed: {exc}")

    return None


class RagClient:
    """Client for interacting with the Spring Boot RAG application."""

    def __init__(
        self,
        base_url: str | None = None,
        auth_token: str | None = None,
        timeout: float = 180.0,
        auto_iam: bool = True,
        debug: bool = False,
        session: requests.Session | None = None,
    ) -> None:
        """Initialize RAG application HTTP client.

        Args:
            base_url: Base URL of Spring Boot app. If None, reads `RAG_APP_URL` env or defaults to http://localhost:8080.
            auth_token: Explicit Bearer token to include in Authorization header.
            timeout: Timeout in seconds for HTTP requests.
            auto_iam: If True and URL is a Cloud Run endpoint, attempt automatic IAM token discovery.
            debug: Whether to print client debug logs to console.
            session: Optional pre-configured requests.Session instance.
        """
        raw_url = base_url or os.getenv("RAG_APP_URL") or "http://localhost:8080"
        self.base_url = raw_url.rstrip("/")
        self.timeout = timeout
        self.debug = debug
        self.console = Console(stderr=True)
        self.session = session or requests.Session()

        token = auth_token
        if not token and auto_iam and is_cloud_run_url(self.base_url):
            token = resolve_gcp_identity_token(self.base_url)

        if token:
            self.session.headers["Authorization"] = f"Bearer {token}"

    def _log_debug(self, message: str) -> None:
        if self.debug:
            self.console.print(f"[dim cyan][DEBUG CLIENT][/dim cyan] {message}")

    def check_health(self) -> dict[str, Any]:
        """Check the health of the RAG application via Spring Boot Actuator.

        Returns:
            JSON payload from `/actuator/health` (e.g. `{"status": "UP"}`).
        """
        url = f"{self.base_url}/actuator/health"
        self._log_debug(f"GET {url}")
        resp = self.session.get(url, timeout=self.timeout)
        resp.raise_for_status()
        return resp.json()

    def health_check(self) -> bool:
        """Boolean health check helper for service readiness verification."""
        try:
            res = self.check_health()
            return res.get("status") == "UP" or bool(res)
        except Exception as exc:
            self._log_debug(f"Health check failed: {exc}")
            return False

    def get_dataset_stats(self) -> dict[str, Any]:
        """Fetch MuSiQue dataset statistics from `/api/benchmark/musique/stats`."""
        url = f"{self.base_url}/api/benchmark/musique/stats"
        self._log_debug(f"GET {url}")
        resp = self.session.get(url, timeout=self.timeout)
        resp.raise_for_status()
        return resp.json()

    def get_canonical_sample(self) -> list[dict[str, Any]]:
        """Fetch the canonical 100-question benchmark sample (Thesis §4.2.1)."""
        url = f"{self.base_url}/api/benchmark/musique/sample"
        self._log_debug(f"GET {url}")
        resp = self.session.get(url, timeout=self.timeout)
        resp.raise_for_status()
        return resp.json()

    def get_questions(
        self,
        offset: int = 0,
        limit: int = 20,
        hops: int | None = None,
        sample: bool = False,
    ) -> list[dict[str, Any]]:
        """Query MuSiQue questions with pagination and hop filtering.

        Args:
            offset: Offset in dataset.
            limit: Maximum number of questions to return.
            hops: Optional hop count filter (2, 3, or 4).
            sample: If True, limit selection to canonical benchmark sample.
        """
        url = f"{self.base_url}/api/benchmark/musique/questions"
        params: dict[str, Any] = {"offset": offset, "limit": limit}
        if hops is not None:
            params["hops"] = hops
        if sample:
            params["sample"] = "true"

        self._log_debug(f"GET {url} params={params}")
        resp = self.session.get(url, params=params, timeout=self.timeout)
        resp.raise_for_status()
        return resp.json()

    def get_question(self, question_id: str) -> dict[str, Any]:
        """Fetch full details for a single MuSiQue question by ID."""
        url = f"{self.base_url}/api/benchmark/musique/questions/{question_id}"
        self._log_debug(f"GET {url}")
        resp = self.session.get(url, timeout=self.timeout)
        resp.raise_for_status()
        return resp.json()

    def query_question(
        self,
        question_id: str,
        modules: Iterable[str] | None = None,
        table_name: str | None = None,
        top_k: int | None = None,
    ) -> dict[str, Any]:
        """Run single-question RAG benchmark query against the application.

        Args:
            question_id: Unique question ID to evaluate.
            modules: Set or list of optimization modules to apply (e.g. ['M1', 'M2']).
            table_name: Optional override for the vector store table.
            top_k: Optional override for number of chunks to retrieve.

        Returns:
            JSON response conforming to `MusiqueBenchmarkQueryResponse`.
        """
        url = f"{self.base_url}/api/benchmark/musique/questions/{question_id}/query"
        payload: dict[str, Any] = {}
        if modules is not None:
            payload["modules"] = list(modules)
        if table_name is not None:
            payload["tableName"] = table_name
        if top_k is not None:
            payload["topK"] = top_k

        self._log_debug(f"POST {url} payload={payload}")
        t0 = time.perf_counter()
        resp = self.session.post(url, json=payload, timeout=self.timeout)
        elapsed = (time.perf_counter() - t0) * 1000.0
        self._log_debug(f"POST {url} completed in {elapsed:.1f}ms status={resp.status_code}")
        resp.raise_for_status()
        return resp.json()

    def query(
        self,
        query: str,
        modules: Iterable[str] | None = None,
        table_name: str | None = None,
    ) -> dict[str, Any]:
        """Execute an arbitrary RAG query against `POST /api/query`.

        Args:
            query: The user query or question text.
            modules: Optional list of optimization modules to apply.
            table_name: Optional override for the target vector store table (e.g. 'vector_store_nordea').

        Returns:
            JSON response conforming to `QueryResponse` (answer, retrievedChunks, timingsMs, tokenUsage, totalLatencyMs).
        """
        url = f"{self.base_url}/api/query"
        payload: dict[str, Any] = {"query": query}
        if modules is not None:
            payload["modules"] = list(modules)
        if table_name is not None:
            payload["tableName"] = table_name

        self._log_debug(f"POST {url} payload={payload}")
        t0 = time.perf_counter()
        resp = self.session.post(url, json=payload, timeout=self.timeout)
        elapsed = (time.perf_counter() - t0) * 1000.0
        self._log_debug(f"POST {url} completed in {elapsed:.1f}ms status={resp.status_code}")
        resp.raise_for_status()
        return resp.json()

    def batch_query(
        self,
        question_ids: list[str] | None = None,
        limit: int | None = None,
        offset: int | None = None,
        hops: int | None = None,
        sample: bool | None = None,
        modules: Iterable[str] | None = None,
        table_name: str | None = None,
        top_k: int | None = None,
    ) -> dict[str, Any]:
        """Execute a batch query on the server and return aggregate results.

        Args:
            question_ids: Specific question IDs to execute, or None to use dataset filters.
            limit: Maximum questions to evaluate.
            offset: Offset in dataset.
            hops: Optional hop count filter.
            sample: If True, evaluates against canonical benchmark sample.
            modules: Optimization modules to enable.
            table_name: Vector store table override.
            top_k: Retrieval top-k override.

        Returns:
            JSON response conforming to `BenchmarkBatchQueryResponse`.
        """
        url = f"{self.base_url}/api/benchmark/musique/batch-query"
        payload: dict[str, Any] = {}
        if question_ids is not None:
            payload["questionIds"] = question_ids
        if limit is not None:
            payload["limit"] = limit
        if offset is not None:
            payload["offset"] = offset
        if hops is not None:
            payload["hops"] = hops
        if sample is not None:
            payload["sample"] = sample
        if modules is not None:
            payload["modules"] = list(modules)
        if table_name is not None:
            payload["tableName"] = table_name
        if top_k is not None:
            payload["topK"] = top_k

        self._log_debug(f"POST {url} payload={payload}")
        resp = self.session.post(url, json=payload, timeout=self.timeout)
        resp.raise_for_status()
        return resp.json()

    def close(self) -> None:
        """Close underlying HTTP session."""
        self.session.close()

    def __enter__(self) -> RagClient:
        return self

    def __exit__(self, exc_type: Any, exc_val: Any, exc_tb: Any) -> None:
        self.close()


# Backwards compatibility alias
RagBenchmarkClient = RagClient
