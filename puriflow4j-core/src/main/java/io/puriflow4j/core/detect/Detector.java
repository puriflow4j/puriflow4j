/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.detect;

import io.puriflow4j.core.api.models.DetectionResult;

public interface Detector {
    String name();

    DetectionResult detect(String input);
}
