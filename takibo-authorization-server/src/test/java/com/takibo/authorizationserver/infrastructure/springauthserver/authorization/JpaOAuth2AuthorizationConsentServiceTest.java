package com.takibo.authorizationserver.infrastructure.springauthserver.authorization;

import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2AuthorizationConsentEntity;
import com.takibo.authorizationserver.infrastructure.jpa.repository.OAuth2AuthorizationConsentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JpaOAuth2AuthorizationConsentService} (TAS-GRANTS-02) : clé de lecture globale
 * {@code (registered_client_id, principal_name)}, jamais un identifiant de compte.
 */
class JpaOAuth2AuthorizationConsentServiceTest {

    private static final UUID ORG_ID = UUID.fromString("674b889c-4d4e-47bd-bdf6-972dc84f1b49");
    private static final UUID SPACE_ID = UUID.fromString("8932f9bc-0af0-4c64-94c8-abb0150c348b");
    private static final String REGISTERED_CLIENT_ID = "registered-client-1";

    private final OAuth2AuthorizationConsentRepository consents =
            mock(OAuth2AuthorizationConsentRepository.class);
    private final RegisteredClientRepository registeredClientRepository =
            mock(RegisteredClientRepository.class);

    private final JpaOAuth2AuthorizationConsentService service =
            new JpaOAuth2AuthorizationConsentService(consents, registeredClientRepository);

    @Test
    void given_a_new_consent_when_saved_then_it_carries_org_space_and_authorities_from_the_client() {
        RegisteredClient client = spaceClient();
        when(registeredClientRepository.findById(REGISTERED_CLIENT_ID)).thenReturn(client);
        when(consents.findByRegisteredClientIdAndPrincipalName(REGISTERED_CLIENT_ID, "user@takibo.test"))
                .thenReturn(Optional.empty());
        OAuth2AuthorizationConsent consent = OAuth2AuthorizationConsent
                .withId(REGISTERED_CLIENT_ID, "user@takibo.test")
                .authority(new SimpleGrantedAuthority("SCOPE_api.read"))
                .authority(new SimpleGrantedAuthority("SCOPE_api.write"))
                .build();

        service.save(consent);

        ArgumentCaptor<OAuth2AuthorizationConsentEntity> captor =
                ArgumentCaptor.forClass(OAuth2AuthorizationConsentEntity.class);
        verify(consents).save(captor.capture());
        OAuth2AuthorizationConsentEntity entity = captor.getValue();
        assertThat(entity.getOrgId()).isEqualTo(ORG_ID);
        assertThat(entity.getSpaceId()).isEqualTo(SPACE_ID);
        assertThat(entity.getRegisteredClientId()).isEqualTo(REGISTERED_CLIENT_ID);
        assertThat(entity.getPrincipalName()).isEqualTo("user@takibo.test");
        assertThat(entity.getPrincipalAccountId()).isNull();
        assertThat(entity.getSubjectType()).isEqualTo("HUMAN");
        assertThat(entity.getAuthorities().split(",")).containsExactlyInAnyOrder(
                "SCOPE_api.read", "SCOPE_api.write");
    }

    @Test
    void given_an_existing_consent_when_saved_again_then_the_same_row_is_reused() {
        RegisteredClient client = spaceClient();
        when(registeredClientRepository.findById(REGISTERED_CLIENT_ID)).thenReturn(client);
        UUID existingId = UUID.randomUUID();
        OAuth2AuthorizationConsentEntity existing = OAuth2AuthorizationConsentEntity.builder()
                .id(existingId)
                .orgId(ORG_ID)
                .spaceId(SPACE_ID)
                .registeredClientId(REGISTERED_CLIENT_ID)
                .subjectType("HUMAN")
                .principalName("user@takibo.test")
                .authorities("SCOPE_api.read")
                .build();
        when(consents.findByRegisteredClientIdAndPrincipalName(REGISTERED_CLIENT_ID, "user@takibo.test"))
                .thenReturn(Optional.of(existing));
        OAuth2AuthorizationConsent consent = OAuth2AuthorizationConsent
                .withId(REGISTERED_CLIENT_ID, "user@takibo.test")
                .authority(new SimpleGrantedAuthority("SCOPE_api.read"))
                .authority(new SimpleGrantedAuthority("SCOPE_api.write"))
                .build();

        service.save(consent);

        ArgumentCaptor<OAuth2AuthorizationConsentEntity> captor =
                ArgumentCaptor.forClass(OAuth2AuthorizationConsentEntity.class);
        verify(consents).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(existingId);
    }

    @Test
    void given_a_saved_consent_when_reloaded_by_client_and_principal_then_it_round_trips() {
        RegisteredClient client = spaceClient();
        when(registeredClientRepository.findById(REGISTERED_CLIENT_ID)).thenReturn(client);
        OAuth2AuthorizationConsentEntity entity = OAuth2AuthorizationConsentEntity.builder()
                .id(UUID.randomUUID())
                .orgId(ORG_ID)
                .spaceId(SPACE_ID)
                .registeredClientId(REGISTERED_CLIENT_ID)
                .subjectType("HUMAN")
                .principalName("user@takibo.test")
                .authorities("SCOPE_api.read,SCOPE_api.write")
                .build();
        when(consents.findByRegisteredClientIdAndPrincipalName(REGISTERED_CLIENT_ID, "user@takibo.test"))
                .thenReturn(Optional.of(entity));

        OAuth2AuthorizationConsent reloaded = service.findById(REGISTERED_CLIENT_ID, "user@takibo.test");

        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getRegisteredClientId()).isEqualTo(REGISTERED_CLIENT_ID);
        assertThat(reloaded.getPrincipalName()).isEqualTo("user@takibo.test");
        assertThat(reloaded.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("SCOPE_api.read", "SCOPE_api.write");
    }

    @Test
    void given_no_matching_consent_when_reloaded_then_null_is_returned() {
        when(consents.findByRegisteredClientIdAndPrincipalName(REGISTERED_CLIENT_ID, "nobody@takibo.test"))
                .thenReturn(Optional.empty());

        assertThat(service.findById(REGISTERED_CLIENT_ID, "nobody@takibo.test")).isNull();
    }

    @Test
    void given_the_registered_client_now_resolves_under_a_different_boundary_then_reload_fails_closed() {
        // Meme garde que JpaOAuth2AuthorizationService : un consentement sauvegarde pour
        // org-A/space-X ne doit pas se relire silencieusement si le meme registered_client_id
        // resout desormais sous org-B/space-Y -- un client repris pourrait sinon sauter
        // l'ecran de consentement pour un tenant qui n'a jamais consenti a rien.
        OAuth2AuthorizationConsentEntity entity = OAuth2AuthorizationConsentEntity.builder()
                .id(UUID.randomUUID())
                .orgId(ORG_ID)
                .spaceId(SPACE_ID)
                .registeredClientId(REGISTERED_CLIENT_ID)
                .subjectType("HUMAN")
                .principalName("user@takibo.test")
                .authorities("SCOPE_api.read")
                .build();
        when(consents.findByRegisteredClientIdAndPrincipalName(REGISTERED_CLIENT_ID, "user@takibo.test"))
                .thenReturn(Optional.of(entity));
        RegisteredClient movedClient = TestRegisteredClients
                .spaceClientBuilder(REGISTERED_CLIENT_ID, UUID.randomUUID(), UUID.randomUUID())
                .build();
        when(registeredClientRepository.findById(REGISTERED_CLIENT_ID)).thenReturn(movedClient);

        assertThatThrownBy(() -> service.findById(REGISTERED_CLIENT_ID, "user@takibo.test"))
                .isInstanceOf(DataRetrievalFailureException.class);
    }

    @Test
    void given_an_unresolvable_client_when_saving_then_it_fails_closed() {
        when(registeredClientRepository.findById(REGISTERED_CLIENT_ID)).thenReturn(null);
        OAuth2AuthorizationConsent consent = OAuth2AuthorizationConsent
                .withId(REGISTERED_CLIENT_ID, "user@takibo.test")
                .authority(new SimpleGrantedAuthority("SCOPE_api.read"))
                .build();

        assertThatThrownBy(() -> service.save(consent))
                .isInstanceOf(DataRetrievalFailureException.class);
    }

    @Test
    void given_a_consent_when_removed_then_the_matching_row_is_deleted() {
        UUID entityId = UUID.randomUUID();
        OAuth2AuthorizationConsentEntity entity = OAuth2AuthorizationConsentEntity.builder()
                .id(entityId)
                .registeredClientId(REGISTERED_CLIENT_ID)
                .subjectType("HUMAN")
                .principalName("user@takibo.test")
                .authorities("SCOPE_api.read")
                .build();
        when(consents.findByRegisteredClientIdAndPrincipalName(REGISTERED_CLIENT_ID, "user@takibo.test"))
                .thenReturn(Optional.of(entity));
        OAuth2AuthorizationConsent consent = OAuth2AuthorizationConsent
                .withId(REGISTERED_CLIENT_ID, "user@takibo.test")
                .authority(new SimpleGrantedAuthority("SCOPE_api.read"))
                .build();

        service.remove(consent);

        verify(consents).deleteById(entityId);
    }

    private static RegisteredClient spaceClient() {
        return TestRegisteredClients.spaceClientBuilder(REGISTERED_CLIENT_ID, ORG_ID, SPACE_ID).build();
    }
}
