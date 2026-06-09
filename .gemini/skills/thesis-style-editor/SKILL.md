---
name: thesis-style-editor
description: Checks grammar, academic tone, and refines style in LaTeX chapters of the thesis, preserving the user's specific voice and structure without complete rewrites.
---

# Thesis Style Editor

This skill provides a structured workflow for proofreading, editing, and checking the academic tone of Master's Thesis chapters in LaTeX.

## Workflows

### Review and Edit a LaTeX Chapter
When asked to review or proofread a chapter (e.g., in `thesis/chapters/`):
1. **Analyze the Document**: View the target `.tex` file using the `view_file` tool to understand the current draft structure.
2. **Scan for Issues**: Check for:
   - Grammatical errors and awkward phrasing.
   - Informal language or subjective assertions (aim for objective academic tone).
   - Correct LaTeX citation commands (`\cite`, `\citep`, `\citet`).
3. **Make Targeted Replacements**: Do NOT rewrite whole paragraphs. Use `replace_file_content` or `multi_replace_file_content` to perform precise, localized corrections.
4. **Explain the Edits**: Document each change clearly to the user, explaining why the correction was made (e.g., passive vs. active voice, tone improvement, grammatical fix).

## Style Rules
- **No Global Rewrites**: Preserve the user's voice, phrasing, and organization. Keep modifications minimal and purposeful.
- **Academic Precision**: Maintain third-person perspective and objective phrasing.
- **Reference Integrity**: Ensure cite keys match existing entries in the BibTeX files.
