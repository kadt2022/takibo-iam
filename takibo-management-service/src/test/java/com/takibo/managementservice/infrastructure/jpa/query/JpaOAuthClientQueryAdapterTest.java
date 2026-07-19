package com.takibo.managementservice.infrastructure.jpa.query;

import com.takibo.managementservice.infrastructure.jpa.repository.OAuth2ClientJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaOAuthClientQueryAdapterTest {

    private static final UUID ORG = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OTHER_ORG = UUID.fromString("dddddddd-0000-0000-0000-000000000009");

    @Mock
    private OAuth2ClientJpaRepository clients;

    @InjectMocks
    private JpaOAuthClientQueryAdapter adapter;

    @Test
    void countsAllClientsOfTheOrganization_acrossItsSpaces() {
        // 2 clients répartis dans 2 Spaces de la même org : org_id est porté par
        // chaque ligne, donc countByOrgId les totalise sans parcourir les Spaces.
        when(clients.countByOrgId(ORG)).thenReturn(2L);

        assertThat(adapter.countClients(ORG)).isEqualTo(2L);
    }

    @Test
    void zeroForOrganizationWithoutClients() {
        when(clients.countByOrgId(ORG)).thenReturn(0L);

        assertThat(adapter.countClients(ORG)).isZero();
    }

    @Test
    void singleCountQuery_scopedToGivenOrganization() {
        when(clients.countByOrgId(ORG)).thenReturn(5L);

        adapter.countClients(ORG);

        // Une seule requête, strictement sur l'org donnée — un client d'une autre
        // organisation n'entre jamais dans le total.
        verify(clients).countByOrgId(ORG);
        verify(clients, never()).countByOrgId(OTHER_ORG);
    }

    @Test
    void countsOnly_neverLoadsClientsNorSecrets() {
        when(clients.countByOrgId(ORG)).thenReturn(1L);

        adapter.countClients(ORG);

        // Contrat : on compte, on ne charge jamais d'entité client (donc jamais
        // aucun hash de secret) pour compter.
        verify(clients, never()).findAll();
        verify(clients, never()).findById(any());
        verify(clients, never()).findByIdAndOrgIdAndSpaceId(any(), any(), any());
    }
}
