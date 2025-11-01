package io.puriflow4j.core.preset

import io.puriflow4j.core.api.model.*
import io.puriflow4j.core.detect.*
import spock.lang.Specification

/**
 * Tests for DetectorRegistry:
 * - defaultTypes content
 * - build() uses defaults when types are null/empty
 * - GenericKVBlocklistDetector injected only when policy is present (allow/block not empty)
 * - deterministic ordering of detectors
 * - honoring custom enabled set
 */
class DetectorRegistrySpec extends Specification {

    def "defaultTypes contains the expected detector set"() {
        when:
        def defaults = DetectorRegistry.defaultTypes()

        then:
        defaults.containsAll(EnumSet.of(
                DetectorType.EMAIL,
                DetectorType.TOKEN_BEARER,
                DetectorType.CLOUD_ACCESS_KEY,
                DetectorType.API_TOKEN_WELL_KNOWN,
                DetectorType.BASIC_AUTH,
                DetectorType.DB_CREDENTIAL,
                DetectorType.URL_REDACTOR,
                DetectorType.PRIVATE_KEY,
                DetectorType.CREDIT_CARD,
                DetectorType.PASSWORD_KV,
                DetectorType.IBAN,
                DetectorType.IP
        ))
        defaults.size() == 12
    }

    def "build() with null types uses defaults and does NOT inject policy-detector when no policy configured"() {
        given:
        def kv = KVPatternConfig.of(/*allow*/[], /*block*/[])
        def reg = new DetectorRegistry()

        when:
        def detectors = reg.build(null, kv)
        def classes = detectors*.getClass()

        then:
        !classes.contains(GenericKVBlocklistDetector) // no policy → no policy-detector
        classes.containsAll([
                PasswordKVDetector,
                ApiTokenWellKnownDetector,
                CloudAccessKeyDetector,
                BasicAuthDetector,
                DbCredentialDetector,
                UrlRedactorDetector,
                TokenDetector,
                CreditCardDetector,
                EmailDetector,
                IbanDetector,
                IpDetector,
                PrivateKeyDetector
        ] as Set)
    }

    def "build() injects GenericKVBlocklistDetector FIRST when allow/block policy is present"() {
        given:
        def kv = KVPatternConfig.of(/*allow*/["traceId"], /*block*/["x-auth-token"])
        def reg = new DetectorRegistry()

        when:
        def detectors = reg.build(null, kv)

        then:
        detectors.size() >= 1
        detectors.first().class == GenericKVBlocklistDetector
    }

    def "detectors appear in deterministic, overlap-friendly order"() {
        given:
        def kv = KVPatternConfig.of(["traceId"], ["x-auth-token"])
        def reg = new DetectorRegistry()

        when:
        def detectors = reg.build(null, kv)
        def names = detectors*.class*.simpleName

        then:
        // Expected prefix ordering:
        names[0] == "GenericKVBlocklistDetector"            // policy
        names.indexOf("PasswordKVDetector")       > 0       // generic KV secrets
        names.indexOf("ApiTokenWellKnownDetector") > names.indexOf("PasswordKVDetector")
        names.indexOf("CloudAccessKeyDetector")   > names.indexOf("ApiTokenWellKnownDetector")
        names.indexOf("BasicAuthDetector")        > names.indexOf("CloudAccessKeyDetector")

        names.indexOf("DbCredentialDetector")     > names.indexOf("BasicAuthDetector")  // structured creds
        names.indexOf("UrlRedactorDetector")      > names.indexOf("DbCredentialDetector")

        names.indexOf("TokenDetector")            > names.indexOf("UrlRedactorDetector") // tokens

        // data formats afterwards
        names.indexOf("CreditCardDetector")       > names.indexOf("TokenDetector")
        names.indexOf("EmailDetector")            > names.indexOf("CreditCardDetector")
        names.indexOf("IbanDetector")             > names.indexOf("EmailDetector")
        names.indexOf("IpDetector")               > names.indexOf("IbanDetector")

        // private key at the end
        names.last() == "PrivateKeyDetector"
    }

    def "honors custom enabled set (subset)"() {
        given:
        def kv = KVPatternConfig.of([], [])
        def reg = new DetectorRegistry()
        def types = [
                DetectorType.EMAIL,
                DetectorType.TOKEN_BEARER,
                DetectorType.PASSWORD_KV
        ]

        when:
        def detectors = reg.build(types, kv)
        def classes = detectors*.class as Set

        then:
        classes == [
                PasswordKVDetector,
                TokenDetector,
                EmailDetector
        ] as Set
    }
}