package demo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
public class DemoController {
    @GetMapping("/log")
    public String log() {
        var email = "alice@example.com";
        var aws   = "AKIA1234567890ABCDE1";
        var jwt   = "eyJ.hdr.pay.sig";
        log.info("User email: {}, awsKey: {}, token: {}", email, aws, jwt);
        log.info("password=MySecret123");
        return "OK";
    }
}
