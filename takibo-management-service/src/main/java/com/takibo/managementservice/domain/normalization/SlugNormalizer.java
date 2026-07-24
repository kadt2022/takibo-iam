package com.takibo.managementservice.domain.normalization;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class SlugNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern BOUNDARY_HYPHENS = Pattern.compile("(^-|-$)");

    private SlugNormalizer() {
    }

    public static String normalize(String rawValue) {
        return Optional.ofNullable(rawValue)
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .map(value -> Normalizer.normalize(value, Normalizer.Form.NFD))
                .map(value -> COMBINING_MARKS.matcher(value).replaceAll(""))
                .map(value -> NON_ALPHANUMERIC.matcher(value).replaceAll("-"))
                .map(value -> BOUNDARY_HYPHENS.matcher(value).replaceAll(""))
                .orElse("");
    }
}
