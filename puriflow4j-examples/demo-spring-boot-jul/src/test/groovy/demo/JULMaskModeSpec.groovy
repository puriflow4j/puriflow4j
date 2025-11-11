package demo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.DirtiesContext
import spock.lang.Specification
import spock.lang.Stepwise

import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Integration tests for JUL (java.util.logging) path.
 *
 * This test:
 *  - Forces Spring Boot to use JavaLoggingSystem (pure JUL backend)
 *  - Redirects System.err (ConsoleHandler output) into memory
 *  - Makes HTTP calls to DemoController endpoints
 *  - Asserts that logs printed to System.err are properly sanitized by Puriflow
 *
 * No custom handlers, formatters, or context changes.
 * We simply read what JUL really prints — exactly what happens in real applications.
 */
@Stepwise
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = [TestApp, DemoController],
        properties = [
                "spring.config.name=__none__",

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
        ]
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JULMaskModeSpec extends Specification {

    @SpringBootApplication
    static class TestApp {}

    @LocalServerPort int port
    @Autowired TestRestTemplate rest

    // --- Capture System.err ---
    private static PrintStream ORIG_ERR
    private static ByteArrayOutputStream ERR_BUFFER

    def setupSpec() {
        ORIG_ERR = System.err
        ERR_BUFFER = new ByteArrayOutputStream(16 * 1024)
        System.setErr(new PrintStream(ERR_BUFFER, true, StandardCharsets.UTF_8))
    }

    def cleanupSpec() {
        if (ORIG_ERR != null) System.setErr(ORIG_ERR)
    }

    def setup() {
        ERR_BUFFER.reset()
    }

    private String url(String path) { "http://localhost:$port$path" }

    /** Waits until JUL ConsoleHandler output contains the given substring (or returns all logs). */
    private String waitStderrContains(String needle,
                                      Duration timeout = Duration.ofSeconds(2),
                                      Duration step = Duration.ofMillis(100)) {
        long end = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < end) {
            String s = ERR_BUFFER.toString(StandardCharsets.UTF_8)
            if (needle == null || needle.isEmpty() || s.contains(needle)) return s
            Thread.sleep(step.toMillis())
        }
        return ERR_BUFFER.toString(StandardCharsets.UTF_8)
    }

    // --------------------------------------------------------------
    // Core tests — same logic as for Logback and Log4j2 integrations
    // --------------------------------------------------------------

    def "GET /log/message -> masked message line"() {
        when:
        def resp = rest.getForEntity(url("/log/message"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logs = waitStderrContains("Login attempt:")
        assert !logs.contains("alice@example.com") : "Raw email leaked:\n${logs}"
        assert logs.contains("Login attempt: email=[MASKED_EMAIL], Authorization: Bearer [MASKED_TOKEN], awsKey=[MASKED_ACCESS_KEY], x-auth-token=lol13")
    }

    def "GET /log/card -> masked credit card"() {
        when:
        def resp = rest.getForEntity(url("/log/card"), String)

        then:
        resp.statusCode == HttpStatus.OK
        and:
        assert waitStderrContains("Charge card=").contains("Charge card=[MASKED_CARD]")
    }

    def "GET /log/secrets -> masked password and API key"() {
        when:
        def resp = rest.getForEntity(url("/log/secrets"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logs = waitStderrContains("password=")
        assert logs.contains("password=[MASKED]")
        assert logs.contains("x-api-key=[MASKED_ACCESS_KEY]")
    }

    def "GET /log/mdc -> no raw JWT leaked"() {
        when:
        def resp = rest.getForEntity(url("/log/mdc"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logs = waitStderrContains("Plain message without args")
        assert logs.contains("Plain message without args")
        assert !logs.contains("eyJ.hdr.pay.sig")
    }

    def "GET /log/iban -> masked IBAN"() {
        when:
        def resp = rest.getForEntity(url("/log/iban"), String)

        then:
        resp.statusCode == HttpStatus.OK
        and:
        assert waitStderrContains("payout iban=").contains("payout iban=[MASKED_IBAN]")
    }

    def "GET /log/ip -> masked IP"() {
        when:
        def resp = rest.getForEntity(url("/log/ip"), String)

        then:
        resp.statusCode == HttpStatus.OK
        and:
        assert waitStderrContains("client ip=").contains("client ip=[MASKED_IP]")
    }

    def "GET /log/error -> masked error fragments"() {
        when:
        def resp = rest.getForEntity(url("/log/error"), String)

        then:
        resp.statusCode.is5xxServerError()

        and:
        def logs = waitStderrContains("Servlet.service() for servlet")
        assert logs.contains("""Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed: java.lang.RuntimeException: Failed to save user, token=[MASKED_TOKEN]] with root cause
[Masked] SQLException: password=[MASKED] url=jdbc:postgresql://[MASKED_URL]
\tat demo.DemoController.error(DemoController.java:85)
\tat jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)
""")
    }

    def "GET /log/error-nested -> nested masked causes"() {
        when:
        def resp = rest.getForEntity(url("/log/error-nested"), String)

        then:
        resp.statusCode.is5xxServerError()

        and:
        def logs = waitStderrContains("top-level msg")
        assert logs.contains("""Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed: java.lang.RuntimeException: top-level msg] with root cause
[Masked] SQLException: jdbcUrl=jdbc:postgresql://[MASKED_URL] secret=[MASKED]
\tat demo.DemoController.nestedError(DemoController.java:95)
\tat jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)
""")
    }

    def "GET /log/embedded -> masked embedded stacktrace"() {
        when:
        def resp = rest.getForEntity(url("/log/embedded"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logs = waitStderrContains("Embedded block start:")
        assert logs.contains("""Embedded block start:
java.lang.RuntimeException: password=[MASKED]
\tat com.acme.Foo.bar(Foo.java:10)
\tat com.acme.Foo.baz(Foo.java:20)
Caused by: java.sql.SQLException: url=jdbc:postgresql://[MASKED_URL] user=[MASKED_USER]
  at org.postgresql.Driver.connect(Driver.java:42)
""")
    }

    def "GET /log/plain -> unmasked simple message"() {
        when:
        def resp = rest.getForEntity(url("/log/plain"), String)

        then:
        resp.statusCode == HttpStatus.OK
        and:
        assert waitStderrContains("Hello world").contains("Hello world")
    }

    def "GET /log/status -> unmasked key-values"() {
        when:
        def resp = rest.getForEntity(url("/log/status"), String)

        then:
        resp.statusCode == HttpStatus.OK
        and:
        assert waitStderrContains("status=OK").contains("status=OK, count=42")
    }

    def "GET /log/url -> masked URL query param"() {
        when:
        def resp = rest.getForEntity(url("/log/url"), String)

        then:
        resp.statusCode == HttpStatus.OK
        and:
        assert waitStderrContains("calling url=").contains("calling url=https://[MASKED_URL]")
    }

    def "GET /log/basic-auth -> masked Basic header"() {
        when:
        def resp = rest.getForEntity(url("/log/basic-auth"), String)

        then:
        resp.statusCode == HttpStatus.OK
        and:
        assert waitStderrContains("Auth header=").contains("Auth header=Basic [MASKED_BASIC_AUTH]")
    }

    def "GET /log/private-key -> masked PEM content"() {
        when:
        def resp = rest.getForEntity(url("/log/private-key"), String)

        then:
        resp.statusCode == HttpStatus.OK
        and:
        assert waitStderrContains("[MASKED_PRIVATE_KEY]").contains("[MASKED_PRIVATE_KEY]")
    }
}
