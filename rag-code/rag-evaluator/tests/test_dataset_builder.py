"""Unit tests for Nordea Personal Banking MuSiQue Dataset Builder."""

import json
from pathlib import Path
from unittest.mock import MagicMock

import pytest
from langchain_core.messages import AIMessage

from src.dataset_builder import (
    CorpusIndexer,
    CorpusParagraph,
    NordeaMusiqueDatasetBuilder,
    load_existing_musique_dataset,
    load_multihop_questions_from_csv,
    load_questions_from_csv,
    STANDARD_REFUSAL_FI,
    STANDARD_REFUSAL_EN,
)


@pytest.fixture
def temp_csv(tmp_path: Path) -> Path:
    csv_file = tmp_path / "questions.csv"
    csv_file.write_text(
        "question\n"
        "Miten tilaan rinnakkaiskortin lapselle?\n"
        "Miten avaan lapselle tilin?\n"
        "Miten tilaan rinnakkaiskortin lapselle?\n"  # duplicate
        "Kuinka voin sulkea alaikäisen tilin?\n",
        encoding="utf-8",
    )
    return csv_file


@pytest.fixture
def temp_corpus(tmp_path: Path) -> Path:
    corpus_dir = tmp_path / "md" / "pages"
    corpus_dir.mkdir(parents=True)

    page1 = corpus_dir / "kortit.md"
    page1.write_text(
        "---\n"
        'url: "https://www.nordea.fi/henkiloasiakkaat/kortit"\n'
        'title: "Maksukortit ja rinnakkaiskortti"\n'
        "---\n\n"
        "# Maksukortit\n\n"
        "## Rinnakkaiskortti lapselle\n\n"
        "Rinnakkaiskortin tilaaminen lapselle onnistuu helposti huoltajan verkkopankissa "
        "valitsemalla korttiasetukset ja lisäämällä alaikäisen tiedot. Kortti toimitetaan "
        "postitse kotiin kahden viikon kuluessa tilauksesta ja se aktivoidaan mobiilissa.\n\n"
        "## Kortin turvarajat\n\n"
        "Kortin turvarajaa voi muuttaa reaaliaikaisesti Nordea Mobile -sovelluksessa tai "
        "verkkopankissa kohdassa Kortit ja Turvarajat. Muutos astuu voimaan heti ilman viivettä.\n",
        encoding="utf-8",
    )

    page2 = corpus_dir / "tilit.md"
    page2.write_text(
        "---\n"
        'url: "https://www.nordea.fi/henkiloasiakkaat/tilit"\n'
        'title: "Tilit ja säästäminen"\n'
        "---\n\n"
        "# Pankkitilit\n\n"
        "## Lapsen tilin avaaminen\n\n"
        "Alaikäiselle voi avata tilin kumpikin huoltaja yhdessä sähköisesti verkossa "
        "tai asioimalla konttorissa. Tiliin voidaan liittää säästölipas ja verkkopankkitunnukset.\n",
        encoding="utf-8",
    )
    return tmp_path / "md"


def test_load_questions_from_csv(temp_csv: Path) -> None:
    questions = load_questions_from_csv(temp_csv)
    assert len(questions) == 3
    assert questions[0] == "Miten tilaan rinnakkaiskortin lapselle?"
    assert questions[1] == "Miten avaan lapselle tilin?"
    assert questions[2] == "Kuinka voin sulkea alaikäisen tilin?"


def test_corpus_indexer(temp_corpus: Path) -> None:
    indexer = CorpusIndexer(temp_corpus)
    assert len(indexer.paragraphs) >= 3

    candidates = indexer.search_candidates("Miten tilaan rinnakkaiskortin lapselle?", top_k=2)
    assert len(candidates) > 0
    top = candidates[0]
    assert "rinnakkaiskortti" in top.title.lower() or "rinnakkaiskortti" in top.paragraph_text.lower()


def test_build_musique_sample_schema(temp_corpus: Path) -> None:
    indexer = CorpusIndexer(temp_corpus)
    mock_llm = MagicMock()
    builder = NordeaMusiqueDatasetBuilder(corpus_indexer=indexer, llm=mock_llm)

    p1 = CorpusParagraph(
        doc_id="doc_001",
        title="Rinnakkaiskortti",
        paragraph_text="Rinnakkaiskortin voi tilata verkkopankista.",
    )
    p2 = CorpusParagraph(
        doc_id="doc_002",
        title="Turvarajat",
        paragraph_text="Turvarajoja voi muuttaa mobiilissa.",
    )

    sample = builder._build_musique_sample(
        sample_id="2hop__nordea_0001",
        question="Miten tilaan rinnakkaiskortin ja miten sen turvarajaa muutetaan?",
        answer="Rinnakkaiskortin voi tilata verkkopankista ja sen turvarajaa voi muuttaa mobiilipankissa.",
        aliases=["Verkkopankista tilaamalla ja mobiilissa rajaa muuttamalla."],
        supporting_paras=[p1, p2],
        candidate_paras=[p1, p2],
        decomposition=[
            {"question": "Mistä rinnakkaiskortin voi tilata?", "answer": "Verkkopankista.", "support_index": 0},
            {"question": "Miten turvarajaa voi muuttaa?", "answer": "Mobiilissa.", "support_index": 1},
        ],
        answerable=True,
        target_pool_size=5,
    )

    # Validate exact MuSiQue keys
    expected_keys = {"id", "question", "answer", "answer_aliases", "paragraphs", "question_decomposition", "answerable", "hopCount"}
    assert set(sample.keys()) == expected_keys
    assert sample["id"] == "2hop__nordea_0001"
    assert sample["answerable"] is True
    assert len(sample["paragraphs"]) == 5

    # Check paragraph items
    for p in sample["paragraphs"]:
        assert "idx" in p
        assert "title" in p
        assert "paragraph_text" in p
        assert "is_supporting" in p
        assert isinstance(p["is_supporting"], bool)

    # Exactly 2 supporting paragraphs
    supporting = [p for p in sample["paragraphs"] if p["is_supporting"]]
    assert len(supporting) == 2

    # Decomposition
    decomp = sample["question_decomposition"]
    assert len(decomp) == 2
    assert decomp[0]["paragraph_support_idx"] is not None
    # Remapped index must point to a supporting paragraph
    idx0 = decomp[0]["paragraph_support_idx"]
    assert sample["paragraphs"][idx0]["is_supporting"] is True


def test_out_of_domain_sample(temp_corpus: Path) -> None:
    indexer = CorpusIndexer(temp_corpus)
    mock_llm = MagicMock()
    builder = NordeaMusiqueDatasetBuilder(corpus_indexer=indexer, llm=mock_llm)

    sample = builder._build_musique_sample(
        sample_id="ood__nordea_0001",
        question="Miten S-Pankin bonus toimii?",
        answer=STANDARD_REFUSAL_FI,
        aliases=[STANDARD_REFUSAL_EN],
        supporting_paras=[],
        candidate_paras=[],
        decomposition=[],
        answerable=False,
        target_pool_size=5,
    )

    assert sample["answerable"] is False
    assert sample["answer"] == STANDARD_REFUSAL_FI
    assert len(sample["question_decomposition"]) == 0
    # No supporting paragraphs in OOD
    assert all(not p["is_supporting"] for p in sample["paragraphs"])


def test_builder_delay_configuration(temp_corpus: Path) -> None:
    indexer = CorpusIndexer(temp_corpus)
    mock_llm = MagicMock()

    builder_default = NordeaMusiqueDatasetBuilder(corpus_indexer=indexer, llm=mock_llm)
    assert builder_default.delay_seconds == 2.5

    builder_custom = NordeaMusiqueDatasetBuilder(corpus_indexer=indexer, llm=mock_llm, delay_seconds=3.5)
    assert builder_custom.delay_seconds == 3.5

    builder_negative = NordeaMusiqueDatasetBuilder(corpus_indexer=indexer, llm=mock_llm, delay_seconds=-1.0)
    assert builder_negative.delay_seconds == 0.0


def test_builder_pacing_delay_executed(temp_corpus: Path, tmp_path: Path, monkeypatch) -> None:
    indexer = CorpusIndexer(temp_corpus)
    mock_llm = MagicMock()
    mock_llm.invoke.return_value = AIMessage(
        content=json.dumps({
            "is_answerable": True,
            "hop_count": 1,
            "supporting_indices": [0],
            "answer": "Kortin voi tilata verkkopankissa.",
            "answer_aliases": ["Verkkopankista."],
            "decomposition": [],
        })
    )

    builder = NordeaMusiqueDatasetBuilder(corpus_indexer=indexer, llm=mock_llm, delay_seconds=2.0)

    sleep_calls = []
    monkeypatch.setattr("time.sleep", lambda s: sleep_calls.append(s))

    out_file = tmp_path / "out.jsonl"
    questions = [
        "Miten tilaan rinnakkaiskortin lapselle?",
        "Miten avaan lapselle tilin?",
    ]

    builder.build_dataset(questions=questions, output_file=out_file, target_size=2)

    # Verified that sleep was called with 2.0s
    assert len(sleep_calls) >= 1
    assert sleep_calls[0] == 2.0


def test_load_multihop_questions_from_csv(tmp_path: Path) -> None:
    csv_file = tmp_path / "multihop.csv"
    csv_file.write_text(
        "composite_question,sub_question_1,sub_question_2\n"
        '"Miten avaan lapselle tilin ja tilaan kortin?","Miten avaan lapselle tilin?","Miten tilaan kortin?"\n'
        '"Mitä etuja Goldissa on ja mikä sen hinta on?","Mitä etuja Goldissa on?","Mikä on Goldin hinta?"\n',
        encoding="utf-8",
    )
    records = load_multihop_questions_from_csv(csv_file)
    assert len(records) == 2
    assert records[0]["composite_question"] == "Miten avaan lapselle tilin ja tilaan kortin?"
    assert records[0]["sub_question_1"] == "Miten avaan lapselle tilin?"
    assert records[0]["sub_question_2"] == "Miten tilaan kortin?"


def test_search_multihop_candidates(temp_corpus: Path) -> None:
    indexer = CorpusIndexer(temp_corpus)
    mock_llm = MagicMock()
    builder = NordeaMusiqueDatasetBuilder(corpus_indexer=indexer, llm=mock_llm)

    cands = builder._search_multihop_candidates(
        sub_q1="Miten tilaan rinnakkaiskortin lapselle?",
        sub_q2="Miten avaan lapselle tilin?",
        top_k_per_sub=2,
    )
    assert len(cands) >= 2
    # Ensure doc_ids are distinct
    doc_ids = [c.doc_id for c in cands]
    assert len(doc_ids) == len(set(doc_ids))


def test_build_dataset_with_multihop(temp_corpus: Path, tmp_path: Path) -> None:
    indexer = CorpusIndexer(temp_corpus)
    mock_llm = MagicMock()

    # Configure mock responses: first for single-hop, then for multi-hop
    def mock_side_effect(messages):
        content = messages[1].content
        if "ja miten" in content.lower():
            # multi-hop
            return AIMessage(content=json.dumps({
                "is_answerable": True,
                "hop_count": 2,
                "supporting_indices": [0, 1],
                "answer": "Tilin voi avata verkossa ja kortin tilata verkkopankissa.",
                "answer_aliases": ["Verkosta tili ja mobiilista kortti."],
                "decomposition": [
                    {"question": "Miten tili avataan?", "answer": "Verkossa.", "support_index": 0},
                    {"question": "Miten kortti tilataan?", "answer": "Mobiilissa.", "support_index": 1},
                ],
            }))
        else:
            # single-hop
            return AIMessage(content=json.dumps({
                "is_answerable": True,
                "hop_count": 1,
                "supporting_indices": [0],
                "answer": "Tilin voi avata verkossa.",
                "answer_aliases": ["Verkkopankissa."],
                "decomposition": [],
            }))

    mock_llm.invoke.side_effect = mock_side_effect
    builder = NordeaMusiqueDatasetBuilder(corpus_indexer=indexer, llm=mock_llm, delay_seconds=0.0)

    out_file = tmp_path / "musique_out.jsonl"
    single_qs = ["Miten avaan lapselle tilin?"]
    mh_qs = [{
        "composite_question": "Miten avaan lapselle tilin ja miten tilaan rinnakkaiskortin?",
        "sub_question_1": "Miten avaan lapselle tilin?",
        "sub_question_2": "Miten tilaan rinnakkaiskortin lapselle?",
    }]

    samples = builder.build_dataset(
        questions=single_qs,
        multihop_questions=mh_qs,
        output_file=out_file,
        target_size=3,
        single_hop_ratio=0.34,
        multi_hop_ratio=0.34,
        ood_ratio=0.32,
    )

    assert len(samples) == 3
    categories = [s["id"].split("__")[0] for s in samples]
    assert "1hop" in categories
    assert "2hop" in categories
    assert "ood" in categories


def test_corpus_indexer_with_stopwords_and_inflections(temp_corpus: Path) -> None:
    indexer = CorpusIndexer(temp_corpus)
    # Question with stopwords 'Miten', 'saan', 'lapseni' and inflected 'kortin'
    cands = indexer.search_candidates("Miten saan turvarajan muutettua lapseni kortille?", top_k=2)
    assert len(cands) > 0
    top = cands[0]
    assert "turvaraja" in top.title.lower() or "turvaraja" in top.paragraph_text.lower()


def test_verify_and_synthesize_error_separation(temp_corpus: Path, monkeypatch) -> None:
    monkeypatch.setattr("time.sleep", lambda _: None)
    indexer = CorpusIndexer(temp_corpus)
    mock_llm = MagicMock()
    builder = NordeaMusiqueDatasetBuilder(corpus_indexer=indexer, llm=mock_llm, delay_seconds=0.0)
    cands = indexer.paragraphs[:2]

    # 1. Test unanswerable response with reason
    mock_llm.invoke.return_value = AIMessage(
        content=json.dumps({
            "is_answerable": False,
            "reason": "Aineistossa ei mainita alaikäisen tilin sulkemista.",
        })
    )
    res_unanswerable = builder._verify_and_synthesize("Kuinka suljen alaikäisen tilin?", cands)
    assert res_unanswerable["status"] == "unanswerable"
    assert "sulke" in res_unanswerable["reason"]

    # 2. Test API error handling
    mock_llm.invoke.side_effect = RuntimeError("ResourceExhausted: 429 quota exceeded")
    res_api_err = builder._verify_and_synthesize("Miten tilaan kortin?", cands)
    assert res_api_err["status"] == "api_error"
    assert "429" in res_api_err["error"]


def test_build_dataset_shuffle_behavior(temp_corpus: Path, tmp_path: Path, monkeypatch) -> None:
    monkeypatch.setattr("time.sleep", lambda _: None)
    indexer = CorpusIndexer(temp_corpus)
    mock_llm = MagicMock()
    processed_order = []

    def mock_invoke(messages):
        content = messages[1].content
        # Extract question from user_content
        q_line = content.splitlines()[0]
        q_text = q_line.replace("KYSYMYS: ", "").strip()
        processed_order.append(q_text)
        return AIMessage(
            content=json.dumps({
                "is_answerable": True,
                "hop_count": 1,
                "supporting_indices": [0],
                "answer": "Test answer",
                "answer_aliases": [],
                "decomposition": [],
            })
        )

    mock_llm.invoke.side_effect = mock_invoke
    builder = NordeaMusiqueDatasetBuilder(corpus_indexer=indexer, llm=mock_llm, delay_seconds=0.0)

    questions = [f"Kysymys {i}" for i in range(10)]
    out_file = tmp_path / "shuffle_out.jsonl"

    # Test with shuffle=False: preserves sequential order
    processed_order.clear()
    builder.build_dataset(
        questions=questions,
        output_file=out_file,
        target_size=5,
        single_hop_ratio=1.0,
        multi_hop_ratio=0.0,
        ood_ratio=0.0,
        shuffle=False,
    )
    assert processed_order == questions[:5]

    # Test with shuffle=True: order is randomized
    out_file_random = tmp_path / "shuffle_out_random.jsonl"
    processed_order.clear()
    builder.build_dataset(
        questions=questions,
        output_file=out_file_random,
        target_size=5,
        single_hop_ratio=1.0,
        multi_hop_ratio=0.0,
        ood_ratio=0.0,
        shuffle=True,
        seed=42,
    )
    assert processed_order != questions[:5]
    assert len(processed_order) == 5
    assert all(q in questions for q in processed_order)


def test_load_existing_musique_dataset(tmp_path: Path) -> None:
    non_existent = tmp_path / "does_not_exist.jsonl"
    assert load_existing_musique_dataset(non_existent) == []

    test_file = tmp_path / "test.jsonl"
    test_file.write_text(
        json.dumps({"id": "1hop__nordea_0001", "question": "Kysymys 1"}) + "\n"
        "corrupt line\n"
        + json.dumps({"id": "2hop__nordea_0001", "question": "Kysymys 2"}) + "\n",
        encoding="utf-8",
    )
    records = load_existing_musique_dataset(test_file)
    assert len(records) == 2
    assert records[0]["id"] == "1hop__nordea_0001"
    assert records[1]["id"] == "2hop__nordea_0001"


def test_build_dataset_resume_behavior(temp_corpus: Path, tmp_path: Path) -> None:
    indexer = CorpusIndexer(temp_corpus)
    mock_llm = MagicMock()
    mock_llm.invoke.return_value = AIMessage(
        content=json.dumps({
            "is_answerable": True,
            "hop_count": 2,
            "supporting_indices": [0, 1],
            "answer": "Generated multi answer",
            "answer_aliases": [],
            "decomposition": [
                {"id": 1, "question": "Sub 1", "answer": "Ans 1", "paragraph_support_idx": 0},
                {"id": 2, "question": "Sub 2", "answer": "Ans 2", "paragraph_support_idx": 1},
            ],
        })
    )
    builder = NordeaMusiqueDatasetBuilder(corpus_indexer=indexer, llm=mock_llm, delay_seconds=0.0)

    out_file = tmp_path / "resume_test.jsonl"

    # Seed with 2 single-hop and 1 multi-hop question (total 3)
    existing_items = [
        {"id": "1hop__nordea_0001", "question": "Existing single 1", "answer": "A1", "paragraphs": []},
        {"id": "1hop__nordea_0002", "question": "Existing single 2", "answer": "A2", "paragraphs": []},
        {"id": "2hop__nordea_0001", "question": "Existing multi 1", "answer": "M1", "paragraphs": []},
    ]
    with out_file.open("w", encoding="utf-8") as f:
        for it in existing_items:
            f.write(json.dumps(it, ensure_ascii=False) + "\n")

    single_pool = ["Existing single 1", "Existing single 2", "New single 3"]
    multi_pool = [
        {"composite_question": "Existing multi 1", "sub_question_1": "Sub 1", "sub_question_2": "Sub 2"},
        {"composite_question": "New multi 2", "sub_question_1": "Sub 1", "sub_question_2": "Sub 2"},
    ]

    # Target: 4 items (2 single, 2 multi, 0 ood).
    # Since single already has 2, it should generate 0 single-hop and only 1 multi-hop!
    result = builder.build_dataset(
        questions=single_pool,
        output_file=out_file,
        target_size=4,
        single_hop_ratio=0.50,
        multi_hop_ratio=0.50,
        ood_ratio=0.0,
        multihop_questions=multi_pool,
        resume=True,
    )

    assert len(result) == 4
    # Check that backup file was created
    bak_file = out_file.with_suffix(out_file.suffix + ".bak")
    assert bak_file.exists()
    bak_records = load_existing_musique_dataset(bak_file)
    assert len(bak_records) == 3

    # Check IDs and content
    assert result[0]["id"] == "1hop__nordea_0001"
    assert result[1]["id"] == "1hop__nordea_0002"
    assert result[2]["id"] == "2hop__nordea_0001"
    assert result[3]["id"] == "2hop__nordea_0002"
    assert result[3]["question"] == "New multi 2"

    # LLM should only have been invoked ONCE (for the 1 missing multi-hop question)!
    assert mock_llm.invoke.call_count == 1





