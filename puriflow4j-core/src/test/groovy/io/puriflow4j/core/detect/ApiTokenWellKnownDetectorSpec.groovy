package io.puriflow4j.core.detect

import spock.lang.Specification

/**
 * Tests for ApiTokenWellKnownDetector:
 *  - Stripe (sk_/pk_ with test/live)
 *  - Slack (xox[a|b|p|r|s]-...)
 *  - GitHub (gh[pousr]_..., github_pat_...)
 *  - no false positives on short/invalid strings
 *  - multiple tokens in one line
 *  - replacement mask and span boundaries correctness
 */
class ApiTokenWellKnownDetectorSpec extends Specification {

    def detector = new ApiTokenWellKnownDetector()

    def "detects Stripe secret and publishable keys (test/live) and masks them"() {
        given:
        def msg = "stripe: sk_test_ABCDEFGHIJ12345 and pk_live_9Z8Y7X6W5V4U3T2S1R"
        when:
        def res = detector.detect(msg)
        def masked = applySpans(msg, res)
        then:
        res.found()
        res.spans().size() == 2
        masked == "stripe: [MASKED_API_TOKEN] and [MASKED_API_TOKEN]"
    }

    def "detects Slack tokens with xox*- prefix and masks them"() {
        given:
        def msg = "slack: xoxb-12345678 more xoxp-abcdefghi and xoxa-00000000"
        when:
        def res = detector.detect(msg)
        def masked = applySpans(msg, res)
        then:
        res.found()
        res.spans().size() == 3
        masked == "slack: [MASKED_API_TOKEN] more [MASKED_API_TOKEN] and [MASKED_API_TOKEN]"
    }

    def "detects GitHub tokens gh*-_ and github_pat_ and masks them"() {
        given:
        def ghp = "ghp_" + "A"*22
        def gho = "gho_" + "bC3dE4fG5hI6jK7LmN8pq"
        def pat = "github_pat_" + "X"*10 + "_" + "Y"*12
        def msg = "push with $ghp and also $gho ; PAT=$pat."
        when:
        def res = detector.detect(msg)
        def masked = applySpans(msg, res)
        then:
        res.found()
        res.spans().size() == 3
        masked == "push with [MASKED_API_TOKEN] and also [MASKED_API_TOKEN] ; PAT=[MASKED_API_TOKEN]."
    }

    def "does not match lookalikes that are too short or malformed"() {
        expect:
        !detector.detect("sk_test_123").found()              // too short
        !detector.detect("xoxb-1234567").found()             // needs 8+ after dash
        !detector.detect("ghp_1234567890123456789").found()  // need 20+ payload
        !detector.detect("github_pat_1234567890").found()    // need 20+ payload (underscores allowed inside)
        !detector.detect("random xoxz-abcdefghi").found()    // xox(z) not allowed
    }

    def "multiple tokens in one line produce non-overlapping, correctly ordered spans"() {
        given:
        def a = "sk_live_" + "QWERTY12345"
        def b = "xoxs-abcdefgh"
        def c = "ghs_" + "Z"*22
        def msg = "$a $b $c"
        when:
        def res = detector.detect(msg)
        then:
        res.found()
        res.spans().size() == 3

        and: "spans are ordered by start position (check using non-mutating sort)"
        def starts = res.spans()*.start()
        def sortedStarts = starts.sort(false)  // returns a sorted copy, leaves original intact
        starts == sortedStarts

        and: "none overlap"
        def spans = new ArrayList<>(res.spans()) // defensive copy
        for (int i=1; i<spans.size(); i++) {
            assert spans[i-1].end() <= spans[i].start()
        }

        and: "applying spans yields exactly three masks"
        applySpans(msg, res) == "[MASKED_API_TOKEN] [MASKED_API_TOKEN] [MASKED_API_TOKEN]"
    }

    def "boundary conditions: tokens next to punctuation are still detected thanks to word boundaries"() {
        given:
        def msg = "keys: (sk_test_ABCDEFGHIJ12345),xoxb-12345678;ghp_" + "K"*21 + "!"
        when:
        def res = detector.detect(msg)
        def masked = applySpans(msg, res)
        then:
        res.found()
        res.spans().size() == 3
        masked == "keys: ([MASKED_API_TOKEN]),[MASKED_API_TOKEN];[MASKED_API_TOKEN]!"
    }

    // -------- helpers --------

    /**
     * Applies spans to the original message to simulate Sanitizer output.
     * Spans are assumed to be [start,end) with replacement strings.
     * IMPORTANT: always work on a mutable copy, spans() is immutable.
     */
    private static String applySpans(String message, def detectionResult) {
        if (!detectionResult.found()) return message
        // make a defensive, mutable copy and sort without mutating original
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