package com.dippa.ragbenchmark.domain;

/**
 * Request payload passed into {@link ModelRouter}.
 *
 * @param query the user query to route
 * @param hopCountHint optional ground-truth reasoning depth (e.g. from MuSiQue dataset) used by {@link RoutingMode#ORACLE}
 */
public record RoutingRequest(String query, Integer hopCountHint) {

    public RoutingRequest(String query) {
        this(query, null);
    }
}
