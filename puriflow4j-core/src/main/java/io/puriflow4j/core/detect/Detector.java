package io.puriflow4j.core.detect;

import io.puriflow4j.core.api.models.DetectionResult;

public interface Detector {
    String name();
    DetectionResult detect(String input);
}
