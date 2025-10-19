package io.puriflow4j.core.detect;

import io.puriflow4j.core.api.*;
import java.util.*;
import java.util.regex.*;

public final class KVDetector implements Detector {
    private final String name;
    private final Pattern pattern;
    private final String replacement;

    public KVDetector(String name, String regexWithValGroup, String replacement) {
        this.name = name;
        this.pattern = Pattern.compile(regexWithValGroup);
        this.replacement = replacement;
    }

    @Override public String name() { return name; }

    @Override
    public DetectionResult detect(String input) {
        if (input == null || input.isEmpty()) return DetectionResult.empty();
        Matcher m = pattern.matcher(input);
        List<DetectionResult.Span> spans = new ArrayList<>();
        while (m.find()) {
            try {
                spans.add(new DetectionResult.Span(m.start("val"), m.end("val"), replacement));
            } catch (IllegalArgumentException ignore) {}
        }
        return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, spans);
    }
}
