/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package demo;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.UUID;

@RestController
@Slf4j
public class DemoController {

    @GetMapping("/login")
    public String login(@RequestParam(defaultValue = "alice@example.com") String email) {
        String jwt = "eyJ.hdr.pay.sig";
        String aws = "AKIA1234567890ABCDE1";
        MDC.put("traceId", UUID.randomUUID().toString());
        MDC.put("token", jwt); // будет замаскировано в MDC
        log.info("Login attempt: email={}, Authorization: Bearer {}, awsKey={}", email, jwt, aws);
        return "ok";
    }

    @GetMapping("/pay")
    public String pay() {
        String card = "4539 1488 0343 6467"; // валидный Luhn
        log.info("Charge card={}", card);
        return "ok";
    }

    @GetMapping("/secret")
    public String secret() {
        log.info("password={}", "MySuperSecret123");
        log.info("x-api-key={}", "AbC1234567890def");
        return "ok";
    }

    @GetMapping("/db-error")
    public String dbError() throws Exception {
        try {
            // симулируем типичное сообщение драйвера с чувствительными кусками
            throw new SQLException("password=SuperSecret123 url=jdbc:postgresql://db.prod/acme");
        } catch (SQLException e) {
            // завернуть как бизнес-ошибку, чтобы попасть в логи с cause
            throw new RuntimeException("Failed to save user, token=eyJ.hdr.pay.sig", e);
        }
    }
}