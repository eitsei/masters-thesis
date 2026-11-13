"""Enterprise Personal Banking Benchmark Dataset Builder (MuSiQue Schema).

Constructs an enterprise benchmark dataset adhering 100% to the MuSiQue JSONL schema:
- Ingests Finnish personal banking questions from CSV (e.g. qs_fi.csv)
- Segments and indexes crawled personal banking Markdown corpus
- Matches questions with candidate corpus passages via lexical and semantic retrieval
- Verifies ground-truth support, formulates canonical answers, and constructs reasoning
  hops via Gemini 3.8 Flash (Vertex AI global endpoint)
- Assembles standardized 20-paragraph context pools (supporting + hard/random distractors)
- Synthesizes 20% out-of-domain refusal questions with standardized refusal strings
- Conforms to Thesis §4.4 (40% single-hop, 40% multi-hop, 20% out-of-domain)
"""

from __future__ import annotations

import csv
import json
import logging
import random
import re
import shutil
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Optional, Sequence

from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.messages import HumanMessage, SystemMessage
from rapidfuzz import fuzz
from rich.console import Console
from rich.table import Table

try:
    from src.judge_factory import JudgeFactory
except ImportError:
    from .judge_factory import JudgeFactory

logger = logging.getLogger(__name__)

STANDARD_REFUSAL_FI = "Annettu konteksti ei sisällä riittävästi tietoa kysymykseen vastaamiseksi."
STANDARD_REFUSAL_EN = "The provided context does not contain sufficient information to answer this query."


def load_existing_musique_dataset(jsonl_path: Path | str) -> list[dict[str, Any]]:
    """Reads and parses existing MuSiQue-schema JSONL records."""
    path = Path(jsonl_path)
    if not path.exists():
        return []
    records: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                try:
                    records.append(json.loads(line))
                except Exception as e:
                    logger.warning("Could not parse JSON line in %s: %s", jsonl_path, e)
    return records


# Common Finnish question and glue words filtered during candidate search
FINNISH_STOPWORDS = {
    "miten", "kuinka", "mitä", "mikä", "miksi", "milloin", "kuka", "kenen",
    "paljonko", "onko", "voinko", "voiko", "saako", "saan", "saanko", "voin",
    "on", "ja", "tai", "vai", "että", "joka", "se", "hän", "ne", "olen", "olet",
    "olla", "myös", "itse", "minä", "sinä", "me", "te", "he", "tämä", "tuo",
    "nämä", "nuo", "kun", "jos", "niin", "kuin", "vaan", "eli", "sekä", "hei",
}

# Canonical Out-of-Domain financial / non-enterprise questions
OUT_OF_DOMAIN_QUERIES = [
    "Miten S-Pankin S-Etukortilla kerrytettävä bonus siirretään osuuskaupan jäsentilille?",
    "Kuinka OP-bonuksia käytetään Pohjola Vakuutuksen vakuutusmaksujen kuittaamiseen?",
    "Voiko Danske Bankin mobiilipankissa yhdistää useamman eri pankkiryhmän tilit samaan näkymään?",
    "Miten K-Plussa-pisteet muunnetaan rahaksi pankkitilille?",
    "Millä ehdoilla Aktia myöntää asuntolainaa ulkomailla asuvalle henkilölle?",
    "Miten Bitcoin-louhinnan tuotot ilmoitetaan Verohallinnon OmaVero-palvelussa?",
    "Mikä on Svea Bankin säästötilin tämänhetkinen talletuskorko?",
    "Miten Handelsbankenin konttoriverkoston sulkeminen Suomessa vaikuttaa talletussuojaan?",
    "Miten voin ostaa New Yorkin pörssissä noteerattuja optiosopimuksia ilman välityspalkkiota?",
    "Miten Kelan maksama toimeentulotuki huomioidaan vuokravakuustilillä?",
    "Millainen korkomarginaali Säästöpankin Prime-lainoissa on verrattuna 12 kk euriboriin?",
    "Miten asunnon ostaminen Espanjasta rahoitetaan paikallisen espanjalaisen pankin kautta?",
    "Voiko Revolut-kortille asettaa automaattisen valuutanvaihdon ennalta määrätyllä kurssilla?",
    "Miten Pop Pankin jäsenosuus vaikuttaa lainaneuvotteluissa annettavaan marginaaliin?",
    "Kuinka suuri on kryptovaluuttapörssi Binancen perimä nostopalkkio euroissa SEPA-siirroille?",
    "Miten yrityksen osinkojen ennakonpidätys toimitetaan Viron verojärjestelmässä?",
    "Miten voin lunastaa Veikkauksen pelivoitot suoraan ulkomaiselle PayPal-tililleni?",
    "Miten Alankomaiden asuntolainamarkkinoiden verovähennysoikeus poikkeaa Suomen mallista?",
    "Minkälaisia kuluja Nordnet perii pohjoismaisten osakkeiden säilyttämisestä arvo-osuustilillä?",
    "Miten S-Pankin mikrosäästäminen toimii jokaisen korttiostoksen yhteydessä?",
]


@dataclass
class CorpusParagraph:
    """Represents a clean, indexed paragraph/section from the enterprise corpus."""

    doc_id: str
    title: str
    paragraph_text: str
    source_url: str = ""
    token_count: int = 0


class CorpusIndexer:
    """Indexes and segments crawled personal banking Markdown documents into structured paragraphs."""

    def __init__(self, corpus_dir: Path | str) -> None:
        self.corpus_dir = Path(corpus_dir)
        self.paragraphs: list[CorpusParagraph] = []
        self._index_corpus()

    def _index_corpus(self) -> None:
        if not self.corpus_dir.exists():
            logger.warning(f"Corpus directory not found: {self.corpus_dir}")
            return

        md_files = list(self.corpus_dir.rglob("*.md"))
        logger.info(f"Indexing {len(md_files)} Markdown files from {self.corpus_dir}...")

        doc_idx = 0
        for md_file in md_files:
            try:
                content = md_file.read_text(encoding="utf-8")
            except Exception as e:
                logger.warning(f"Could not read {md_file}: {e}")
                continue

            url_match = re.search(r'url:\s*["\']?([^"\'\n]+)["\']?', content)
            source_url = url_match.group(1).strip() if url_match else ""

            # Extract main title from frontmatter or first heading
            title_match = re.search(r'title:\s*["\']?([^"\'\n]+)["\']?', content)
            base_title = title_match.group(1).strip() if title_match else ""
            if not base_title:
                h1_match = re.search(r"^#\s+(.+)$", content, re.MULTILINE)
                base_title = h1_match.group(1).strip() if h1_match else md_file.stem

            # Split into sections based on headings (## or ###)
            sections = re.split(r"(?m)^(?=#{1,3}\s+)", content)
            for sec in sections:
                sec_clean = sec.strip()
                if not sec_clean:
                    continue

                lines = sec_clean.splitlines()
                first_line = lines[0].strip()
                if first_line.startswith("#"):
                    sec_title = first_line.lstrip("#").strip()
                    body_text = "\n".join(lines[1:]).strip()
                else:
                    sec_title = base_title
                    body_text = sec_clean

                # Remove frontmatter block if present in body
                body_text = re.sub(r"^---[\s\S]*?---\n*", "", body_text).strip()
                # Clean links and images
                body_text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", body_text)
                body_text = re.sub(r"!\[[^\]]*\]\([^)]+\)", "", body_text)

                words = body_text.split()
                if len(words) < 15:  # Skip tiny fragments / navigation leftovers
                    continue

                # Combine section title with base title for clarity
                effective_title = (
                    f"{base_title} - {sec_title}"
                    if sec_title and sec_title != base_title
                    else base_title
                )

                doc_idx += 1
                self.paragraphs.append(
                    CorpusParagraph(
                        doc_id=f"doc_{doc_idx:04d}",
                        title=effective_title,
                        paragraph_text=body_text,
                        source_url=source_url,
                        token_count=len(words),
                    )
                )

        logger.info(f"Indexed {len(self.paragraphs)} canonical paragraphs.")

    def search_candidates(self, query: str, top_k: int = 10) -> list[CorpusParagraph]:
        """Performs lexical and semantic scoring to return top-K candidate paragraphs."""
        if not self.paragraphs:
            return []

        # Extract content tokens by filtering stopwords and short fragments
        q_tokens = [w for w in re.findall(r"\w+", query.lower()) if w not in FINNISH_STOPWORDS and len(w) > 2]
        if not q_tokens:
            q_tokens = [w for w in re.findall(r"\w+", query.lower()) if len(w) > 2]
        q_clean = " ".join(q_tokens) if q_tokens else query.lower()

        # Extract stems (first 4 characters for words >= 5 characters) for inflection resilience
        stems = [w[:4] for w in q_tokens]

        scored: list[tuple[float, CorpusParagraph]] = []
        for p in self.paragraphs:
            title_lower = p.title.lower()
            body_lower = p.paragraph_text.lower()
            full_lower = f"{title_lower}\n{body_lower}"

            # 1. Stem hit ratio
            stem_hits = sum(1 for s in stems if s in full_lower)
            stem_score = stem_hits / max(1, len(stems))

            # 2. Rapidfuzz token set ratio on title
            title_fuzz = fuzz.token_set_ratio(q_clean, title_lower) / 100.0

            # 3. Rapidfuzz token set ratio on paragraph text (capped at 1500 chars)
            body_fuzz = fuzz.token_set_ratio(q_clean, body_lower[:1500]) / 100.0

            # Combined weighted score: stems (40%), title (35%), body (25%)
            combined_score = 0.40 * stem_score + 0.35 * title_fuzz + 0.25 * body_fuzz
            scored.append((combined_score, p))

        scored.sort(key=lambda x: x[0], reverse=True)
        return [p for _, p in scored[:top_k]]



def load_questions_from_csv(csv_path: Path | str) -> list[str]:
    """Loads and deduplicates Finnish questions from a CSV file."""
    path = Path(csv_path)
    if not path.exists():
        raise FileNotFoundError(f"Question CSV not found: {path}")

    questions: list[str] = []
    seen = set()

    with path.open("r", encoding="utf-8") as f:
        reader = csv.reader(f)
        header = next(reader, None)
        q_idx = 0
        if header:
            for i, col in enumerate(header):
                if col.strip().lower() in ("question", "kysymys", "query"):
                    q_idx = i
                    break

        for row in reader:
            if not row or len(row) <= q_idx:
                continue
            q_text = row[q_idx].strip()
            if q_text and q_text not in seen:
                seen.add(q_text)
                questions.append(q_text)

    return questions


def load_multihop_questions_from_csv(csv_path: Path | str) -> list[dict[str, str]]:
    """Loads composite multi-hop questions with sub-questions from a CSV file.

    Expected CSV columns: composite_question, sub_question_1, sub_question_2
    (Falls back gracefully if header names vary).
    """
    path = Path(csv_path)
    if not path.exists():
        raise FileNotFoundError(f"Multi-hop question CSV not found: {path}")

    records: list[dict[str, str]] = []
    seen = set()

    with path.open("r", encoding="utf-8") as f:
        reader = csv.reader(f)
        header = next(reader, None)
        comp_idx = 0
        sub1_idx = 1
        sub2_idx = 2

        if header:
            for i, col in enumerate(header):
                cl = col.strip().lower()
                if cl in ("composite_question", "question", "kysymys", "yhdistelmä"):
                    comp_idx = i
                elif cl in ("sub_question_1", "sub_q1", "alikysymys_1", "haara_1"):
                    sub1_idx = i
                elif cl in ("sub_question_2", "sub_q2", "alikysymys_2", "haara_2"):
                    sub2_idx = i

        for row in reader:
            if not row or len(row) <= comp_idx:
                continue
            comp_q = row[comp_idx].strip()
            if not comp_q or comp_q in seen:
                continue
            seen.add(comp_q)

            sub1 = row[sub1_idx].strip() if len(row) > sub1_idx else ""
            sub2 = row[sub2_idx].strip() if len(row) > sub2_idx else ""

            records.append({
                "composite_question": comp_q,
                "sub_question_1": sub1,
                "sub_question_2": sub2,
            })

    return records


class EnterpriseMusiqueDatasetBuilder:
    """Builds a stratified 100-question MuSiQue benchmark dataset from enterprise corpus and FAQs."""

    def __init__(
        self,
        corpus_indexer: CorpusIndexer,
        llm: Optional[BaseChatModel] = None,
        judge_model: str = "gemini-3.5-flash",
        delay_seconds: float = 2.5,
        console: Optional[Console] = None,
    ) -> None:
        self.indexer = corpus_indexer
        self.console = console or Console()
        self.judge_model = judge_model
        self.delay_seconds = max(0.0, float(delay_seconds))
        if llm is None:
            self.llm, _ = JudgeFactory.create_judge(provider="gemini", model=judge_model)
        else:
            self.llm = llm

    def _search_multihop_candidates(
        self,
        sub_q1: str,
        sub_q2: str,
        top_k_per_sub: int = 6,
    ) -> list[CorpusParagraph]:
        """Performs two-branch retrieval for both sub-questions and merges unique paragraphs."""
        seen = set()
        merged: list[CorpusParagraph] = []

        c1 = self.indexer.search_candidates(sub_q1, top_k=top_k_per_sub) if sub_q1 else []
        for c in c1:
            if c.doc_id not in seen:
                seen.add(c.doc_id)
                merged.append(c)

        c2 = self.indexer.search_candidates(sub_q2, top_k=top_k_per_sub) if sub_q2 else []
        for c in c2:
            if c.doc_id not in seen:
                seen.add(c.doc_id)
                merged.append(c)

        return merged

    def _verify_and_synthesize(
        self,
        question: str,
        candidates: list[CorpusParagraph],
    ) -> dict[str, Any]:
        """Prompts Gemini to verify ground truth, extract supporting passages, and formulate answer.

        Returns a structured dictionary with:
        - status: 'ok', 'unanswerable', or 'api_error'
        - data: synthesized MuSiQue dict if status == 'ok'
        - reason: explanation string if status == 'unanswerable'
        - error: error details if status == 'api_error'
        """
        if not self.llm or not candidates:
            return {"status": "unanswerable", "reason": "Ei kandidaattikappaleita tai LLM ei alustettu."}

        contexts_text = ""
        for i, c in enumerate(candidates):
            contexts_text += f"\n--- [KAPPALE {i}] (Otsikko: {c.title}) ---\n{c.paragraph_text}\n"

        system_prompt = (
            "Olet suomalaisen finanssialan ja pankkipalveluiden RAG-benchmark-asiantuntija.\n"
            "Tehtäväsi on analysoida asiakaskysymys ja annetut yrityksen henkilöasiakassivuston tekstikappaleet.\n"
            "Noudata ehdottomia sääntöjä:\n"
            "1. Vastaa kysymykseen AINOASTAAN ja YKSINOMAAN annettujen tekstikappaleiden sisältämien faktojen perusteella.\n"
            "2. Jos teksti ei vastaa kysymykseen suoraan ja luotettavasti:\n"
            "   - 'is_answerable': false\n"
            "   - 'reason': Lyhyt suomenkielinen selitys miksi teksti ei riitä (1 virke).\n"
            "3. Jos teksti vastaa kysymykseen:\n"
            "   - 'is_answerable': true\n"
            "   - 'reason': Lyhyt perustelu miksi teksti vastaa.\n"
            "   - 'supporting_indices': lista kappaleiden indekseistä (esim. [0] tai [0, 2]), jotka sisältävät vastauksen faktat.\n"
            "   - 'hop_count': 1 jos vastaus löytyy yhdestä kappaleesta, 2 jos vastaus vaatii kahden eri kappaleen/tuotteen yhdistämistä.\n"
            "   - 'answer': Täsmällinen, faktuaalinen ja selkeä asiantuntijavastaus (1-3 virkettä suomeksi).\n"
            "   - 'answer_aliases': 1-2 vaihtoehtoista hyväksyttävää ja luonnollista vastausmuotoilua.\n"
            "   - 'decomposition': Jos hop_count == 2, jaa kysymys kahteen alikysymykseen: [{\"question\": \"...\", \"answer\": \"...\", \"support_index\": X}]. Jos hop_count == 1, tyhjä lista [].\n\n"
            "Palauta tulos AINOASTAAN validina JSON-objektina ilman markdown-koodiblokkeja seuraavassa muodossa:\n"
            "{\n"
            '  "is_answerable": true,\n'
            '  "reason": "...",\n'
            '  "hop_count": 1,\n'
            '  "supporting_indices": [0],\n'
            '  "answer": "...",\n'
            '  "answer_aliases": ["..."],\n'
            '  "decomposition": []\n'
            "}"
        )

        user_content = f"KYSYMYS: {question}\n\nKANDIDAATTIKAPPALEET:\n{contexts_text}"

        max_retries = 3
        backoff_delay = 2.0
        last_error = None

        for attempt in range(max_retries):
            try:
                resp = self.llm.invoke([SystemMessage(content=system_prompt), HumanMessage(content=user_content)])
                resp_text = resp.content.strip()
                # Clean markdown code fences if model returned them
                if resp_text.startswith("```"):
                    resp_text = re.sub(r"^```[a-zA-Z]*\n?", "", resp_text)
                    resp_text = re.sub(r"\n?```$", "", resp_text)
                resp_text = resp_text.strip()
                data = json.loads(resp_text)

                if data.get("is_answerable"):
                    if data.get("supporting_indices") and data.get("answer"):
                        return {"status": "ok", "data": data}
                    else:
                        return {
                            "status": "unanswerable",
                            "reason": data.get("reason", "Vastausta tai tuki-indeksejä ei pystytty muodostamaan."),
                        }
                else:
                    return {
                        "status": "unanswerable",
                        "reason": data.get("reason", "Aineisto ei sisällä riittävästi tietoa kysymykseen vastaamiseksi."),
                    }
            except Exception as e:
                last_error = e
                err_str = str(e)
                if "429" in err_str or "ResourceExhausted" in err_str or "quota" in err_str.lower():
                    logger.warning(
                        f"Vertex AI 429 rate limit hit on attempt {attempt + 1}/{max_retries}. "
                        f"Backing off for {backoff_delay:.1f}s: {e}"
                    )
                    time.sleep(backoff_delay)
                    backoff_delay *= 2.0
                else:
                    logger.debug(f"LLM verification error for '{question[:40]}': {e}")
                    break

        return {"status": "api_error", "error": str(last_error)}


    def _build_musique_sample(
        self,
        sample_id: str,
        question: str,
        answer: str,
        aliases: list[str],
        supporting_paras: list[CorpusParagraph],
        candidate_paras: list[CorpusParagraph],
        decomposition: list[dict[str, Any]],
        answerable: bool = True,
        target_pool_size: int = 20,
        hop_count: int = 1,
    ) -> dict[str, Any]:
        """Assembles a single MuSiQue-schema record with 20 shuffled paragraphs."""
        pool: list[dict[str, Any]] = []
        used_ids = set()

        # 1. Add supporting paragraphs
        for p in supporting_paras:
            used_ids.add(p.doc_id)
            pool.append({
                "title": p.title,
                "paragraph_text": p.paragraph_text,
                "is_supporting": True,
                "orig_doc_id": p.doc_id,
            })

        # 2. Add hard negative distractors from candidate pool
        for c in candidate_paras:
            if c.doc_id not in used_ids and len(pool) < target_pool_size:
                used_ids.add(c.doc_id)
                pool.append({
                    "title": c.title,
                    "paragraph_text": c.paragraph_text,
                    "is_supporting": False,
                    "orig_doc_id": c.doc_id,
                })

        # 3. Add random distractors from full corpus if pool is not yet full
        all_available = [p for p in self.indexer.paragraphs if p.doc_id not in used_ids]
        random.shuffle(all_available)
        while len(pool) < target_pool_size and all_available:
            p_rand = all_available.pop()
            used_ids.add(p_rand.doc_id)
            pool.append({
                "title": p_rand.title,
                "paragraph_text": p_rand.paragraph_text,
                "is_supporting": False,
                "orig_doc_id": p_rand.doc_id,
            })

        # 4. If corpus has very few paragraphs (e.g. test fixtures), pad with available distractors
        if len(pool) < target_pool_size and self.indexer.paragraphs:
            pad_candidates = [p for p in self.indexer.paragraphs if p.doc_id not in [sp.doc_id for sp in supporting_paras]]
            pad_source = pad_candidates if pad_candidates else self.indexer.paragraphs
            pad_counter = 1
            while len(pool) < target_pool_size:
                p_pad = pad_source[pad_counter % len(pad_source)]
                pad_doc_id = f"{p_pad.doc_id}_pad_{pad_counter}"
                pool.append({
                    "title": p_pad.title,
                    "paragraph_text": p_pad.paragraph_text,
                    "is_supporting": False,
                    "orig_doc_id": pad_doc_id,
                })
                pad_counter += 1

        # 4. Shuffle paragraphs so supporting ones are not in predictable positions
        random.shuffle(pool)

        # 5. Assign 0-indexed positions and map decomposition indices
        doc_id_to_shuffled_idx = {}
        final_paragraphs = []
        for idx, item in enumerate(pool):
            doc_id_to_shuffled_idx[item["orig_doc_id"]] = idx
            final_paragraphs.append({
                "idx": idx,
                "title": item["title"],
                "paragraph_text": item["paragraph_text"],
                "is_supporting": item["is_supporting"],
            })

        # Remap decomposition step supporting index
        final_decomp = []
        for step_idx, step in enumerate(decomposition):
            supp_doc_id = None
            orig_sup_idx = step.get("support_index")
            if orig_sup_idx is not None and orig_sup_idx < len(supporting_paras):
                supp_doc_id = supporting_paras[orig_sup_idx].doc_id

            remapped_idx = doc_id_to_shuffled_idx.get(supp_doc_id) if supp_doc_id else None
            final_decomp.append({
                "id": step_idx + 1,
                "question": step.get("question", ""),
                "answer": step.get("answer", ""),
                "paragraph_support_idx": remapped_idx,
            })

        return {
            "id": sample_id,
            "question": question,
            "answer": answer,
            "answer_aliases": aliases,
            "hopCount": hop_count,
            "paragraphs": final_paragraphs,
            "question_decomposition": final_decomp,
            "answerable": answerable,
        }

    def build_dataset(
        self,
        questions: list[str],
        output_file: Path | str,
        target_size: int = 100,
        single_hop_ratio: float = 0.40,
        multi_hop_ratio: float = 0.40,
        ood_ratio: float = 0.20,
        limit_input: Optional[int] = None,
        multihop_questions: Optional[list[dict[str, str]]] = None,
        shuffle: bool = True,
        seed: int = 42,
        resume: bool = True,
    ) -> list[dict[str, Any]]:
        """Processes questions and builds the full stratified MuSiQue-schema JSONL dataset."""
        out_path = Path(output_file)
        out_path.parent.mkdir(parents=True, exist_ok=True)

        if target_size >= 3:
            target_single = int(round(target_size * single_hop_ratio)) if single_hop_ratio > 0 else 0
            target_multi = int(round(target_size * multi_hop_ratio)) if multi_hop_ratio > 0 else 0
            if ood_ratio > 0:
                target_ood = max(1, target_size - target_single - target_multi)
            else:
                target_ood = 0
            if single_hop_ratio > 0 and target_single == 0:
                target_single = 1
            if multi_hop_ratio > 0 and target_multi == 0:
                target_multi = 1
        else:
            target_single = 1 if single_hop_ratio > 0 else 0
            target_multi = 1 if multi_hop_ratio > 0 else 0
            target_ood = max(0, target_size - target_single - target_multi)

        existing_samples: list[dict[str, Any]] = []
        existing_single: list[dict[str, Any]] = []
        existing_multi: list[dict[str, Any]] = []
        existing_ood: list[dict[str, Any]] = []
        seen_questions: set[str] = set()

        if resume and out_path.exists():
            existing_samples = load_existing_musique_dataset(out_path)
            for s in existing_samples:
                sid = str(s.get("id", ""))
                q_text = str(s.get("question", "")).strip().lower()
                if q_text:
                    seen_questions.add(q_text)
                if sid.startswith("1hop__"):
                    existing_single.append(s)
                elif sid.startswith("2hop__"):
                    existing_multi.append(s)
                elif sid.startswith("ood__"):
                    existing_ood.append(s)

        target_single_needed = max(0, target_single - len(existing_single))
        target_multi_needed = max(0, target_multi - len(existing_multi))
        target_ood_needed = max(0, target_ood - len(existing_ood))

        self.console.print(f"[bold cyan]Building Enterprise MuSiQue Benchmark Dataset (Target: {target_size})[/bold cyan]")
        if existing_samples:
            self.console.print(
                f"[bold cyan]Jatketaan aiemmasta tiedostosta: {out_path}[/bold cyan]\n"
                f"Valmiina: [green]{len(existing_single)}/{target_single} Single-Hop[/green] | "
                f"[magenta]{len(existing_multi)}/{target_multi} Multi-Hop[/magenta] | "
                f"[yellow]{len(existing_ood)}/{target_ood} OOD[/yellow] (Yhteensä {len(existing_samples)}/{target_size})\n"
                f"Puuttuu: [green]{target_single_needed} Single-Hop[/green] | "
                f"[magenta]{target_multi_needed} Multi-Hop[/magenta] | "
                f"[yellow]{target_ood_needed} OOD[/yellow]"
            )
        else:
            self.console.print(
                f"Targets: [green]{target_single} Single-Hop[/green] | "
                f"[magenta]{target_multi} Multi-Hop[/magenta] | "
                f"[yellow]{target_ood} Out-of-Domain[/yellow]"
            )

        single_hop_samples: list[dict[str, Any]] = list(existing_single)
        multi_hop_samples: list[dict[str, Any]] = list(existing_multi)
        ood_samples: list[dict[str, Any]] = list(existing_ood)

        new_single: list[dict[str, Any]] = []
        new_multi: list[dict[str, Any]] = []
        new_ood: list[dict[str, Any]] = []

        single_candidates = list(questions)
        mh_candidates = list(multihop_questions) if multihop_questions else []

        if shuffle:
            rng = random.Random(seed)
            rng.shuffle(single_candidates)
            rng.shuffle(mh_candidates)

        single_pool = single_candidates[:limit_input] if limit_input else single_candidates
        mh_pool = mh_candidates[:limit_input] if limit_input else mh_candidates

        # -------------------------------------------------------------
        # Phase 1: Single-Hop Factual Queries (from single_pool)
        # -------------------------------------------------------------
        self.console.print(f"\n[bold green]─── Phase 1: Single-Hop Factual Queries (Target: {target_single}, Tarvitaan: {target_single_needed}) ───[/bold green]")
        if len(single_hop_samples) >= target_single:
            self.console.print("  [dim green]✓ Single-hop -tavoite on jo saavutettu, ohitetaan Phase 1.[/dim green]")
        else:
            for i, q in enumerate(single_pool, 1):
                if len(single_hop_samples) >= target_single:
                    break
                if q.strip().lower() in seen_questions:
                    continue

                candidates = self.indexer.search_candidates(q, top_k=8)
                if not candidates:
                    self.console.print(f"  [dim yellow]✗ [{i}/{len(single_pool)}] No corpus candidates: \"{q[:50]}\"[/dim yellow]")
                    continue

                q_disp = (q[:45] + "...") if len(q) > 45 else q
                with self.console.status(f"[bold cyan][{i}/{len(single_pool)}] Querying Gemini for \"{q_disp}\"...[/bold cyan]", spinner="dots"):
                    res = self._verify_and_synthesize(q, candidates)

                # Pacing delay after LLM call
                if self.delay_seconds > 0 and len(single_hop_samples) < target_single:
                    time.sleep(self.delay_seconds)

                if isinstance(res, dict) and "status" in res:
                    if res["status"] != "ok":
                        if res["status"] == "api_error":
                            self.console.print(f"  [bold red]✗ [{i}/{len(single_pool)}] API-virhe (429/Verkko):[/bold red] \"{q_disp}\" [dim]({res.get('error', '')})[/dim]")
                        else:
                            reason = res.get("reason", "Aineisto ei sisällä riittävästi tietoa")
                            self.console.print(f"  [dim yellow]✗ [{i}/{len(single_pool)}] Hylätty (Ei vastausta):[/dim yellow] \"{q_disp}\" [dim]↳ Syy: {reason}[/dim]")
                        continue
                    synth_data = res.get("data", {})
                elif isinstance(res, dict) and res.get("is_answerable"):
                    synth_data = res
                else:
                    self.console.print(f"  [dim yellow]✗ [{i}/{len(single_pool)}] Hylätty (Ei vastausta):[/dim yellow] \"{q_disp}\"")
                    continue

                supp_indices = synth_data.get("supporting_indices", [])
                supp_paras = [candidates[idx] for idx in supp_indices if idx < len(candidates)]
                if not supp_paras:
                    self.console.print(f"  [dim yellow]✗ [{i}/{len(single_pool)}] Hylätty (Ei tukikappaleita):[/dim yellow] \"{q_disp}\"")
                    continue

                hops = synth_data.get("hop_count", 1)
                ans = synth_data.get("answer", "")
                aliases = synth_data.get("answer_aliases", [])

                sample_id = f"1hop__enterprise_{len(single_hop_samples) + 1:04d}"
                sample = self._build_musique_sample(
                    sample_id=sample_id,
                    question=q,
                    answer=ans,
                    aliases=aliases,
                    supporting_paras=supp_paras[:1],
                    candidate_paras=candidates,
                    decomposition=[],
                    answerable=True,
                    hop_count=hops,
                )
                single_hop_samples.append(sample)
                new_single.append(sample)
                seen_questions.add(q.strip().lower())
                supp_title = supp_paras[0].title[:35]
                ans_disp = (ans[:60] + "...") if len(ans) > 60 else ans
                self.console.print(
                    f"  [bold green]✓ [{len(single_hop_samples)}/{target_single}] Hyväksytty 1-Hop:[/bold green] "
                    f"\"{q_disp}\"\n    [dim]↳ Tuki: \"{supp_title}...\" | Vastaus: \"{ans_disp}\"[/dim]"
                )

        # -------------------------------------------------------------
        # Phase 2: Multi-Hop Relational Queries (from mh_pool)
        # -------------------------------------------------------------
        self.console.print(f"\n[bold magenta]─── Phase 2: Multi-Hop Relational Queries (Target: {target_multi}, Tarvitaan: {target_multi_needed}) ───[/bold magenta]")
        if len(multi_hop_samples) >= target_multi:
            self.console.print("  [dim magenta]✓ Multi-hop -tavoite on jo saavutettu, ohitetaan Phase 2.[/dim magenta]")
        elif mh_pool:
            for i, item in enumerate(mh_pool, 1):
                if len(multi_hop_samples) >= target_multi:
                    break
                comp_q = item.get("composite_question") or ""
                sub1 = item.get("sub_question_1") or ""
                sub2 = item.get("sub_question_2") or ""
                if not comp_q:
                    continue
                if comp_q.strip().lower() in seen_questions:
                    continue

                candidates = self._search_multihop_candidates(sub1, sub2, top_k_per_sub=6)
                if not candidates:
                    self.console.print(f"  [dim yellow]✗ [{i}/{len(mh_pool)}] Ei ehdokkaita: \"{comp_q[:50]}\"[/dim yellow]")
                    continue

                q_disp = (comp_q[:45] + "...") if len(comp_q) > 45 else comp_q
                with self.console.status(f"[bold magenta][{i}/{len(mh_pool)}] Querying Gemini for \"{q_disp}\"...[/bold magenta]", spinner="dots"):
                    res = self._verify_and_synthesize(comp_q, candidates)

                # Pacing delay after LLM call
                if self.delay_seconds > 0 and len(multi_hop_samples) < target_multi:
                    time.sleep(self.delay_seconds)

                # Evaluate response status
                if isinstance(res, dict) and "status" in res:
                    if res["status"] != "ok":
                        if res["status"] == "api_error":
                            self.console.print(f"  [bold red]✗ [{i}/{len(mh_pool)}] API-virhe (429/Verkko):[/bold red] \"{q_disp}\" [dim]({res.get('error', '')})[/dim]")
                        else:
                            reason = res.get("reason", "Aineisto ei sisällä riittävästi tietoa")
                            self.console.print(f"  [dim yellow]✗ [{i}/{len(mh_pool)}] Hylätty (Ei vastausta):[/dim yellow] \"{q_disp}\" [dim]↳ Syy: {reason}[/dim]")
                        continue
                    synth_data = res.get("data", {})
                elif isinstance(res, dict) and res.get("is_answerable"):
                    synth_data = res
                else:
                    self.console.print(f"  [dim yellow]✗ [{i}/{len(mh_pool)}] Hylätty (Ei vastausta):[/dim yellow] \"{q_disp}\"")
                    continue

                supp_indices = synth_data.get("supporting_indices", [])
                supp_paras = [candidates[idx] for idx in supp_indices if idx < len(candidates)]
                if not supp_paras:
                    self.console.print(f"  [dim yellow]✗ [{i}/{len(mh_pool)}] Hylätty (Ei tukikappaleita):[/dim yellow] \"{q_disp}\"")
                    continue

                hops = synth_data.get("hop_count", 2)
                ans = synth_data.get("answer", "")
                aliases = synth_data.get("answer_aliases", [])
                decomp = synth_data.get("decomposition", [])

                if len(supp_paras) < 2 and len(candidates) >= 2:
                    supp_paras.append(candidates[1])

                sample_id = f"2hop__enterprise_{len(multi_hop_samples) + 1:04d}"
                sample = self._build_musique_sample(
                    sample_id=sample_id,
                    question=comp_q,
                    answer=ans,
                    aliases=aliases,
                    supporting_paras=supp_paras[:2],
                    candidate_paras=candidates,
                    decomposition=decomp,
                    answerable=True,
                    hop_count=hops,
                )
                multi_hop_samples.append(sample)
                new_multi.append(sample)
                seen_questions.add(comp_q.strip().lower())
                decomp_count = len(decomp)
                ans_disp = (ans[:60] + "...") if len(ans) > 60 else ans
                self.console.print(
                    f"  [bold magenta]✓ [{len(multi_hop_samples)}/{target_multi}] Hyväksytty 2-Hop:[/bold magenta] "
                    f"\"{q_disp}\"\n    [dim]↳ {len(supp_paras[:2])} tukikappaletta, {decomp_count} alikysymystä | Vastaus: \"{ans_disp}\"[/dim]"
                )

        else:
            self.console.print("  [yellow]No dedicated multi-hop questions CSV provided.[/yellow]")

        # -------------------------------------------------------------
        # Phase 3: Assemble Out-of-Domain Refusal Samples
        # -------------------------------------------------------------
        self.console.print(f"\n[bold yellow]─── Phase 3: Out-of-Domain Refusal Queries (Target: {target_ood}, Tarvitaan: {target_ood_needed}) ───[/bold yellow]")
        if len(ood_samples) >= target_ood:
            self.console.print("  [dim yellow]✓ OOD -tavoite on jo saavutettu, ohitetaan Phase 3.[/dim yellow]")
        else:
            for i in range(target_ood_needed):
                q_ood = OUT_OF_DOMAIN_QUERIES[(len(ood_samples) + i) % len(OUT_OF_DOMAIN_QUERIES)]
                sample_id = f"ood__enterprise_{len(ood_samples) + 1:04d}"
                # OOD has no supporting paragraphs, only distractors
                sample = self._build_musique_sample(
                    sample_id=sample_id,
                    question=q_ood,
                    answer=STANDARD_REFUSAL_FI,
                    aliases=[STANDARD_REFUSAL_EN],
                    supporting_paras=[],
                    candidate_paras=[],
                    decomposition=[],
                    answerable=False,
                    hop_count=0,
                )
                ood_samples.append(sample)
                new_ood.append(sample)
                seen_questions.add(q_ood.strip().lower())
                q_disp = (q_ood[:50] + "...") if len(q_ood) > 50 else q_ood
                self.console.print(f"  [bold yellow]✓ [{len(ood_samples)}/{target_ood}] Added OOD Refusal:[/bold yellow] \"{q_disp}\"")

        # Backup existing file if resuming and it exists
        if existing_samples and out_path.exists():
            bak_path = out_path.with_suffix(out_path.suffix + ".bak")
            try:
                shutil.copy2(out_path, bak_path)
                self.console.print(f"[dim]Turvakopio luotu: {bak_path}[/dim]")
            except Exception as e:
                self.console.print(f"[dim yellow]Turvakopion luonti epäonnistui: {e}[/dim yellow]")

        # Assemble final samples
        if existing_samples:
            # Preserve existing samples exactly in order, append newly generated ones
            all_samples = list(existing_samples) + new_single + new_multi + new_ood
        else:
            # Clean run: interleave and merge all samples
            all_samples = []
            in_domain = single_hop_samples + multi_hop_samples
            random.seed(42)
            random.shuffle(in_domain)

            # Evenly distribute OOD questions across the dataset
            ood_idx = 0
            step = max(1, (len(in_domain) + len(ood_samples)) // max(1, len(ood_samples) + 1))
            for idx in range(1, len(in_domain) + len(ood_samples) + 1):
                if ood_idx < len(ood_samples) and (idx % step == 0 or not in_domain):
                    all_samples.append(ood_samples[ood_idx])
                    ood_idx += 1
                elif in_domain:
                    all_samples.append(in_domain.pop(0))
                elif ood_idx < len(ood_samples):
                    all_samples.append(ood_samples[ood_idx])
                    ood_idx += 1

        # Write final JSONL
        with out_path.open("w", encoding="utf-8") as f:
            for s in all_samples:
                f.write(json.dumps(s, ensure_ascii=False) + "\n")

        self.console.print(f"\n[bold green]✓ Successfully generated {len(all_samples)} MuSiQue-schema questions to:[/bold green] [cyan]{out_path}[/cyan]")

        # Print Rich Summary Table
        table = Table(title="Generated Enterprise MuSiQue Benchmark Dataset Summary", show_header=True)
        table.add_column("Category", style="cyan")
        table.add_column("Hop Count", justify="right", style="magenta")
        table.add_column("Count", justify="right", style="green")
        table.add_column("Paragraphs/Query", justify="right", style="yellow")
        table.add_column("Ground Truth Answer Style", style="white")

        c_single = len(single_hop_samples)
        c_multi = len(multi_hop_samples)
        c_ood = len(ood_samples)

        table.add_row("Single-Hop Factual", "1", str(c_single), "20 (1 supporting, 19 distractors)", "Factual 1-3 sentences")
        table.add_row("Multi-Hop Relational", "2", str(c_multi), "20 (2 supporting, 18 distractors)", "Relational/comparative with decomposition")
        table.add_row("Out-of-Domain Refusal", "0", str(c_ood), "20 (0 supporting, 20 distractors)", f"'{STANDARD_REFUSAL_FI}'")
        table.add_row("[bold]Total[/bold]", "--", f"[bold]{len(all_samples)}[/bold]", "20", "--")

        self.console.print(table)
        return all_samples
