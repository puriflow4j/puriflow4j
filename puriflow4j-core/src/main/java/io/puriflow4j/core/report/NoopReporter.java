/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.report;

import io.puriflow4j.core.api.model.Finding;
import java.util.List;

public final class NoopReporter implements Reporter {
    @Override
    public void report(List<Finding> findings) {
        /* no-op */
    }
}
