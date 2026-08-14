Status: resolved
Recommendation strength: Worth exploring

# Shallow pass-through DTOs duplicate application-layer records field-for-field

## Files
- `rag-benchmark/src/main/java/com/dippa/ragbenchmark/application/IngestionResult.java`
- `rag-benchmark/src/main/java/com/dippa/ragbenchmark/api/IngestDocumentResponse.java`
- `rag-benchmark/src/main/java/com/dippa/ragbenchmark/api/DocumentController.java` (lines 27-32)
- `rag-benchmark/src/main/java/com/dippa/ragbenchmark/application/RetrievedChunk.java`
- `rag-benchmark/src/main/java/com/dippa/ragbenchmark/api/RetrievedChunkDto.java`
- `rag-benchmark/src/main/java/com/dippa/ragbenchmark/api/QueryController.java` (lines 49-51, `toDto`)

## Problem
`IngestionResult(documentId, chunkCount, chunkIds)` and
`IngestDocumentResponse(documentId, chunkCount, chunkIds)` are byte-for-byte identical
shapes; `DocumentController.ingest` does nothing but copy the three fields across the
api/application seam. Same pattern, smaller scale, for `RetrievedChunk` →
`RetrievedChunkDto` via `QueryController.toDto`. Deletion test: delete
`IngestDocumentResponse`, return `IngestionResult` directly — nothing breaks, because
nothing was hidden by the extra type. Contrast with `QueryResponse`, which does real
work (enum-name mapping, timing assembly) and is *not* shallow — this finding is
specifically about the two 1:1 copies.

## Proposed solution
Drop the 1:1 DTOs and serialize the application-layer records directly, or explicitly
document why api/application stay decoupled if that's intentional going forward.

## Benefits
Locality (one fewer file per concept to keep in sync), leverage (deletes an
indirection layer with no capability), removes two currently-untested mapping methods
from the surface entirely (see issue 03).

## Resolution
Deleted `IngestDocumentResponse.java` and `RetrievedChunkDto.java`.
`DocumentController.ingest` now returns `ResponseEntity<IngestionResult>` directly.
`QueryController` no longer has a `toDto` method; `QueryResponse.retrievedChunks` is
now typed `List<RetrievedChunk>`. Confirmed live (real Ollama chat + embedding calls,
not just compiled): JSON field names are unchanged for both endpoints — ingest still
returns `{"documentId","chunkCount","chunkIds"}`, query still returns
`retrievedChunks: [{"id","snippet"}, ...]` — so this is invisible to any API caller.
`IngestionResult`/`RetrievedChunk` javadoc updated to note they double as response
bodies now.
