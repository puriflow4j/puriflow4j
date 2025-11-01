package io.puriflow4j.core.detect

import io.puriflow4j.core.api.model.DetectionResult
import io.puriflow4j.core.preset.KVPatternConfig
import spock.lang.Specification

class GenericKVBlocklistDetectorSpec extends Specification {

    def "masks value when key is blocklisted (equals sign)"() {
        given:
        def kv = KVPatternConfig.of([], ["x-auth-token"])
        def det = new GenericKVBlocklistDetector(kv)
        def msg = "user=bob, x-auth-token=secret123, ok=true"

        expect:
        applySpans(msg, det.detect(msg)) == "user=bob, x-auth-token=[MASKED], ok=true"
    }

    def "masks value when key is blocklisted (colon)"() {
        given:
        def kv = KVPatternConfig.of([], ["apiKey"])
        def det = new GenericKVBlocklistDetector(kv)
        def msg = "apiKey: XYZ-987"

        expect:
        applySpans(msg, det.detect(msg)) == "apiKey: [MASKED]"
    }

    def "key normalization handles dashes/underscores/camelCase and case-insensitivity"() {
        given:
        def kv = KVPatternConfig.of([], ["xauthtoken"]) // normalized target
        def det = new GenericKVBlocklistDetector(kv)

        expect:
        applySpans("X-AUTH-TOKEN=abc", det.detect("X-AUTH-TOKEN=abc")) == "X-AUTH-TOKEN=[MASKED]"
        applySpans("x_auth_token=abc",  det.detect("x_auth_token=abc"))  == "x_auth_token=[MASKED]"
        applySpans("xAuthToken=abc",    det.detect("xAuthToken=abc"))    == "xAuthToken=[MASKED]"
    }

    def "allowlist alone does not mask"() {
        given:
        def kv = KVPatternConfig.of(["sessionId"], [])
        def det = new GenericKVBlocklistDetector(kv)
        def msg = "sessionId=KEEP_ME"

        expect:
        !det.detect(msg).found()
        applySpans(msg, det.detect(msg)) == msg
    }

    def "blocklist wins over allowlist on the same key"() {
        given:
        def kv = KVPatternConfig.of(["password"], ["password"])
        def det = new GenericKVBlocklistDetector(kv)
        def msg = "password=TopSecret"

        expect:
        applySpans(msg, det.detect(msg)) == "password=[MASKED]"
    }

    def "multiple KV pairs: ordered by start and non-overlapping"() {
        given:
        def kv = KVPatternConfig.of([], ["apiKey","token"])
        def det = new GenericKVBlocklistDetector(kv)
        def msg = "a=1, apiKey = A1B2C3; token: XYZ , z=9"

        when:
        def res = det.detect(msg)

        then:
        res.found()
        res.spans().size() == 2
        assertOrderedNonOverlapping(res)

        and:
        applySpans(msg, res) == "a=1, apiKey = [MASKED]; token: [MASKED] , z=9"
    }

    def "value stops at delimiters: comma/semicolon/quote/brackets/space"() {
        given:
        def kv = KVPatternConfig.of([], ["cookie"])
        def det = new GenericKVBlocklistDetector(kv)

        expect:
        applySpans('cookie=abc, next=1',       det.detect('cookie=abc, next=1'))       == 'cookie=[MASKED], next=1'
        applySpans('cookie=abc; next=1',       det.detect('cookie=abc; next=1'))       == 'cookie=[MASKED]; next=1'
        applySpans('cookie=abc" trail',        det.detect('cookie=abc" trail'))        == 'cookie=[MASKED]" trail'
        applySpans('cookie=abc) trail',        det.detect('cookie=abc) trail'))        == 'cookie=[MASKED]) trail'
        applySpans('cookie=abc] trail',        det.detect('cookie=abc] trail'))        == 'cookie=[MASKED]] trail'
        applySpans('cookie=abc} trail',        det.detect('cookie=abc} trail'))        == 'cookie=[MASKED]} trail'
        applySpans('cookie=abc trail',         det.detect('cookie=abc trail'))         == 'cookie=[MASKED] trail'
    }

    def "non-blocklisted keys are ignored (left for specific detectors)"() {
        given:
        def kv = KVPatternConfig.of([], ["token"]) // password is not in blocklist here
        def det = new GenericKVBlocklistDetector(kv)
        def msg = "password=P@ss, token=abc123"

        expect:
        applySpans(msg, det.detect(msg)) == "password=P@ss, token=[MASKED]"
    }

    // ------------- helpers -------------

    private static String applySpans(String msg, DetectionResult res) {
        if (!res.found() || res.spans().isEmpty()) return msg
        def spans = new ArrayList<>(res.spans())
        // sort by start asc, end desc (stable for merge semantics)
        spans.sort { a, b -> (a.start() <=> b.start()) ?: (b.end() <=> a.end()) }

        StringBuilder out = new StringBuilder(msg.length() + 16)
        int pos = 0
        for (def s : spans) {
            if (s.start() > pos) out.append(msg, pos, s.start())
            out.append(s.replacement())
            pos = s.end()
        }
        if (pos < msg.length()) out.append(msg, pos, msg.length())
        return out.toString()
    }

    private static void assertOrderedNonOverlapping(DetectionResult res) {
        def spans = new ArrayList<>(res.spans())
        spans.sort { a, b -> (a.start() <=> b.start()) ?: (b.end() <=> a.end()) }

        assert spans*.start() == new ArrayList<>(spans*.start()).with { it.sort(false); it }
        for (int i = 1; i < spans.size(); i++) {
            assert spans[i - 1].end() <= spans[i].start()
        }
    }
}
