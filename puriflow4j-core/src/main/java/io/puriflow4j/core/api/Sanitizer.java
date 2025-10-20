/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.api;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.puriflow4j.core.api.models.*;
import io.puriflow4j.core.detect.Detector;
import java.util.*;
import java.util.stream.Collectors;

/** Composite sanitizer that runs all detectors and applies right-to-left replacements. */
public final class Sanitizer {

    @SuppressFBWarnings
    public record Result(String sanitized, List<Finding> findings) {}

    private final List<Detector> detectors;
    private final Action defaultAction;

    public Sanitizer(List<Detector> detectors, Action defaultAction) {
        this.detectors = List.copyOf(detectors);
        this.defaultAction = defaultAction == null ? Action.MASK : defaultAction;
    }

    public Result applyDetailed(String input, String loggerName) {
        if (input == null || input.isEmpty()) return new Result(input, List.of());

        List<DetectionResult.Span> spans = new ArrayList<>();
        for (Detector d : detectors) {
            var r = d.detect(input);
            if (r.found()) spans.addAll(r.spans());
        }
        if (spans.isEmpty()) return new Result(input, List.of());

        // sort right-to-left to keep indices stable
        spans.sort(Comparator.comparingInt(DetectionResult.Span::start).reversed());

        StringBuilder sb = new StringBuilder(input);
        int lastEnd = Integer.MAX_VALUE;
        List<Finding> findings = new ArrayList<>();

        for (var s : spans) {
            int start = Math.max(0, s.start());
            int end = Math.min(sb.length(), s.end());
            if (end > lastEnd) continue; // skip overlaps already replaced to the right

            if (defaultAction == Action.MASK || defaultAction == Action.REDACT) {
                sb.replace(start, end, s.replacement());
            }
            findings.add(new Finding(s.type(), defaultAction, loggerName));
            lastEnd = start;
        }

        // deduplicate findings per log record
        var uniq = findings.stream()
                .collect(Collectors.toMap(f -> f.type() + "|" + f.action() + "|" + f.loggerName(), f -> f, (a, b) -> a))
                .values()
                .stream()
                .toList();

        return new Result(sb.toString(), uniq);
    }

    public String apply(String input) {
        return applyDetailed(input, null).sanitized();
    }
}
