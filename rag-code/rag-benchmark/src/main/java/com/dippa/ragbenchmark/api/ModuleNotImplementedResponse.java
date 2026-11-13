package com.dippa.ragbenchmark.api;

import java.util.List;

/**
 * Error body returned with HTTP 501 when an unknown or unsupported optimization module is
 * requested. In this benchmark testbed, modules M1–M4 are fully supported; inactive providers
 * return HTTP 400 with specific configuration error codes instead.
 */
public record ModuleNotImplementedResponse(String error, List<String> modules, String message) {
}
