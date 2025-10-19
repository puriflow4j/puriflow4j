package io.puriflow4j.core.api;

public interface Detector {
    String name();
    DetectionResult detect(String input);
}
