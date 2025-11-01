package io.puriflow4j.core.api

import io.puriflow4j.core.api.*
import io.puriflow4j.core.api.model.*
import spock.lang.Specification

/**
 * Unit tests for Sanitizer:
 * - no detections → original message returned
 * - non-overlapping spans → applied in order
 * - overlapping spans → merged; replacement of the first merged span wins
 * - adjacent spans → merged into a single span
 * - findings keep original indices and carry provided Action
 */
class SanitizerSpec extends Specification {

    def "returns original message and no findings when there are no detectors"() {
        given:
        def sanitizer = new Sanitizer(List.of(), Action.MASK)

        when:
        def res = sanitizer.applyDetailed("hello world", "logger")

        then:
        res.sanitized() == "hello world"
        res.findings().isEmpty()
    }

    def "returns original message when detectors produce no spans"() {
        given:
        def d = fixedDetector([]) // no spans
        def sanitizer = new Sanitizer(List.of(d), Action.MASK)

        when:
        def res = sanitizer.applyDetailed("no secrets here", "logger")

        then:
        res.sanitized() == "no secrets here"
        res.findings().isEmpty()
    }

    def "applies multiple non-overlapping replacements"() {
        given:
        def msg = "abc def ghi"
        def spans = [
                spanBySubstring(msg, "abc", "[X]", "x"),
                spanBySubstring(msg, "ghi", "[Z]", "z")
        ]
        def sanitizer = new Sanitizer(List.of(fixedDetector(spans)), Action.MASK)

        when:
        def res = sanitizer.applyDetailed(msg, "logger")

        then:
        res.sanitized() == "[X] def [Z]"
        res.findings().size() == 2
        res.findings()*.type() as Set == ["x","z"] as Set
        res.findings().every { it.action() == Action.MASK }
    }

    def "overlapping spans are merged; first replacement wins"() {
        given:
        // message:    0123456789012
        // text:       "abc def xyz"
        // span1 (wider): "abc def"  → [A]
        // span2 (inner):     "def"  → [B]
        def msg = "abc def xyz"
        def s1 = spanByRange(msg, 0, 7, "[A]", "wide")  // "abc def"
        def s2 = spanByRange(msg, 4, 7, "[B]", "inner") // "def"
        def sanitizer = new Sanitizer(List.of(fixedDetector([s1, s2])), Action.MASK)

        when:
        def res = sanitizer.applyDetailed(msg, "logger")

        then:
        // merged span uses first replacement → entire "abc def" becomes [A]
        res.sanitized() == "[A] xyz"
        // findings still report a single merged region (positions of the merged span)
        res.findings().size() == 1
        with(res.findings()[0]) {
            type() == "wide"    // comes from the first (kept) span
            action() == Action.MASK
            start() == 0
            end() == 7
        }
    }

    def "adjacent spans are merged into one"() {
        given:
        // message: "abcdef"
        // spans: "abc" [0..3) and "def" [3..6) → adjacent → merged
        def msg = "abcdef"
        def s1 = spanBySubstring(msg, "abc", "[L]", "part1")
        def s2 = spanBySubstring(msg, "def", "[R]", "part2")
        def sanitizer = new Sanitizer(List.of(fixedDetector([s1, s2])), Action.MASK)

        when:
        def res = sanitizer.applyDetailed(msg, "logger")

        then:
        // merged uses replacement of the first span
        res.sanitized() == "[L]"
        res.findings().size() == 1
        with(res.findings()[0]) {
            start() == 0
            end() == 6
            type() == "part1"
        }
    }

    def "findings preserve original indices and use provided Action (WARN)"() {
        given:
        def msg = "token=abc123"
        def s = spanBySubstring(msg, "abc123", "[MASKED]", "token")
        def sanitizer = new Sanitizer(List.of(fixedDetector([s])), Action.WARN)

        when:
        def res = sanitizer.applyDetailed(msg, "auth")

        then:
        res.sanitized() == "token=[MASKED]"
        res.findings().size() == 1
        with(res.findings()[0]) {
            type() == "token"
            action() == Action.WARN
            // indices refer to original message:
            start() == msg.indexOf("abc123")
            end()   == msg.indexOf("abc123") + "abc123".length()
        }
    }

    def "apply() delegates to applyDetailed().sanitized()"() {
        given:
        def msg = "email=alice@example.com"
        def s = spanBySubstring(msg, "alice@example.com", "[MASKED_EMAIL]", "email")
        def sanitizer = new Sanitizer(List.of(fixedDetector([s])), Action.MASK)

        expect:
        sanitizer.apply(msg, "logger") == "email=[MASKED_EMAIL]"
    }

    // ---------- helpers ----------

    /**
     * Build a detector that always returns the provided spans for any input string.
     * Useful to test Sanitizer merging and replacement without regex logic.
     */
    private static Detector fixedDetector(List<DetectionResult.Span> spans) {
        return new Detector() {
            @Override
            DetectionResult detect(String s) {
                return spans == null || spans.isEmpty()
                        ? new DetectionResult(false, List.of())
                        : new DetectionResult(true, List.copyOf(spans))
            }
        }
    }

    /** Create a Span by locating the first occurrence of a substring in message. */
    private static DetectionResult.Span spanBySubstring(String message, String substring, String replacement, String type) {
        int start = message.indexOf(substring)
        assert start >= 0 : "substring '" + substring + "' not found in '" + message + "'"
        int end = start + substring.length()
        return new DetectionResult.Span(start, end, type, replacement)
    }

    /** Create a Span for an explicit [start, end) range. */
    private static DetectionResult.Span spanByRange(String message, int start, int end, String replacement, String type) {
        assert start >= 0 && end <= message.length() && start < end
        return new DetectionResult.Span(start, end, type, replacement)
    }
}