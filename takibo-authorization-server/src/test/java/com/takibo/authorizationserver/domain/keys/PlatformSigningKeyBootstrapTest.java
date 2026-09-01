package com.takibo.authorizationserver.domain.keys;

import com.takibo.authorizationserver.domain.keys.model.GeneratedSigningKeyMaterial;
import com.takibo.authorizationserver.domain.keys.model.KeyStatus;
import com.takibo.authorizationserver.domain.keys.model.NewSigningKey;
import com.takibo.authorizationserver.domain.keys.model.TasSigningKey;
import com.takibo.authorizationserver.domain.keys.port.SecretCipher;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyMaterialGenerator;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyRepository;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Décision d'amorçage de la première clé de plateforme (TAS-KEYS-BOOTSTRAP-01).
 * <p>
 * Quatre situations, et ce qui les sépare est le cœur du récit : une installation vierge
 * s'amorce, une installation déjà pourvue ne fait rien, une installation dont l'histoire des
 * clés existe sans émettrice active refuse de démarrer, et une course perdue repart avec la
 * clé de la gagnante.
 * <p>
 * La preuve que la course est réellement arbitrée par PostgreSQL vit dans
 * {@code SigningKeyBootstrapIntegrationTest} : ici, l'écriture est simulée, et ce sont les
 * conséquences de son verdict qui sont vérifiées.
 */
@ExtendWith(MockitoExtension.class)
class PlatformSigningKeyBootstrapTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    @Mock private SigningKeyRepository keys;
    @Mock private SigningKeyWriter writer;
    @Mock private SigningKeyMaterialGenerator generator;
    @Mock private SecretCipher cipher;

    private PlatformSigningKeyBootstrap bootstrap() {
        return new PlatformSigningKeyBootstrap(
                keys, writer, generator, cipher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void given_an_empty_installation_then_a_first_issuer_is_created() {
        when(keys.findActivePlatformIssuer(NOW)).thenReturn(Optional.empty());
        when(keys.hasPlatformKeyHistory()).thenReturn(false);
        generatorProduces("kid-fresh");
        when(writer.tryActivateFirstIssuer(any())).thenReturn(true);

        PlatformSigningKeyBootstrap.Outcome outcome = bootstrap().ensurePlatformIssuer();

        assertThat(outcome.kid()).isEqualTo("kid-fresh");
        assertThat(outcome.created()).isTrue();
    }

    @Test
    void given_an_active_issuer_then_nothing_is_written_and_its_kid_is_kept() {
        when(keys.findActivePlatformIssuer(NOW)).thenReturn(Optional.of(anActiveKey("kid-existing")));

        PlatformSigningKeyBootstrap.Outcome outcome = bootstrap().ensurePlatformIssuer();

        assertThat(outcome.kid()).isEqualTo("kid-existing");
        assertThat(outcome.created()).isFalse();
        // Ni écriture, ni même génération : un redémarrage ne doit pas consommer d'entropie
        // ni laisser de clé orpheline derrière lui.
        verify(writer, never()).tryActivateFirstIssuer(any());
        verify(generator, never()).generate();
    }

    @Test
    void given_a_key_history_without_an_active_issuer_then_the_startup_is_refused() {
        when(keys.findActivePlatformIssuer(NOW)).thenReturn(Optional.empty());
        when(keys.hasPlatformKeyHistory()).thenReturn(true);

        assertThatThrownBy(() -> bootstrap().ensurePlatformIssuer())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PLATFORM_SIGNING_KEY_HISTORY_WITHOUT_ACTIVE_ISSUER");

        // Le point du récit : ne pas « réparer » une base incohérente en fabriquant une clé.
        verify(writer, never()).tryActivateFirstIssuer(any());
        verify(generator, never()).generate();
    }

    @Test
    void given_a_lost_race_then_the_winner_kid_is_adopted() {
        when(keys.findActivePlatformIssuer(NOW))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(anActiveKey("kid-winner")));
        when(keys.hasPlatformKeyHistory()).thenReturn(false);
        generatorProduces("kid-loser");
        when(writer.tryActivateFirstIssuer(any())).thenReturn(false);

        PlatformSigningKeyBootstrap.Outcome outcome = bootstrap().ensurePlatformIssuer();

        assertThat(outcome.kid()).isEqualTo("kid-winner");
        assertThat(outcome.created()).isFalse();
    }

    @Test
    void given_a_lost_race_but_no_issuer_to_be_found_then_the_startup_is_refused() {
        // Ne devrait pas arriver : l'insertion en conflit attend la fin de la transaction
        // gagnante avant de rendre zéro. Si cela survenait quand même, mieux vaut l'échec net
        // qu'un démarrage sans clé de signature.
        when(keys.findActivePlatformIssuer(NOW)).thenReturn(Optional.empty());
        when(keys.hasPlatformKeyHistory()).thenReturn(false);
        generatorProduces("kid-loser");
        when(writer.tryActivateFirstIssuer(any())).thenReturn(false);

        assertThatThrownBy(() -> bootstrap().ensurePlatformIssuer())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PLATFORM_SIGNING_KEY_BOOTSTRAP_LOST_ITS_RACE_AND_FOUND_NO_ISSUER");
    }

    @Test
    void given_a_created_key_then_its_private_material_is_sealed_before_being_written() {
        when(keys.findActivePlatformIssuer(NOW)).thenReturn(Optional.empty());
        when(keys.hasPlatformKeyHistory()).thenReturn(false);
        generatorProduces("kid-fresh");
        when(writer.tryActivateFirstIssuer(any())).thenReturn(true);

        bootstrap().ensurePlatformIssuer();

        org.mockito.ArgumentCaptor<NewSigningKey> captor =
                org.mockito.ArgumentCaptor.forClass(NewSigningKey.class);
        verify(writer).tryActivateFirstIssuer(captor.capture());
        assertThat(captor.getValue().privateKeyEncrypted()).isEqualTo("sealed:private-kid-fresh");
    }

    // ---------- Fixtures ----------

    private void generatorProduces(String kid) {
        when(generator.generate()).thenReturn(new GeneratedSigningKeyMaterial(
                kid, "RS256", "RSA", "sig",
                Map.of("kty", "RSA", "kid", kid),
                "private-" + kid));
        when(cipher.encrypt(any(), any()))
                .thenAnswer(call -> "sealed:" + call.getArgument(1, String.class));
    }

    private static TasSigningKey anActiveKey(String kid) {
        OffsetDateTime now = NOW.atOffset(ZoneOffset.UTC);
        return new TasSigningKey(
                UUID.randomUUID(), null, kid, "RS256", "RSA", "sig", true,
                KeyStatus.ACTIVE, Map.of("kty", "RSA", "kid", kid), "sealed",
                null, null, null, now, now);
    }
}
