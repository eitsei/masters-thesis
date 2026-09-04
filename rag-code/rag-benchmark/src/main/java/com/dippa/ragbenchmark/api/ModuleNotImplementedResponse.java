package com.dippa.ragbenchmark.api;

import java.util.List;

/**
 * Error body returned with HTTP 501 when one or more requested optimization modules aren't
 * implemented yet.
 */
public record ModuleNotImplementedResponse(String error, List<String> modules, String message) {
}
