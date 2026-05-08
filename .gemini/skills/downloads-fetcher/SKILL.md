---
name: downloads-fetcher
description: Fetches academic papers and documents from the Windows Downloads folder (C:\Users\ajasu\Downloads) into the WSL workspace. Use when the user wants to import new research papers or documents for processing.
---

# Downloads Fetcher

This skill automates the retrieval of files from the Windows host machine into the WSL environment.

## Workflows

### Import Recent Downloads
1. List the most recent files in the Downloads folder.
2. Identify the target files (e.g., PDFs for RAG research).
3. Copy the files to the appropriate directory (e.g., `sources/background/` or `sources/literature_review/`).

## Tool Usage

The skill includes a helper script `scripts/fetch.sh`.

### List files
```bash
bash .skills_dev/downloads-fetcher/scripts/fetch.sh list
```

### Fetch a specific file
```bash
bash .skills_dev/downloads-fetcher/scripts/fetch.sh fetch "filename.pdf" "sources/background"
```

## Considerations
- The Windows Downloads folder is mapped to `/mnt/c/Users/ajasu/Downloads`.
- If a filename contains spaces, ensure it is properly quoted in the shell command.
