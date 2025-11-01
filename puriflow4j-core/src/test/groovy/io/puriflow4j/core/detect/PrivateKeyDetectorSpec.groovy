/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.detect

import io.puriflow4j.core.api.model.DetectionResult
import spock.lang.Specification

class PrivateKeyDetectorSpec extends Specification {

    def det = new PrivateKeyDetector()

    def "detects RSA private key block and masks it"() {
        given:
        def pem = """
        -----BEGIN RSA PRIVATE KEY-----
        MIICWwIBAAKBgQC+...
        -----END RSA PRIVATE KEY-----
        """.trim()

        expect:
        def res = det.detect(pem)
        res.found()
        applySpans(pem, res) == "[MASKED_PRIVATE_KEY]"
    }

    def "detects EC private key block and masks it"() {
        given:
        def pem = """
        -----BEGIN EC PRIVATE KEY-----
        MHcCAQEEIHsT...
        -----END EC PRIVATE KEY-----
        """.trim()

        expect:
        def res = det.detect(pem)
        res.found()
        applySpans(pem, res) == "[MASKED_PRIVATE_KEY]"
    }

    def "detects generic OPENSSH private key block and masks it"() {
        given:
        def pem = """
        -----BEGIN OPENSSH PRIVATE KEY-----
        b3BlbnNzaC1rZXktdjEA...
        -----END OPENSSH PRIVATE KEY-----
        """.trim()

        expect:
        def res = det.detect(pem)
        res.found()
        applySpans(pem, res) == "[MASKED_PRIVATE_KEY]"
    }

    def "multiple private keys in one text are masked"() {
        given:
        def text = """
        User 1 key:
        -----BEGIN RSA PRIVATE KEY-----
        AAAAB3...
        -----END RSA PRIVATE KEY-----

        User 2 key:
        -----BEGIN EC PRIVATE KEY-----
        AAAAC3...
        -----END EC PRIVATE KEY-----
        """.stripIndent().trim()

        when:
        def res = det.detect(text)

        then:
        res.found()
        res.spans().size() == 2

        and:
        applySpans(text, res) == """
        User 1 key:
        [MASKED_PRIVATE_KEY]

        User 2 key:
        [MASKED_PRIVATE_KEY]
        """.stripIndent().trim()
    }

    def "no detection for unrelated text"() {
        expect:
        !det.detect("This is just a message. No keys here.").found()
    }

    def "null and empty input produces no spans"() {
        expect:
        !det.detect(null).found()
        !det.detect("").found()
    }

    // ---------- helper ----------
    private static String applySpans(String s, DetectionResult res) {
        if (s == null) return null
        if (!res.found() || res.spans().isEmpty()) return s
        def spans = new ArrayList<>(res.spans())
        spans.sort { a, b -> (a.start() <=> b.start()) ?: (b.end() <=> a.end()) }

        def out = new StringBuilder(s.length() + 16)
        int pos = 0
        for (def sp : spans) {
            if (sp.start() > pos) out.append(s, pos, sp.start())
            out.append(sp.replacement())
            pos = sp.end()
        }
        if (pos < s.length()) out.append(s, pos, s.length())
        return out.toString()
    }
}
