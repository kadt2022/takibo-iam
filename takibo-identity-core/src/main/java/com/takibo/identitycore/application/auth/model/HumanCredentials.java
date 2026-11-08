package com.takibo.identitycore.application.auth.model;

/**
 * Credentials humains bruts, tels que saisis. Ils ne portent aucune frontière : l'Organization
 * vient toujours d'un {@link TrustedOrganizationContext} établi côté serveur.
 */
public record HumanCredentials(String email, String password) {

    /** Le mot de passe n'apparaît jamais dans un journal, une trace ou une exception. */
    @Override
    public String toString() {
        return "HumanCredentials[email=" + email + ", password=***]";
    }
}
