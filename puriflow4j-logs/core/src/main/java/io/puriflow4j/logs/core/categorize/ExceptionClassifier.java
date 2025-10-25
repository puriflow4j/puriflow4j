/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.logs.core.categorize;

import io.puriflow4j.logs.core.model.ThrowableView;

/** Classifies exceptions into broad categories regardless of rendering mode. */
public interface ExceptionClassifier {
    CategoryResult classify(ThrowableView tv);

    /** Simple DTO with label and optional sub-code for future use. */
    record CategoryResult(String label) {
        public static final CategoryResult NONE = new CategoryResult(null);

        public boolean hasLabel() {
            return label != null && !label.isBlank();
        }
    }

    /** Null object: never classifies anything. */
    static ExceptionClassifier noop() {
        return tv -> CategoryResult.NONE;
    }
}
