"""Factory for constructing LLM judges and embedding models for RAGAS evaluation.

Supports Google Vertex AI (Gemini + Claude via Model Garden) and Ollama.
"""

from __future__ import annotations

import logging
import os
from typing import Any

import requests
from langchain_core.embeddings import Embeddings
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_openai import ChatOpenAI, OpenAIEmbeddings

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Ollama endpoint discovery
# ---------------------------------------------------------------------------

DEFAULT_OLLAMA_CANDIDATES = [
    "http://172.21.64.1:11434",  # WSL2 host IP
    "http://localhost:11434",    # Standard localhost
    "http://127.0.0.1:11434",   # Loopback IP
]


def discover_ollama_endpoint(
    candidate_urls: list[str] | None = None,
    timeout: float = 1.0,
) -> str:
    """Discover an active Ollama endpoint.

    Checks candidates sequentially via GET /api/tags. If an endpoint responds
    with HTTP 200, it is returned immediately. If none respond, falls back to
    ``OLLAMA_BASE_URL`` env or the first candidate.
    """
    env_url = os.getenv("OLLAMA_BASE_URL")
    candidates: list[str] = []
    if env_url and env_url.strip():
        candidates.append(env_url.strip().rstrip("/"))

    if candidate_urls:
        candidates.extend([c.rstrip("/") for c in candidate_urls if c not in candidates])
    else:
        for c in DEFAULT_OLLAMA_CANDIDATES:
            if c not in candidates:
                candidates.append(c)

    for url in candidates:
        try:
            resp = requests.get(f"{url}/api/tags", timeout=timeout)
            if resp.status_code == 200:
                logger.info(f"Discovered active Ollama endpoint at: {url}")
                return url
        except Exception:
            continue

    fallback = candidates[0] if candidates else "http://172.21.64.1:11434"
    logger.debug(f"No active Ollama endpoint responded. Falling back to default: {fallback}")
    return fallback


# ---------------------------------------------------------------------------
# Vertex AI async client patch (gRPC event-loop fix for RAGAS)
# ---------------------------------------------------------------------------

_VERTEX_PATCHED = False


def _patch_vertex_ai_async_prediction_client() -> None:
    """Patches ChatVertexAI to re-bind async_client if the active asyncio event loop changes.

    Fixes a known issue where ChatVertexAI caches an async gRPC PredictionServiceClient
    tied to a single event loop. When evaluation frameworks (such as RAGAS) run multiple
    evaluation passes across separate event loops (via asyncio.run), subsequent
    calls raise 'RuntimeError: Event loop is closed' and cause gRPC InterceptedCall
    cancellation errors (__del__ AttributeError).
    """
    global _VERTEX_PATCHED
    if _VERTEX_PATCHED:
        return

    import asyncio
    from typing import cast
    from google.api_core.client_options import ClientOptions
    from langchain_google_vertexai import ChatVertexAI
    from langchain_google_vertexai._base import _get_async_prediction_client

    # Defensively patch gRPC InterceptedCall.__del__ cancellation bug
    try:
        import grpc.aio._interceptor as grpc_interceptor

        orig_cancel = grpc_interceptor.InterceptedCall.cancel

        def _safe_cancel(self):
            if not hasattr(self, "_interceptors_task"):
                return False
            return orig_cancel(self)

        grpc_interceptor.InterceptedCall.cancel = _safe_cancel
    except Exception:
        pass

    # Patch ChatVertexAI.async_prediction_client property to bind to current loop
    def _safe_async_prediction_client(self):
        current_loop = None
        try:
            current_loop = asyncio.get_running_loop()
        except RuntimeError:
            pass

        client_loop = getattr(self, "_async_client_loop", None)
        if self.async_client is not None and (
            client_loop is None
            or client_loop.is_closed()
            or (current_loop is not None and client_loop is not current_loop)
        ):
            self.async_client = None

        if self.async_client is None:
            self.async_client = _get_async_prediction_client(
                endpoint_version=self.endpoint_version,
                credentials=self.credentials,
                client_options=cast(ClientOptions, self.client_options),
                user_agent=self._user_agent,
            )
            self._async_client_loop = current_loop
        return self.async_client

    ChatVertexAI.async_prediction_client = property(_safe_async_prediction_client)
    _VERTEX_PATCHED = True


# ---------------------------------------------------------------------------
# Shared config resolution helpers
# ---------------------------------------------------------------------------

def _resolve_gcp_project(kwargs: dict[str, Any]) -> str:
    """Resolve GCP project: kwargs → env → google.auth.default() → fallback."""
    import google.auth

    project = kwargs.pop("project", None)
    if not project:
        project = os.getenv("GOOGLE_CLOUD_PROJECT_ID") or os.getenv("GCP_PROJECT")
    if not project:
        ag_proj = os.getenv("ANTIGRAVITY_PROJECT_ID")
        if ag_proj not in (None, "default-cli-project", "default-project"):
            project = ag_proj
    if not project:
        try:
            _, project = google.auth.default()
        except Exception:
            pass
    return project or "default-project"


def _resolve_vertex_locations(kwargs: dict[str, Any], *, is_claude: bool) -> tuple[str, str]:
    """Resolve (model_location, embedding_location) for Vertex AI.

    - Gemini 3.x frontier models → ``global`` endpoint
    - Claude on Vertex → ``ANTHROPIC_VERTEX_LOCATION`` or ``global``
    - Embeddings (text-embedding-004) → always regional (default ``us-central1``)
    """
    if is_claude:
        model_loc = (
            kwargs.pop("location", None)
            or os.getenv("ANTHROPIC_VERTEX_LOCATION")
            or os.getenv("GOOGLE_CLOUD_LOCATION")
            or os.getenv("GCP_REGION")
            or "global"
        )
    else:
        model_loc = (
            kwargs.pop("location", None)
            or os.getenv("GOOGLE_CLOUD_LOCATION")
            or os.getenv("GCP_REGION")
            or "global"
        )

    emb_loc = (
        kwargs.pop("embedding_location", None)
        or os.getenv("GOOGLE_CLOUD_EMBEDDING_LOCATION")
        or (model_loc if model_loc != "global" else "us-central1")
    )
    return model_loc, emb_loc


def _resolve_embedding_model(explicit: str | None) -> str:
    """Resolve embedding model for Vertex AI, filtering Ollama-only models."""
    if explicit:
        return explicit.removeprefix("models/")
    env_emb = os.getenv("JUDGE_EMBEDDING_MODEL")
    if env_emb and "nomic" not in env_emb.lower():
        return env_emb.removeprefix("models/")
    return "text-embedding-004"


# ---------------------------------------------------------------------------
# Builder functions (one per concrete LLM + Embedding combination)
# ---------------------------------------------------------------------------

def _build_ollama(
    model: str | None,
    embedding_model: str | None,
    base_url: str | None,
    temperature: float,
    **kwargs: Any,
) -> tuple[BaseChatModel, Embeddings]:
    """Build Ollama judge via OpenAI-compatible /v1 endpoint."""
    active_url = (base_url or discover_ollama_endpoint()).rstrip("/")
    judge_model = model or os.getenv("JUDGE_MODEL") or "gemma4:e2b"
    emb_model = embedding_model or os.getenv("JUDGE_EMBEDDING_MODEL") or "nomic-embed-text:latest"

    llm = ChatOpenAI(
        base_url=f"{active_url}/v1",
        api_key="ollama",
        model=judge_model,
        temperature=temperature,
        **kwargs,
    )
    embeddings = OpenAIEmbeddings(
        base_url=f"{active_url}/v1",
        api_key="ollama",
        model=emb_model,
        check_embedding_ctx_length=False,
    )
    return llm, embeddings


def _build_claude_vertex(
    model: str | None,
    embedding_model: str | None,
    temperature: float,
    **kwargs: Any,
) -> tuple[BaseChatModel, Embeddings]:
    """Build Claude judge via Vertex AI Model Garden."""
    from langchain_google_vertexai.model_garden import ChatAnthropicVertex
    from langchain_google_vertexai import VertexAIEmbeddings

    _patch_vertex_ai_async_prediction_client()

    judge_model = model or os.getenv("JUDGE_MODEL") or "claude-sonnet-5"
    project = _resolve_gcp_project(kwargs)
    model_loc, emb_loc = _resolve_vertex_locations(kwargs, is_claude=True)
    emb_model = _resolve_embedding_model(embedding_model)
    max_tokens = kwargs.pop("max_output_tokens", kwargs.pop("max_tokens", 4096))

    llm = ChatAnthropicVertex(
        model_name=judge_model,
        project=project,
        location=model_loc,
        temperature=temperature,
        max_output_tokens=max_tokens,
        **kwargs,
    )
    embeddings = VertexAIEmbeddings(
        model_name=emb_model,
        project=project,
        location=emb_loc,
    )
    return llm, embeddings


def _build_gemini_vertex(
    model: str | None,
    embedding_model: str | None,
    temperature: float,
    **kwargs: Any,
) -> tuple[BaseChatModel, Embeddings]:
    """Build Gemini judge via Vertex AI."""
    from langchain_google_vertexai import ChatVertexAI, VertexAIEmbeddings

    _patch_vertex_ai_async_prediction_client()

    judge_model = model or os.getenv("JUDGE_MODEL") or "gemini-3.5-flash-lite"
    project = _resolve_gcp_project(kwargs)
    model_loc, emb_loc = _resolve_vertex_locations(kwargs, is_claude=False)
    emb_model = _resolve_embedding_model(embedding_model)

    llm = ChatVertexAI(
        model_name=judge_model,
        project=project,
        location=model_loc,
        temperature=temperature,
        **kwargs,
    )
    embeddings = VertexAIEmbeddings(
        model_name=emb_model,
        project=project,
        location=emb_loc,
    )
    return llm, embeddings


def _build_vertex_maas_openai(
    model: str | None,
    embedding_model: str | None,
    temperature: float,
    **kwargs: Any,
) -> tuple[BaseChatModel, Embeddings]:
    """Build a Vertex AI MaaS judge via the OpenAI-compatible endpoint.

    Third-party models hosted on Vertex AI Model Garden (e.g. Meta Llama 4)
    expose an OpenAI-compatible ``/chat/completions`` endpoint rather than
    the Gemini ``GenerateContent`` API.  This builder constructs the correct
    regional URL and uses a GCP OAuth2 access token as the API key.
    """
    import google.auth
    from google.auth.transport.requests import Request
    from langchain_google_vertexai import VertexAIEmbeddings

    judge_model = model or os.getenv("JUDGE_MODEL") or "meta/llama-4-scout-17b-16e-instruct-maas"
    project = _resolve_gcp_project(kwargs)

    # MaaS models are regional (e.g. us-east5), never "global"
    maas_location = (
        kwargs.pop("location", None)
        or os.getenv("VERTEX_MAAS_LOCATION")
        or os.getenv("GOOGLE_CLOUD_MAAS_LOCATION")
        or "us-east5"
    )
    emb_loc = (
        kwargs.pop("embedding_location", None)
        or os.getenv("GOOGLE_CLOUD_EMBEDDING_LOCATION")
        or "us-central1"
    )
    emb_model = _resolve_embedding_model(embedding_model)
    max_tokens = kwargs.pop("max_output_tokens", kwargs.pop("max_tokens", 4096))

    # Obtain GCP access token for authentication
    credentials, _ = google.auth.default(
        scopes=["https://www.googleapis.com/auth/cloud-platform"],
    )
    credentials.refresh(Request())

    base_url = (
        f"https://{maas_location}-aiplatform.googleapis.com/v1beta1"
        f"/projects/{project}/locations/{maas_location}/endpoints/openapi"
    )

    llm = ChatOpenAI(
        model=judge_model,
        base_url=base_url,
        api_key=credentials.token,
        temperature=temperature,
        max_tokens=max_tokens,
        max_retries=2,
        **kwargs,
    )
    embeddings = VertexAIEmbeddings(
        model_name=emb_model,
        project=project,
        location=emb_loc,
    )
    return llm, embeddings


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

_PROVIDER_ALIASES: dict[str, str] = {
    "ollama": "ollama",
    "gemini": "vertex-gemini",
    "google": "vertex-gemini",
    "vertex": "vertex",          # auto-detect Claude vs Gemini from model name
    "vertexai": "vertex",
    "claude": "vertex-claude",
    "anthropic": "vertex-claude",
    "llama": "vertex-maas",
    "meta": "vertex-maas",
    "maas": "vertex-maas",
}


class JudgeFactory:
    """Factory for RAG evaluation judge models and embeddings."""

    @staticmethod
    def create_judge(
        provider: str | None = None,
        model: str | None = None,
        embedding_model: str | None = None,
        base_url: str | None = None,
        temperature: float = 0.0,
        **kwargs: Any,
    ) -> tuple[BaseChatModel, Embeddings]:
        """Create an LLM judge and associated embedding model for RAGAS.

        Args:
            provider: 'claude', 'gemini', 'vertex', or 'ollama'.
                      Defaults to ``JUDGE_PROVIDER`` env or ``'claude'``.
            model: Judge model name (e.g. ``'claude-sonnet-5'``).
                   Defaults to ``JUDGE_MODEL`` env or provider default.
            embedding_model: Embedding model name.
                             Defaults to ``JUDGE_EMBEDDING_MODEL`` or provider default.
            base_url: Base URL for Ollama (optional, auto-discovered if omitted).
            temperature: LLM temperature (default 0.0 for deterministic evaluation).
            **kwargs: Extra parameters passed to the model constructors.

        Returns:
            Tuple of (llm_judge, embedding_model).
        """
        selected = (provider or os.getenv("JUDGE_PROVIDER") or "claude").strip().lower()
        key = _PROVIDER_ALIASES.get(selected)
        if key is None:
            raise ValueError(
                f"Unsupported judge provider: '{selected}'. "
                f"Supported: {', '.join(sorted(set(_PROVIDER_ALIASES.keys())))}"
            )

        if key == "ollama":
            return _build_ollama(model, embedding_model, base_url, temperature, **kwargs)

        if key == "vertex-claude":
            return _build_claude_vertex(model, embedding_model, temperature, **kwargs)

        if key == "vertex-gemini":
            return _build_gemini_vertex(model, embedding_model, temperature, **kwargs)

        if key == "vertex-maas":
            return _build_vertex_maas_openai(model, embedding_model, temperature, **kwargs)

        # key == "vertex" → auto-detect from model name
        effective_model = model or os.getenv("JUDGE_MODEL") or ""
        model_lower = effective_model.lower()
        if "claude" in model_lower or "sonnet" in model_lower:
            return _build_claude_vertex(model, embedding_model, temperature, **kwargs)
        if "llama" in model_lower or "meta/" in model_lower or "-maas" in model_lower:
            return _build_vertex_maas_openai(model, embedding_model, temperature, **kwargs)
        return _build_gemini_vertex(model, embedding_model, temperature, **kwargs)


def get_judge(
    provider: str | None = None,
    model: str | None = None,
    embedding_model: str | None = None,
    base_url: str | None = None,
    temperature: float = 0.0,
    **kwargs: Any,
) -> tuple[BaseChatModel, Embeddings]:
    """Convenience helper to create judge LLM and embeddings."""
    return JudgeFactory.create_judge(
        provider=provider,
        model=model,
        embedding_model=embedding_model,
        base_url=base_url,
        temperature=temperature,
        **kwargs,
    )


def test_judge_connectivity(
    provider: str | None = None,
    model: str | None = None,
    embedding_model: str | None = None,
    base_url: str | None = None,
) -> bool:
    """Test connectivity to the configured judge LLM and embeddings endpoint.

    Performs a lightweight invocation and embedding to ensure the judge
    infrastructure is responsive before starting long evaluation runs.

    Returns:
        True if both LLM and embedding tests succeed, False otherwise.
    """
    try:
        llm, embeddings = JudgeFactory.create_judge(
            provider=provider,
            model=model,
            embedding_model=embedding_model,
            base_url=base_url,
        )

        # 1. Test LLM invocation
        res = llm.invoke("Say hello in one word.")
        if not res or not getattr(res, "content", None):
            logger.error("Judge LLM invocation returned empty response.")
            return False

        # 2. Test Embedding invocation
        if embeddings is not None:
            vec = embeddings.embed_query("Connectivity check.")
            if not vec or len(vec) == 0:
                logger.error("Judge embeddings returned empty vector.")
                return False

        logger.info(f"Successfully verified judge connectivity for provider='{provider or 'default'}'.")
        return True

    except Exception as exc:
        logger.error(f"Judge connectivity test failed: {exc}", exc_info=True)
        return False


# Tell pytest not to collect this production helper function as a test
test_judge_connectivity.__test__ = False
