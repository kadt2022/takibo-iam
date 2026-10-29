package com.takibo.securitymanagement.domain.service.policy;

import com.takibo.securitymanagement.domain.model.Effect;
import com.takibo.securitymanagement.domain.model.PolicyDecision;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/** Construction des décisions et évaluation ordonnée des exigences d'une surface. */
final class Decisions {

    static final String POL_ORG_MISMATCH = "POL_ORG_MISMATCH";
    static final String ORG_MISMATCH_REASON = "Token organization does not match the target organization";

    private Decisions() {
    }

    static PolicyDecision deny(String policyId, String reason) {
        return decision(Effect.DENY, policyId, reason);
    }

    static PolicyDecision permit(String policyId, String reason) {
        return decision(Effect.PERMIT, policyId, reason);
    }

    /**
     * Exigence d'une surface : tant que sa condition tient, l'évaluation passe à la suivante ;
     * sinon elle rend son refus. Les conditions sont évaluées paresseusement, dans l'ordre :
     * une condition peut donc supposer que les précédentes tiennent.
     */
    record Requirement(BooleanSupplier condition, String policyId, String reason) {

        static Requirement require(BooleanSupplier condition, String policyId, String reason) {
            return new Requirement(condition, policyId, reason);
        }

        private boolean unmet() {
            return !condition.getAsBoolean();
        }
    }

    /** Motif d'autorisation : accordé quand sa condition tient. Évalué paresseusement, dans l'ordre. */
    record Grant(BooleanSupplier condition, String reason) {

        static Grant grant(BooleanSupplier condition, String reason) {
            return new Grant(condition, reason);
        }

        private boolean applies() {
            return condition.getAsBoolean();
        }
    }

    /** Autorisation par le premier motif qui s'applique, vide si aucun ne s'applique. */
    static Optional<PolicyDecision> firstGrant(String policyId, Grant... grants) {
        return Arrays.stream(grants)
                .filter(Grant::applies)
                .findFirst()
                .map(applicable -> permit(policyId, applicable.reason()));
    }

    /** Refus de la première exigence non satisfaite, vide si toutes tiennent. */
    static Optional<PolicyDecision> firstUnmet(Requirement... requirements) {
        return Arrays.stream(requirements)
                .filter(Requirement::unmet)
                .findFirst()
                .map(unmet -> deny(unmet.policyId(), unmet.reason()));
    }

    private static PolicyDecision decision(Effect effect, String policyId, String reason) {
        return PolicyDecision.builder()
                .effect(effect)
                .policyId(policyId)
                .reason(reason)
                .build();
    }
}
