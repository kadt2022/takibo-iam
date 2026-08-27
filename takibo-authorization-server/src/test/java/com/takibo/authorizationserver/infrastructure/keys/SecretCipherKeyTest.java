package com.takibo.authorizationserver.infrastructure.keys;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Invariants d'une cle de chiffrement au repos (TAS-GRANTS-02A).
 * <p>
 * L'identifiant voyage en clair dans chaque chiffre, entre deux separateurs {@code $}. Sa
 * forme n'est donc pas une preference : un identifiant contenant un separateur rendrait le
 * decoupage ambigu, et un chiffre indechiffrable.
 */
class SecretCipherKeyTest {

    private static final byte[] MATERIAL = new byte[32];

    // ---------- Longueur de la matiere ----------

    @Test
    void given_material_of_exactly_32_bytes_then_the_key_is_built() {
        assertThatCode(() -> new SecretCipherKey("k1", new byte[32])).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "matiere de {0} octets refusee")
    @ValueSource(ints = {0, 1, 16, 24, 31, 33, 48, 64})
    void given_any_other_material_length_then_the_key_is_refused(int length) {
        // Exactement 32, et pas « au moins 32 ». JCE n'accepte que 16, 24 ou 32 octets : une
        // matiere de 33 construirait l'objet sans broncher puis ferait echouer chaque
        // chiffrement. 16 et 24 sont valides pour JCE mais refuses ici, TAS imposant AES-256.
        assertThatThrownBy(() -> new SecretCipherKey("k1", new byte[length]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SECRET_CIPHER_KEY_MUST_BE_32_BYTES");
    }

    @Test
    void given_no_material_then_the_key_is_refused() {
        assertThatThrownBy(() -> new SecretCipherKey("k1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SECRET_CIPHER_KEY_MUST_BE_32_BYTES");
    }

    // ---------- Forme de l'identifiant ----------

    @ParameterizedTest(name = "identifiant {0} accepte")
    @ValueSource(strings = {"k1", "platform-2026-08", "KEY_01", "a", "0123456789"})
    void given_a_well_formed_identifier_then_the_key_is_built(String id) {
        assertThatCode(() -> new SecretCipherKey(id, MATERIAL)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "identifiant [{0}] refuse")
    @ValueSource(strings = {"", " ", "k 1", "k$1", "clé", "k.1", "k/1", "k+1", "k:1"})
    void given_a_malformed_identifier_then_the_key_is_refused(String id) {
        // Le separateur du format est '$' : un identifiant qui en contient, ou qui contient un
        // caractere de l'alphabet base64 hors du jeu autorise, rendrait le decoupage ambigu.
        assertThatThrownBy(() -> new SecretCipherKey(id, MATERIAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SECRET_CIPHER_KEY_ID_INVALID");
    }

    @Test
    void given_no_identifier_then_the_key_is_refused() {
        assertThatThrownBy(() -> new SecretCipherKey(null, MATERIAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SECRET_CIPHER_KEY_ID_INVALID");
    }

    @Test
    void given_an_identifier_longer_than_64_characters_then_the_key_is_refused() {
        assertThatThrownBy(() -> new SecretCipherKey("k".repeat(65), MATERIAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SECRET_CIPHER_KEY_ID_INVALID");
    }

    // ---------- La matiere ne fuit pas ----------

    @Test
    void given_a_key_then_its_material_cannot_be_altered_from_outside() {
        byte[] mutable = new byte[32];
        SecretCipherKey key = new SecretCipherKey("k1", mutable);

        mutable[0] = 42;
        assertThat(key.material()[0]).isZero();

        key.material()[1] = 42;
        assertThat(key.material()[1]).isZero();
    }

    @Test
    void given_a_key_then_its_text_form_never_exposes_the_material() {
        // Un record afficherait sa matiere par defaut. Une cle dans un log est une cle perdue.
        SecretCipherKey key = new SecretCipherKey("platform-2026-08", MATERIAL);

        assertThat(key.toString())
                .contains("platform-2026-08")
                .doesNotContain("material")
                .doesNotContain("[B@");
    }

    @Test
    void given_two_keys_then_equality_compares_the_material_by_value() {
        SecretCipherKey first = new SecretCipherKey("k1", new byte[32]);
        SecretCipherKey same = new SecretCipherKey("k1", new byte[32]);
        byte[] other = new byte[32];
        other[0] = 1;

        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(first).isNotEqualTo(new SecretCipherKey("k1", other));
        assertThat(first).isNotEqualTo(new SecretCipherKey("k2", new byte[32]));
    }
}
