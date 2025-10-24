/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.api;

import io.puriflow4j.core.api.model.Action;
import io.puriflow4j.core.api.model.DetectionResult;
import io.puriflow4j.core.api.model.Finding;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Composite sanitizer that runs all detectors and applies right-to-left replacements. */
public final class Sanitizer {
    private final List<Detector> detectors;
    private final Action action; // derived from Mode (MASK→MASK, DRY_RUN→WARN)

    public Sanitizer(List<Detector> detectors, Action action) {
        this.detectors = List.copyOf(detectors);
        this.action = action;
    }

    public String apply(String message, String loggerName) {
        return applyDetailed(message, loggerName).sanitized();
    }

    public Result applyDetailed(String message, String loggerName) {
        if (message == null || message.isEmpty()) return new Result(message, List.of());
        List<DetectionResult.Span> all = new ArrayList<>();
        for (Detector d : detectors) {
            var r = d.detect(message);
            if (r.found()) all.addAll(r.spans());
        }
        if (all.isEmpty()) return new Result(message, List.of());

        // merge overlaps (by earliest start, prefer longer span)
        all.sort(Comparator.comparingInt(DetectionResult.Span::start)
                .thenComparing(
                        Comparator.comparingInt(DetectionResult.Span::end).reversed()));
        List<DetectionResult.Span> merged = new ArrayList<>();
        int curS = -1, curE = -1;
        String curType = null, curRep = null;
        for (DetectionResult.Span s : all) {
            if (merged.isEmpty()) {
                merged.add(s);
                curS = s.start();
                curE = s.end();
                curType = s.type();
                curRep = s.replacement();
                continue;
            }
            DetectionResult.Span last = merged.get(merged.size() - 1);
            if (s.start() <= last.end()) {
                // overlap → extend if needed
                int newEnd = Math.max(last.end(), s.end());
                if (newEnd != last.end() || !Objects.equals(last.replacement(), s.replacement())) {
                    merged.set(
                            merged.size() - 1,
                            new DetectionResult.Span(last.start(), newEnd, last.type(), last.replacement()));
                }
            } else {
                merged.add(s);
            }
        }

        // build output
        StringBuilder out = new StringBuilder(message.length() + 16);
        int pos = 0;
        List<Finding> findings = new ArrayList<>(merged.size());
        for (DetectionResult.Span s : merged) {
            if (s.start() > pos) out.append(message, pos, s.start());
            out.append(s.replacement());
            findings.add(new Finding(s.type(), action, s.start(), s.end()));
            pos = s.end();
        }
        if (pos < message.length()) out.append(message, pos, message.length());

        return new Result(out.toString(), List.copyOf(findings));
    }

    public record Result(String sanitized, List<Finding> findings) {}
}
