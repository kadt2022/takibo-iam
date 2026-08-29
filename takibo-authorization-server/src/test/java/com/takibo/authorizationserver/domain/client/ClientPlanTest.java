package com.takibo.authorizationserver.domain.client;

import com.takibo.authorizationserver.infrastructure.springauthserver.token.TakiboTokenClaims;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Amarre {@link ClientPlan} au canon des claims (TAS-GRANTS-01).
 * <p>
 * Le domaine fige ses propres chaines plutot que d'importer les constantes de la couche
 * d'infrastructure : la dependance irait dans le mauvais sens. Le prix de ce choix est
 * qu'une divergence deviendrait silencieuse — un plan resolu correctement produirait un
 * claim que les consommateurs ne reconnaitraient pas.
 * <p>
 * Ces tests sont l'amarre. Ils appartiennent volontairement au paquet du domaine et ne
 * regardent vers {@link TakiboTokenClaims} que pour verifier l'egalite.
 */
class ClientPlanTest {

    @Test
    void given_each_plan_then_its_claim_value_matches_the_canonical_scope_level() {
        assertThat(ClientPlan.PLATFORM.claimValue()).isEqualTo(TakiboTokenClaims.SCOPE_PLATFORM);
        assertThat(ClientPlan.ORGANIZATION.claimValue())
                .isEqualTo(TakiboTokenClaims.SCOPE_ORGANIZATION);
        assertThat(ClientPlan.SPACE.claimValue()).isEqualTo(TakiboTokenClaims.SCOPE_SPACE);
    }

    @Test
    void given_each_plan_then_its_tenant_source_matches_the_canonical_value() {
        // Un client PLATFORM tient son autorite de l'instance, pas d'un enregistrement
        // situe : sa provenance differe, et c'est ce que le claim doit dire.
        assertThat(ClientPlan.PLATFORM.tenantSource()).isEqualTo(TakiboTokenClaims.SOURCE_PLATFORM);
        assertThat(ClientPlan.ORGANIZATION.tenantSource())
                .isEqualTo(TakiboTokenClaims.SOURCE_OAUTH2_CLIENT);
        assertThat(ClientPlan.SPACE.tenantSource())
                .isEqualTo(TakiboTokenClaims.SOURCE_OAUTH2_CLIENT);
    }

    @Test
    void given_each_plan_then_the_required_frontier_is_stated_once() {
        assertThat(ClientPlan.PLATFORM.requiresOrganization()).isFalse();
        assertThat(ClientPlan.PLATFORM.requiresSpace()).isFalse();

        assertThat(ClientPlan.ORGANIZATION.requiresOrganization()).isTrue();
        assertThat(ClientPlan.ORGANIZATION.requiresSpace()).isFalse();

        assertThat(ClientPlan.SPACE.requiresOrganization()).isTrue();
        assertThat(ClientPlan.SPACE.requiresSpace()).isTrue();
    }
}
