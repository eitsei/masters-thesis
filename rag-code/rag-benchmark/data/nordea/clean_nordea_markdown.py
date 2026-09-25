#!/usr/bin/env python3
"""Nordea Markdown Corpus Cleaning Script.

Cleans web crawling and document extraction noise from Nordea markdown files:
1. Removes cookie / marketing video notices ("Hups! Jos haluat nähdä...").
2. Removes screen reader / new window annotations ("Avautuu uuteen ikkunaan").
3. Removes repetitive help desk footer blocks ("## Apua asiointiin...").
4. Cleans up empty headings and collapses consecutive blank lines.
5. Preserves YAML frontmatter intact.

Usage:
    python3 clean_nordea_markdown.py --dry-run
    python3 clean_nordea_markdown.py --in-place [--backup]
    python3 clean_nordea_markdown.py --dir /path/to/md --dry-run
"""

from __future__ import annotations

import argparse
import difflib
import re
import shutil
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional


# --- Regex Patterns for Noise Removal ---

COOKIE_NOTICE_PATTERN = re.compile(
    r"^[^\n]*Hups! Jos haluat nähdä tällaista sisältöä, hyväksy ensin Nordean markkinointievästeet[^\n]*(?:\r?\n|$)",
    re.MULTILINE,
)

HELP_FOOTER_PATTERN = re.compile(
    r"##\s+Apua asiointiin\s*\n+"
    r"\s*###\s+Henkilöasiakkaat\s*\n+"
    r"\s*-\s+Kysy heti chatissa\s*\n+"
    r"\s*-\s+Usein kysytyt\s*\n+"
    r"\s*-\s+Soita meille\s*\n+"
    r"\s*-\s+Nordea Omaposti[^\n]*(?:\r?\n|$)",
    re.MULTILINE,
)

WINDOW_ANNOTATION_PATTERN = re.compile(r" *Avautuu uuteen ikkunaan")

EMPTY_HEADING_PATTERN = re.compile(r"^[ \t]*#{1,6}[ \t]*(?:\r?\n|$)", re.MULTILINE)

FRONTMATTER_PATTERN = re.compile(r"^---\n.*?\n---\n", re.DOTALL)


@dataclass
class FileStats:
    path: Path
    cookie_notices: int = 0
    window_annotations: int = 0
    help_footers: int = 0
    empty_headings: int = 0
    consecutive_newlines_collapsed: int = 0
    trailing_newline_added: bool = False
    original_size: int = 0
    cleaned_size: int = 0

    @property
    def has_noise_patterns(self) -> bool:
        return (
            self.cookie_notices > 0
            or self.window_annotations > 0
            or self.help_footers > 0
            or self.empty_headings > 0
        )

    @property
    def is_modified(self) -> bool:
        return (
            self.has_noise_patterns
            or self.consecutive_newlines_collapsed > 0
            or self.trailing_newline_added
        )


@dataclass
class CleaningReport:
    total_files_examined: int = 0
    files_with_noise: int = 0
    total_files_modified: int = 0
    total_cookie_notices: int = 0
    total_window_annotations: int = 0
    total_help_footers: int = 0
    total_empty_headings: int = 0
    files_cookie_notices: int = 0
    files_window_annotations: int = 0
    files_help_footers: int = 0
    files_empty_headings: int = 0
    files_newlines_collapsed: int = 0
    files_trailing_newline_added: int = 0
    file_stats: list[FileStats] = field(default_factory=list)


def split_frontmatter(content: str) -> tuple[str, str]:
    """Splits frontmatter (if present) from document body.

    Preserves frontmatter intact (everything between starting --- and ending ---).
    """
    m = FRONTMATTER_PATTERN.match(content)
    if m:
        frontmatter = m.group(0)
        body = content[len(frontmatter):]
        return frontmatter, body
    return "", content


def clean_markdown_content(content: str, file_path: Path) -> tuple[str, FileStats]:
    """Applies all cleaning rules to a single markdown file content.

    Returns the cleaned content and statistics about changes applied.
    """
    stats = FileStats(path=file_path, original_size=len(content))

    frontmatter, body = split_frontmatter(content)

    # 1. Remove Help footer block:
    # ## Apua asiointiin
    # ### Henkilöasiakkaat
    # - Kysy heti chatissa
    # - Usein kysytyt
    # - Soita meille
    # - Nordea Omaposti
    body, f_count = HELP_FOOTER_PATTERN.subn("", body)
    stats.help_footers = f_count

    # 2. Remove Cookie / marketing video notice:
    # "Hups! Jos haluat nähdä tällaista sisältöä, hyväksy ensin Nordean markkinointievästeet"
    body, c_count = COOKIE_NOTICE_PATTERN.subn("", body)
    stats.cookie_notices = c_count

    # 3. Remove Screen reader / window annotations:
    # "Avautuu uuteen ikkunaan"
    w_matches = re.findall(r"Avautuu uuteen ikkunaan", body)
    stats.window_annotations = len(w_matches)
    if stats.window_annotations > 0:
        cleaned_lines = []
        for line in body.splitlines():
            if "Avautuu uuteen ikkunaan" in line:
                line = WINDOW_ANNOTATION_PATTERN.sub("", line)
                # Clean up multiple spaces and trailing spaces created on the line (preserving leading indent)
                line = re.sub(r"(?<=\S)[ \t]{2,}", " ", line).rstrip()
            cleaned_lines.append(line)
        body = "\n".join(cleaned_lines)

    # 4. Remove empty headings (e.g. "## " or "### " with no text)
    body, eh_count = EMPTY_HEADING_PATTERN.subn("", body)
    stats.empty_headings = eh_count

    # 5. Collapse 3 or more consecutive newlines into 2 (one blank line between blocks)
    new_body, nl_count = re.subn(r"\n{3,}", "\n\n", body)
    stats.consecutive_newlines_collapsed = nl_count
    body = new_body

    # 6. Ensure trailing newline at end of file
    if not content.endswith("\n"):
        stats.trailing_newline_added = True

    body = body.rstrip() + "\n"

    cleaned_content = frontmatter + body
    stats.cleaned_size = len(cleaned_content)

    return cleaned_content, stats


def generate_diff(original: str, cleaned: str, file_path: Path, base_dir: Path) -> str:
    """Generates unified diff between original and cleaned text."""
    try:
        rel_path = file_path.relative_to(base_dir)
    except ValueError:
        rel_path = file_path

    diff = difflib.unified_diff(
        original.splitlines(keepends=True),
        cleaned.splitlines(keepends=True),
        fromfile=f"a/{rel_path} (original)",
        tofile=f"b/{rel_path} (cleaned)",
        n=3,
    )
    return "".join(diff)


def process_directory(
    target_dir: Path,
    dry_run: bool = True,
    backup: bool = False,
    sample_file_pattern: Optional[str] = None,
) -> tuple[CleaningReport, Optional[str], Optional[Path]]:
    """Scans and cleans markdown files in target_dir.

    Returns the cleaning report, sample diff preview string, and sample file path.
    """
    if not target_dir.is_dir():
        raise FileNotFoundError(f"Target directory does not exist: {target_dir}")

    report = CleaningReport()
    # Find all .md files (excluding any .bak or backup files)
    md_files = sorted(
        p for p in target_dir.rglob("*.md")
        if not p.name.endswith(".bak") and p.is_file()
    )

    sample_diff: Optional[str] = None
    sample_path: Optional[Path] = None

    # Track best candidate for sample diff (file with all 3 noise patterns preferred)
    best_candidate_path: Optional[Path] = None
    best_candidate_diff: Optional[str] = None
    best_candidate_score: int = -1

    for file_path in md_files:
        report.total_files_examined += 1

        try:
            original_content = file_path.read_text(encoding="utf-8")
        except UnicodeDecodeError as e:
            print(f"Warning: Skipping non-UTF8 file {file_path}: {e}", file=sys.stderr)
            continue

        cleaned_content, stats = clean_markdown_content(original_content, file_path)
        report.file_stats.append(stats)

        # Update aggregate counters
        report.total_cookie_notices += stats.cookie_notices
        report.total_window_annotations += stats.window_annotations
        report.total_help_footers += stats.help_footers
        report.total_empty_headings += stats.empty_headings

        if stats.cookie_notices > 0:
            report.files_cookie_notices += 1
        if stats.window_annotations > 0:
            report.files_window_annotations += 1
        if stats.help_footers > 0:
            report.files_help_footers += 1
        if stats.empty_headings > 0:
            report.files_empty_headings += 1
        if stats.consecutive_newlines_collapsed > 0:
            report.files_newlines_collapsed += 1
        if stats.trailing_newline_added:
            report.files_trailing_newline_added += 1

        if stats.has_noise_patterns:
            report.files_with_noise += 1

        if stats.is_modified:
            report.total_files_modified += 1

            # Check if this file qualifies as sample diff
            diff = generate_diff(original_content, cleaned_content, file_path, target_dir)

            if sample_file_pattern and sample_file_pattern in str(file_path):
                sample_diff = diff
                sample_path = file_path

            score = (
                (1 if stats.cookie_notices > 0 else 0)
                + (1 if stats.window_annotations > 0 else 0)
                + (1 if stats.help_footers > 0 else 0)
            )
            if score > best_candidate_score:
                best_candidate_score = score
                best_candidate_path = file_path
                best_candidate_diff = diff

            # Write changes if in-place mode
            if not dry_run:
                if backup:
                    backup_path = file_path.with_name(f"{file_path.name}.bak")
                    shutil.copy2(file_path, backup_path)
                file_path.write_text(cleaned_content, encoding="utf-8")

    if not sample_diff:
        sample_diff = best_candidate_diff
        sample_path = best_candidate_path

    return report, sample_diff, sample_path


def print_report(
    report: CleaningReport,
    target_dir: Path,
    dry_run: bool,
    backup: bool,
    sample_diff: Optional[str],
    sample_path: Optional[Path],
) -> None:
    mode_str = "DRY RUN (no files modified)" if dry_run else f"IN-PLACE (backup={backup})"

    print("=" * 68)
    print("Nordea Markdown Cleaning Report")
    print(f"Mode:             {mode_str}")
    print(f"Target Directory: {target_dir.resolve()}")
    print("=" * 68)
    print()
    print("Execution Summary:")
    print(f"  Files examined:                {report.total_files_examined:>6}")
    print(f"  Files with noise patterns:     {report.files_with_noise:>6}")
    print(f"  Total files modified:          {report.total_files_modified:>6}")
    print()
    print("Noise Pattern Occurrences:")
    print(
        f"  Cookie notices removed:        {report.total_cookie_notices:>6}  "
        f"(across {report.files_cookie_notices} files)"
    )
    print(
        f"  Window annotations removed:    {report.total_window_annotations:>6}  "
        f"(across {report.files_window_annotations} files)"
    )
    print(
        f"  Help footers removed:          {report.total_help_footers:>6}  "
        f"(across {report.files_help_footers} files)"
    )
    print(
        f"  Empty headings removed:        {report.total_empty_headings:>6}  "
        f"(across {report.files_empty_headings} files)"
    )
    print()
    print("Structural Normalization:")
    print(f"  Files with newlines collapsed: {report.files_newlines_collapsed:>6}")
    print(f"  Trailing newline added:        {report.files_trailing_newline_added:>6}")
    print()

    if sample_diff and sample_path:
        print("=" * 68)
        print(f"Sample Diff Preview: {sample_path.name}")
        print("=" * 68)
        print(sample_diff)
        print("=" * 68)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Clean extracted Nordea markdown documents."
    )
    default_dir = Path(__file__).resolve().parent / "md"
    parser.add_argument(
        "--dir",
        type=Path,
        default=default_dir,
        help=f"Directory containing markdown files (default: {default_dir})",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        default=False,
        help="Scan and print statistics without modifying files (default behavior)",
    )
    parser.add_argument(
        "--in-place",
        action="store_true",
        default=False,
        help="Write cleaned content back to files",
    )
    parser.add_argument(
        "--backup",
        action="store_true",
        default=False,
        help="When used with --in-place, creates a .bak backup file before overwriting",
    )
    parser.add_argument(
        "--sample-file",
        type=str,
        default=None,
        help="Substring or filename to select for the diff preview",
    )

    args = parser.parse_args()

    if args.dry_run and args.in_place:
        parser.error("Cannot specify both --dry-run and --in-place")

    # Dry-run is default if --in-place is not specified
    dry_run = not args.in_place

    if args.backup and dry_run:
        print("Note: --backup specified with dry-run; no backup files will be created.", file=sys.stderr)

    try:
        report, sample_diff, sample_path = process_directory(
            target_dir=args.dir,
            dry_run=dry_run,
            backup=args.backup,
            sample_file_pattern=args.sample_file,
        )
    except FileNotFoundError as e:
        print(f"Error: {e}", file=sys.stderr)
        return 1

    print_report(
        report=report,
        target_dir=args.dir,
        dry_run=dry_run,
        backup=args.backup,
        sample_diff=sample_diff,
        sample_path=sample_path,
    )

    return 0


if __name__ == "__main__":
    sys.exit(main())
