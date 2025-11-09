package io.puriflow4j.logs.core.sanitize

import io.puriflow4j.core.api.Detector
import io.puriflow4j.core.api.Sanitizer
import io.puriflow4j.core.api.model.Action
import io.puriflow4j.core.api.model.DetectionResult
import spock.lang.Specification

import java.util.regex.Pattern

class MdcSanitizerSpec extends Specification {

    // Comment (EN): Minimal detector used by tests:
    //  - Prefer key=value masking for keys 'secret' or 'password' (mask only the value part).
    //  - If no key=value matched, mask bare 'secret' substrings in the text.
    private Sanitizer mkSanitizer() {
        Detector det = new Detector() {
            private final Pattern P_KV = Pattern.compile("(?i)\\b(secret|password)\\s*[:=]\\s*([^\\s,;]+)")
            private final Pattern P_BARE_SECRET = Pattern.compile("(?i)secret")

            @Override
            DetectionResult detect(String s) {
                if (s == null || s.isEmpty()) return DetectionResult.empty()

                def spans = new ArrayList<DetectionResult.Span>()

                // 1) Prefer key=value masking (mask only the value)
                def m1 = P_KV.matcher(s)
                boolean anyKv = false
                while (m1.find()) {
                    anyKv = true
                    spans.add(new DetectionResult.Span(m1.start(2), m1.end(2), "kv", "[MASKED]"))
                }

                // 2) Only if no key=value matched, mask bare "secret" substrings
                if (!anyKv) {
                    def m2 = P_BARE_SECRET.matcher(s)
                    while (m2.find()) {
                        spans.add(new DetectionResult.Span(m2.start(), m2.end(), "bare", "[MASKED]"))
                    }
                }

                return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, List.copyOf(spans))
            }
        }
        new Sanitizer(List.of(det), Action.MASK)
    }

    def "returns empty immutable map for null or empty MDC"() {
        given:
        def san = new MdcSanitizer(mkSanitizer())

        when: "null MDC"
        def outNull = san.sanitize(null, "demo")

        then: "we return empty immutable map (not null)"
        outNull != null
        outNull.isEmpty()
        when:
        outNull.put("x", "y")
        then:
        thrown(UnsupportedOperationException)

        when: "empty MDC"
        def outEmpty = san.sanitize([:], "demo")

        then:
        outEmpty.isEmpty()
        when:
        outEmpty.put("x", "y")
        then:
        thrown(UnsupportedOperationException)
    }

    def "sanitizes MDC using existing detectors (KV takes precedence over bare)"() {
        given:
        def san = new MdcSanitizer(mkSanitizer())
        def mdc = [
                user     : "alice",
                password : "supersecret", // will be processed as "password=supersecret" -> value becomes [MASKED]
                mode     : "test"
        ]

        when:
        def out = san.sanitize(mdc, "demo.Logger")

        then:
        out.get("user") == "alice"
        // Comment (EN): KV detector wins => "[MASKED]" (not "super[MASKED]")
        out.get("password") == "[MASKED]"
        out.get("mode") == "test"

        and: "original map is untouched"
        mdc.get("password") == "supersecret"
    }

    def "preserves null or empty values; masks KV when present"() {
        given:
        def san = new MdcSanitizer(mkSanitizer())
        def mdc = [
                key1: "",
                key2: null,
                key3: "secret=abc" // processed as "key3=secret=abc" -> masks value after the last '=' span
        ]

        when:
        def out = san.sanitize(mdc, "demo")

        then:
        out.get("key1") == ""
        out.get("key2") == null
        // Comment (EN): Expected value part becomes "[MASKED]"
        out.get("key3") == "secret=[MASKED]"
    }

    def "returned map is unmodifiable"() {
        given:
        def san = new MdcSanitizer(mkSanitizer())
        def mdc = [foo: "bar"]

        when:
        def out = san.sanitize(mdc, "demo")
        out.put("newKey", "value") // attempt to modify

        then:
        thrown(UnsupportedOperationException)
    }
}
