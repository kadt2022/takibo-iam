package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import org.junit.jupiter.api.Test;

import java.security.Key;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MinimumRsaKeySizeSelectorTest {

    @Test
    void rsa_keys_shorter_than_2048_bits_are_dropped_and_other_keys_are_kept() throws Exception {
        Key rsa1024 = rsaPublicKey(1024);
        Key rsa2047 = rsaPublicKey(2047);
        Key rsa2048 = rsaPublicKey(2048);
        KeyPairGenerator ec = KeyPairGenerator.getInstance("EC");
        ec.initialize(new ECGenParameterSpec("secp256r1"));
        Key p256 = ec.generateKeyPair().getPublic();
        MinimumRsaKeySizeSelector selector =
                new MinimumRsaKeySizeSelector((header, context) -> List.of(rsa1024, rsa2047, rsa2048, p256));

        assertThat(selector.selectJWSKeys(new JWSHeader(JWSAlgorithm.RS256), null))
                .containsExactly(rsa2048, p256);
    }

    @Test
    void only_weak_keys_leave_nothing_to_verify_with() throws Exception {
        Key rsa1024 = rsaPublicKey(1024);
        MinimumRsaKeySizeSelector selector = new MinimumRsaKeySizeSelector((header, context) -> List.of(rsa1024));

        assertThat(selector.selectJWSKeys(new JWSHeader(JWSAlgorithm.RS256), null)).isEmpty();
    }

    private static Key rsaPublicKey(int bits) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return generator.generateKeyPair().getPublic();
    }
}
