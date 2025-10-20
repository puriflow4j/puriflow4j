package io.puriflow4j.core.api.models;

/**
 * A single detection occurrence (type + action + logger), useful for metrics.
 */
public record Finding(String type, Action action, String loggerName) { }
