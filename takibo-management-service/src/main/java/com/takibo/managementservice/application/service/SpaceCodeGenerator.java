package com.takibo.managementservice.application.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class SpaceCodeGenerator {

    public String normalizeOrGenerate(String requestedCode, String name) {
        String base = (requestedCode == null || requestedCode.isBlank()) ? name : requestedCode;
        String normalized = normalize(base);
        if (normalized.length() < 3) {
            normalized = normalized + "-" + randomSuffix();
        }
        return normalized;
    }

    public String nextCandidate(String baseOrCandidate) {
        return baseOrCandidate + "-" + randomSuffix();
    }

    private String normalize(String input) {
        String s = Normalizer.normalize(input, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9]+", "-")
            .replaceAll("(^-|-$)", "");

        if (s.length() > 30) {
            s = s.substring(0, 30).replaceAll("(-+$)", "");
        }
        return s;
    }

    private String randomSuffix() {
        int n = ThreadLocalRandom.current().nextInt(1000, 9999);
        return String.valueOf(n);
    }
}
