package io.puriflow4j.core.preset;

import io.puriflow4j.core.api.Detector;
import io.puriflow4j.core.detect.KVDetector;
import io.puriflow4j.core.detect.RegexDetector;

import java.util.List;

public final class BuiltInDetectors {
    private BuiltInDetectors(){}

    public static List<Detector> minimal() {
        return List.of(
                // "bare" values
                new RegexDetector("email",
                        "(?i)\\b[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}\\b",
                        "[MASKED_EMAIL]"),
                new RegexDetector("awsKey",
                        "\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b",
                        "[MASKED_AWS_KEY]"),
                new RegexDetector("jwtBare",
                        "\\b[A-Za-z0-9_-]+\\.[A-Za-z0-9._-]+\\.[A-Za-z0-9._-]+\\b",
                        "[MASKED_JWT]"),

                // key=value / key: value — leave key, masking only value
                new KVDetector("passwordKV",
                        "(?i)(?:password|secret|api[_-]?key)\\s*[:=]\\s*(?<val>[^\\s,;]+)",
                        "[MASKED]"),
                new KVDetector("tokenKV",
                        "(?i)(?:token|bearer[_-]?token|auth[_-]?token)\\s*[:=]\\s*(?<val>[A-Za-z0-9_-]+\\.[A-Za-z0-9._-]+\\.[A-Za-z0-9._-]+)",
                        "[MASKED_JWT]")
        );
    }
}
