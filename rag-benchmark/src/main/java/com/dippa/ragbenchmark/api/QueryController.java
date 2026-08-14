package com.dippa.ragbenchmark.api;

import com.dippa.ragbenchmark.application.RagQueryResult;
import com.dippa.ragbenchmark.application.RagQueryService;
import com.dippa.ragbenchmark.domain.ModuleId;
import jakarta.validation.Valid;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for running the RAG query pipeline.
 */
@RestController
@RequestMapping("/api/query")
public class QueryController {

    private final RagQueryService ragQueryService;

    public QueryController(RagQueryService ragQueryService) {
        this.ragQueryService = ragQueryService;
    }

    @PostMapping
    public ResponseEntity<QueryResponse> query(@Valid @RequestBody QueryRequest request) {
        Set<ModuleId> modules = parseModules(request.modules());
        RagQueryResult result = ragQueryService.query(request.query(), modules);

        List<String> modulesApplied = result.modulesApplied().stream()
                .map(Enum::name)
                .toList();
        TimingsDto timings = new TimingsDto(result.retrievalMs(), result.generationMs());

        return ResponseEntity.ok(
                new QueryResponse(result.answer(), modulesApplied, result.retrievedChunks(), timings));
    }

    private Set<ModuleId> parseModules(List<String> rawModules) {
        if (rawModules == null || rawModules.isEmpty()) {
            return Set.of();
        }
        return rawModules.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(ModuleId::parse)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ModuleId.class)));
    }
}
