"""Nordea Personal Banking Benchmark Dataset Builder (MuSiQue Schema).

Constructs an enterprise benchmark dataset adhering 100% to the MuSiQue JSONL schema:
- Ingests Finnish personal banking questions from CSV (e.g. qs_fi.csv)
- Segments and indexes crawled personal banking Markdown corpus (nordea.fi/henkiloasiakkaat)
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

# Canonical Out-of-Domain financial / non-Nordea questions
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
    """Represents a clean, indexed paragraph/section from the Nordea corpus."""

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

        scored: list[tuple[float, CorpusParagraph]] = []
        q_lower = query.lower()
        q_tokens = set(re.findall(r"\w+", q_lower))

        for p in self.paragraphs:
            p_text = f"{p.title}\n{p.paragraph_text}".lower()
            # Fast token overlap
            p_tokens = set(re.findall(r"\w+", p_text))
            token_overlap = len(q_tokens & p_tokens) / max(1, len(q_tokens))

            # Rapidfuzz token set ratio for Finnish compound word resilience
            fuzzy_ratio = fuzz.token_set_ratio(query, p.title + " " + p.paragraph_text[:200]) / 100.0

            combined_score = 0.4 * token_overlap + 0.6 * fuzzy_ratio
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


class NordeaMusiqueDatasetBuilder:
    """Builds a stratified 100-question MuSiQue benchmark dataset from Nordea corpus and FAQs."""

    def __init__(
        self,
        corpus_indexer: CorpusIndexer,
        llm: Optional[BaseChatModel] = None,
        judge_model: str = "gemini-3.8-flash",
        console: Optional[Console] = None,
    ) -> None:
        self.indexer = corpus_indexer
        self.console = console or Console()
        self.judge_model = judge_model
        if llm is None:
            self.llm, _ = JudgeFactory.create_judge(provider="gemini", model=judge_model)
        else:
            self.llm = llm

    def _verify_and_synthesize(
        self,
        question: str,
        candidates: list[CorpusParagraph],
    ) -> Optional[dict[str, Any]]:
        """Prompts Gemini to verify ground truth, extract supporting passages, and formulate answer."""
        if not self.llm or not candidates:
            return None

        contexts_text = ""
        for i, c in enumerate(candidates):
            contexts_text += f"\n--- [KAPPALE {i}] (Otsikko: {c.title}) ---\n{c.paragraph_text}\n"

        system_prompt = (
            "Olet suomalaisen finanssialan ja pankkipalveluiden RAG-benchmark-asiantuntija.\n"
            "Tehtäväsi on analysoida asiakaskysymys ja annetut Nordean henkilöasiakassivuston tekstikappaleet.\n"
            "Noudata ehdottomia sääntöjä:\n"
            "1. Vastaa kysymykseen AINOASTAAN ja YKSINOMAAN annettujen tekstikappaleiden sisältämien faktojen perusteella.\n"
            "2. Jos teksti ei vastaa kysymykseen suoraan ja luotettavasti, aseta 'is_answerable': false.\n"
            "3. Jos teksti vastaa kysymykseen:\n"
            "   - 'is_answerable': true\n"
            "   - 'supporting_indices': lista kappaleiden indekseistä (esim. [0] tai [0, 2]), jotka sisältävät vastauksen faktat.\n"
            "   - 'hop_count': 1 jos vastaus löytyy yhdestä kappaleesta, 2 jos vastaus vaatii kahden eri kappaleen/tuotteen yhdistämistä.\n"
            "   - 'answer': Täsmällinen, faktuaalinen ja selkeä asiantuntijavastaus (1-3 virkettä suomeksi).\n"
            "   - 'answer_aliases': 1-2 vaihtoehtoista hyväksyttävää ja luonnollista vastausmuotoilua.\n"
            "   - 'decomposition': Jos hop_count == 2, jaa kysymys kahteen alikysymykseen: [{\"question\": \"...\", \"answer\": \"...\", \"support_index\": X}]. Jos hop_count == 1, tyhjä lista [].\n\n"
            "Palauta tulos AINOASTAAN validina JSON-objektina ilman markdown-koodiblokkeja seuraavassa muodossa:\n"
            "{\n"
            '  "is_answerable": true,\n'
            '  "hop_count": 1,\n'
            '  "supporting_indices": [0],\n'
            '  "answer": "...",\n'
            '  "answer_aliases": ["..."],\n'
            '  "decomposition": []\n'
            "}"
        )

        user_content = f"KYSYMYS: {question}\n\nKANDIDAATTIKAPPALEET:\n{contexts_text}"

        try:
            resp = self.llm.invoke([SystemMessage(content=system_prompt), HumanMessage(content=user_content)])
            resp_text = resp.content.strip()
            # Clean markdown code fences if model returned them
            if resp_text.startswith("```"):
                resp_text = re.sub(r"^```[a-zA-Z]*\n?", "", resp_text)
                resp_text = re.sub(r"\n?```$", "", resp_text)
            resp_text = resp_text.strip()
            data = json.loads(resp_text)
            if data.get("is_answerable") and data.get("supporting_indices") and data.get("answer"):
                return data
        except Exception as e:
            logger.debug(f"LLM verification error for '{question[:40]}': {e}")

        return None

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
    ) -> list[dict[str, Any]]:
        """Processes questions and builds the full stratified MuSiQue-schema JSONL dataset."""
        out_path = Path(output_file)
        out_path.parent.mkdir(parents=True, exist_ok=True)

        if target_size >= 3:
            target_single = max(1, int(round(target_size * single_hop_ratio)))
            target_multi = max(1, int(round(target_size * multi_hop_ratio)))
            target_ood = max(1, target_size - target_single - target_multi)
        else:
            target_single = 1
            target_multi = 0
            target_ood = max(0, target_size - 1)

        self.console.print(f"[bold cyan]Building Nordea MuSiQue Benchmark Dataset (Target: {target_size})[/bold cyan]")
        self.console.print(
            f"Targets: [green]{target_single} Single-Hop[/green] | "
            f"[magenta]{target_multi} Multi-Hop[/magenta] | "
            f"[yellow]{target_ood} Out-of-Domain[/yellow]"
        )

        single_hop_samples: list[dict[str, Any]] = []
        multi_hop_samples: list[dict[str, Any]] = []

        q_pool = questions[:limit_input] if limit_input else questions

        with self.console.status("[bold green]Evaluating questions against corpus with LLM...[/bold green]"):
            for i, q in enumerate(q_pool):
                if len(single_hop_samples) >= target_single and len(multi_hop_samples) >= target_multi:
                    break

                candidates = self.indexer.search_candidates(q, top_k=8)
                if not candidates:
                    continue

                res = self._verify_and_synthesize(q, candidates)
                if not res:
                    continue

                supp_indices = res.get("supporting_indices", [])
                supp_paras = [candidates[idx] for idx in supp_indices if idx < len(candidates)]
                if not supp_paras:
                    continue

                hops = res.get("hop_count", 1)
                ans = res.get("answer", "")
                aliases = res.get("answer_aliases", [])
                decomp = res.get("decomposition", [])

                if hops >= 2 and len(multi_hop_samples) < target_multi:
                    sample_id = f"2hop__nordea_{len(multi_hop_samples) + 1:04d}"
                    sample = self._build_musique_sample(
                        sample_id=sample_id,
                        question=q,
                        answer=ans,
                        aliases=aliases,
                        supporting_paras=supp_paras,
                        candidate_paras=candidates,
                        decomposition=decomp,
                        answerable=True,
                    )
                    multi_hop_samples.append(sample)
                    logger.info(f"Accepted multi-hop question [{len(multi_hop_samples)}/{target_multi}]: {q[:50]}")
                elif hops == 1 and len(single_hop_samples) < target_single:
                    sample_id = f"1hop__nordea_{len(single_hop_samples) + 1:04d}"
                    sample = self._build_musique_sample(
                        sample_id=sample_id,
                        question=q,
                        answer=ans,
                        aliases=aliases,
                        supporting_paras=supp_paras,
                        candidate_paras=candidates,
                        decomposition=[],
                        answerable=True,
                    )
                    single_hop_samples.append(sample)
                    logger.info(f"Accepted single-hop question [{len(single_hop_samples)}/{target_single}]: {q[:50]}")

        # Assemble Out-of-Domain Refusal Samples
        ood_samples: list[dict[str, Any]] = []
        for i in range(target_ood):
            q_ood = OUT_OF_DOMAIN_QUERIES[i % len(OUT_OF_DOMAIN_QUERIES)]
            sample_id = f"ood__nordea_{i + 1:04d}"
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
            )
            ood_samples.append(sample)

        # Interleave and merge all samples
        all_samples: list[dict[str, Any]] = []
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
        table = Table(title="Generated Nordea MuSiQue Benchmark Dataset Summary", show_header=True)
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
