package com.takibo.managementservice.application.common;

public final class TakiboCodeNormalizer {

    private TakiboCodeNormalizer() {}

    /**
     * Canonical normalization for org and space codes.
     * Output: lowercase-kebab-case, diacritics stripped, max 80 chars.
     * Mirrors the SQL: regexp_replace(regexp_replace(lower(code), '[^a-z0-9]+', '-', 'g'), '^-|-$', '', 'g')
     */
    public static String normalize(String input) {
        return Slugifier.slug(input);
    }

    /**
     * Normalize an organization code.
     * Throws if the result is shorter than 3 characters — org codes are strong
     * tenant boundaries and must be explicitly chosen.
     */
    public static String normalizeOrg(String input) {
        String result = normalize(input);
        if (result.length() < 3) {
            throw new IllegalArgumentException(
                "Organization code must be between 3 and 80 characters after normalization"
            );
        }
        return result;
    }

    /**
     * Normalize a space code.
     * If the result is shorter than 3 characters, pad with the given numeric suffix.
     * Callers provide the suffix so they control uniqueness (e.g. random or retry-derived).
     */
    public static String normalizeSpace(String input, int suffix) {
        String result = normalize(input);
        if (result.length() < 3) {
            result = result + "-" + suffix;
        }
        return result;
    }
}
