package com.dippa.ragbenchmark.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Thrown when an unrecognized or unsupported {@link ModuleId} is requested by a caller.
 * In the thesis testbed, all four modules M1–M4 are supported; this exception serves as a
 * fail-fast guard ensuring callers receive HTTP 501 rather than unhandled behavior.
 */
public class ModuleNotImplementedException extends RuntimeException {

    private final Set<ModuleId> modules;

    public ModuleNotImplementedException(Set<ModuleId> modules) {
        super("Module(s) not implemented: " + modules);
        this.modules = modules == null || modules.isEmpty()
                ? EnumSet.noneOf(ModuleId.class)
                : EnumSet.copyOf(modules);
    }

    public Set<ModuleId> getModules() {
        return modules;
    }
}
