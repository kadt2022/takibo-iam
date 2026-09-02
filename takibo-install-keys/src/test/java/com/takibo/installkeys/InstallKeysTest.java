package com.takibo.installkeys;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Les trois valeurs produites par l'installation (TAKIBO-INSTALL-KEYS-01).
 * <p>
 * La confrontation aux invariants réels du serveur — {@code SecretCipherKey},
 * {@code HmacSha256UserCodeHmac} — vit dans {@link InstallKeysContractTest} : ici, seules les
 * propriétés que ce module garantit lui-même sont vérifiées.
 */
class InstallKeysTest {

    private static final Pattern KEY_ID_PATTERN =
            Pattern.compile("k-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    @Test
    void given_a_generation_then_both_materials_are_32_bytes() {
        InstallKeys keys = InstallKeys.generate(new SecureRandom());

        assertThat(keys.cipherKeyMaterial()).hasSize(32);
        assertThat(keys.userCodeHmacMaterial()).hasSize(32);
    }

    @Test
    void given_a_generation_then_the_two_materials_differ() {
        InstallKeys keys = InstallKeys.generate(new SecureRandom());

        assertThat(keys.cipherKeyMaterial()).isNotEqualTo(keys.userCodeHmacMaterial());
    }

    @Test
    void given_two_generations_then_nothing_repeats() {
        // Aucune graine fixe, aucune derivation : deux installations n'ont jamais les memes
        // secrets, et c'est ce qui fait que la compromission de l'une n'atteint pas l'autre.
        InstallKeys first = InstallKeys.generate(new SecureRandom());
        InstallKeys second = InstallKeys.generate(new SecureRandom());

        assertThat(first.cipherKeyId()).isNotEqualTo(second.cipherKeyId());
        assertThat(first.cipherKeyMaterial()).isNotEqualTo(second.cipherKeyMaterial());
        assertThat(first.userCodeHmacMaterial()).isNotEqualTo(second.userCodeHmacMaterial());
    }

    @Test
    void given_a_generation_then_the_key_id_is_an_opaque_uuid_without_a_date() {
        String id = InstallKeys.generate(new SecureRandom()).cipherKeyId();

        assertThat(id).matches(KEY_ID_PATTERN);
        // Le point de la decision : rien dans l'identifiant ne se lit comme une date. L'ordre
        // des cles appartient aux metadonnees de la base.
        assertThat(id).doesNotContain(String.valueOf(java.time.Year.now().getValue()));
    }

    @Test
    void given_a_generation_then_both_materials_are_valid_base64() {
        InstallKeys keys = InstallKeys.generate(new SecureRandom());

        assertThat(Base64.getDecoder().decode(keys.cipherKeyBase64()))
                .isEqualTo(keys.cipherKeyMaterial());
        assertThat(Base64.getDecoder().decode(keys.userCodeHmacKeyBase64()))
                .isEqualTo(keys.userCodeHmacMaterial());
    }

    @Test
    void given_the_same_material_twice_then_the_construction_is_refused() {
        // Ne peut pas arriver par hasard : sa survenue denoncerait un generateur casse, et
        // reutiliser la meme matiere pour chiffrer et pour authentifier annulerait la
        // separation de roles du HMAC.
        byte[] shared = new byte[32];

        assertThatThrownBy(() -> new InstallKeys("k-" + UUID.randomUUID(), shared, shared))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INSTALL_KEYS_MUST_NOT_SHARE_THE_SAME_MATERIAL");
    }

    @Test
    void given_a_material_of_the_wrong_length_then_the_construction_is_refused() {
        assertThatThrownBy(() -> new InstallKeys("k-1", new byte[31], new byte[32]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INSTALL_CIPHER_KEY_MUST_BE_32_BYTES");

        assertThatThrownBy(() -> new InstallKeys("k-1", new byte[32], new byte[33]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INSTALL_USER_CODE_HMAC_KEY_MUST_BE_32_BYTES");
    }

    @Test
    void given_a_generated_key_then_its_text_form_never_reveals_the_material() {
        // Un record imprime ses composants par defaut : sans redefinition, un simple log
        // suffirait a publier les deux secrets.
        InstallKeys keys = InstallKeys.generate(new SecureRandom());

        assertThat(keys.toString())
                .contains(keys.cipherKeyId())
                .doesNotContain(keys.cipherKeyBase64())
                .doesNotContain(keys.userCodeHmacKeyBase64());
    }
}
