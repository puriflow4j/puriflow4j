package io.puriflow4j.core.detect

import io.puriflow4j.core.api.model.DetectionResult
import spock.lang.Specification

class EmailDetectorSpec extends Specification {

    def detector = new EmailDetector()

    def "detects and masks a simple email"() {
        expect:
        def msg = "Contact me at alice@example.com please"
        def res = detector.detect(msg)
        res.found()
        res.spans().size() == 1
        applySpans(msg, res) == "Contact me at [MASKED_EMAIL] please"
    }

    def "detects multiple emails and masks all"() {
        given:
        def msg = "to: alice@example.com, bob.smith@sub.example.co.uk; cc: support@ex-ample.io"

        when:
        def res = detector.detect(msg)

        then:
        res.found()
        res.spans().size() == 3

        and: "ordered by start and non-overlapping"
        assertOrderedNonOverlapping(res)

        and:
        applySpans(msg, res) == "to: [MASKED_EMAIL], [MASKED_EMAIL]; cc: [MASKED_EMAIL]"
    }

    def "case-insensitive matching (local and domain)"() {
        given:
        def msg = "User: ALICE+test@Example.COM"

        expect:
        applySpans(msg, detector.detect(msg)) == "User: [MASKED_EMAIL]"
    }

    def "emails next to punctuation still detected"() {
        given:
        def msg = "(alice@example.com), end."

        expect:
        applySpans(msg, detector.detect(msg)) == "([MASKED_EMAIL]), end."
    }

    def "does not match invalid email without dot in domain"() {
        expect:
        !detector.detect("user@localhost").found()
    }

    def "does not match obviously non-email tokens"() {
        expect:
        !detector.detect("not@an email").found()
        !detector.detect("at:user@example,com").found()
    }

    def "mixed content: only emails are masked"() {
        given:
        def msg = "email=alice@example.com token=eyJ.hdr.pay.sig"

        expect:
        applySpans(msg, detector.detect(msg)) == "email=[MASKED_EMAIL] token=eyJ.hdr.pay.sig"
    }

    // ------------ helpers ------------

    private static String applySpans(String msg, DetectionResult res) {
        if (!res.found() || res.spans().isEmpty()) return msg
        // Sort on a mutable copy: by start asc, then end desc — стабильно для слияния интервалов
        def spans = new ArrayList<>(res.spans())
        spans.sort { a, b -> (a.start() <=> b.start()) ?: (b.end() <=> a.end()) }

        StringBuilder out = new StringBuilder(msg.length() + 16)
        int pos = 0
        for (def s : spans) {
            if (s.start() > pos) out.append(msg, pos, s.start())
            out.append(s.replacement())
            pos = s.end()
        }
        if (pos < msg.length()) out.append(msg, pos, msg.length())
        return out.toString()
    }

    private static void assertOrderedNonOverlapping(DetectionResult res) {
        def spans = new ArrayList<>(res.spans())
        spans.sort { a, b -> (a.start() <=> b.start()) ?: (b.end() <=> a.end()) }

        // ordered by start
        assert spans*.start() == new ArrayList<>(spans*.start()).with { it.sort(false); it }

        // non-overlapping
        for (int i = 1; i < spans.size(); i++) {
            assert spans[i - 1].end() <= spans[i].start()
        }
    }
}
