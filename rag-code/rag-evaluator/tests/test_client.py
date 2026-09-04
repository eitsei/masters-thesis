"""Unit tests for the RAG application HTTP client."""

from unittest.mock import MagicMock, patch
import pytest
import requests

from src.client import RagClient, is_cloud_run_url, resolve_gcp_identity_token


class TestRagClientInitialization:
    def test_default_url(self, monkeypatch):
        monkeypatch.delenv("RAG_APP_URL", raising=False)
        client = RagClient(auto_iam=False)
        assert client.base_url == "http://localhost:8080"

    def test_url_from_env(self, monkeypatch):
        monkeypatch.setenv("RAG_APP_URL", "http://my-rag-host:9090/")
        client = RagClient(auto_iam=False)
        assert client.base_url == "http://my-rag-host:9090"

    def test_explicit_url(self):
        client = RagClient(base_url="https://rag-prod.example.com/", auto_iam=False)
        assert client.base_url == "https://rag-prod.example.com"

    def test_explicit_auth_token(self):
        client = RagClient(base_url="http://localhost:8080", auth_token="my-test-token", auto_iam=False)
        assert client.session.headers.get("Authorization") == "Bearer my-test-token"


class TestCloudRunAndIAM:
    def test_is_cloud_run_url(self):
        assert is_cloud_run_url("https://rag-benchmark-xyz-uc.a.run.app") is True
        assert is_cloud_run_url("https://service.run.app/path") is True
        assert is_cloud_run_url("http://localhost:8080") is False
        assert is_cloud_run_url("https://internal.company.com") is False

    def test_resolve_gcp_identity_token_from_env(self, monkeypatch):
        monkeypatch.setenv("GCP_ID_TOKEN", "env-token-12345")
        token = resolve_gcp_identity_token("https://test.run.app")
        assert token == "env-token-12345"

    def test_resolve_gcp_identity_token_via_gcloud(self, monkeypatch):
        monkeypatch.delenv("GCP_ID_TOKEN", raising=False)
        monkeypatch.delenv("IDENTITY_TOKEN", raising=False)
        monkeypatch.delenv("GCP_TOKEN", raising=False)

        mock_proc = MagicMock()
        mock_proc.stdout = "gcloud-cli-token\n"
        with patch("subprocess.run", return_value=mock_proc):
            token = resolve_gcp_identity_token("https://test.run.app")
            assert token == "gcloud-cli-token"

    def test_client_auto_attaches_iam_token_for_cloud_run(self, monkeypatch):
        monkeypatch.setenv("GCP_ID_TOKEN", "auto-iam-token")
        client = RagClient(base_url="https://my-service.run.app", auto_iam=True)
        assert client.session.headers.get("Authorization") == "Bearer auto-iam-token"


class TestRagClientEndpoints:
    @pytest.fixture
    def mock_session(self):
        session = MagicMock(spec=requests.Session)
        session.headers = {}
        return session

    @pytest.fixture
    def client(self, mock_session):
        return RagClient(
            base_url="http://localhost:8080",
            session=mock_session,
            auto_iam=False,
            timeout=60.0,
        )

    def test_check_health(self, client, mock_session):
        mock_resp = MagicMock()
        mock_resp.json.return_value = {"status": "UP"}
        mock_session.get.return_value = mock_resp

        result = client.check_health()
        assert result == {"status": "UP"}
        mock_session.get.assert_called_once_with("http://localhost:8080/actuator/health", timeout=60.0)

    def test_get_dataset_stats(self, client, mock_session):
        mock_resp = MagicMock()
        mock_resp.json.return_value = {
            "totalQuestions": 1000,
            "twoHopCount": 400,
            "threeHopCount": 300,
            "fourHopCount": 300,
            "canonicalSampleSize": 100,
        }
        mock_session.get.return_value = mock_resp

        result = client.get_dataset_stats()
        assert result["totalQuestions"] == 1000
        mock_session.get.assert_called_once_with(
            "http://localhost:8080/api/benchmark/musique/stats", timeout=60.0
        )

    def test_get_canonical_sample(self, client, mock_session):
        mock_resp = MagicMock()
        mock_resp.json.return_value = [{"id": "q1", "question": "test?"}]
        mock_session.get.return_value = mock_resp

        result = client.get_canonical_sample()
        assert len(result) == 1
        assert result[0]["id"] == "q1"
        mock_session.get.assert_called_once_with(
            "http://localhost:8080/api/benchmark/musique/sample", timeout=60.0
        )

    def test_get_questions(self, client, mock_session):
        mock_resp = MagicMock()
        mock_resp.json.return_value = [{"id": "q1"}]
        mock_session.get.return_value = mock_resp

        result = client.get_questions(offset=10, limit=5, hops=2, sample=True)
        assert len(result) == 1
        mock_session.get.assert_called_once_with(
            "http://localhost:8080/api/benchmark/musique/questions",
            params={"offset": 10, "limit": 5, "hops": 2, "sample": "true"},
            timeout=60.0,
        )

    def test_get_question(self, client, mock_session):
        mock_resp = MagicMock()
        mock_resp.json.return_value = {"id": "q99", "question": "Who is X?"}
        mock_session.get.return_value = mock_resp

        result = client.get_question("q99")
        assert result["id"] == "q99"
        mock_session.get.assert_called_once_with(
            "http://localhost:8080/api/benchmark/musique/questions/q99", timeout=60.0
        )

    def test_query_question(self, client, mock_session):
        mock_resp = MagicMock()
        mock_resp.json.return_value = {
            "questionId": "q1",
            "generatedAnswer": "42",
            "timings": {"retrievalMs": 10, "generationMs": 20},
        }
        mock_session.post.return_value = mock_resp

        result = client.query_question("q1", modules=["M1", "M2"], table_name="custom_table", top_k=8)
        assert result["questionId"] == "q1"
        mock_session.post.assert_called_once_with(
            "http://localhost:8080/api/benchmark/musique/questions/q1/query",
            json={"modules": ["M1", "M2"], "tableName": "custom_table", "topK": 8},
            timeout=60.0,
        )

    def test_query(self, client, mock_session):
        mock_resp = MagicMock()
        mock_resp.json.return_value = {
            "answer": "Test answer",
            "modulesApplied": ["M1"],
            "retrievedChunks": [{"id": "c1", "snippet": "text"}],
            "timingsMs": {"retrievalMs": 15, "generationMs": 50},
            "tokenUsage": {"promptTokens": 100, "completionTokens": 20},
            "totalLatencyMs": 65,
        }
        mock_session.post.return_value = mock_resp

        result = client.query("What is X?", modules=["M1"], table_name="vector_store_nordea")
        assert result["answer"] == "Test answer"
        mock_session.post.assert_called_once_with(
            "http://localhost:8080/api/query",
            json={"query": "What is X?", "modules": ["M1"], "tableName": "vector_store_nordea"},
            timeout=60.0,
        )

    def test_batch_query(self, client, mock_session):
        mock_resp = MagicMock()
        mock_resp.json.return_value = {"totalQuestions": 2, "averageLatencyMs": 15.0}
        mock_session.post.return_value = mock_resp

        result = client.batch_query(
            question_ids=["q1", "q2"],
            limit=2,
            hops=2,
            sample=True,
            modules=["M0"],
            table_name="vector_store_test",
            top_k=5,
        )
        assert result["totalQuestions"] == 2
        mock_session.post.assert_called_once_with(
            "http://localhost:8080/api/benchmark/musique/batch-query",
            json={
                "questionIds": ["q1", "q2"],
                "limit": 2,
                "hops": 2,
                "sample": True,
                "modules": ["M0"],
                "tableName": "vector_store_test",
                "topK": 5,
            },
            timeout=60.0,
        )

    def test_http_error_raised(self, client, mock_session):
        mock_resp = MagicMock()
        mock_resp.raise_for_status.side_effect = requests.HTTPError("404 Not Found")
        mock_session.get.return_value = mock_resp

        with pytest.raises(requests.HTTPError):
            client.get_question("unknown_id")

    def test_context_manager(self, mock_session):
        with RagClient(base_url="http://localhost:8080", session=mock_session, auto_iam=False) as client:
            assert client.base_url == "http://localhost:8080"
        mock_session.close.assert_called_once()
