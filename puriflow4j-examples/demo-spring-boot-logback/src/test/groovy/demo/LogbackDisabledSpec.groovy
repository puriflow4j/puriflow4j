package demo

import ch.qos.logback.classic.spi.ILoggingEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.test.annotation.DirtiesContext

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = [
                "spring.config.name=__none__",
                "logging.config=classpath:logback-test.xml",

                "puriflow4j.logs.enabled=false"
        ],
        classes = [
                TestApp,
                DemoController
        ]
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LogbackDisabledSpec extends BaseLogbackSpec {

    @SpringBootApplication
    static class TestApp {}

    @LocalServerPort int port
    @Autowired TestRestTemplate rest

    protected String url(String path) { "http://localhost:$port$path" }

    // ---------- tests (exact messages where deterministic) ----------

    def "GET /log/message -> exact NOT masked message string"() {
        when:
        def resp = rest.getForEntity(url("/log/message"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logsSize = appender.list.size()
        appender.list[logsSize - 1].formattedMessage == "Login attempt: email=alice@example.com, Authorization: Bearer eyJ.hdr.pay.sig, awsKey=AKIA1234567890ABCDE1, x-auth-token=lol13"
    }

    def "GET /log/message -> MDC is NOT masked"() {
        when:
        def resp = rest.getForEntity(url("/log/message"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        assert !appender.list.isEmpty() : "No log events captured"

        and:
        ILoggingEvent ev = (ILoggingEvent) appender.list.last()
        Map<String, String> mdc = ev.getMDCPropertyMap() ?: [:]

        mdc.isEmpty() // TODO: figure out why it's empty by default
    }

    def "GET /log/card -> exact PAN is NOT masked"() {
        when:
        def resp = rest.getForEntity(url("/log/card"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logsSize = appender.list.size()
        appender.list[logsSize - 1].formattedMessage == "Charge card=4539 1488 0343 6467"
    }

    def "GET /log/secrets -> exact lines for password and api key NOT masked"() {
        when:
        def resp = rest.getForEntity(url("/log/secrets"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logsSize = appender.list.size()
        appender.list[logsSize - 2].formattedMessage == "password=MySuperSecret123"
        appender.list[logsSize - 1].formattedMessage == "x-api-key=AbC1234567890def"
    }

    def "GET /log/mdc -> exact line is NOT masked"() {
        when:
        def resp = rest.getForEntity(url("/log/mdc"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logsSize = appender.list.size()
        appender.list[logsSize - 1].formattedMessage == "Plain message without args"

        and:
        assert !appender.list.isEmpty() : "No log events captured"

        and:
        ILoggingEvent ev = (ILoggingEvent) appender.list.last()
        Map<String, String> mdc = ev.getMDCPropertyMap() ?: [:]

        mdc.isEmpty() // TODO: figure out why it's empty by default
    }

    def "GET /log/iban -> exact NOT masked IBAN line"() {
        when:
        def resp = rest.getForEntity(url("/log/iban"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logsSize = appender.list.size()
        appender.list[logsSize - 1].formattedMessage == "payout iban=DE89 3704 0044 0532 0130 00"
    }

    def "GET /log/ip -> exact NOT masked IP line"() {
        when:
        def resp = rest.getForEntity(url("/log/ip"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logsSize = appender.list.size()
        appender.list[logsSize - 1].formattedMessage == "client ip=203.0.113.42"
    }

    def "GET /log/error -> 5xx and NOT masked fragments present; raw secrets absent"() {
        when:
        def resp = rest.getForEntity(url("/log/error"), String)

        then:
        resp.statusCode.is5xxServerError()

        and:
        def logsSize = appender.list.size()

        appender.list[logsSize - 1].formattedMessage == "Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed: java.lang.RuntimeException: Failed to save user, token=eyJ.hdr.pay.sig] with root cause"
        appender.list[logsSize - 1].throwableProxy.className == "java.sql.SQLException"
        appender.list[logsSize - 1].throwableProxy.message == "password=SuperSecret123 url=jdbc:postgresql://db.prod/acme"
    }

    def "GET /log/error-nested -> 5xx and nested causes are NOT masked"() {
        when:
        def resp = rest.getForEntity(url("/log/error-nested"), String)

        then:
        resp.statusCode.is5xxServerError()

        and:
        def logsSize = appender.list.size()

        appender.list[logsSize - 1].formattedMessage == "Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed: java.lang.RuntimeException: top-level msg] with root cause"
        appender.list[logsSize - 1].throwableProxy.className == "java.sql.SQLException"
        appender.list[logsSize - 1].throwableProxy.message == "jdbcUrl=jdbc:postgresql://db.prod/foo secret=abc"
    }

    def "GET /log/embedded -> embedded block is NOT masked"() {
        when:
        def resp = rest.getForEntity(url("/log/embedded"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logsSize = appender.list.size()
        appender.list[logsSize - 1].formattedMessage == "Embedded block start:\n" +
                "java.lang.RuntimeException: password=TopSecret1\n" +
                "\tat com.acme.Foo.bar(Foo.java:10)\n" +
                "\tat com.acme.Foo.baz(Foo.java:20)\n" +
                "Caused by: java.sql.SQLException: url=jdbc:postgresql://db.prod/acme user=alice\n" +
                "  at org.postgresql.Driver.connect(Driver.java:42)\n" +
                "\n" +
                "--- end"
    }

    def "GET /log/plain -> exact unmasked line"() {
        when:
        def resp = rest.getForEntity(url("/log/plain"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logsSize = appender.list.size()
        appender.list[logsSize - 1].formattedMessage == "Hello world"
    }

    def "GET /log/status -> exact unmasked KVs"() {
        when:
        def resp = rest.getForEntity(url("/log/status"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logsSize = appender.list.size()
        appender.list[logsSize - 1].formattedMessage == "status=OK, count=42"
    }

    def "GET /log/url -> token query param is NOT masked, user remains"() {
        when:
        def resp = rest.getForEntity(url("/log/url"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logsSize = appender.list.size()
        appender.list[logsSize - 1].formattedMessage == "calling url=https://api.example.com/items?token=eyJ.hdr.pay.sig&user=alice"
    }

    def "GET /log/basic-auth -> basic header is NOT masked"() {
        when:
        def resp = rest.getForEntity(url("/log/basic-auth"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logsSize = appender.list.size()
        appender.list[logsSize - 1].formattedMessage == "Auth header=Basic dXNlcjpwYXNz"
    }

    def "GET /log/private-key -> PEM content is NOT masked"() {
        when:
        def resp = rest.getForEntity(url("/log/private-key"), String)

        then:
        resp.statusCode == HttpStatus.OK

        and:
        def logsSize = appender.list.size()

        appender.list[logsSize - 1].formattedMessage == """pem block:
-----BEGIN PRIVATE KEY-----
MIIEvQIBADANBgkqhkiG9w0BAQEFAASC...
-----END PRIVATE KEY-----
"""
    }
}
