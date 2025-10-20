package demo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class DemoController {

    @GetMapping("/log")
    public String log(@RequestHeader(value="Authorization", required=false) String auth) {
        var email = "alice@example.com";
        var aws   = "AKIA1234567890ABCDE1";
        var jwt   = "eyJ.hdr.pay.sig";

        log.info("User email: {}, awsKey: {}, token: {}", email, aws, jwt);
        log.info("password=MySecret123");
        if (auth != null) {
            log.info("Authorization: Bearer {}", auth);
        }
        // credit card example (valid Luhn)
        log.info("Card: 4539 1488 0343 6467");
        log.info("SSN: 1234");
        return "OK";
    }
}
