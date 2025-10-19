package io.puriflow4j.core.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Sanitizer {
    private final List<Detector> detectors;

    public Sanitizer(List<Detector> detectors) {
        this.detectors = List.copyOf(detectors);
    }

    public String apply(String input) {
        if (input == null || input.isEmpty()) return input;

        List<DetectionResult.Span> all = new ArrayList<>();
        for (Detector d : detectors) {
            var r = d.detect(input);
            if (r.found()) all.addAll(r.spans());
        }
        if (all.isEmpty()) return input;

        all.sort(Comparator.comparingInt(DetectionResult.Span::start).reversed());

        StringBuilder sb = new StringBuilder(input);
        int lastEnd = Integer.MAX_VALUE;
        for (var s : all) {
            int start = Math.max(0, s.start());
            int end   = Math.min(sb.length(), s.end());
            if (end > lastEnd) continue;
            sb.replace(start, end, s.replacement());
            lastEnd = start;
        }
        return sb.toString();
    }
}
