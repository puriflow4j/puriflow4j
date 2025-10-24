/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.api.model;

/** A single detection occurrence (type + action + start + end), useful for metrics. */
public record Finding(String type, Action action, int start, int end) {}
