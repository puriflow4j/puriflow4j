package io.puriflow4j.core.preset;

import io.puriflow4j.core.api.models.DetectorType;
import io.puriflow4j.core.detect.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Builds the concrete Detector instances from a logical DetectorType list.
 */
public final class DetectorRegistry {

    public static EnumSet<DetectorType> defaultTypes() {
        return EnumSet.of(DetectorType.EMAIL, DetectorType.JWT, DetectorType.PASSWORD, DetectorType.AWS_KEY);
    }

    public List<Detector> build(List<DetectorType> types, KVPatternConfig kvCfg) {
        List<Detector> out = new ArrayList<>();

        // allowlist flags per logical type
        boolean allowToken = kvCfg.isKeyAllowed("token") || kvCfg.isKeyAllowed("authorization");
        boolean allowEmailKey = kvCfg.isKeyAllowed("email") || kvCfg.isKeyAllowed("userEmail");
        boolean allowAwsKeyKey = kvCfg.isKeyAllowed("awskey") || kvCfg.isKeyAllowed("aws-key") || kvCfg.isKeyAllowed("aws_key");
        boolean allowCardKey = kvCfg.isKeyAllowed("card") || kvCfg.isKeyAllowed("creditcard") || kvCfg.isKeyAllowed("cc");

        for (DetectorType t : types) {
            switch (t) {
                case EMAIL -> {
                    // KV email: mask only the value; honor key allow/block lists
                    out.add(new KVDetector(
                            "emailKV",
                            // Java string needs escaping: \\s, \\b
                            "(?i)(?<key>email|userEmail|user_email)\\s*[:=]\\s*(?<val>\\b[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}\\b)",
                            "email", "[MASKED_EMAIL]", kvCfg
                    ));
                    // Bare email only if an email-like key is NOT allowlisted
                    if (!allowEmailKey) {
                        out.add(new RegexDetector(
                                "emailBare",
                                "(?i)\\b[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}\\b",
                                "email", "[MASKED_EMAIL]"
                        ));
                    }
                }

                case JWT -> {
                    // KV token: mask value only when key is an token-ish key
                    out.add(new KVDetector(
                            "jwtKV",
                            "(?i)(?<key>token|bearer[_-]?token|auth[_-]?token)\\s*[:=]\\s*(?<val>[A-Za-z0-9_-]+\\.[A-Za-z0-9._-]+\\.[A-Za-z0-9._-]+)",
                            "jwt", "[MASKED_JWT]", kvCfg
                    ));
                    // Bare only if corresponding key is NOT allowlisted
                    if (!allowToken) {
                        out.add(new RegexDetector(
                                "jwtBare",
                                "\\b[A-Za-z0-9_-]+\\.[A-Za-z0-9._-]+\\.[A-Za-z0-9._-]+\\b",
                                "jwt", "[MASKED_JWT]"
                        ));
                    }
                }

                case PASSWORD -> {
                    out.add(new KVDetector(
                            "passwordKV",
                            "(?i)(?<key>pass(word)?|pwd|passphrase|secret|api[_-]?key)\\s*[:=]\\s*(?<val>(?!https?://)[^\\s,;]{6,128})",
                            "password", "[MASKED]", kvCfg
                    ));
                    // no bare password detector by default (too many FPs)
                }

                case AWS_KEY -> {
                    // KV form (common in structured logs)
                    out.add(new KVDetector(
                            "awsKeyKV",
                            "(?i)(?<key>awskey|aws-key|aws_key)\\s*[:=]\\s*(?<val>(?:AKIA|ASIA)[0-9A-Z]{16})",
                            "awsKey", "[MASKED_AWS_KEY]", kvCfg
                    ));
                    // Bare only if corresponding key is NOT allowlisted
                    if (!allowAwsKeyKey) {
                        out.add(new RegexDetector(
                                "awsKeyBare",
                                "\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b",
                                "awsKey", "[MASKED_AWS_KEY]"
                        ));
                    }
                }

                case AUTHORIZATION -> {
                    out.add(new AuthorizationBearerKVDetector("[MASKED_JWT]", kvCfg));
                }

                case CREDIT_CARD -> {
                    // KV form: card/creditCard/cc = 16 digits etc. (we still Luhn-check bare detector)
                    out.add(new KVDetector(
                            "creditCardKV",
                            "(?i)(?<key>card|credit(card)?|cc)\\s*[:=]\\s*(?<val>(?:\\d[ -]?){13,19})",
                            "creditCard", "[MASKED_CARD]", kvCfg
                    ));
                    if (!allowCardKey) {
                        out.add(new CreditCardDetector("[MASKED_CARD]"));
                    }
                }
            }
        }

        return out;
    }
}
