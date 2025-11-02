/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package demo;

import java.sql.SQLException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

/**
 * Test controller that emits log lines containing a variety of sensitive tokens
 * and error situations, so we can verify masking, MDC handling, and stacktrace shortening.
 */
@RestController
@Slf4j
public class DemoController {

    /** Message-level secrets in structured args: JWT, AWS key, x-auth-token; email must stay visible. */
    @GetMapping("/log/message")
    public String message(@RequestParam(defaultValue = "alice@example.com") String email) {
        String jwt = "eyJ.hdr.pay.sig";
        String aws = "AKIA1234567890ABCDE1";
        MDC.put("traceId", UUID.randomUUID().toString());
        MDC.put("token", jwt); // should be masked if MDC is rendered by layout
        log.info(
                "Login attempt: email={}, Authorization: Bearer {}, awsKey={}, x-auth-token={}",
                email,
                jwt,
                aws,
                "lol13");
        return "ok";
    }

    /** PAN (credit card) should be masked. */
    @GetMapping("/log/card")
    public String card() {
        String card = "4539 1488 0343 6467"; // valid Luhn
        log.info("Charge card={}", card);
        return "ok";
    }

    /** Common secrets like password and x-api-key should be masked. */
    @GetMapping("/log/secrets")
    public String secrets() {
        log.info("password={}", "MySuperSecret123");
        log.info("x-api-key={}", "AbC1234567890def");
        return "ok";
    }

    /** MDC-only test: put only secret into MDC and emit a benign message. */
    @GetMapping("/log/mdc")
    public String mdc() {
        String jwt = "eyJ.hdr.pay.sig";
        MDC.put("token", jwt);
        log.info("Plain message without args");
        return "ok";
    }

    /** IBAN should be masked. */
    @GetMapping("/log/iban")
    public String iban() {
        log.info("payout iban={}", "DE89 3704 0044 0532 0130 00");
        return "ok";
    }

    /** IP address should be masked if detector is enabled. */
    @GetMapping("/log/ip")
    public String ip() {
        log.info("client ip={}", "203.0.113.42");
        return "ok";
    }

    /** Database-style exception: should be categorized [DB], message masked, and stacktrace shortened. */
    @GetMapping("/log/error")
    public String error() {
        try {
            throw new SQLException("password=SuperSecret123 url=jdbc:postgresql://db.prod/acme");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save user, token=eyJ.hdr.pay.sig", e);
        }
    }

    /** Nested causes to ensure full cause chain handling in compact mode's single 'Caused by' header. */
    @GetMapping("/log/error-nested")
    public String nestedError() {
        try {
            throw new SQLException("jdbcUrl=jdbc:postgresql://db.prod/foo secret=abc");
        } catch (SQLException e) {
            IllegalStateException mid = new IllegalStateException("mid-level cause with token eyJ.hdr.pay.sig", e);
            throw new RuntimeException("top-level msg", mid);
        }
    }

    /** Embedded stacktrace-looking text inside a normal log line should be scanned and masked. */
    @GetMapping("/log/embedded")
    public String embedded() {
        String faux =
                """
                java.lang.RuntimeException: password=TopSecret1
                	at com.acme.Foo.bar(Foo.java:10)
                	at com.acme.Foo.baz(Foo.java:20)
                Caused by: java.sql.SQLException: url=jdbc:postgresql://db.prod/acme user=alice
                  at org.postgresql.Driver.connect(Driver.java:42)
                """;
        log.info("Embedded block start:\n{}\n--- end", faux);
        return "ok";
    }

    /** Purely benign content: nothing should be masked. */
    @GetMapping("/log/plain")
    public String plain() {
        log.info("Hello world");
        return "ok";
    }

    /** Non-secret KV fields: these should remain as-is. */
    @GetMapping("/log/status")
    public String status() {
        log.info("status={}, count={}", "OK", 42);
        return "ok";
    }

    /** URL redaction — access token must be masked, other benign params remain. */
    @GetMapping("/log/url")
    public String url() {
        log.info("calling url={}", "https://api.example.com/items?token=eyJ.hdr.pay.sig&user=alice");
        return "ok";
    }

    /** Basic auth header base64 should be masked. */
    @GetMapping("/log/basic-auth")
    public String basicAuth() {
        log.info("Auth header={}", "Basic dXNlcjpwYXNz");
        return "ok";
    }

    /** PEM-like private key content must be masked. */
    @GetMapping("/log/private-key")
    public String privateKey() {
        String pem =
                """
                -----BEGIN PRIVATE KEY-----
                MIIEvQIBADANBgkqhkiG9w0BAQEFAASC...
                -----END PRIVATE KEY-----
                """;
        log.info("pem block:\n{}", pem);
        return "ok";
    }
}
