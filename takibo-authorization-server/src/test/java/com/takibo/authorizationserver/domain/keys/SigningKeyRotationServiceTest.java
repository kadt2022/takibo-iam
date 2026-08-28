package com.takibo.authorizationserver.domain.keys;

import com.nimbusds.jose.jwk.JWK;
import com.takibo.authorizationserver.domain.keys.model.NewSigningKey;
import com.takibo.authorizationserver.domain.keys.port.SecretCipher;
import com.takibo.authorizationserver.domain.keys.port.SecretContext;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ce que la rotation assemble avant d'écrire (TAS-GRANTS-02A).
 * <p>
 * Le service ne fait que trois choses : générer une matière neuve, la chiffrer sous le bon
 * contexte, et confier l'activation au port d'écriture. Ces tests fixent chacune, sans base
 * réelle — la persistance elle-même est couverte en intégration.
 */
@ExtendWith(MockitoExtension.class)
class SigningKeyRotationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T12:00:00Z");

    @Mock private SigningKeyWriter writer;
    @Mock private SecretCipher cipher;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private SigningKeyRotationService service() {
        return new SigningKeyRotationService(writer, cipher, clock);
    }

    @Test
    void given_a_rotation_then_the_activated_key_is_rsa_2048_with_signature_use() {
        when(cipher.encrypt(any(), any())).thenReturn("v1$k$sealed");

        service().rotate(Duration.ofMinutes(10));

        ArgumentCaptor<NewSigningKey> captured = ArgumentCaptor.forClass(NewSigningKey.class);
        verify(writer).activateNewIssuer(captured.capture(), any());

        NewSigningKey key = captured.getValue();
        assertThat(key.alg()).isEqualTo("RS256");
        assertThat(key.kty()).isEqualTo("RSA");
        assertThat(key.keyUse()).isEqualTo("sig");
        assertThat(key.kid()).isNotBlank();
    }

    @Test
    void given_a_rotation_then_the_public_json_carries_no_private_parameter() {
        when(cipher.encrypt(any(), any())).thenReturn("v1$k$sealed");

        service().rotate(Duration.ofMinutes(10));

        ArgumentCaptor<NewSigningKey> captured = ArgumentCaptor.forClass(NewSigningKey.class);
        verify(writer).activateNewIssuer(captured.capture(), any());

        // toPublicJWK().toJSONObject() et non toJSONObject() : la seconde inclurait "d", "p",
        // "q" et les autres parametres prives RSA.
        assertThat(captured.getValue().publicJwkJson()).doesNotContainKeys("d", "p", "q");
    }

    @Test
    void given_a_rotation_then_the_private_material_is_encrypted_under_the_new_key_id() {
        when(cipher.encrypt(any(), any())).thenReturn("v1$k$sealed");

        String kid = service().rotate(Duration.ofMinutes(10));

        ArgumentCaptor<SecretContext> contextCaptor = ArgumentCaptor.forClass(SecretContext.class);
        ArgumentCaptor<String> plaintextCaptor = ArgumentCaptor.forClass(String.class);
        verify(cipher).encrypt(contextCaptor.capture(), plaintextCaptor.capture());

        assertThat(contextCaptor.getValue()).isEqualTo(SecretContext.signingKeyMaterial(kid));
        // Le clair chiffre est le JWK complet, matiere privee incluse : c'est lui que
        // PersistentJwkSource s'attend a retrouver au dechiffrement.
        JWK plaintext = assertDoesNotThrowJwkParse(plaintextCaptor.getValue());
        assertThat(plaintext.isPrivate()).isTrue();
        assertThat(plaintext.getKeyID()).isEqualTo(kid);
    }

    @Test
    void given_a_rotation_then_the_encrypted_output_is_what_gets_persisted() {
        when(cipher.encrypt(any(), any())).thenReturn("v1$k$exactly-this-sealed-value");

        service().rotate(Duration.ofMinutes(10));

        ArgumentCaptor<NewSigningKey> captured = ArgumentCaptor.forClass(NewSigningKey.class);
        verify(writer).activateNewIssuer(captured.capture(), any());

        assertThat(captured.getValue().privateKeyEncrypted()).isEqualTo("v1$k$exactly-this-sealed-value");
    }

    @Test
    void given_a_grace_period_then_the_retirement_instant_is_now_plus_that_period() {
        when(cipher.encrypt(any(), any())).thenReturn("v1$k$sealed");

        service().rotate(Duration.ofMinutes(10));

        verify(writer).activateNewIssuer(any(), eq(NOW.plus(Duration.ofMinutes(10))));
    }

    @Test
    void given_two_successive_rotations_then_each_produces_a_distinct_key_identifier() {
        when(cipher.encrypt(any(), any())).thenReturn("v1$k$sealed");

        String first = service().rotate(Duration.ofMinutes(10));
        String second = service().rotate(Duration.ofMinutes(10));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void given_no_grace_period_then_rotation_is_refused() {
        assertThatThrownBy(() -> service().rotate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SIGNING_KEY_ROTATION_REQUIRES_A_GRACE_PERIOD");
    }

    @Test
    void given_a_negative_grace_period_then_rotation_is_refused() {
        assertThatThrownBy(() -> service().rotate(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SIGNING_KEY_ROTATION_REQUIRES_A_GRACE_PERIOD");
    }

    @Test
    void given_a_zero_grace_period_then_rotation_is_accepted() {
        // Retrait immediat, cas degenere mais legitime : l'appelant sait qu'aucun token n'a pu
        // etre signe entre l'instant present et l'activation.
        when(cipher.encrypt(any(), any())).thenReturn("v1$k$sealed");

        service().rotate(Duration.ZERO);

        verify(writer).activateNewIssuer(any(), eq(NOW));
    }

    private static JWK assertDoesNotThrowJwkParse(String json) {
        try {
            return JWK.parse(json);
        } catch (Exception e) {
            throw new AssertionError("La matiere chiffree ne portait pas un JWK valide", e);
        }
    }
}
