/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.preset;

import io.puriflow4j.core.api.Detector;
import io.puriflow4j.core.api.model.*;
import io.puriflow4j.core.detect.*;
import java.util.*;

/**
 * Builds a list of {@link Detector} instances based on logical {@link DetectorType}s
 * and the provided {@link KVPatternConfig}.
 *
 * <p>The registry automatically includes the {@link GenericKVBlocklistDetector}
 * whenever any allowlist/blocklist keys are configured. This guarantees that
 * policy-based masking (for keys like <code>x-auth-token</code>, <code>apiKey</code>, etc.)
 * is always enforced, even if the user forgets to declare this detector explicitly.</p>
 *
 * <h3>Detector ordering</h3>
 * <ul>
 *   <li><b>1.</b> Policy detectors (always run first)</li>
 *   <li><b>2.</b> Generic KV detectors (passwords, secrets, etc.)</li>
 *   <li><b>3.</b> Structured and URL-based detectors (DB credentials, URLs)</li>
 *   <li><b>4.</b> Token/Authorization detectors</li>
 *   <li><b>5.</b> Data format detectors (credit cards, emails, IPs, etc.)</li>
 *   <li><b>6.</b> Private keys</li>
 * </ul>
 */
public final class DetectorRegistry {

    /** Default enabled detectors (user may override in YAML). */
    public static EnumSet<DetectorType> defaultTypes() {
        return EnumSet.of(
                DetectorType.EMAIL,
                DetectorType.TOKEN_BEARER,
                DetectorType.CLOUD_ACCESS_KEY,
                DetectorType.API_TOKEN_WELL_KNOWN,
                DetectorType.BASIC_AUTH,
                DetectorType.DB_CREDENTIAL,
                DetectorType.URL_REDACTOR,
                DetectorType.PRIVATE_KEY,
                DetectorType.CREDIT_CARD,
                DetectorType.PASSWORD_KV,
                DetectorType.IBAN,
                DetectorType.IP);
    }

    /**
     * Build detectors in a deterministic order.
     *
     * @param types  the logical types enabled in config (may be null/empty)
     * @param kvCfg  the key policy configuration (never null)
     * @return immutable list of active detectors
     */
    public List<Detector> build(List<DetectorType> types, KVPatternConfig kvCfg) {
        Objects.requireNonNull(kvCfg, "KVPatternConfig cannot be null");

        // Determine which detectors are enabled (use defaults if not provided)
        EnumSet<DetectorType> enabled =
                (types == null || types.isEmpty()) ? EnumSet.copyOf(defaultTypes()) : EnumSet.copyOf(types);

        // Always prepend GenericKVBlocklistDetector if there is any policy configured
        boolean hasPolicy = !kvCfg.allow().isEmpty() || !kvCfg.block().isEmpty();

        List<Detector> out = new ArrayList<>();
        if (hasPolicy) {
            out.add(new GenericKVBlocklistDetector(kvCfg));
        }

        // --- 1) Generic KV-based detectors ---
        if (enabled.contains(DetectorType.PASSWORD_KV)) out.add(new PasswordKVDetector(kvCfg));
        if (enabled.contains(DetectorType.API_TOKEN_WELL_KNOWN)) out.add(new ApiTokenWellKnownDetector());
        if (enabled.contains(DetectorType.CLOUD_ACCESS_KEY)) out.add(new CloudAccessKeyDetector(kvCfg));
        if (enabled.contains(DetectorType.BASIC_AUTH)) out.add(new BasicAuthDetector());

        // --- 2) DB / URL detectors ---
        if (enabled.contains(DetectorType.DB_CREDENTIAL)) out.add(new DbCredentialDetector(kvCfg));
        if (enabled.contains(DetectorType.URL_REDACTOR)) out.add(new UrlRedactorDetector());

        // --- 3) Tokens / headers ---
        if (enabled.contains(DetectorType.TOKEN_BEARER)) out.add(new TokenDetector());

        // --- 4) Data format detectors ---
        if (enabled.contains(DetectorType.CREDIT_CARD)) out.add(new CreditCardDetector());
        if (enabled.contains(DetectorType.EMAIL)) out.add(new EmailDetector());
        if (enabled.contains(DetectorType.IBAN)) out.add(new IbanDetector());
        if (enabled.contains(DetectorType.IP)) out.add(new IpDetector());

        // --- 5) Private keys ---
        if (enabled.contains(DetectorType.PRIVATE_KEY)) out.add(new PrivateKeyDetector());

        return List.copyOf(out);
    }
}
