package com.takibo.authorizationserver.infrastructure.keys;

import com.nimbusds.jose.jwk.JWK;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneratedSigningKeyMaterialTest {

    @Test
    void given_a_private_jwk_then_the_material_is_built() {
        GeneratedSigningKeyMaterial material =
                RsaSigningKeyGenerator.generate();

        assertThatCode(() -> new GeneratedSigningKeyMaterial(material.privateJwk()))
                .doesNotThrowAnyException();
    }

    @Test
    void given_a_public_only_jwk_then_the_material_is_refused() {
        JWK publicOnly = RsaSigningKeyGenerator.generate().privateJwk().toPublicJWK();

        assertThatThrownBy(() -> new GeneratedSigningKeyMaterial(publicOnly))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GENERATED_SIGNING_KEY_MUST_CARRY_PRIVATE_MATERIAL");
    }

    @Test
    void given_no_jwk_then_the_material_is_refused() {
        assertThatThrownBy(() -> new GeneratedSigningKeyMaterial(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GENERATED_SIGNING_KEY_MUST_CARRY_PRIVATE_MATERIAL");
    }

    @Test
    void given_generated_material_then_it_carries_the_expected_algorithm_and_use() {
        GeneratedSigningKeyMaterial material = RsaSigningKeyGenerator.generate();

        assertThat(material.privateJwk().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(material.privateJwk().getKeyType().getValue()).isEqualTo("RSA");
        assertThat(material.privateJwk().getKeyUse().getValue()).isEqualTo("sig");
        assertThat(material.privateJwk().getKeyID()).isNotBlank();
    }

    @Test
    void given_two_generations_then_each_produces_a_distinct_key_identifier() {
        String first = RsaSigningKeyGenerator.generate().privateJwk().getKeyID();
        String second = RsaSigningKeyGenerator.generate().privateJwk().getKeyID();

        assertThat(first).isNotEqualTo(second);
    }
}
