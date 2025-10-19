package io.puriflow4j.core

import io.puriflow4j.core.api.Sanitizer
import io.puriflow4j.core.preset.BuiltInDetectors
import spock.lang.Specification

class SanitizerSpec extends Specification {

    def "masks email, aws key, jwt and only value for password/token kv"() {
        given:
        def s = new Sanitizer(BuiltInDetectors.minimal())

        when:
        def input = "User email: alice@example.com, awsKey: AKIA1234567890ABCDE1, token: eyJ.hdr.pay.sig, password=MySecret"
        def output = s.apply(input)

        then: "original secrets are removed"
        !output.contains("alice@example.com")
        !output.contains("AKIA1234567890ABCDE1")
        !output.contains("eyJ.hdr.pay.sig")
        !output.contains("MySecret")

        and: "replacements are present and keys kept"
        output.contains("[MASKED_EMAIL]")
        output.contains("[MASKED_AWS_KEY]")
        output.contains("[MASKED_JWT]")
        output.contains("password=[MASKED]")
        output.contains("token: [MASKED_JWT]")

        and:
        output == "User email: [MASKED_EMAIL], awsKey: [MASKED_AWS_KEY], token: [MASKED_JWT], password=[MASKED]"
    }

    def "keeps unrelated text intact"() {
        given:
        def input = new Sanitizer(BuiltInDetectors.minimal())

        when:
        def output = input.apply("hello 123")

        then:
        output == "hello 123"
    }
}
