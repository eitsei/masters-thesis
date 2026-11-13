package com.dippa.ragbenchmark.api;

import com.dippa.ragbenchmark.domain.ChunkPersistenceException;
import com.dippa.ragbenchmark.domain.InvalidModuleException;
import com.dippa.ragbenchmark.domain.ModuleId;
import com.dippa.ragbenchmark.domain.ModuleNotImplementedException;
import com.dippa.ragbenchmark.domain.ParentExpansionException;
import com.dippa.ragbenchmark.domain.PromptCacheConfigurationException;
import com.dippa.ragbenchmark.domain.PromptCacheUnavailableException;
import com.dippa.ragbenchmark.domain.RerankingConfigurationException;
import com.dippa.ragbenchmark.domain.RerankingException;
import com.dippa.ragbenchmark.domain.RouterConfigurationException;
import com.dippa.ragbenchmark.domain.RouterException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain exceptions into HTTP responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);


    @ExceptionHandler(ModuleNotImplementedException.class)
    public ResponseEntity<ModuleNotImplementedResponse> handleModuleNotImplemented(
            ModuleNotImplementedException exception) {
        List<String> modules = ModuleId.sortedNames(exception.getModules());
        ModuleNotImplementedResponse body = new ModuleNotImplementedResponse(
                "module_not_implemented",
                modules,
                "The following module(s) are not implemented in this version: " + modules);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(body);
    }

    @ExceptionHandler(InvalidModuleException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidModule(InvalidModuleException exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "bad_request", "message", exception.getMessage()));
    }

    @ExceptionHandler(ChunkPersistenceException.class)
    public ResponseEntity<ChunkPersistenceErrorResponse> handleChunkPersistence(ChunkPersistenceException exception) {
        ChunkPersistenceErrorResponse body = new ChunkPersistenceErrorResponse(
                "chunk_persistence_failed",
                exception.getMessage(),
                exception.getDocumentId(),
                exception.getChunkIds());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(ParentExpansionException.class)
    public ResponseEntity<Map<String, Object>> handleParentExpansion(ParentExpansionException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "parent_expansion_failed", "message", exception.getMessage()));
    }

    @ExceptionHandler(RerankingException.class)
    public ResponseEntity<Map<String, Object>> handleReranking(RerankingException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "reranking_failed", "message", exception.getMessage()));
    }

    @ExceptionHandler(RerankingConfigurationException.class)
    public ResponseEntity<Map<String, Object>> handleRerankingConfiguration(RerankingConfigurationException exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "reranking_not_configured", "message", exception.getMessage()));
    }

    @ExceptionHandler(PromptCacheConfigurationException.class)
    public ResponseEntity<Map<String, Object>> handlePromptCacheConfiguration(PromptCacheConfigurationException exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "prompt_cache_not_configured", "message", exception.getMessage()));
    }

    @ExceptionHandler(PromptCacheUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handlePromptCacheUnavailable(PromptCacheUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "prompt_cache_unavailable", "message", exception.getMessage()));
    }

    @ExceptionHandler(RouterConfigurationException.class)
    public ResponseEntity<Map<String, Object>> handleRouterConfiguration(RouterConfigurationException exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "router_not_configured", "message", exception.getMessage()));
    }

    @ExceptionHandler(RouterException.class)
    public ResponseEntity<Map<String, Object>> handleRouter(RouterException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "routing_failed", "message", exception.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "not_found", "message", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "bad_request", "message", exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception exception) {
        logger.error("Unhandled exception processing request: {}", exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "internal_server_error",
                        "exception", exception.getClass().getSimpleName(),
                        "message", exception.getMessage() != null ? exception.getMessage() : "null"));
    }
}

