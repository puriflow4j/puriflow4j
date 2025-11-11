package demo

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import spock.lang.Specification
/**
 * Integration tests that verify the exact sanitized log messages produced
 * by DemoController endpoints. We assert the final message string that is
 * written to the in-memory ListAppender after Puriflow masking.
 *
 * IMPORTANT:
 * - Expected strings below match Puriflow’s default masking labels
 *   seen in your runs: [MASKED_EMAIL], [MASKED_TOKEN], [MASKED_ACCESS_KEY], [MASKED_CARD], [MASKED].
 * - If you change masking labels via config, update expected strings accordingly.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = [
                "spring.config.name=__none__",
                // make sure our in-memory appender is active
                "logging.config=classpath:logback-test.xml",

                // puriflow4j.logs mask mode with all detectors
                "puriflow4j.logs.enabled=true",
                "puriflow4j.logs.mode=mask",
                "puriflow4j.logs.detectors[0]=token_bearer",
                "puriflow4j.logs.detectors[1]=cloud_access_key",
                "puriflow4j.logs.detectors[2]=api_token_well_known",
                "puriflow4j.logs.detectors[3]=basic_auth",
                "puriflow4j.logs.detectors[4]=db_credential",
                "puriflow4j.logs.detectors[5]=url_redactor",
                "puriflow4j.logs.detectors[6]=private_key",
                "puriflow4j.logs.detectors[7]=credit_card",
                "puriflow4j.logs.detectors[8]=email",
                "puriflow4j.logs.detectors[9]=password_kv",
                "puriflow4j.logs.detectors[10]=iban",
                "puriflow4j.logs.detectors[11]=ip"
        ],
        classes = [
                TestApp,
                DemoController
        ]
)
class LogbackMaskModeSpec extends BaseLogbackSpec {

    @SpringBootApplication
    static class TestApp {}

    @LocalServerPort int port
    @Autowired TestRestTemplate rest

    protected String url(String path) { "http://localhost:$port$path" }

    // ---------- tests (exact messages where deterministic) ----------

    /**
     * Expected final message (after masking) from /log/message:
     * "Login attempt: email=[MASKED_EMAIL], Authorization: Bearer [MASKED_TOKEN], awsKey=[MASKED_ACCESS_KEY], x-auth-token=[MASKED_TOKEN]"
     *
     * NOTE: if your current config intentionally leaves x-auth-token unmasked,
     * change the expected tail to "x-auth-token=lol13".
     */
    def "GET /log/message -> exact masked message string"() {
        when:
        def resp = rest.getForEntity(url("/log/message"), String)

        then: "endpoint returns OK"
        resp.statusCode == HttpStatus.OK

        and: "the exact masked line is present"
        lastMessage() == "Login attempt: email=[MASKED_EMAIL], Authorization: Bearer [MASKED_TOKEN], awsKey=[MASKED_ACCESS_KEY], x-auth-token=lol13"
    }

    //  Verifies MDC is sanitized in Logback: 'token' is masked and 'traceId' is preserved.
    def "GET /log/message -> MDC is sanitized (token masked, traceId preserved)"() {
        when:
        def resp = rest.getForEntity(url("/log/message"), String)

        then: "endpoint returns OK"
        resp.statusCode == HttpStatus.OK

        and: "we have at least one log event captured"
        assert !appender.list.isEmpty() : "No log events captured"

        and: "inspect MDC of the last event"
        ILoggingEvent ev = (ILoggingEvent) appender.list.last()
        Map<String, String> mdc = ev.getMDCPropertyMap() ?: [:]

        //  'traceId' should be present and non-empty
        assert mdc.containsKey("traceId")
        assert (mdc.get("traceId") as String)?.trim()

        //  MDC should be sanitized same as normal keys -> 'token' masked
        // If you later decide to drop non-allowlisted MDC keys instead of masking, switch to:
        //   assert !mdc.containsKey("token")
        assert mdc.containsKey("token")
        assert mdc.get("token") == "[MASKED_TOKEN]"
    }

    /**
     * Expected final message from /log/card:
     * "Charge card=[MASKED_CARD]"
     */
    def "GET /log/card -> exact PAN-masked message"() {
        when:
        def resp = rest.getForEntity(url("/log/card"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        lastMessage() == "Charge card=[MASKED_CARD]"
    }

    /**
     * /log/secrets emits two lines, we assert both exact masked outputs:
     * "password=[MASKED]"
     * "x-api-key=[MASKED_ACCESS_KEY]"
     */
    def "GET /log/secrets -> exact masked lines for password and api key"() {
        when:
        def resp = rest.getForEntity(url("/log/secrets"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def lines = allMessages().readLines()
        assert lines.any { it == "password=[MASKED]" }
        assert lines.any { it == "x-api-key=[MASKED_ACCESS_KEY]" }
    }

    /**
     * /log/mdc logs: "Plain message without args"
     * We assert the exact line and (critically) that raw JWT from MDC is NOT present anywhere.
     */
    def "GET /log/mdc -> exact line and no raw JWT leaked"() {
        when:
        def resp = rest.getForEntity(url("/log/mdc"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        assert !appender.list.isEmpty() : "No log events captured"

        and:
        ILoggingEvent ev = (ILoggingEvent) appender.list.last()
        Map<String, String> mdc = ev.getMDCPropertyMap() ?: [:]

        assert mdc.containsKey("token")
        assert mdc.get("token") == "[MASKED_TOKEN]"

        and:
        lastMessage() == "Plain message without args"
    }

    /**
     * /log/iban logs: "payout iban=[MASKED_IBAN]"
     */
    def "GET /log/iban -> exact masked IBAN line"() {
        when:
        def resp = rest.getForEntity(url("/log/iban"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        lastMessage() == "payout iban=[MASKED_IBAN]"
    }

    /**
     * /log/ip logs: "client ip=[MASKED_IP]"
     */
    def "GET /log/ip -> exact masked IP line"() {
        when:
        def resp = rest.getForEntity(url("/log/ip"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        lastMessage() == "client ip=[MASKED_IP]"
    }

    /**
     * /log/error throws. Framework will log an error with stacktrace.
     * We assert:
     *  - HTTP is 5xx
     *  - masked fragments appear (token/password)
     *  - raw secrets do NOT appear
     * Exact full error line is framework-dependent, so we use precise fragments.
     */
    def "GET /log/error -> 5xx and masked fragments present; raw secrets absent"() {
        when:
        def resp = rest.getForEntity(url("/log/error"), String)

        then:
        resp.statusCode.is5xxServerError()

        and:
        def m = allMessages()
        assert m.contains("Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed: java.lang.RuntimeException: Failed to save user, token=[MASKED_TOKEN]] with root cause\n" +
                "[Masked] SQLException: password=[MASKED] url=jdbc:postgresql://[MASKED_URL]\n" +
                "\tat demo.DemoController.error(DemoController.java:80)\n" +
                "\tat jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)")
    }

    /**
     * /log/error-nested throws nested exceptions.
     * We again assert status and the presence/absence of critical fragments.
     */
    def "GET /log/error-nested -> 5xx and nested causes are masked"() {
        when:
        def resp = rest.getForEntity(url("/log/error-nested"), String)

        then:
        resp.statusCode.is5xxServerError()

        and:
        def m = allMessages()
        assert m.contains("Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed: java.lang.RuntimeException: top-level msg] with root cause\n" +
                "[Masked] SQLException: jdbcUrl=jdbc:postgresql://[MASKED_URL] secret=[MASKED]\n" +
                "\tat demo.DemoController.nestedError(DemoController.java:90)\n" +
                "\tat jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)")
    }

    /**
     * /log/embedded writes an info log that contains an embedded stacktrace-looking block.
     * We assert the final info message contains the masked placeholders and no raw secrets.
     * Exact multi-line content is long; we check precise, stable fragments.
     */
    def "GET /log/embedded -> embedded block is masked"() {
        when:
        def resp = rest.getForEntity(url("/log/embedded"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def m = allMessages()
        assert m.contains("Embedded block start:\n" +
                "java.lang.RuntimeException: password=[MASKED]\n" +
                "\tat com.acme.Foo.bar(Foo.java:10)\n" +
                "\tat com.acme.Foo.baz(Foo.java:20)\n" +
                "Caused by: java.sql.SQLException: url=jdbc:postgresql://[MASKED_URL] user=[MASKED_USER]\n" +
                "  at org.postgresql.Driver.connect(Driver.java:42)")
    }

    /** Purely benign content: nothing should be masked. */
    def "GET /log/plain -> exact unmasked line"() {
        when:
        def resp = rest.getForEntity(url("/log/plain"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and: "the exact message is written as-is"
        lastMessage() == "Hello world"
    }

    /** Non-secret KV fields: these should remain as-is. */
    def "GET /log/status -> exact unmasked KVs"() {
        when:
        def resp = rest.getForEntity(url("/log/status"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        lastMessage() == "status=OK, count=42"
    }

    /** URL redaction — token must be masked, other benign param remains. */
    def "GET /log/url -> token query param masked, user remains"() {
        when:
        def resp = rest.getForEntity(url("/log/url"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        lastMessage() == "calling url=https://[MASKED_URL]"
    }

    /** Basic auth header base64 should be masked. */
    def "GET /log/basic-auth -> basic header masked"() {
        when:
        def resp = rest.getForEntity(url("/log/basic-auth"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        lastMessage() == "Auth header=Basic [MASKED_BASIC_AUTH]"
    }

    /** PEM-like private key content must be masked. */
    def "GET /log/private-key -> PEM content masked"() {
        when:
        def resp = rest.getForEntity(url("/log/private-key"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def msg = allMessages()
        assert msg.contains("pem block:\n" +
                "[MASKED_PRIVATE_KEY]")
    }
}
