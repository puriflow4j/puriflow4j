package io.puriflow4j.core.detect

import io.puriflow4j.core.api.model.DetectionResult
import spock.lang.Specification

class CreditCardDetectorSpec extends Specification {

    def detector = new CreditCardDetector()

    def "valid credit card numbers are detected and masked"() {
        given:
        def msg = "My card is 4111 1111 1111 1111 for payment"

        when:
        def res = detector.detect(msg)

        then:
        res.found()
        res.spans().size() == 1
        sanitized(msg, res) == "My card is [MASKED_CARD] for payment"
    }

    def "card numbers with dashes are detected"() {
        given:
        def msg = "Please use 5500-0000-0000-0004"

        when:
        def res = detector.detect(msg)

        then:
        res.found()
        res.spans().size() == 1
        sanitized(msg, res) == "Please use [MASKED_CARD]"
    }

    def "invalid Luhn numbers are ignored"() {
        given:
        def msg = "This looks like a card 4111 1111 1111 1112 but it's invalid"

        expect:
        !detector.detect(msg).found()
    }

    def "digits too short or too long are ignored"() {
        given:
        def msg = "12 3456 7890 123 is too short, and 12345678901234567890 is too long"

        expect:
        !detector.detect(msg).found()
    }

    def "multiple valid cards in one line are detected separately"() {
        given:
        def msg = "C1: 4111111111111111, C2: 5500000000000004"

        when:
        def res = detector.detect(msg)

        then:
        res.found()
        res.spans().size() == 2
        sanitized(msg, res) == "C1: [MASKED_CARD], C2: [MASKED_CARD]"
    }

    def "handles null and empty inputs"() {
        expect:
        !detector.detect(null).found()
        !detector.detect("").found()
    }

    // ---------- helper ----------

    private static String sanitized(String msg, DetectionResult res) {
        // make a mutable copy before sorting
        def spans = new ArrayList<>(res.spans())
        spans.sort { a, b -> a.start() <=> b.start() ?: b.end() <=> a.end() }

        def sb = new StringBuilder(msg.length())
        int pos = 0
        for (def s : spans) {
            if (s.start() > pos) sb.append(msg, pos, s.start())
            sb.append(s.replacement())
            pos = s.end()
        }
        if (pos < msg.length()) sb.append(msg, pos, msg.length())
        sb.toString()
    }
}
