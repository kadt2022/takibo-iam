package com.takibo.authorizationserver.domain.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Déduction de la frontière d'un client à partir de sa seule paire (orgId, spaceId)
 * (TMS-OAUTH-CLIENT-BOUNDARY-01).
 * <p>
 * Le plan n'est pas stocké : aucune colonne {@code plan} n'existe dans {@code oauth2_clients},
 * et c'est délibéré. Une valeur stockée pourrait un jour contredire la frontière réelle portée
 * par les deux colonnes ; déduite, elle ne le peut pas.
 */
class ClientPlanBoundaryTest {

    private static final UUID ORG = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000a");
    private static final UUID SPACE = UUID.fromString("bbbbbbbb-0000-0000-0000-00000000000b");

    @Test
    void given_no_organization_and_no_space_then_the_client_is_platform() {
        assertThat(ClientPlan.of(null, null)).isEqualTo(ClientPlan.PLATFORM);
    }

    @Test
    void given_an_organization_without_a_space_then_the_client_is_organization() {
        assertThat(ClientPlan.of(ORG, null)).isEqualTo(ClientPlan.ORGANIZATION);
    }

    @Test
    void given_both_an_organization_and_a_space_then_the_client_is_space() {
        assertThat(ClientPlan.of(ORG, SPACE)).isEqualTo(ClientPlan.SPACE);
    }

    @Test
    void given_a_space_without_an_organization_then_it_fails_instead_of_guessing() {
        // La base l'interdit par ck_oauth2_clients_boundary. Cette garde couvre le cas ou une
        // ligne y aurait echappe — restauration partielle, migration manuelle, contrainte
        // desactivee : elle leve plutot que de produire un plan approximatif.
        assertThatThrownBy(() -> ClientPlan.of(null, SPACE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLIENT_BOUNDARY_INCONSISTENT");
    }

    @Test
    void given_each_plan_then_its_own_requirements_agree_with_the_deduction() {
        // Coherence interne : ce que le plan exige doit correspondre a la forme qui le produit.
        assertThat(ClientPlan.of(null, null).requiresOrganization()).isFalse();
        assertThat(ClientPlan.of(ORG, null).requiresOrganization()).isTrue();
        assertThat(ClientPlan.of(ORG, null).requiresSpace()).isFalse();
        assertThat(ClientPlan.of(ORG, SPACE).requiresSpace()).isTrue();
    }
}
