package com.takibo.managementservice.application.service;

import com.takibo.managementservice.domain.policy.SpaceCodePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class SpaceCodeGenerator {

    private final SpaceCodePolicy spaceCodePolicy;

    public String generateInitialCode(
            String requestedCode,
            String spaceName
    ) {
        return spaceCodePolicy.normalizeCandidateCode(
                spaceCodePolicy.resolveBaseCode(requestedCode, spaceName),
                randomSuffix()
        );
    }

    public String generateNextCandidate(String currentCandidateCode) {
        return currentCandidateCode + "-" + randomSuffix();
    }

    private int randomSuffix() {
        return ThreadLocalRandom.current().nextInt(1000, 9999);
    }
}
