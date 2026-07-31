package com.takibo.identitycore.application.auth.model;

/**
 * Provenance réelle du contexte porté par un token humain.
 *
 * <p>La valeur est choisie par TIS-CORE puis transmise telle quelle à TAS. Elle
 * décrit comment la frontière du token a été établie ; TAS ne la déduit pas.</p>
 */
public enum HumanTokenSource {

    ORGANIZATION_LOGIN("human_login"),
    SPACE_SELECTION("human_space_selection");

    private final String claimValue;

    HumanTokenSource(String claimValue) {
        this.claimValue = claimValue;
    }

    public String claimValue() {
        return claimValue;
    }
}
