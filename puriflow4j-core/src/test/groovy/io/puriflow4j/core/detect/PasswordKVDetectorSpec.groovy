/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.detect

import io.puriflow4j.core.api.model.DetectionResult
import io.puriflow4j.core.preset.KVPatternConfig
import spock.lang.Specification

class PasswordKVDetectorSpec extends Specification {

    def "masks password-like KV pairs with '=' and ':' separators"() {
        given:
        def det = new PasswordKVDetector(KVPatternConfig.of([], []))
        def msg1 = "password=MySecret123"
        def msg2 = "pwd: Sup3rP@ss"
        def msg3 = "passphrase = ultraSecret"

        expect:
        applySpans(msg1, det.detect(msg1)) == "password=[MASKED]"
        applySpans(msg2, det.detect(msg2)) == "pwd: [MASKED]"
        applySpans(msg3, det.detect(msg3)) == "passphrase = [MASKED]"
    }

    def "stops value at whitespace/comma/semicolon"() {
        given:
        def det = new PasswordKVDetector(KVPatternConfig.of([], []))
        def msg = "pwd=abc123, role=admin; note=ok"

        expect:
        applySpans(msg, det.detect(msg)) == "pwd=[MASKED], role=admin; note=ok"
    }

    def "is case-insensitive for keys"() {
        given:
        def det = new PasswordKVDetector(KVPatternConfig.of([], []))
        def msg = "PaSsWoRd=wow"

        expect:
        applySpans(msg, det.detect(msg)) == "PaSsWoRd=[MASKED]"
    }

    def "allowlist prevents masking for that key"() {
        given:
        def kv = KVPatternConfig.of(["password"], [])   // allow 'password'
        def det = new PasswordKVDetector(kv)
        def msg = "password=letMePass"

        expect:
        !det.detect(msg).found()
        applySpans(msg, det.detect(msg)) == msg
    }

    def "blocklist overrides allowlist"() {
        given:
        def kv = KVPatternConfig.of(["password"], ["password"]) // both allowed and blocked -> block wins
        def det = new PasswordKVDetector(kv)
        def msg = "password=letMePass"

        expect:
        applySpans(msg, det.detect(msg)) == "password=[MASKED]"
    }

    def "multiple occurrences in one line are ordered and non-overlapping"() {
        given:
        def det = new PasswordKVDetector(KVPatternConfig.of([], []))
        def msg = "password=one; secret=two pwd=three"

        when:
        def res = det.detect(msg)

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
        applySpans(msg, res) == "password=[MASKED]; secret=[MASKED] pwd=[MASKED]"
    }

    def "ignores unrelated keys"() {
        given:
        def det = new PasswordKVDetector(KVPatternConfig.of([], []))
        def msg = "apikey=abc token=zzz user=bob"

        expect:
        !det.detect(msg).found()
        applySpans(msg, det.detect(msg)) == msg
    }

    def "null and empty inputs are no-ops"() {
        given:
        def det = new PasswordKVDetector(KVPatternConfig.of([], []))

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
