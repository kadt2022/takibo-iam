package com.takibo.authorizationserver.infrastructure.keys;

import com.nimbusds.jose.jwk.JWK;
import com.takibo.authorizationserver.domain.keys.model.GeneratedSigningKeyMaterial;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RsaSigningKeyGeneratorTest {

    private final RsaSigningKeyGenerator generator = new RsaSigningKeyGenerator();

    @Test
    void given_a_generation_then_the_material_carries_the_expected_algorithm_and_use()
            throws java.text.ParseException {
        GeneratedSigningKeyMaterial material = generator.generate();

        assertThat(material.alg()).isEqualTo("RS256");
        assertThat(material.kty()).isEqualTo("RSA");
        assertThat(material.keyUse()).isEqualTo("sig");
        assertThat(material.kid()).isNotBlank();
        assertThat(material.publicJwkJson()).containsEntry("kid", material.kid());

        // La partie privee complete, pas seulement la publique : c'est ce que le service de
        // rotation chiffre avant ecriture.
        JWK parsed = JWK.parse(material.privateKeyMaterial());
        assertThat(parsed.isPrivate()).isTrue();
        assertThat(parsed.getKeyID()).isEqualTo(material.kid());
    }

    @Test
    void given_a_public_jwk_json_then_it_carries_no_private_parameter()
            throws java.text.ParseException {
        GeneratedSigningKeyMaterial material = generator.generate();

        JWK announced = JWK.parse(
                com.nimbusds.jose.util.JSONObjectUtils.toJSONString(material.publicJwkJson()));

        assertThat(announced.isPrivate()).isFalse();
    }

    @Test
    void given_two_generations_then_each_produces_a_distinct_key_identifier() {
        String first = generator.generate().kid();
        String second = generator.generate().kid();

        assertThat(first).isNotEqualTo(second);
    }
}
