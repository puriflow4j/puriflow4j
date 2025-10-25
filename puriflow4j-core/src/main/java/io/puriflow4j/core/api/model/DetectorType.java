/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.api.model;

/** Logical detector types users configure in YAML. */
public enum DetectorType {
    TOKEN_BEARER, // JWT + Authorization: Bearer
    CLOUD_ACCESS_KEY, // AWS/Azure/GCP + x-api-key
    API_TOKEN_WELL_KNOWN, // Stripe/Slack/GitHub/...
    BASIC_AUTH, // Authorization: Basic base64(user:pass)
    URL_REDACTOR,
    DB_CREDENTIAL, // DSN/JDBC/URL с user:pass and others
    PRIVATE_KEY, // -----BEGIN … PRIVATE KEY-----
    CREDIT_CARD, // Luhn
    EMAIL,
    PASSWORD_KV, // password/pwd/passwd …
    IBAN,
    IP
}
