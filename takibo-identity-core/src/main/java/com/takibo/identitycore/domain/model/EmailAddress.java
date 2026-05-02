package com.takibo.identitycore.domain.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.net.IDN;

/**
 * Value object representing an email address (canonical form).
 * - Trims spaces, Unicode NFC normalization
 * - Lower-cases local part + domain (case-insensitive semantics)
 * - IDN: domain normalized to ASCII (Punycode) for robust uniqueness
 * - Validates length and structure without DB-specific assumptions
 */
public record EmailAddress(String value) {

    // Simple, robust pattern AFTER canonicalization (domain ascii via IDN)
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@" +
                    "(?:[A-Za-z0-9-]+\\.)+[A-Za-z0-9-]{2,63}$"
    );

    // RFC-ish practical limits (common practice)
    private static final int MAX_TOTAL   = 320; // tolérant; souvent 254 en pratique
    private static final int MAX_LOCAL   = 64;
    private static final int MAX_DOMAIN  = 255;

    public EmailAddress {
        Objects.requireNonNull(value, "Email address cannot be null");

        // 1) Trim global
        String raw = value.trim();
        int at = raw.lastIndexOf('@');
        if (at <= 0 || at >= raw.length() - 1) {
            throw new IllegalArgumentException("Invalid email address format: " + value);
        }

        // 2) Découpe
        String local = raw.substring(0, at);
        String domain = raw.substring(at + 1);

        // 3) Unicode NFC (évite homoglyphes/variantes)
        local  = Normalizer.normalize(local, Normalizer.Form.NFC);
        domain = Normalizer.normalize(domain, Normalizer.Form.NFC);

        // 4) Case-insensitive (pratique courante côté systèmes auth)
        local  = local.toLowerCase(Locale.ROOT);
        domain = domain.toLowerCase(Locale.ROOT);

        // 5) IDN: domaine en ASCII (Punycode) pour une unicité robuste
        String domainAscii;
        try {
            domainAscii = IDN.toASCII(domain, IDN.USE_STD3_ASCII_RULES);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid internationalized domain: " + domain);
        }

        // 6) Recompose la forme canonique
        String canonical = local + "@" + domainAscii;

        // 7) Validation structure + tailles pratiques
        if (!EMAIL_PATTERN.matcher(canonical).matches()) {
            throw new IllegalArgumentException("Invalid email address format: " + value);
        }
        if (canonical.length() > MAX_TOTAL || local.length() > MAX_LOCAL || domainAscii.length() > MAX_DOMAIN) {
            throw new IllegalArgumentException("Email address too long: " + value);
        }

        // 8) Stocke la forme canonique (base des comparaisons/égalité)
        value = canonical;
    }

    /** Local part (already canonicalized). */
    public String localPart() {
        int at = value.indexOf('@');
        return value.substring(0, at);
    }

    /** Domain (IDN ASCII canonicalized). */
    public String domain() {
        int at = value.indexOf('@');
        return value.substring(at + 1);
    }
}
