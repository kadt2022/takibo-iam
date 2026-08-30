package com.takibo.authorizationserver.domain.authorization;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenHashTest {

    @Test
    void given_a_value_then_the_hash_is_64_lowercase_hex_characters() {
        String hash = TokenHash.sha256Hex("a-token-value");

        assertThat(hash).hasSize(64).matches("^[a-f0-9]{64}$");
    }

    @Test
    void given_the_same_value_twice_then_the_hash_is_identical() {
        // Contrairement au chiffrement (non deterministe), le hash doit etre stable :
        // c'est precisement ce qui permet de retrouver une ligne par sa valeur.
        assertThat(TokenHash.sha256Hex("busa-finance-token"))
                .isEqualTo(TokenHash.sha256Hex("busa-finance-token"));
    }

    @Test
    void given_two_distinct_values_then_the_hashes_differ() {
        assertThat(TokenHash.sha256Hex("token-a"))
                .isNotEqualTo(TokenHash.sha256Hex("token-b"));
    }

    @Test
    void given_a_null_value_then_it_fails_closed() {
        assertThatThrownBy(() -> TokenHash.sha256Hex(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void given_a_known_value_then_the_hash_matches_the_reference_sha256() {
        // Non-regression sur l'algorithme lui-meme, contre le vecteur connu SHA-256("").
        assertThat(TokenHash.sha256Hex(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }
}
