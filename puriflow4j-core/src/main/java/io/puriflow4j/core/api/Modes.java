/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.api;

import io.puriflow4j.core.api.model.Action;
import io.puriflow4j.core.api.model.Mode;

public final class Modes {
    private Modes() {}

    public static Action actionFor(Mode mode) {
        return switch (mode) {
            case DRY_RUN -> Action.WARN; // detect & report only
            case MASK -> Action.MASK; // replace matched spans
            case STRICT -> Action.MASK; // future: escalate to REDACT for some types
        };
    }
}
