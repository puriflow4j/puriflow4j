package io.puriflow4j.core.detect;

import io.puriflow4j.core.api.*;
import java.util.*;
import java.util.regex.*;

public final class RegexDetector implements Detector {
    private final String name;
    private final Pattern pattern;
    private final String replacement;

    public RegexDetector(String name, String regex, String replacement) {
        this.name = name;
        this.pattern = Pattern.compile(regex);
        this.replacement = replacement;
    }

    @Override public String name() { return name; }

    @Override
    public DetectionResult detect(String input) {
        if (input == null || input.isEmpty()) return DetectionResult.empty();
        Matcher m = pattern.matcher(input);
        List<DetectionResult.Span> spans = new ArrayList<>();
        while (m.find()) spans.add(new DetectionResult.Span(m.start(), m.end(), replacement));
        return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, spans);
    }
}
