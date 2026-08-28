package com.takibo.authorizationserver.infrastructure.keys;

import com.takibo.authorizationserver.domain.keys.model.KeyStatus;
import com.takibo.authorizationserver.domain.keys.model.NewSigningKey;
import com.takibo.authorizationserver.domain.keys.model.TasSigningKey;
import com.takibo.authorizationserver.infrastructure.jpa.entity.TasSigningKeyEntity;
import com.takibo.authorizationserver.infrastructure.jpa.repository.TasSigningKeyJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Traduction entité-domaine de {@link JpaSigningKeyRepository} (TAS-GRANTS-02A).
 * <p>
 * Le repository Spring Data sous-jacent est simulé : ce test ne vérifie pas les requêtes
 * JPQL elles-mêmes — {@code SigningKeyRepositoryIntegrationTest} et
 * {@code SigningKeyRotationIntegrationTest} le font sur PostgreSQL réel — mais que
 * l'adaptateur construit correctement l'entité à écrire et traduit correctement l'entité lue.
 */
@ExtendWith(MockitoExtension.class)
class JpaSigningKeyRepositoryTest {

    private static final Instant AT = Instant.parse("2026-08-27T12:00:00Z");
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock private TasSigningKeyJpaRepository jpa;

    private JpaSigningKeyRepository repository() {
        return new JpaSigningKeyRepository(jpa);
    }

    // ---------- Lecture ----------

    @Test
    void given_an_active_issuer_entity_then_it_is_translated_to_the_domain_record() {
        when(jpa.findActivePlatformIssuer(AT.atOffset(ZoneOffset.UTC)))
                .thenReturn(Optional.of(anEntity()));

        Optional<TasSigningKey> result = repository().findActivePlatformIssuer(AT);

        assertThat(result).isPresent();
        TasSigningKey key = result.get();
        assertThat(key.id()).isEqualTo(ID);
        assertThat(key.orgId()).isNull();
        assertThat(key.kid()).isEqualTo("kid-1");
        assertThat(key.alg()).isEqualTo("RS256");
        assertThat(key.kty()).isEqualTo("RSA");
        assertThat(key.keyUse()).isEqualTo("sig");
        assertThat(key.issuer()).isTrue();
        assertThat(key.status()).isEqualTo(KeyStatus.ACTIVE);
        assertThat(key.publicJwkJson()).containsEntry("kty", "RSA");
        assertThat(key.privateKeyEncrypted()).isEqualTo("v1$kid-1$sealed");
    }

    @Test
    void given_no_active_issuer_then_the_read_side_returns_empty() {
        when(jpa.findActivePlatformIssuer(any())).thenReturn(Optional.empty());

        assertThat(repository().findActivePlatformIssuer(AT)).isEmpty();
    }

    @Test
    void given_publishable_entities_then_each_is_translated() {
        when(jpa.findPublishable(AT.atOffset(ZoneOffset.UTC)))
                .thenReturn(List.of(anEntity()));

        List<TasSigningKey> result = repository().findPublishable(AT);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).kid()).isEqualTo("kid-1");
    }

    @Test
    void given_no_instant_then_read_operations_are_refused() {
        JpaSigningKeyRepository repo = repository();

        assertThatThrownBy(() -> repo.findActivePlatformIssuer(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SIGNING_KEY_LOOKUP_REQUIRES_AN_INSTANT");
        assertThatThrownBy(() -> repo.findPublishable(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SIGNING_KEY_LOOKUP_REQUIRES_AN_INSTANT");
    }

    // ---------- Ecriture ----------

    @Test
    void given_a_new_key_then_it_is_persisted_as_an_active_platform_issuer() {
        NewSigningKey newKey = aNewSigningKey();

        repository().activateNewIssuer(newKey, AT);

        ArgumentCaptor<TasSigningKeyEntity> captor = ArgumentCaptor.forClass(TasSigningKeyEntity.class);
        verify(jpa).save(captor.capture());

        TasSigningKeyEntity saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOrgId()).isNull();
        assertThat(saved.getKid()).isEqualTo("kid-new");
        assertThat(saved.getAlg()).isEqualTo("RS256");
        assertThat(saved.getKty()).isEqualTo("RSA");
        assertThat(saved.getKeyUse()).isEqualTo("sig");
        assertThat(saved.isIssuer()).isTrue();
        assertThat(saved.getStatus()).isEqualTo(KeyStatus.ACTIVE);
        assertThat(saved.getPublicJwkJson()).isEqualTo(newKey.publicJwkJson());
        assertThat(saved.getPrivateKeyEncrypted()).isEqualTo(newKey.privateKeyEncrypted());
    }

    @Test
    void given_a_new_key_then_the_current_issuer_is_retired_with_the_given_expiry() {
        repository().activateNewIssuer(aNewSigningKey(), AT);

        verify(jpa).retireCurrentPlatformIssuer(AT.atOffset(ZoneOffset.UTC));
    }

    @Test
    void given_two_distinct_activations_then_each_produces_a_distinct_persisted_identifier() {
        JpaSigningKeyRepository repo = repository();

        repo.activateNewIssuer(aNewSigningKey(), AT);
        repo.activateNewIssuer(aNewSigningKey(), AT);

        ArgumentCaptor<TasSigningKeyEntity> captor = ArgumentCaptor.forClass(TasSigningKeyEntity.class);
        verify(jpa, times(2)).save(captor.capture());

        List<TasSigningKeyEntity> saved = captor.getAllValues();
        assertThat(saved.get(0).getId()).isNotEqualTo(saved.get(1).getId());
    }

    @Test
    void given_no_new_key_then_activation_is_refused() {
        JpaSigningKeyRepository repo = repository();

        assertThatThrownBy(() -> repo.activateNewIssuer(null, AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SIGNING_KEY_ACTIVATION_REQUIRES_A_NEW_KEY");
    }

    @Test
    void given_no_retirement_instant_then_activation_is_refused() {
        JpaSigningKeyRepository repo = repository();
        NewSigningKey newKey = aNewSigningKey();

        assertThatThrownBy(() -> repo.activateNewIssuer(newKey, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SIGNING_KEY_LOOKUP_REQUIRES_AN_INSTANT");
    }

    @Test
    void given_more_than_one_issuer_retired_then_activation_is_refused() {
        // Ne devrait jamais arriver : garde-fou si l'invariant de l'index unique etait un
        // jour contourne. Mieux vaut echouer ici qu'activer une cle sur une base incoherente.
        when(jpa.retireCurrentPlatformIssuer(any())).thenReturn(2);
        JpaSigningKeyRepository repo = repository();
        NewSigningKey newKey = aNewSigningKey();

        assertThatThrownBy(() -> repo.activateNewIssuer(newKey, AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MORE_THAN_ONE_PLATFORM_ISSUER_WAS_RETIRED");
    }

    @Test
    void given_zero_or_one_issuer_retired_then_activation_proceeds() {
        when(jpa.retireCurrentPlatformIssuer(any())).thenReturn(0);
        repository().activateNewIssuer(aNewSigningKey(), AT);
        verify(jpa).save(any());
    }

    // ---------- Fixtures ----------

    private static NewSigningKey aNewSigningKey() {
        return new NewSigningKey(
                "kid-new", "RS256", "RSA", "sig",
                Map.of("kty", "RSA", "kid", "kid-new"),
                "v1$kid-new$sealed");
    }

    private static TasSigningKeyEntity anEntity() {
        OffsetDateTime now = AT.atOffset(ZoneOffset.UTC);
        return TasSigningKeyEntity.builder()
                .id(ID)
                .orgId(null)
                .kid("kid-1")
                .alg("RS256")
                .kty("RSA")
                .keyUse("sig")
                .issuer(true)
                .status(KeyStatus.ACTIVE)
                .publicJwkJson(Map.of("kty", "RSA", "kid", "kid-1"))
                .privateKeyEncrypted("v1$kid-1$sealed")
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
