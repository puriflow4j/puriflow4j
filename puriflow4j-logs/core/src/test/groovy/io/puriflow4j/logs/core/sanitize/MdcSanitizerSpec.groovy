package io.puriflow4j.logs.core.sanitize

import io.puriflow4j.core.api.Detector
import io.puriflow4j.core.api.Sanitizer
import io.puriflow4j.core.api.model.Action
import io.puriflow4j.core.api.model.DetectionResult
import spock.lang.Specification

import java.util.regex.Pattern

class MdcSanitizerSpec extends Specification {

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
        return new Sanitizer(List.of(det), Action.MASK)
    }

    def "returns same map instance for null or empty MDC"() {
        given:
        def san = new MdcSanitizer(mkSanitizer())

        expect:
        san.sanitize(null, "demo") == null
        san.sanitize([:], "demo").isEmpty()
    }

    def "sanitizes values based on configured sanitizer"() {
        given:
        def san = new MdcSanitizer(mkSanitizer())
        def mdc = [
                user     : "alice",
                password : "supersecret",
                mode     : "test"
        ]

        when:
        def out = san.sanitize(mdc, "demo.Logger")

        then:
        out.get("user") == "alice"
        out.get("password") == "super[MASKED]"
        out.get("mode") == "test"
        // original map is untouched
        mdc.get("password") == "supersecret"
    }

    def "preserves null or empty values"() {
        given:
        def san = new MdcSanitizer(mkSanitizer())
        def mdc = [
                key1: "",
                key2: null,
                key3: "secret=abc"
        ]

        when:
        def out = san.sanitize(mdc, "demo")

        then:
        out.get("key1") == ""
        out.get("key2") == null
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
