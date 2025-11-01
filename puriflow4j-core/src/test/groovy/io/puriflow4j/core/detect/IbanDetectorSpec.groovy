package io.puriflow4j.core.detect

import io.puriflow4j.core.api.model.DetectionResult
import spock.lang.Specification

class IbanDetectorSpec extends Specification {

    def det = new IbanDetector()

    private static String applySpans(String msg, DetectionResult res) {
        def spans = new ArrayList<>(res.spans())
        spans.sort { a, b -> (a.start() <=> b.start()) ?: (b.end() <=> a.end()) }
        def sb = new StringBuilder()
        int pos = 0
        spans.each { s ->
            if (pos < s.start()) sb.append(msg.substring(pos, s.start()))
            sb.append(s.replacement())
            pos = s.end()
        }
        if (pos < msg.length()) sb.append(msg.substring(pos))
        sb.toString()
    }

    def "valid DE IBAN with spaces is detected and masked"() {
        given:
        def msg = "Pay to IBAN: DE89 3704 0044 0532 0130 00 please"

        when:
        def res = det.detect(msg)

        then:
        res.found()
        applySpans(msg, res) == "Pay to IBAN: [MASKED_IBAN] please"
    }

    def "valid GB IBAN with dashes and lowercase is detected and masked"() {
        given:
        def msg = "iban=gb82-west-1234-5698-7654-32"

        when:
        def res = det.detect(msg)

        then:
        res.found()
        applySpans(msg, res) == "iban=[MASKED_IBAN]"
    }

    def "multiple IBANs in one line are masked and spans are ordered"() {
        given:
        def a = "DE89 3704 0044 0532 0130 00"
        def b = "GB82 WEST 1234 5698 7654 32"
        def msg = "A:$a B:$b"

        when:
        def res = det.detect(msg)

        then:
        res.found()
        res.spans().size() == 2

        and:
        def starts = res.spans()*.start()
        def sorted = new ArrayList<>(starts); sorted.sort(false)
        starts == sorted

        and:
        applySpans(msg, res) == "A:[MASKED_IBAN] B:[MASKED_IBAN]"
    }

    def "invalid random looks-like-IBAN is not detected (bad checksum)"() {
        given:
        def msg = "Looks like IBAN: DE44 5001 0517 X4 0732"

        expect:
        !det.detect(msg).found()
    }
}