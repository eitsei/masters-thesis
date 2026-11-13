package com.dippa.ragbenchmark.domain;

/**
 * Thrown when a raw module identifier string (e.g. from a query request) doesn't match any
 * {@link ModuleId}. Distinct from {@link ModuleNotImplementedException}: that one means "this
 * module exists but isn't built yet," this one means "no such module exists at all."
 */
public class InvalidModuleException extends RuntimeException {

    public InvalidModuleException(String rawValue, Throwable cause) {
        super("Unknown module: " + rawValue, cause);
    }
}
