package com.dippa.ragbenchmark.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Thrown when a {@link ModuleId} (or set of them) is requested by a caller but has no
 * implementation wired up yet. In v1, all of M1-M4 fall into this bucket — the baseline
 * (M0) pipeline fails fast rather than silently ignoring the request.
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
