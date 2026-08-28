package com.takibo.authorizationserver.domain.keys;

import com.takibo.authorizationserver.domain.keys.model.GeneratedSigningKeyMaterial;
import com.takibo.authorizationserver.domain.keys.model.NewSigningKey;
import com.takibo.authorizationserver.domain.keys.port.SecretCipher;
import com.takibo.authorizationserver.domain.keys.port.SecretContext;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyMaterialGenerator;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Ce que l'amorçage et la rotation assemblent avant d'écrire (TAS-GRANTS-02A).
 * <p>
 * Le service ne fait que trois choses : demander une matière neuve au générateur, la chiffrer
 * sous le bon contexte, et confier l'activation au port d'écriture — {@link #initializeFirstIssuer()}
 * pour une installation vide, {@link SigningKeyRotationService#rotate} pour remplacer une
 * émettrice existante. Ces tests fixent chacune, sans base réelle ni bibliothèque
 * cryptographique — la persistance est couverte en intégration, la génération RSA dans
 * {@code RsaSigningKeyGeneratorTest}.
 */
@ExtendWith(MockitoExtension.class)
class SigningKeyRotationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T12:00:00Z");

    @Mock private SigningKeyMaterialGenerator generator;
    @Mock private SigningKeyWriter writer;
    @Mock private SecretCipher cipher;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final AtomicInteger kidSequence = new AtomicInteger();

    private SigningKeyRotationService service() {
        return new SigningKeyRotationService(generator, writer, cipher, clock);
    }

    private GeneratedSigningKeyMaterial aGeneratedMaterial() {
        String kid = "kid-" + kidSequence.incrementAndGet();
        return new GeneratedSigningKeyMaterial(
                kid, "RS256", "RSA", "sig",
                Map.of("kty", "RSA", "kid", kid),
                "{\"kty\":\"RSA\",\"kid\":\"" + kid + "\",\"d\":\"private-part\"}");
    }

    // ---------- Amorcage ----------

    @Test
    void given_no_active_issuer_then_initialize_first_issuer_activates_the_generated_key() {
        GeneratedSigningKeyMaterial material = aGeneratedMaterial();
        when(generator.generate()).thenReturn(material);
        when(cipher.encrypt(any(), any())).thenReturn("v1$k$sealed");

        String kid = service().initializeFirstIssuer();

        assertThat(kid).isEqualTo(material.kid());
        ArgumentCaptor<NewSigningKey> captured = ArgumentCaptor.forClass(NewSigningKey.class);
        verify(writer).activateFirstIssuer(captured.capture());
        assertThat(captured.getValue().kid()).isEqualTo(material.kid());
        assertThat(captured.getValue().privateKeyEncrypted()).isEqualTo("v1$k$sealed");
    }

    @Test
    void given_first_issuer_activation_then_the_rotation_write_path_is_never_called() {
        when(generator.generate()).thenReturn(aGeneratedMaterial());
        when(cipher.encrypt(any(), any())).thenReturn("v1$k$sealed");

        service().initializeFirstIssuer();

        verify(writer, never()).activateNewIssuer(any(), any());
    }

    // ---------- Rotation ----------

    @Test
    void given_a_rotation_then_the_activated_key_carries_the_generated_material() {
        GeneratedSigningKeyMaterial material = aGeneratedMaterial();
        when(generator.generate()).thenReturn(material);
        when(cipher.encrypt(any(), any())).thenReturn("v1$k$sealed");

        service().rotate(Duration.ofMinutes(10));

        ArgumentCaptor<NewSigningKey> captured = ArgumentCaptor.forClass(NewSigningKey.class);
        verify(writer).activateNewIssuer(captured.capture(), any());

        NewSigningKey key = captured.getValue();
        assertThat(key.alg()).isEqualTo(material.alg());
        assertThat(key.kty()).isEqualTo(material.kty());
        assertThat(key.keyUse()).isEqualTo(material.keyUse());
        assertThat(key.kid()).isEqualTo(material.kid());
        assertThat(key.publicJwkJson()).isEqualTo(material.publicJwkJson());
    }

    @Test
    void given_a_rotation_then_the_private_material_is_encrypted_under_the_new_key_id() {
        GeneratedSigningKeyMaterial material = aGeneratedMaterial();
        when(generator.generate()).thenReturn(material);
        when(cipher.encrypt(any(), any())).thenReturn("v1$k$sealed");

        service().rotate(Duration.ofMinutes(10));

        ArgumentCaptor<SecretContext> contextCaptor = ArgumentCaptor.forClass(SecretContext.class);
        ArgumentCaptor<String> plaintextCaptor = ArgumentCaptor.forClass(String.class);
        verify(cipher).encrypt(contextCaptor.capture(), plaintextCaptor.capture());

        assertThat(contextCaptor.getValue())
                .isEqualTo(SecretContext.signingKeyMaterial(material.kid()));
        // Le clair chiffre est exactement la matiere fournie par le generateur : le service
        // ne la transforme pas, il la chiffre.
        assertThat(plaintextCaptor.getValue()).isEqualTo(material.privateKeyMaterial());
    }

    @Test
    void given_a_rotation_then_the_encrypted_output_is_what_gets_persisted() {
        when(generator.generate()).thenReturn(aGeneratedMaterial());
        when(cipher.encrypt(any(), any())).thenReturn("v1$k$exactly-this-sealed-value");

        service().rotate(Duration.ofMinutes(10));

        ArgumentCaptor<NewSigningKey> captured = ArgumentCaptor.forClass(NewSigningKey.class);
        verify(writer).activateNewIssuer(captured.capture(), any());

        assertThat(captured.getValue().privateKeyEncrypted())
                .isEqualTo("v1$k$exactly-this-sealed-value");
    }

    @Test
    void given_an_overlap_then_the_retirement_instant_is_now_plus_that_overlap() {
        when(generator.generate()).thenReturn(aGeneratedMaterial());
        when(cipher.encrypt(any(), any())).thenReturn("v1$k$sealed");

        service().rotate(Duration.ofMinutes(10));

        verify(writer).activateNewIssuer(any(), eq(NOW.plus(Duration.ofMinutes(10))));
    }

    @Test
    void given_two_successive_rotations_then_each_uses_its_own_generated_material() {
        when(generator.generate())
                .thenReturn(aGeneratedMaterial())
                .thenReturn(aGeneratedMaterial());
        when(cipher.encrypt(any(), any())).thenReturn("v1$k$sealed");

        String first = service().rotate(Duration.ofMinutes(10));
        String second = service().rotate(Duration.ofMinutes(10));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void given_no_overlap_then_rotation_is_refused() {
        SigningKeyRotationService rotation = service();

        assertThatThrownBy(() -> rotation.rotate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SIGNING_KEY_ROTATION_REQUIRES_A_POSITIVE_OVERLAP");
    }

    @Test
    void given_a_negative_overlap_then_rotation_is_refused() {
        SigningKeyRotationService rotation = service();

        assertThatThrownBy(() -> rotation.rotate(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SIGNING_KEY_ROTATION_REQUIRES_A_POSITIVE_OVERLAP");
    }

    @Test
    void given_a_zero_overlap_then_rotation_is_refused() {
        // Un chevauchement nul retirerait l'ancienne emettrice avant l'expiration des JWT
        // encore valides qu'elle a signes : Capitaine Pi, revue de PR #54.
        SigningKeyRotationService rotation = service();

        assertThatThrownBy(() -> rotation.rotate(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SIGNING_KEY_ROTATION_REQUIRES_A_POSITIVE_OVERLAP");
    }

    @Test
    void given_a_refused_overlap_then_nothing_is_generated_nor_written() {
        // La validation precede toute generation : un appel refuse ne doit produire aucune
        // matiere ni la jeter apres coup.
        SigningKeyRotationService rotation = service();

        assertThatThrownBy(() -> rotation.rotate(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(generator, writer, cipher);
    }
}
