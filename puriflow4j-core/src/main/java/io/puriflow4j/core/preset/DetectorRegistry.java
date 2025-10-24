/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.preset;

import io.puriflow4j.core.api.Detector;
import io.puriflow4j.core.api.model.DetectorType;
import io.puriflow4j.core.detect.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Builds the concrete Detector instances from a logical DetectorType list. */
public final class DetectorRegistry {
    public static EnumSet<DetectorType> defaultTypes() {
        return EnumSet.of(
                DetectorType.EMAIL,
                DetectorType.TOKEN_BEARER,
                DetectorType.CLOUD_ACCESS_KEY,
                DetectorType.API_TOKEN_WELL_KNOWN,
                DetectorType.BASIC_AUTH,
                DetectorType.DB_CREDENTIAL,
                DetectorType.PRIVATE_KEY,
                DetectorType.CREDIT_CARD,
                DetectorType.PASSWORD_KV);
    }

    /** Build detectors. kvCfg is used by KV-aware detectors to honor allow/block keys. */
    public List<Detector> build(List<DetectorType> types, KVPatternConfig kvCfg) {
        var list = new ArrayList<Detector>(types.size());
        for (var t : types) {
            switch (t) {
                case EMAIL -> list.add(new EmailDetector());
                case TOKEN_BEARER -> list.add(new TokenDetector());
                case CLOUD_ACCESS_KEY -> list.add(new CloudAccessKeyDetector(kvCfg));
                case API_TOKEN_WELL_KNOWN -> list.add(new ApiTokenWellKnownDetector());
                case BASIC_AUTH -> list.add(new BasicAuthDetector());
                case DB_CREDENTIAL -> list.add(new DbCredentialDetector(kvCfg)); // KV-aware
                case PRIVATE_KEY -> list.add(new PrivateKeyDetector());
                case CREDIT_CARD -> list.add(new CreditCardDetector());
                case PASSWORD_KV -> list.add(new PasswordKVDetector(kvCfg)); // honors allow/block
                case IBAN -> list.add(new IbanDetector());
                case IP -> list.add(new IpDetector());
            }
        }
        return List.copyOf(list);
    }
}
