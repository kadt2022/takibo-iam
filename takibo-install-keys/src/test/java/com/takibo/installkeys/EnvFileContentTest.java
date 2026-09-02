package com.takibo.installkeys;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le contrat de sortie (TAKIBO-INSTALL-KEYS-01) : ce que doit contenir le fichier, et surtout
 * ce qu'il ne doit pas contenir.
 * <p>
 * Chaque test correspond à une façon connue de casser l'import : un commentaire qu'un
 * analyseur refuse, un guillemet qui entre dans la valeur, un {@code \r} qui casse le décodage
 * base64, une dernière ligne sans terminaison qu'un analyseur ignore.
 */
class EnvFileContentTest {

    private final InstallKeys keys = InstallKeys.generate(new SecureRandom());
    private final String content = EnvFileContent.render(keys);

    @Test
    void given_generated_keys_then_the_file_holds_exactly_three_lines() {
        assertThat(content.split("\n", -1)).hasSize(4); // trois lignes + la chaine vide finale
    }

    @Test
    void given_generated_keys_then_the_three_expected_variables_are_present_with_their_values() {
        Map<String, String> parsed = parseAsEnvFile(content);

        assertThat(parsed).containsExactly(
                Map.entry("TAKIBO_TAS_CIPHER_KEY_ID", keys.cipherKeyId()),
                Map.entry("TAKIBO_TAS_CIPHER_KEY", keys.cipherKeyBase64()),
                Map.entry("TAKIBO_TAS_USER_CODE_HMAC_KEY", keys.userCodeHmacKeyBase64()));
    }

    @Test
    void given_generated_keys_then_the_file_ends_with_a_final_newline() {
        // Sans elle, certains analyseurs ignorent la derniere ligne : c'est une cle qui
        // disparait sans que rien ne le signale.
        assertThat(content).endsWith("\n");
    }

    @Test
    void given_generated_keys_then_the_file_uses_lf_even_on_windows() {
        // System.lineSeparator() produirait \r\n ici, et le \r final entrerait dans la valeur
        // decodee cote serveur.
        assertThat(content).doesNotContain("\r");
    }

    @Test
    void given_generated_keys_then_the_file_carries_no_comment_and_no_quote() {
        assertThat(content).doesNotContain("#").doesNotContain("\"").doesNotContain("'");
    }

    @Test
    void given_a_base64_padding_in_a_value_then_the_line_still_parses() {
        // Le padding base64 introduit un '=' dans la valeur ; le decoupage se fait sur le
        // premier '=' de la ligne, donc la valeur reste entiere.
        Map<String, String> parsed = parseAsEnvFile(content);

        assertThat(parsed.get("TAKIBO_TAS_CIPHER_KEY")).isEqualTo(keys.cipherKeyBase64());
        assertThat(java.util.Base64.getDecoder().decode(parsed.get("TAKIBO_TAS_CIPHER_KEY")))
                .hasSize(32);
    }

    /** Découpage sur le premier {@code =}, comme le font les analyseurs visés. */
    private static Map<String, String> parseAsEnvFile(String content) {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String line : content.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            int separator = line.indexOf('=');
            parsed.put(line.substring(0, separator), line.substring(separator + 1));
        }
        return parsed;
    }
}
