"""Factory for constructing LLM judges and embedding models for RAGAS evaluation.

Supports Anthropic Claude (via Google Cloud Vertex AI), Google Gemini, and Ollama.
"""

from __future__ import annotations

import logging
import os
from typing import Any

import requests
from langchain_anthropic import ChatAnthropic
from langchain_core.embeddings import Embeddings
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_google_genai import ChatGoogleGenerativeAI, GoogleGenerativeAIEmbeddings
from langchain_openai import ChatOpenAI, OpenAIEmbeddings

logger = logging.getLogger(__name__)

DEFAULT_OLLAMA_CANDIDATES = [
    "http://172.21.64.1:11434",  # WSL2 host IP
    "http://localhost:11434",    # Standard localhost
    "http://127.0.0.1:11434",    # Loopback IP
]


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


def discover_ollama_endpoint(
    candidate_urls: list[str] | None = None,
    timeout: float = 1.0,
) -> str:
    """Discover an active Ollama endpoint.

    Checks candidates sequentially via GET /api/tags. If an endpoint responds
    with HTTP 200, it is returned immediately. If none respond, falls back to
    `OLLAMA_BASE_URL` env or the first candidate.
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
            provider: 'claude', 'vertex', 'gemini', or 'ollama'. Defaults to `JUDGE_PROVIDER` env or 'claude'.
            model: Judge model name (e.g. 'claude-sonnet-5'). Defaults to `JUDGE_MODEL` env or provider default.
            embedding_model: Embedding model name. Defaults to `JUDGE_EMBEDDING_MODEL` or provider default.
            base_url: Base URL for Ollama (optional, auto-discovered if omitted).
            temperature: LLM temperature (default 0.0 for deterministic evaluation).
            **kwargs: Extra parameters passed to the model constructors.

        Returns:
            Tuple of (llm_judge, embedding_model).
        """
        selected_provider = (
            provider or os.getenv("JUDGE_PROVIDER") or "claude"
        ).strip().lower()

        if selected_provider == "ollama":
            active_base_url = (base_url or discover_ollama_endpoint()).rstrip("/")
            judge_model = model or os.getenv("JUDGE_MODEL") or "gemma4:e2b"
            emb_model = embedding_model or os.getenv("JUDGE_EMBEDDING_MODEL") or "nomic-embed-text:latest"

            llm = ChatOpenAI(
                base_url=f"{active_base_url}/v1",
                api_key="ollama",
                model=judge_model,
                temperature=temperature,
                **kwargs,
            )
            embeddings = OpenAIEmbeddings(
                base_url=f"{active_base_url}/v1",
                api_key="ollama",
                model=emb_model,
                check_embedding_ctx_length=False,
            )
            return llm, embeddings

        elif selected_provider in ("gemini", "google", "vertex", "vertexai"):
            judge_model = model
            if not judge_model:
                env_model = os.getenv("JUDGE_MODEL")
                if env_model and ("gemini" in env_model.lower() or ("gemma" not in env_model.lower() and "claude" not in env_model.lower())):
                    judge_model = env_model
                elif selected_provider in ("vertex", "vertexai"):
                    judge_model = "claude-sonnet-5"
                else:
                    judge_model = "gemini-3.5-flash-lite"

            # If user selected vertex with a Claude model (e.g. claude-sonnet-5)
            if "claude" in judge_model.lower() or "sonnet" in judge_model.lower():
                from langchain_google_vertexai.model_garden import ChatAnthropicVertex
                from langchain_google_vertexai import VertexAIEmbeddings
                import google.auth

                _patch_vertex_ai_async_prediction_client()

                project = (
                    kwargs.pop("project", None)
                    or os.getenv("GOOGLE_CLOUD_PROJECT_ID")
                    or os.getenv("GCP_PROJECT")
                    or os.getenv("ANTIGRAVITY_PROJECT_ID")
                )
                if not project:
                    try:
                        _, project = google.auth.default()
                    except Exception:
                        pass
                if not project:
                    project = "default-project"

                location = (
                    kwargs.pop("location", None)
                    or os.getenv("ANTHROPIC_VERTEX_LOCATION")
                    or os.getenv("GOOGLE_CLOUD_LOCATION")
                    or os.getenv("GCP_REGION")
                    or "global"
                )

                emb_model = embedding_model
                if not emb_model:
                    env_emb = os.getenv("JUDGE_EMBEDDING_MODEL")
                    if env_emb and "nomic" not in env_emb.lower():
                        emb_model = env_emb
                    else:
                        emb_model = "text-embedding-004"
                vertex_emb_model = emb_model.replace("models/", "") if emb_model.startswith("models/") else emb_model

                emb_location = (
                    kwargs.pop("embedding_location", None)
                    or os.getenv("GOOGLE_CLOUD_EMBEDDING_LOCATION")
                    or "us-central1"
                )

                max_tokens = kwargs.pop("max_output_tokens", kwargs.pop("max_tokens", 4096))

                llm = ChatAnthropicVertex(
                    model_name=judge_model,
                    project=project,
                    location=location,
                    temperature=temperature,
                    max_output_tokens=max_tokens,
                    **kwargs,
                )
                embeddings = VertexAIEmbeddings(
                    model_name=vertex_emb_model,
                    project=project,
                    location=emb_location,
                )
                return llm, embeddings

            emb_model = embedding_model
            if not emb_model:
                env_emb = os.getenv("JUDGE_EMBEDDING_MODEL")
                if env_emb and "nomic" not in env_emb:
                    emb_model = env_emb
                else:
                    emb_model = "text-embedding-004"

            api_key = os.getenv("GOOGLE_AI_API_KEY") or os.getenv("GEMINI_API_KEY")

            if api_key and selected_provider not in ("vertex", "vertexai"):
                llm_kwargs = dict(kwargs)
                llm_kwargs["model"] = judge_model
                llm_kwargs["temperature"] = temperature
                llm_kwargs["google_api_key"] = api_key

                emb_target = emb_model if emb_model.startswith("models/") else f"models/{emb_model}"
                emb_kwargs: dict[str, Any] = {"model": emb_target, "google_api_key": api_key}
                llm = ChatGoogleGenerativeAI(**llm_kwargs)
                embeddings = GoogleGenerativeAIEmbeddings(**emb_kwargs)
                return llm, embeddings
            else:
                from langchain_google_vertexai import ChatVertexAI, VertexAIEmbeddings
                import google.auth

                _patch_vertex_ai_async_prediction_client()

                project = os.getenv("GOOGLE_CLOUD_PROJECT_ID") or os.getenv("GCP_PROJECT")
                if not project:
                    try:
                        _, project = google.auth.default()
                    except Exception:
                        pass

                # Newer Gemini models (e.g., gemini-3.5-flash-lite, gemini-3.8-flash) are available
                # on the 'global' endpoint on Vertex AI, not regional endpoints.
                location = (
                    kwargs.pop("location", None)
                    or os.getenv("GOOGLE_CLOUD_LOCATION")
                    or os.getenv("GCP_REGION")
                    or "global"
                )

                vertex_emb_model = emb_model.replace("models/", "") if emb_model.startswith("models/") else emb_model

                # Text embedding models on Vertex AI (e.g., text-embedding-004) require regional endpoints (e.g. us-central1)
                emb_location = (
                    kwargs.pop("embedding_location", None)
                    or os.getenv("GOOGLE_CLOUD_EMBEDDING_LOCATION")
                    or (location if location != "global" else "us-central1")
                )

                llm = ChatVertexAI(
                    model_name=judge_model,
                    project=project,
                    location=location,
                    temperature=temperature,
                    **kwargs,
                )
                embeddings = VertexAIEmbeddings(
                    model_name=vertex_emb_model,
                    project=project,
                    location=emb_location,
                )
                return llm, embeddings

        elif selected_provider in ("claude", "anthropic", "claude-vertex", "vertex-claude", "claude-direct", "anthropic-direct"):
            judge_model = model
            if not judge_model:
                env_model = os.getenv("JUDGE_MODEL")
                if env_model and ("claude" in env_model.lower() or "sonnet" in env_model.lower() or ("gemini" not in env_model.lower() and "gemma" not in env_model.lower())):
                    judge_model = env_model
                else:
                    judge_model = "claude-sonnet-5"

            # Check if explicit direct Anthropic API was requested via provider name or kwargs
            use_vertex = kwargs.pop("use_vertex", True)
            if selected_provider in ("claude-direct", "anthropic-direct"):
                use_vertex = False

            if use_vertex:
                from langchain_google_vertexai.model_garden import ChatAnthropicVertex
                from langchain_google_vertexai import VertexAIEmbeddings
                import google.auth

                _patch_vertex_ai_async_prediction_client()

                project = (
                    kwargs.pop("project", None)
                    or os.getenv("GOOGLE_CLOUD_PROJECT_ID")
                    or os.getenv("GCP_PROJECT")
                    or os.getenv("ANTIGRAVITY_PROJECT_ID")
                )
                if not project:
                    try:
                        _, project = google.auth.default()
                    except Exception:
                        pass
                if not project:
                    project = "default-project"

                # Claude models on Vertex AI require regional endpoints (e.g. us-east5, europe-west1).
                # If GOOGLE_CLOUD_LOCATION is set to 'global' (for Gemini models), fallback to us-east5.
                location = (
                    kwargs.pop("location", None)
                    or os.getenv("ANTHROPIC_VERTEX_LOCATION")
                    or os.getenv("GOOGLE_CLOUD_LOCATION")
                    or os.getenv("GCP_REGION")
                    or "global"
                )

                emb_model = embedding_model
                if not emb_model:
                    env_emb = os.getenv("JUDGE_EMBEDDING_MODEL")
                    if env_emb and "nomic" not in env_emb.lower():
                        emb_model = env_emb
                    else:
                        emb_model = "text-embedding-004"
                vertex_emb_model = emb_model.replace("models/", "") if emb_model.startswith("models/") else emb_model

                emb_location = (
                    kwargs.pop("embedding_location", None)
                    or os.getenv("GOOGLE_CLOUD_EMBEDDING_LOCATION")
                    or "us-central1"
                )

                max_tokens = kwargs.pop("max_output_tokens", kwargs.pop("max_tokens", 4096))

                llm = ChatAnthropicVertex(
                    model_name=judge_model,
                    project=project,
                    location=location,
                    temperature=temperature,
                    max_output_tokens=max_tokens,
                    **kwargs,
                )
                embeddings = VertexAIEmbeddings(
                    model_name=vertex_emb_model,
                    project=project,
                    location=emb_location,
                )
                return llm, embeddings
            else:
                api_key = os.getenv("ANTHROPIC_API_KEY")
                llm_kwargs = dict(kwargs)
                llm_kwargs["model"] = judge_model
                llm_kwargs["temperature"] = temperature
                if api_key:
                    llm_kwargs["api_key"] = api_key

                llm = ChatAnthropic(**llm_kwargs)

                # Claude does not provide an embeddings API. Fall back to Gemini if available,
                # otherwise Ollama embeddings via discovery.
                if os.getenv("GOOGLE_AI_API_KEY") or os.getenv("GEMINI_API_KEY"):
                    gemini_key = os.getenv("GOOGLE_AI_API_KEY") or os.getenv("GEMINI_API_KEY")
                    embeddings = GoogleGenerativeAIEmbeddings(
                        model=embedding_model or "models/text-embedding-004",
                        google_api_key=gemini_key,
                    )
                else:
                    active_base_url = (base_url or discover_ollama_endpoint()).rstrip("/")
                    emb_model = (
                        embedding_model
                        or os.getenv("JUDGE_EMBEDDING_MODEL")
                        or "nomic-embed-text:latest"
                    )
                    embeddings = OpenAIEmbeddings(
                        base_url=f"{active_base_url}/v1",
                        api_key="ollama",
                        model=emb_model,
                    )
                return llm, embeddings

        else:
            raise ValueError(
                f"Unsupported judge provider: '{selected_provider}'. "
                "Supported providers are 'claude', 'vertex', 'gemini', and 'ollama'."
            )


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
