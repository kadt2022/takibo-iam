package com.takibo.identitycore.application.auth.service;

import com.takibo.identitycore.application.auth.model.HumanCredentials;
import com.takibo.identitycore.application.auth.model.TrustedOrganizationContext;
import com.takibo.identitycore.application.auth.model.VerifiedHuman;
import com.takibo.identitycore.application.auth.port.HumanAccessTokenIssuer;
import com.takibo.identitycore.application.auth.port.HumanAuthenticationCase;
import com.takibo.identitycore.application.rbac.effective.port.in.EffectiveRbacQueryCase;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * TAS-GRANTS-03-A : le cœur d'authentification humaine est réutilisable par le login REST
 * comme par la future session navigateur TAS. Il ne connaît donc ni le web, ni Spring
 * Authorization Server, ni l'émission d'un token, ni le calcul du pouvoir effectif.
 */
class HumanAuthenticationBoundaryTest {

    private static final JavaClasses CORE = new ClassFileImporter().importClasses(
            HumanAuthenticationCase.class, HumanAuthenticationService.class,
            TrustedOrganizationContext.class, HumanCredentials.class, VerifiedHuman.class);

    @Test
    void the_core_depends_neither_on_the_web_nor_on_the_authorization_server() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web..", "org.springframework.security.web..",
                        "org.springframework.security.oauth2..", "jakarta.servlet..",
                        "com.takibo.authorizationserver..", "com.takibo.securitymanagement..")
                .check(CORE);
    }

    @Test
    void the_core_neither_signs_a_token_nor_computes_effective_power() {
        noClasses().should().dependOnClassesThat(describe("the token issuer or the effective RBAC",
                        type -> type.isEquivalentTo(HumanAccessTokenIssuer.class)
                                || type.isEquivalentTo(EffectiveRbacQueryCase.class)))
                .check(CORE);
    }
}
