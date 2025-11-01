/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.detect

import io.puriflow4j.core.api.model.DetectionResult
import spock.lang.Specification
import spock.lang.Unroll

class TokenDetectorSpec extends Specification {

    def det = new TokenDetector()

    def "masks Bearer token in Authorization header"() {
        given:
        def msg = "Authorization: Bearer eyJ.hdr.pay.sig"

        when:
        def res = det.detect(msg)

        then:
        res.found()
        res.spans().size() == 1
        applySpans(msg, res) == "Authorization: Bearer [MASKED_TOKEN]"
    }

    @Unroll
    def "masks KV token forms: '#kv'"(String kv, String expected) {
        when:
        def res = det.detect(kv)

        then:
        res.found()
        res.spans().size() == 1
        applySpans(kv, res) == expected

        where:
        kv                                                 || expected
        "token=eyJ.hdr.pay.sig"                            || "token=[MASKED_TOKEN]"
        "access_token: eyJ.hdr.pay.sig"                    || "access_token: [MASKED_TOKEN]"
        "id-token = eyJ.hdr.pay.sig"                       || "id-token = [MASKED_TOKEN]"
    }

    def "masks bare JWT-like token (no header/key)"() {
        given:
        def msg = "got: eyJ.hdr.pay.sig ; continue"

        when:
        def res = det.detect(msg)

        then:
        res.found()
        res.spans().size() == 1
        applySpans(msg, res) == "got: [MASKED_TOKEN] ; continue"
    }

    def "does not leave '.sig' tail unmasked (regression)"() {
        given:
        def msg = "Failed: token=eyJ.hdr.pay.sig"

        when:
        def res = det.detect(msg)

        then:
        res.found()
        applySpans(msg, res) == "Failed: token=[MASKED_TOKEN]"
        // убедимся, что в выходе точно нет кусочков исходного токена
        !applySpans(msg, res).contains(".sig")
        !applySpans(msg, res).contains("eyJ.")
    }

    def "multiple tokens in one line are all masked and spans are ordered/non-overlapping"() {
        given:
        def t1 = "eyA.bb.ccc"
        def t2 = "aaa.bbb.ccc.ddd"
        def msg = "t1=$t1; Authorization: Bearer $t2; end"

        when:
        def res = det.detect(msg)

        then:
        res.found()
        res.spans().size() == 2

        and: "ordered by start"
        def starts = res.spans()*.start()
        def startsSorted = new ArrayList<>(starts)
        Collections.sort(startsSorted)
        starts == startsSorted

        and: "no overlaps"
        def spans = new ArrayList<>(res.spans())
        spans.sort { a, b -> (a.start() <=> b.start()) ?: (a.end() <=> b.end()) }
        for (int i = 1; i < spans.size(); i++) {
            assert spans[i - 1].end() <= spans[i].start()
        }

        and: "rendered"
        applySpans(msg, res) == "t1=[MASKED_TOKEN]; Authorization: Bearer [MASKED_TOKEN]; end"
    }

    def "token next to punctuation is still detected thanks to boundaries"() {
        given:
        def msg = "(eyJ.hdr.pay.sig), next"

        expect:
        applySpans(msg, det.detect(msg)) == "([MASKED_TOKEN]), next"
    }

    def "no matches for non-token strings"() {
        expect:
        !det.detect("header=Bearer token but without dots").found()
        !det.detect("foo.bar (two segments only) is not a JWT").found()
    }

    def "null and empty input returns no spans"() {
        expect:
        !det.detect(null).found()
        !det.detect("").found()
    }

    // ---------------- helper ----------------
    /**
     * Apply spans to the original string, building the masked output.
     * Spans are assumed non-overlapping (detector already merges them),
     * but we still sort by start for determinism.
     */
    private static String applySpans(String s, DetectionResult res) {
        if (s == null) return null
        if (!res.found() || res.spans().isEmpty()) return s
        def spans = new ArrayList<>(res.spans())
        // stable order: by start, then end
        spans.sort { a, b -> (a.start() <=> b.start()) ?: (a.end() <=> b.end()) }

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
