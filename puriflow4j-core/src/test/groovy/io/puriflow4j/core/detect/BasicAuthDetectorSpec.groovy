package io.puriflow4j.core.detect

import spock.lang.Specification

/**
 * Tests for BasicAuthDetector:
 *  - masks only the credentials part after "Authorization: Basic "
 *  - case-insensitive header
 *  - multiple headers in one line
 *  - boundary/invalid cases
 *  - no false positives on Bearer/JWT or base64-like noise without header
 */
class BasicAuthDetectorSpec extends Specification {

    def detector = new BasicAuthDetector()

    def "masks base64 credentials, keeps header prefix intact"() {
        given:
        def msg = "Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ=="
        when:
        def res = detector.detect(msg)
        def masked = applySpans(msg, res)
        then:
        res.found()
        res.spans().size() == 1
        masked == "Authorization: Basic [MASKED_BASIC_AUTH]"
    }

    def "header is case-insensitive (authorization/basic in any case)"() {
        expect:
        def msg = "${h}: ${b} ${val}"
        def res = detector.detect(msg)
        def masked = applySpans(msg, res)
        res.found()
        masked == "${h}: ${b} [MASKED_BASIC_AUTH]"

        where:
        h               | b        | val
        "authorization" | "basic"  | "QWxhZGRpbjpzZWNyZXQ="
        "AUTHORIZATION" | "BASIC"  | "QWxhZGRpbjpzZWNyZXQ="
        "AuthoRization" | "BaSiC"  | "QWxhZGRpbjpzZWNyZXQ="
    }

    def "multiple Basic headers in one line are all masked"() {
        given:
        def a = "QWxhZGRpbjpPbmU="
        def b = "dXNlcjpwYXNz"
        def msg = "H1: Authorization: Basic ${a} ; H2: Authorization: Basic ${b}"
        when:
        def res = detector.detect(msg)
        def masked = applySpans(msg, res)
        then:
        res.found()
        res.spans().size() == 2
        masked == "H1: Authorization: Basic [MASKED_BASIC_AUTH] ; H2: Authorization: Basic [MASKED_BASIC_AUTH]"
    }

    def "does not match Bearer or JWT tokens"() {
        expect:
        !detector.detect("Authorization: Bearer eyJ.hdr.pay.sig").found()
        !detector.detect("some jwt eyJ.hdr.pay.sig").found()
    }

    def "does not match if value is too short or missing"() {
        expect:
        !detector.detect("Authorization: Basic abc=").found()          // < 6 chars
        !detector.detect("Authorization: Basic ").found()               // empty
        !detector.detect("Authorization: Basic\t").found()
        !detector.detect("Authorization: Basic").found()
    }

    def "does not match base64-like sequences without the header"() {
        expect:
        !detector.detect("QWxhZGRpbjpvcGVuIHNlc2FtZQ==").found()
        !detector.detect("User: QWxhZGRpbjpvcGVuIHNlc2FtZQ==").found()
    }

    def "boundary characters around base64 still match correctly"() {
        given:
        def msg = "Auth: Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==;"
        when:
        def res = detector.detect(msg)
        def masked = applySpans(msg, res)
        then:
        res.found()
        masked == "Auth: Authorization: Basic [MASKED_BASIC_AUTH];"
    }

    // -------- helper --------

    /**
     * Applies spans to original message to simulate Sanitizer output.
     * Makes a defensive mutable copy, because spans() is immutable.
     */
    private static String applySpans(String message, def detectionResult) {
        if (!detectionResult.found()) return message
        def spans = new ArrayList<>(detectionResult.spans())
        spans.sort { a, b -> a.start() <=> b.start() ?: b.end() <=> a.end() }

        StringBuilder out = new StringBuilder(message.length() + 16)
        int pos = 0
        spans.each { s ->
            if (s.start() > pos) out.append(message, pos, s.start())
            out.append(s.replacement())
            pos = s.end()
        }
        if (pos < message.length()) out.append(message, pos, message.length())
        return out.toString()
    }
}