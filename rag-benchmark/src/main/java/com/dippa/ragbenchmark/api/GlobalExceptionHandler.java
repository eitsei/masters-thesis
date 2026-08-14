package com.dippa.ragbenchmark.api;

import com.dippa.ragbenchmark.domain.ChunkPersistenceException;
import com.dippa.ragbenchmark.domain.InvalidModuleException;
import com.dippa.ragbenchmark.domain.ModuleNotImplementedException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain exceptions into HTTP responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ModuleNotImplementedException.class)
    public ResponseEntity<ModuleNotImplementedResponse> handleModuleNotImplemented(
            ModuleNotImplementedException exception) {
        List<String> modules = exception.getModules().stream()
                .map(Enum::name)
                .sorted()
                .toList();
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
}
