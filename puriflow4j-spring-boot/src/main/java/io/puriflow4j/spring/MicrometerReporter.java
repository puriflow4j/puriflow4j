/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.spring;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.MeterRegistry;
import io.puriflow4j.core.api.models.Finding;
import io.puriflow4j.core.report.Reporter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public final class MicrometerReporter implements Reporter {
    private final MeterRegistry registry;
    private final Deque<Finding> ring = new ArrayDeque<>();
    private final int capacity;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "MeterRegistry is a framework-managed, thread-safe component; "
                    + "keeping a reference is required for metrics reporting and it is not exposed via accessors.")
    public MicrometerReporter(MeterRegistry registry, int capacity) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.capacity = Math.max(10, capacity);
    }

    @Override
    public synchronized void report(List<Finding> findings) {
        if (findings == null || findings.isEmpty()) return;
        for (Finding f : findings) {
            registry.counter("puriflow4j_pii_detected_total", "type", f.type()).increment();
            if (ring.size() >= capacity) ring.removeFirst();
            ring.addLast(f);
        }
    }

    /** Returns an unmodifiable snapshot of the recent findings ring buffer. */
    public synchronized List<Finding> recentFindings() {
        return List.copyOf(ring);
    }
}
