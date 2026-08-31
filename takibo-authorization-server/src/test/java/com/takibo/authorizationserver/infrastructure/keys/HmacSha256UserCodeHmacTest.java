package com.takibo.authorizationserver.infrastructure.keys;

import com.takibo.authorizationserver.domain.keys.port.UserCodeHmac;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link HmacSha256UserCodeHmac} (TAS-GRANTS-02) : la clé doit faire exactement 32 octets, et le
 * HMAC produit doit être le vrai HMAC-SHA256, pas une variante maison.
 */
class HmacSha256UserCodeHmacTest {

    /**
     * Vecteur de référence calculé hors de ce code, par OpenSSL :
     * <pre>
     * printf '%s' "WDJB-MJHT" | openssl dgst -sha256 -mac HMAC \
     *   -macopt hexkey:000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f
     * </pre>
     * Sans vecteur externe, un test ne prouverait que la cohérence du code avec lui-même : il
     * passerait tout aussi bien sur une implémentation qui hacherait autre chose que ce que
     * HMAC-SHA256 définit.
     */
    private static final String EXPECTED_HMAC =
            "9021fe4257cf1203686b010f3bc64b7888feb7df1ccb770af3629264fb735b9a";

    @Test
    void given_the_reference_key_and_user_code_then_the_hmac_matches_the_known_vector() {
        UserCodeHmac hmac = new HmacSha256UserCodeHmac(referenceKey());

        assertThat(hmac.hmacHex("WDJB-MJHT")).isEqualTo(EXPECTED_HMAC);
    }

    @Test
    void given_the_same_input_twice_then_the_hmac_is_stable() {
        UserCodeHmac hmac = new HmacSha256UserCodeHmac(referenceKey());

        assertThat(hmac.hmacHex("WDJB-MJHT")).isEqualTo(hmac.hmacHex("WDJB-MJHT"));
    }

    @Test
    void given_two_different_keys_then_the_same_user_code_hashes_differently() {
        // C'est tout l'interet du HMAC face a un SHA-256 simple : sans la cle d'installation,
        // un user_code de faible entropie n'est pas enumerable hors ligne.
        UserCodeHmac first = new HmacSha256UserCodeHmac(referenceKey());
        byte[] otherMaterial = referenceKey();
        otherMaterial[0] ^= (byte) 0xff;
        UserCodeHmac second = new HmacSha256UserCodeHmac(otherMaterial);

        assertThat(first.hmacHex("WDJB-MJHT")).isNotEqualTo(second.hmacHex("WDJB-MJHT"));
    }

    @Test
    void given_a_key_of_exactly_32_bytes_then_it_is_accepted() {
        assertThatCode(() -> new HmacSha256UserCodeHmac(new byte[32])).doesNotThrowAnyException();
    }

    @Test
    void given_a_null_key_then_it_is_refused() {
        assertThatThrownBy(() -> new HmacSha256UserCodeHmac(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("USER_CODE_HMAC_KEY_MUST_BE_32_BYTES");
    }

    @Test
    void given_a_key_of_31_bytes_then_it_is_refused() {
        // Une cle plus courte que la sortie de SHA-256 reduit la resistance recherchee, et
        // SecretKeySpec l'accepterait sans rien dire.
        assertThatThrownBy(() -> new HmacSha256UserCodeHmac(new byte[31]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("USER_CODE_HMAC_KEY_MUST_BE_32_BYTES");
    }

    @Test
    void given_a_key_of_33_bytes_then_it_is_refused() {
        // Plus longue que le bloc utile, HMAC la replie : deux cles differentes deviendraient
        // silencieusement equivalentes.
        assertThatThrownBy(() -> new HmacSha256UserCodeHmac(new byte[33]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("USER_CODE_HMAC_KEY_MUST_BE_32_BYTES");
    }

    @Test
    void given_an_empty_key_then_it_is_refused() {
        assertThatThrownBy(() -> new HmacSha256UserCodeHmac(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("USER_CODE_HMAC_KEY_MUST_BE_32_BYTES");
    }

    private static byte[] referenceKey() {
        byte[] material = new byte[32];
        for (int i = 0; i < material.length; i++) {
            material[i] = (byte) i;
        }
        return material;
    }
}
