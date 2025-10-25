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

@RestController
@Slf4j
public class DemoController {

    @GetMapping("/log")
    public String login(@RequestParam(defaultValue = "alice@example.com") String email) {
        String jwt = "eyJ.hdr.pay.sig";
        String aws = "AKIA1234567890ABCDE1";
        MDC.put("traceId", UUID.randomUUID().toString());
        MDC.put("token", jwt); // to be masked in MDC
        log.info(
                "Login attempt: email={}, Authorization: Bearer {}, awsKey={}, x-auth-token={}",
                email,
                jwt,
                aws,
                "lol13");

        log.info("----------------------------------");
        String card = "4539 1488 0343 6467"; // valid Luhn
        log.info("Charge card={}", card);

        log.info("----------------------------------");
        log.info("password={}", "MySuperSecret123");
        log.info("x-api-key={}", "AbC1234567890def");

        log.info("----------------------------------");
        try {
            // симулируем типичное сообщение драйвера с чувствительными кусками
            throw new SQLException("password=SuperSecret123 url=jdbc:postgresql://db.prod/acme");
        } catch (SQLException e) {
            // завернуть как бизнес-ошибку, чтобы попасть в логи с cause
            throw new RuntimeException("Failed to save user, token=eyJ.hdr.pay.sig", e);
        }

        // return "ok";
    }
}
