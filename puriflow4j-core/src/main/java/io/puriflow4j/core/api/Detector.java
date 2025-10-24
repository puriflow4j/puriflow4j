/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.api;

import io.puriflow4j.core.api.model.DetectionResult;

/** Stateless detector that returns spans (start..end) to replace. */
public interface Detector {
    DetectionResult detect(String message);
}
