/*
 * Copyright (c) 2025 Puriflow4J Contributors
 * Licensed under the Apache License 2.0
 */
package io.puriflow4j.core.detect;

import io.puriflow4j.core.api.Detector;
import io.puriflow4j.core.api.model.DetectionResult;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects and masks IBAN numbers, allowing spaces/dashes (incl. Unicode spaces) and mixed case.
 * Uses a precise start anchor and then grows the candidate minimally until a valid IBAN is found.
 */
public final class IbanDetector implements Detector {
    private static final String TYPE = "iban";
    private static final String MASK = "[MASKED_IBAN]";

    // Start anchor: country code + 2 digits, not preceded by a letter/digit
    private static final Pattern START = Pattern.compile("(?i)(?<![A-Z0-9])([A-Z]{2}\\d{2})");

    // Characters allowed inside an IBAN token (we will post-filter by length and MOD-97)
    private static boolean isIbanChar(int ch) {
        return Character.isLetterOrDigit(ch) || ch == '-' || Character.isWhitespace(ch);
    }

    // Known country lengths (subset is fine for tests)
    private static final Map<String, Integer> COUNTRY_LENGTH;

    static {
        Map<String, Integer> m = new HashMap<>();
        m.put("AL", 28);
        m.put("AD", 24);
        m.put("AT", 20);
        m.put("AZ", 28);
        m.put("BA", 20);
        m.put("BE", 16);
        m.put("BG", 22);
        m.put("BH", 22);
        m.put("BR", 29);
        m.put("CH", 21);
        m.put("CR", 22);
        m.put("CY", 28);
        m.put("CZ", 24);
        m.put("DE", 22);
        m.put("DK", 18);
        m.put("DO", 28);
        m.put("EE", 20);
        m.put("ES", 24);
        m.put("FI", 18);
        m.put("FO", 18);
        m.put("FR", 27);
        m.put("GB", 22);
        m.put("GE", 22);
        m.put("GI", 23);
        m.put("GL", 18);
        m.put("GR", 27);
        m.put("GT", 28);
        m.put("HR", 21);
        m.put("HU", 28);
        m.put("IE", 22);
        m.put("IL", 23);
        m.put("IQ", 23);
        m.put("IS", 26);
        m.put("IT", 27);
        m.put("JO", 30);
        m.put("KW", 30);
        m.put("KZ", 20);
        m.put("LB", 28);
        m.put("LC", 32);
        m.put("LI", 21);
        m.put("LT", 20);
        m.put("LU", 20);
        m.put("LV", 21);
        m.put("MC", 27);
        m.put("MD", 24);
        m.put("ME", 22);
        m.put("MK", 19);
        m.put("MR", 27);
        m.put("MT", 31);
        m.put("MU", 30);
        m.put("NL", 18);
        m.put("NO", 15);
        m.put("PK", 24);
        m.put("PL", 28);
        m.put("PS", 29);
        m.put("PT", 25);
        m.put("QA", 29);
        m.put("RO", 24);
        m.put("RS", 22);
        m.put("SA", 24);
        m.put("SC", 31);
        m.put("SE", 24);
        m.put("SI", 19);
        m.put("SK", 24);
        m.put("SM", 27);
        m.put("TL", 23);
        m.put("TN", 24);
        m.put("TR", 26);
        m.put("UA", 29);
        m.put("VG", 24);
        m.put("XK", 20);
        COUNTRY_LENGTH = Collections.unmodifiableMap(m);
    }

    @Override
    public DetectionResult detect(String s) {
        if (s == null || s.isEmpty()) return DetectionResult.empty();
        List<DetectionResult.Span> spans = new ArrayList<>();

        Matcher m = START.matcher(s);
        int searchFrom = 0;

        while (m.find(searchFrom)) {
            int start = m.start(1); // beginning of CC+2 digits
            int i = m.end(1); // we will grow from here (inclusive of separators/alnum)

            // Grow window over allowed chars
            int limit = s.length();
            int bestEnd = -1; // first end where we got a valid IBAN
            StringBuilder norm = new StringBuilder(40);
            // Seed normalization with the already matched "CCdd"
            norm.append(s, start, i);
            // normalize the seed (no spaces/dashes there anyway)
            String seed = norm.toString().replaceAll("[\\s\\p{Zs}-]+", "");
            norm.setLength(0);
            norm.append(seed);

            // Extend while characters are IBAN-friendly
            while (i < limit && isIbanChar(s.charAt(i))) {
                char ch = s.charAt(i);
                if (ch != '-' && !Character.isWhitespace(ch)) {
                    norm.append(Character.toUpperCase(ch));
                }
                // When normalized length hits [15..34], test plausibility and checksum
                int len = norm.length();
                if (len >= 15 && len <= 34) {
                    if (isPlausible(norm) && mod97Ok(norm)) {
                        bestEnd = i + 1; // substring end is exclusive
                        break; // stop at the *first* valid end → minimal span
                    }
                }
                // Guardrail: if we already exceeded 34 normalized chars, no point to continue
                if (len > 34) break;
                i++;
            }

            if (bestEnd != -1) {
                spans.add(new DetectionResult.Span(start, bestEnd, TYPE, MASK));
                searchFrom = bestEnd; // continue after this IBAN
            } else {
                // No valid IBAN found starting here → continue searching after the start
                searchFrom = m.end(1);
            }
        }

        return spans.isEmpty() ? DetectionResult.empty() : new DetectionResult(true, List.copyOf(spans));
    }

    private static boolean isPlausible(CharSequence iban) {
        if (iban.length() < 15 || iban.length() > 34) return false;
        char c0 = iban.charAt(0), c1 = iban.charAt(1);
        if (!Character.isLetter(c0) || !Character.isLetter(c1)) return false;
        String cc = ("" + Character.toUpperCase(c0) + Character.toUpperCase(c1));
        Integer expected = COUNTRY_LENGTH.get(cc);
        return expected == null || iban.length() == expected;
    }

    // ISO 13616 MOD-97
    private static boolean mod97Ok(CharSequence iban) {
        // move first 4 chars to the end
        StringBuilder sb = new StringBuilder(iban.length());
        sb.append(iban, 4, iban.length()).append(iban, 0, 4);

        int rem = 0;
        for (int k = 0; k < sb.length(); k++) {
            char ch = sb.charAt(k);
            if (ch >= '0' && ch <= '9') {
                rem = (rem * 10 + (ch - '0')) % 97;
            } else {
                int v = Character.toUpperCase(ch) - 'A' + 10;
                rem = (rem * 10 + (v / 10)) % 97;
                rem = (rem * 10 + (v % 10)) % 97;
            }
        }
        return rem == 1;
    }
}
