package com.takibo.managementservice.application.service;

import com.takibo.managementservice.domain.service.TakiboCodeNormalizer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class SpaceCodeGenerator {

    public String normalizeOrGenerate(String requestedCode, String name) {
        String base = (requestedCode == null || requestedCode.isBlank()) ? name : requestedCode;
        int suffix = randomSuffix();
        return TakiboCodeNormalizer.normalizeSpace(normalize(base), suffix);
    }

    public String nextCandidate(String baseOrCandidate) {
        return baseOrCandidate + "-" + randomSuffix();
    }

    private String normalize(String input) {
        String s = TakiboCodeNormalizer.normalize(input);
        if (s.length() > 30) {
            s = s.substring(0, 30).replaceAll("-+$", "");
        }
        return s;
    }

    private int randomSuffix() {
        return ThreadLocalRandom.current().nextInt(1000, 9999);
    }
}
