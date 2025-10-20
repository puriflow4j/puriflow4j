package io.puriflow4j.spring;

import io.micrometer.core.instrument.MeterRegistry;
import io.puriflow4j.core.api.models.Finding;
import io.puriflow4j.core.report.Reporter;

import java.util.*;

public final class MicrometerReporter implements Reporter {
    private final MeterRegistry registry;
    private final Deque<Finding> ring = new ArrayDeque<>();
    private final int capacity;

    public MicrometerReporter(MeterRegistry registry, int capacity) {
        this.registry = registry;
        this.capacity = Math.max(10, capacity);
    }

    @Override
    public synchronized void report(List<Finding> findings) {
        for (Finding f : findings) {
            registry.counter("puriflow4j_pii_detected_total", "type", f.type()).increment();
            if (ring.size() >= capacity) ring.removeFirst();
            ring.addLast(f);
        }
    }

    public synchronized List<Finding> recentFindings() {
        return List.copyOf(ring);
    }
}
