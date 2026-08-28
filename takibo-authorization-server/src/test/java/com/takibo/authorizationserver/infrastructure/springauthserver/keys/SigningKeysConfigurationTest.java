package com.takibo.authorizationserver.infrastructure.springauthserver.keys;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.takibo.authorizationserver.domain.keys.SigningKeyRotationService;
import com.takibo.authorizationserver.domain.keys.port.SecretCipher;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyRepository;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Câblage des beans de {@link SigningKeysConfiguration} (TAS-GRANTS-02A), appelés
 * directement plutôt que résolus via un contexte Spring complet — même technique que
 * {@code TakiboAuthorizationServerConfigurationTest}. Un contexte complet exigerait une
 * émettrice déjà active en base à cause du fail-closed de {@code PersistentJwkSource}, ce
 * que ce test n'a pas besoin de payer pour vérifier l'assemblage des beans.
 */
@ExtendWith(MockitoExtension.class)
class SigningKeysConfigurationTest {

    @Mock private SigningKeyRepository signingKeyRepository;
    @Mock private SigningKeyWriter signingKeyWriter;
    @Mock private SecretCipher secretCipher;

    private final SigningKeysConfiguration configuration = new SigningKeysConfiguration();
    private final Clock clock = Clock.systemUTC();

    @Test
    void given_repository_cipher_and_clock_then_the_persistent_source_is_built() {
        JWKSource<SecurityContext> source =
                configuration.persistentJwkSource(signingKeyRepository, secretCipher, clock);

        assertThat(source).isNotNull();
    }

    @Test
    void given_a_well_formed_base64_key_then_the_cipher_bean_is_built() {
        String base64Key = Base64.getEncoder().encodeToString(new byte[32]);

        SecretCipher cipher = configuration.secretCipher("k1", base64Key);

        assertThat(cipher).isNotNull();
        // Aller-retour minimal : prouve que la matiere decodee est bien celle fournie.
        var context = com.takibo.authorizationserver.domain.keys.port.SecretContext
                .signingKeyMaterial("k1");
        assertThat(cipher.decrypt(context, cipher.encrypt(context, "probe"))).isEqualTo("probe");
    }

    @Test
    void given_a_key_with_surrounding_whitespace_then_it_is_trimmed_before_decoding() {
        String base64Key = "  " + Base64.getEncoder().encodeToString(new byte[32]) + "\n";

        assertThat(configuration.secretCipher("k1", base64Key)).isNotNull();
    }

    @Test
    void given_a_key_that_is_not_valid_base64_then_the_cipher_bean_is_refused() {
        assertThatThrownBy(() -> configuration.secretCipher("k1", "*** pas du base64 ***"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SECRET_CIPHER_KEY_IS_NOT_VALID_BASE64");
    }

    @Test
    void given_writer_cipher_and_clock_then_the_rotation_service_is_built() {
        SigningKeyRotationService service =
                configuration.signingKeyRotationService(signingKeyWriter, secretCipher, clock);

        assertThat(service).isNotNull();
    }

    @Test
    void given_a_jwk_source_then_the_decoder_bean_is_built() {
        JWKSource<SecurityContext> jwkSource = ephemeralSource();

        JwtDecoder decoder = configuration.jwtDecoder(jwkSource);

        assertThat(decoder).isNotNull();
    }

    // ---------- Selecteur de signature : theOneThatCanSign ----------

    @Test
    void given_a_source_with_exactly_one_private_key_then_the_encoder_signs_with_it()
            throws Exception {
        JWK privateJwk = anRsaJwk();
        JWKSource<SecurityContext> source = mock(JWKSource.class);
        when(source.get(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(privateJwk));

        JwtEncoder encoder = configuration.jwtEncoder(source);

        assertThat(encoder).isNotNull();
        // La construction seule n'invoque pas le selecteur : verifie sur un jeton reel.
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .issuer("https://test").subject("s")
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(60))
                .build();
        String token = encoder.encode(
                org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(claims))
                .getTokenValue();
        assertThat(token).isNotBlank();
    }

    @Test
    void given_a_source_with_no_private_key_then_signing_is_refused() {
        // NimbusJwtEncoder court-circuite le selecteur personnalise des qu'un seul candidat
        // correspond a l'algorithme : deux cles publiques, jamais une seule, sont necessaires
        // pour que theOneThatCanSign soit reellement invoque.
        List<JWK> candidates = List.of(anRsaJwk().toPublicJWK(), anRsaJwk().toPublicJWK());

        assertThatThrownBy(() -> selectSigningKey(candidates))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EXPECTED_EXACTLY_ONE_SIGNING_KEY_BUT_FOUND_0");
    }

    @Test
    void given_a_source_with_two_private_keys_then_signing_is_refused() {
        List<JWK> candidates = List.of(anRsaJwk(), anRsaJwk());

        assertThatThrownBy(() -> selectSigningKey(candidates))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EXPECTED_EXACTLY_ONE_SIGNING_KEY_BUT_FOUND_2");
    }

    /** Invoque le selecteur prive via l'unique point d'entree public : encoder un jeton. */
    private void selectSigningKey(List<JWK> candidates) throws Exception {
        JWKSource<SecurityContext> source = mock(JWKSource.class);
        when(source.get(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(candidates);
        JwtEncoder encoder = configuration.jwtEncoder(source);
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .issuer("https://test").subject("s")
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(60))
                .build();
        encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(claims));
    }

    private static JWKSource<SecurityContext> ephemeralSource() {
        JWK jwk = anRsaJwk();
        return new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(
                new com.nimbusds.jose.jwk.JWKSet(jwk));
    }

    private static JWK anRsaJwk() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA_ALGORITHM_UNAVAILABLE_IN_TEST_JVM", e);
        }
    }
}
