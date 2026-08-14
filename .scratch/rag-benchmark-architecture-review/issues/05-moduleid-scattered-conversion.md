Status: open
Recommendation strength: Worth exploring

# ModuleId string conversion is implemented outside ModuleId, split across two unrelated files

## Files
- `rag-benchmark/src/main/java/com/dippa/ragbenchmark/domain/ModuleId.java`
- `rag-benchmark/src/main/java/com/dippa/ragbenchmark/api/QueryController.java` (lines 53-71: `parseModules`, `toModuleId`)
- `rag-benchmark/src/main/java/com/dippa/ragbenchmark/api/GlobalExceptionHandler.java` (lines 20-23: sorted-name formatting)

## Problem
`ModuleId` is a bare four-value enum with no methods. Parsing raw request strings into
it is ad hoc in `QueryController.toModuleId`; formatting a `Set<ModuleId>` back into a
sorted list for the 501 error body is separately ad hoc in `GlobalExceptionHandler`.
Understanding "how a module id round-trips through the system" today requires five
files, none of which is `ModuleId` itself — the module offers no leverage for the one
thing every caller needs to do with it, so that logic gets reinvented at each edge.
Will get worse as M1-M4 land and reuse the same parse/format pair.

## Proposed solution
Give `ModuleId` static `parse(String raw)` (case-insensitive, clear error on unknown
values) and `sortedNames(Set<ModuleId>)`. Have `QueryController` and
`GlobalExceptionHandler` call these instead of reimplementing the conversion.

## Benefits
Locality (one place owns how module ids are spelled on the wire), testability
(`ModuleId.parse`/`sortedNames` become directly unit-testable, currently impossible —
see issue 03).
