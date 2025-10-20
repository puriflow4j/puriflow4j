package io.puriflow4j.core.detect;

import io.puriflow4j.core.api.models.*;
import io.puriflow4j.core.preset.KVPatternConfig;

import java.util.*;
import java.util.regex.*;

/**
 * KV detector masks only the value in "key[:=] value" pairs using named groups (?<key>...) and (?<val>...).
 * If (?<key>) is missing, it falls back to a heuristic key lookup (rare).
 */
public final class KVDetector implements Detector {
    private final String name;
    private final Pattern pattern; // must have (?<val>...), should have (?<key>...)
    private final String type;
    private final String replacement;
    private final KVPatternConfig cfg;

    public KVDetector(String name, String regexWithNamedGroups, String type, String replacement, KVPatternConfig cfg) {
        this.name = name;
        this.pattern = Pattern.compile(regexWithNamedGroups);
        this.type = type;
        this.replacement = replacement;
        this.cfg = (cfg == null) ? KVPatternConfig.defaults() : cfg;
    }

    @Override public String name() { return name; }

    @Override
    public DetectionResult detect(String input) {
        if (input == null || input.isEmpty()) return DetectionResult.empty();
        Matcher m = pattern.matcher(input);
        List<DetectionResult.Span> spans = new ArrayList<>();
        while (m.find()) {
            String key = extractKey(m, input);
            if (!cfg.isKeyAllowed(key) && cfg.isKeyBlocked(key)) {
                try {
                    spans.add(new DetectionResult.Span(m.start("val"), m.end("val"), type, replacement));
                } catch (IllegalArgumentException ignore) {
                    // no named group "val" — skip
                }
            }
        }
        return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, spans);
    }

    private String extractKey(Matcher m, String input) {
        try {
            String k = m.group("key");
            if (k != null) return k;
        } catch (IllegalArgumentException ignore) {
            // no named group "key" — fall back
        }
        // Fallback (rare): look ~40 chars to the left for "<key>[:=]"
        int matchStart = m.start();
        int matchEnd   = m.end();
        int start = Math.max(0, matchStart - 40);
        String left = input.substring(start, Math.min(input.length(), matchEnd));
        Matcher k = Pattern.compile("(?i)([a-z0-9._-]{2,32})\\s*[:=]\\s*$").matcher(left);
        String key = null;
        while (k.find()) key = k.group(1);
        return key == null ? "" : key;
    }
}
