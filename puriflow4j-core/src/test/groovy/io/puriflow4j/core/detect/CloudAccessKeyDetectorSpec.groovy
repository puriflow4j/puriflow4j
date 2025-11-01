package io.puriflow4j.core.detect

import io.puriflow4j.core.preset.KVPatternConfig
import spock.lang.Specification

/**
 * Tests for CloudAccessKeyDetector:
 *  - AWS Access Keys (AKIA/ASIA/AIDA/AGPA)
 *  - Azure SAS URL signature (sig=...)
 *  - Generic KV keys (x-api-key, apiKey, access-key) with allowlist respected
 *  - Multiple matches in one line, ordering and non-overlap
 *  - Boundary/invalid cases and no false positives
 */
class CloudAccessKeyDetectorSpec extends Specification {

    def kvDefaults = KVPatternConfig.defaults()
    def detector    = new CloudAccessKeyDetector(kvDefaults)

    def "detects and masks AWS access keys"() {
        given:
        def msg = "awsKey=AKIA1234567890ABCDE1 other ASIAZZZZZZZZZZZZZZZ"
        when:
        def res = detector.detect(msg)
        def masked = applySpans(msg, res)
        then:
        res.found()
        res.spans().size() == 2
        masked == "awsKey=[MASKED_ACCESS_KEY] other [MASKED_ACCESS_KEY]"
    }

    def "detects and masks Azure SAS sig in URL but preserves prefix"() {
        given:
        def msg = "https://blob.core.windows.net/c?sv=2024-01-01&sig=AbCdEf0123%2Bxyz==&sp=rw"
        when:
        def res = detector.detect(msg)
        def masked = applySpans(msg, res)
        then:
        res.found()
        res.spans().size() == 1
        masked == "https://blob.core.windows.net/c?sv=2024-01-01&sig=[MASKED_ACCESS_KEY]&sp=rw"
    }

    def "masks generic KV tokens like x-api-key and apiKey"() {
        given:
        def msg = "x-api-key=AbC1234567890def, apiKey: zZ9_~+/aa.bB== ; access-key = KKKKKKKK"
        when:
        def res = detector.detect(msg)
        def masked = applySpans(msg, res)
        then:
        res.found()
        res.spans().size() == 3
        masked == "x-api-key=[MASKED_ACCESS_KEY], apiKey: [MASKED_ACCESS_KEY] ; access-key = [MASKED_ACCESS_KEY]"
    }

    def "respects allowlist: allowed key must not be masked"() {
        given:
        // allow 'x-api-key' explicitly
        def kv = KVPatternConfig.of(
                ["traceId","requestId","correlationId","x-api-key"],
                [] // no block
        )
        def d = new CloudAccessKeyDetector(kv)
        def msg = "x-api-key=SENSITIVE123 apiKey=SENSITIVE456"
        when:
        def res = d.detect(msg)
        def masked = applySpans(msg, res)
        then:
        res.found()
        res.spans().size() == 1  // only apiKey masked; x-api-key allowed
        masked == "x-api-key=SENSITIVE123 apiKey=[MASKED_ACCESS_KEY]"
    }

    def "multiple matches in one line are ordered and non-overlapping"() {
        given:
        def a = "AKIA1234567890ABCD1"
        def b = "ASIAABCDEFGHIJKLMNOP"
        def msg = "k1=$a&sig=QWErty12%2Bzz==&k2=$b"

        when:
        def res = detector.detect(msg)

        then:
        res.found()
        res.spans().size() == 3

        and: "no overlap after local sort by start asc, end desc"
        def spans = new ArrayList<>(res.spans())
        spans.sort { x, y -> x.start() <=> y.start() ?: y.end() <=> x.end() }

        for (int i = 1; i < spans.size(); i++) {
            assert spans[i - 1].end() <= spans[i].start()
        }

        and: "rendered output matches expected masking"
        applySpans(msg, res) == "k1=[MASKED_ACCESS_KEY]&sig=[MASKED_ACCESS_KEY]&k2=[MASKED_ACCESS_KEY]"
    }

    def "does not match too-short or malformed values"() {
        expect:
        !detector.detect("AKIA123").found()                         // too short
        !detector.detect("Authorization: Basic QWxhZGRpbjpzZWNyZXQ=").found() // not this detector's domain
        !detector.detect("sig=").found()                            // empty value
    }

    def "does not mask unrelated base64 noise without keys"() {
        expect:
        !detector.detect("random Zm9vYmFyYmF6 with text").found()
    }

    // ---------- helper ----------

    /**
     * Applies spans to original message to simulate Sanitizer output.
     * Defensive mutable copy + stable sort (start asc, end desc).
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
