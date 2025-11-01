/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.detect

import io.puriflow4j.core.api.model.DetectionResult
import spock.lang.Specification

class IpDetectorSpec extends Specification {

    private final IpDetector detector = new IpDetector()

    def "detects and masks a simple IPv4 address"() {
        given:
        def msg = "client 192.168.0.1 connected"

        when:
        def res = detector.detect(msg)

        then:
        res.found()
        res.spans().size() == 1
        applySpans(msg, res) == "client [MASKED_IP] connected"
    }

    def "IPv4 match requires digit boundaries (won't match embedded digits)"() {
        given:
        // '8901' contiguous with previous digits → negative lookahead prevents match
        def msg = "ver123.45.67.8901 build"

        expect:
        !detector.detect(msg).found()
    }

    def "multiple IPv4 addresses are masked, ordered, and non-overlapping"() {
        given:
        def msg = "src=10.0.0.1 dst=172.16.5.6 gw=192.168.1.254"

        when:
        def res = detector.detect(msg)

        then:
        res.found()
        res.spans().size() == 3

        and: "ordered by start"
        def starts = new ArrayList<>(res.spans()*.start())
        def sorted = new ArrayList<>(starts); sorted.sort(false)
        starts == sorted

        and: "no overlap"
        def spans = new ArrayList<>(res.spans())
        spans.sort { a, b -> (a.start() <=> b.start()) ?: (b.end() <=> a.end()) }
        for (int i = 1; i < spans.size(); i++) {
            assert spans[i - 1].end() <= spans[i].start()
        }

        and: "rendered"
        applySpans(msg, res) == "src=[MASKED_IP] dst=[MASKED_IP] gw=[MASKED_IP]"
    }

    def "detects and masks a full IPv6 address (lowercase)"() {
        given:
        def ip = "2001:0db8:85a3:0000:0000:8a2e:0370:7334"
        def msg = "addr=$ip"

        when:
        def res = detector.detect(msg)

        then:
        res.found()
        res.spans().size() == 1
        applySpans(msg, res) == "addr=[MASKED_IP]"
    }

    def "detects and masks a full IPv6 address (mixed case)"() {
        given:
        def ip = "FE80:0000:0000:0000:0202:B3FF:FE1E:8329"
        def msg = "peer=$ip"

        expect:
        applySpans(msg, detector.detect(msg)) == "peer=[MASKED_IP]"
    }

    def "current implementation loosely validates IPv4 (high octets still masked)"() {
        given:
        // NOTE: This is intentionally 'invalid' IPv4 but current regex will still match.
        def msg = "bad ip 999.999.999.999 observed"

        when:
        def res = detector.detect(msg)

        then:
        res.found()
        applySpans(msg, res) == "bad ip [MASKED_IP] observed"
    }

    // ---------- helpers ----------

    /**
     * Applies spans to the original string, replacing ranges with their replacement.
     * Spans are sorted by start asc, end desc, then replaced left->right.
     */
    private static String applySpans(String s, DetectionResult res) {
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
