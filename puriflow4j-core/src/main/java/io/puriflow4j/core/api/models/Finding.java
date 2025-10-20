/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.api.models;

/** A single detection occurrence (type + action + logger), useful for metrics. */
public record Finding(String type, Action action, String loggerName) {}
