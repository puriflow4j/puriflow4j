/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.detect

import io.puriflow4j.core.api.model.DetectionResult
import spock.lang.Specification

class UrlRedactorDetectorSpec extends Specification {

    def det = new UrlRedactorDetector()

    def "redacts jdbc postgresql URL authority+path"() {
        expect:
        applySpans(
                "jdbc:postgresql://db.prod/app",
                det.detect("jdbc:postgresql://db.prod/app")
        ) == "jdbc:postgresql://[MASKED_URL]"
    }

    def "redacts mongodb URL authority+path with query params"() {
        expect:
        applySpans(
                "mongodb://cluster0.example.com/db?authSource=admin",
                det.detect("mongodb://cluster0.example.com/db?authSource=admin")
        ) == "mongodb://[MASKED_URL]"
    }

    def "redacts redis URL with userinfo"() {
        expect:
        applySpans(
                "redis://:secret@localhost:6379/0",
                det.detect("redis://:secret@localhost:6379/0")
        ) == "redis://[MASKED_URL]"
    }

    def "redacts https API URL"() {
        expect:
        applySpans(
                "https://api.example.com/v1/users",
                det.detect("https://api.example.com/v1/users")
        ) == "https://[MASKED_URL]"
    }

    def "redacts s3 URL with bucket and key"() {
        expect:
        applySpans(
                "s3://my-bucket/private/path",
                det.detect("s3://my-bucket/private/path")
        ) == "s3://[MASKED_URL]"
    }

    def "multiple URLs in one line are all redacted and spans are ordered/non-overlapping"() {
        given:
        def msg = "hit https://api.example.com/v1 and jdbc:mysql://db/acme and s3://bucket/x"

        when:
        def res = det.detect(msg)

        then:
        res.found()
        res.spans().size() == 3

        and: "ordered by start"
        def starts = res.spans()*.start()
        def sorted = new ArrayList<>(starts)
        Collections.sort(sorted)
        starts == sorted

        and: "no overlap"
        def spans = new ArrayList<>(res.spans())
        spans.sort { a,b -> (a.start() <=> b.start()) ?: (a.end() <=> b.end()) }
        for (int i=1; i<spans.size(); i++) {
            assert spans[i-1].end() <= spans[i].start()
        }

        and: "rendered"
        applySpans(msg, res) == "hit https://[MASKED_URL] and jdbc:mysql://[MASKED_URL] and s3://[MASKED_URL]"
    }

    def "does not match non-whitelisted schemes (mailto, file)"() {
        expect:
        !det.detect("mail to: mailto:alice@example.com").found()
        !det.detect("open file: file:///var/log/app.log").found()
    }

    def "does not false-positive on emails or plain host strings"() {
        expect:
        applySpans("Contact alice@example.com please", det.detect("Contact alice@example.com please")) ==
                "Contact alice@example.com please"
        applySpans("host=db.prod port=5432", det.detect("host=db.prod port=5432")) ==
                "host=db.prod port=5432"
    }

    def "punctuation boundaries are handled"() {
        given:
        def msg = "see (https://api.example.com/v1/users), thanks."

        expect:
        applySpans(msg, det.detect(msg)) == "see (https://[MASKED_URL]), thanks."
    }

    def "null and empty input returns original"() {
        expect:
        applySpans(null, det.detect(null)) == null
        applySpans("", det.detect("")) == ""
    }

    /**
     * Helper: apply spans to original string to build masked output.
     * We sort spans by start for determinism; detector already guarantees non-overlap.
     */
    private static String applySpans(String s, DetectionResult res) {
        if (s == null) return null
        if (res == null || !res.found() || res.spans().isEmpty()) return s

        def spans = new ArrayList<>(res.spans())
        spans.sort { a,b -> (a.start() <=> b.start()) ?: (a.end() <=> b.end()) }

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
