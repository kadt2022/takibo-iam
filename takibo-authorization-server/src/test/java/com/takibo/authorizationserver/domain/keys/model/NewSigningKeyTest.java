package com.takibo.authorizationserver.domain.keys.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Invariants d'une clé prête à être activée (TAS-GRANTS-02A, rotation). Chaque champ est requis
 * individuellement : une clé de signature incomplète ne doit pas atteindre l'écriture.
 */
class NewSigningKeyTest {

    private static final Map<String, Object> PUBLIC_JWK = Map.of("kty", "RSA", "kid", "k1");

    @Test
    void given_all_fields_present_then_the_key_is_built() {
        assertThatCode(() -> aKey().build()).doesNotThrowAnyException();
    }

    @Test
    void given_a_blank_kid_then_the_key_is_refused() {
        Builder builder = aKey().kid(" ");

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NEW_SIGNING_KEY_REQUIRES_KID");
    }

    @Test
    void given_no_kid_then_the_key_is_refused() {
        Builder builder = aKey().kid(null);

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NEW_SIGNING_KEY_REQUIRES_KID");
    }

    @Test
    void given_a_blank_alg_then_the_key_is_refused() {
        Builder builder = aKey().alg("");

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NEW_SIGNING_KEY_REQUIRES_ALG");
    }

    @Test
    void given_a_blank_kty_then_the_key_is_refused() {
        Builder builder = aKey().kty("");

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NEW_SIGNING_KEY_REQUIRES_KTY");
    }

    @Test
    void given_a_blank_key_use_then_the_key_is_refused() {
        Builder builder = aKey().keyUse("");

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NEW_SIGNING_KEY_REQUIRES_KEY_USE");
    }

    @Test
    void given_a_blank_encrypted_material_then_the_key_is_refused() {
        Builder builder = aKey().privateKeyEncrypted("");

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NEW_SIGNING_KEY_REQUIRES_ENCRYPTED_MATERIAL");
    }

    @Test
    void given_no_public_jwk_then_the_key_is_refused() {
        Builder builder = aKey().publicJwkJson(null);

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NEW_SIGNING_KEY_REQUIRES_PUBLIC_JWK");
    }

    @Test
    void given_an_empty_public_jwk_then_the_key_is_refused() {
        Builder builder = aKey().publicJwkJson(Map.of());

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NEW_SIGNING_KEY_REQUIRES_PUBLIC_JWK");
    }

    @Test
    void given_a_mutable_public_jwk_map_then_it_cannot_be_altered_from_outside() {
        Map<String, Object> mutable = new HashMap<>(PUBLIC_JWK);
        NewSigningKey key = aKey().publicJwkJson(mutable).build();

        mutable.put("kid", "tampered");

        assertThat(key.publicJwkJson()).containsEntry("kid", "k1");
    }

    private static Builder aKey() {
        return new Builder();
    }

    private static final class Builder {
        private String kid = "k1";
        private String alg = "RS256";
        private String kty = "RSA";
        private String keyUse = "sig";
        private Map<String, Object> publicJwkJson = PUBLIC_JWK;
        private String privateKeyEncrypted = "v1$k1$sealed";

        Builder kid(String v) { this.kid = v; return this; }
        Builder alg(String v) { this.alg = v; return this; }
        Builder kty(String v) { this.kty = v; return this; }
        Builder keyUse(String v) { this.keyUse = v; return this; }
        Builder publicJwkJson(Map<String, Object> v) { this.publicJwkJson = v; return this; }
        Builder privateKeyEncrypted(String v) { this.privateKeyEncrypted = v; return this; }

        NewSigningKey build() {
            return new NewSigningKey(kid, alg, kty, keyUse, publicJwkJson, privateKeyEncrypted);
        }
    }
}
