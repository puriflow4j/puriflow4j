package io.puriflow4j.logs.core.sanitize

import io.puriflow4j.core.api.Detector
import io.puriflow4j.core.api.Sanitizer
import io.puriflow4j.core.api.model.Action
import io.puriflow4j.core.api.model.DetectionResult
import spock.lang.Specification

import java.util.regex.Pattern

class MessageSanitizerSpec extends Specification {

    // Build a real Sanitizer so we don't mock finals.
    // Policy:
    //   - mask only the value in key=value for (password|secret)
    //   - mask bare token "token123" (but only if no key=value was matched to avoid double masking)
    private static Sanitizer mkSanitizer() {
        Detector det = new Detector() {
            private final Pattern P_KV   = Pattern.compile("(?i)\\b(password|secret)\\s*[:=]\\s*([^\\s,;]+)")
            private final Pattern P_BARE = Pattern.compile("\\btoken123\\b")

            @Override
            DetectionResult detect(String s) {
                if (s == null || s.isEmpty()) return DetectionResult.empty()

                def spans = new ArrayList<DetectionResult.Span>()

                def m1 = P_KV.matcher(s)
                boolean anyKv = false
                while (m1.find()) {
                    anyKv = true
                    spans.add(new DetectionResult.Span(m1.start(2), m1.end(2), "kv", "[MASKED]"))
                }

                if (!anyKv) {
                    def b = P_BARE.matcher(s)
                    while (b.find()) spans.add(new DetectionResult.Span(b.start(), b.end(), "bare", "[MASKED]"))
                }

                return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, List.copyOf(spans))
            }
        }
        new Sanitizer(List.of(det), Action.MASK)
    }

    def "sanitizes non-empty message using provided Sanitizer"() {
        given:
        def ms = new MessageSanitizer(mkSanitizer())

        expect:
        ms.sanitize("password=abc", "demo.Logger") == "password=[MASKED]"
        ms.sanitize("please use token123 here", "demo.Logger") == "please use [MASKED] here"
        ms.sanitize("no secrets here", "demo.Logger") == "no secrets here"
    }

    def "passes through null and empty strings unchanged"() {
        given:
        def ms = new MessageSanitizer(mkSanitizer())

        expect:
        ms.sanitize(null, "demo") == null
        ms.sanitize("", "demo") == ""
    }

    def "does not alter message when no patterns match"() {
        given:
        def ms = new MessageSanitizer(mkSanitizer())
        def msg = "hello world"

        expect:
        ms.sanitize(msg, "demo.Logger") == msg
    }
}
