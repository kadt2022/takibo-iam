package com.takibo.authorizationserver.infrastructure.keys;

import com.takibo.authorizationserver.domain.keys.port.SecretCipher;
import com.takibo.authorizationserver.domain.keys.port.SecretDecryptionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat du chiffrement au repos (TAS-GRANTS-02A).
 * <p>
 * Ce port protege deux familles de secrets que TAS doit pouvoir <b>relire</b> : la matiere
 * privee des cles de signature, et bientot les valeurs de codes et de tokens du recit 02.
 * Les trois proprietes verifiees ici ne sont pas decoratives :
 * <ul>
 *   <li><b>authentification</b> — sans elle, une cle privee alteree produirait des signatures
 *       invalides sans le moindre diagnostic ;</li>
 *   <li><b>non-determinisme</b> — sans lui, deux chiffres egaux trahiraient deux secrets
 *       egaux, ce qui suffirait a reperer deux comptes partageant un meme code ;</li>
 *   <li><b>indistinction des echecs</b> — altere, tronque ou chiffre avec une autre cle
 *       donnent la meme erreur, pour ne rien apprendre a qui sonde.</li>
 * </ul>
 */
class AesGcmSecretCipherTest {

    private static final String A_PRIVATE_KEY = """
            -----BEGIN PRIVATE KEY-----
            MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC7VJTUt9Us8cKj
            -----END PRIVATE KEY-----""";

    private final SecretCipher cipher = new AesGcmSecretCipher(aKey(1));

    // ---------- Aller-retour ----------

    @Test
    void given_a_secret_when_encrypted_then_it_is_restored_identically() {
        String sealed = cipher.encrypt(A_PRIVATE_KEY);

        assertThat(sealed).isNotEqualTo(A_PRIVATE_KEY);
        assertThat(cipher.decrypt(sealed)).isEqualTo(A_PRIVATE_KEY);
    }

    @Test
    void given_an_empty_secret_then_it_still_round_trips() {
        assertThat(cipher.decrypt(cipher.encrypt(""))).isEmpty();
    }

    @Test
    void given_a_secret_with_non_ascii_characters_then_it_round_trips() {
        String secret = "clé-de-signature-éàü-é中文";

        assertThat(cipher.decrypt(cipher.encrypt(secret))).isEqualTo(secret);
    }

    @Test
    void given_a_ciphertext_then_it_is_storable_as_text() {
        // La colonne private_key_encrypted est un VARCHAR : la sortie doit y tenir telle
        // quelle, sans encodage supplementaire.
        String sealed = cipher.encrypt(A_PRIVATE_KEY);

        assertThat(sealed).matches("^[A-Za-z0-9+/]+={0,2}$");
    }

    // ---------- Non-determinisme ----------

    @Test
    void given_the_same_secret_encrypted_twice_then_the_two_ciphertexts_differ() {
        String first = cipher.encrypt(A_PRIVATE_KEY);
        String second = cipher.encrypt(A_PRIVATE_KEY);

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo(cipher.decrypt(second));
    }

    // ---------- Authentification ----------

    @Test
    void given_a_tampered_ciphertext_then_decryption_is_refused() {
        // Un mode non authentifie, comme CBC, rendrait ici un clair corrompu sans erreur.
        byte[] raw = Base64.getDecoder().decode(cipher.encrypt(A_PRIVATE_KEY));
        raw[raw.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(SecretDecryptionException.class);
    }

    @Test
    void given_a_tampered_initialisation_vector_then_decryption_is_refused() {
        byte[] raw = Base64.getDecoder().decode(cipher.encrypt(A_PRIVATE_KEY));
        raw[0] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(SecretDecryptionException.class);
    }

    @Test
    void given_a_ciphertext_from_another_key_then_decryption_is_refused() {
        SecretCipher other = new AesGcmSecretCipher(aKey(2));
        String sealedElsewhere = other.encrypt(A_PRIVATE_KEY);

        assertThatThrownBy(() -> cipher.decrypt(sealedElsewhere))
                .isInstanceOf(SecretDecryptionException.class);
    }

    // ---------- Entrees malformees ----------

    @Test
    void given_a_truncated_ciphertext_then_decryption_is_refused() {
        String truncated = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});

        assertThatThrownBy(() -> cipher.decrypt(truncated))
                .isInstanceOf(SecretDecryptionException.class);
    }

    @Test
    void given_a_ciphertext_that_is_not_base64_then_decryption_is_refused() {
        assertThatThrownBy(() -> cipher.decrypt("*** pas du base64 ***"))
                .isInstanceOf(SecretDecryptionException.class);
    }

    @Test
    void given_a_blank_ciphertext_then_decryption_is_refused() {
        assertThatThrownBy(() -> cipher.decrypt("   "))
                .isInstanceOf(SecretDecryptionException.class);
    }

    @Test
    void given_a_null_plaintext_then_encryption_is_refused() {
        assertThatThrownBy(() -> cipher.encrypt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SECRET_CIPHER_REQUIRES_PLAINTEXT");
    }

    // ---------- Longueur de cle ----------

    @Test
    void given_a_key_of_exactly_32_bytes_then_the_cipher_is_built() {
        assertThatCode(() -> new AesGcmSecretCipher(new byte[32])).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "cle de {0} octets refusee")
    @ValueSource(ints = {0, 1, 16, 24, 31, 33, 48, 64})
    void given_any_other_key_length_then_the_cipher_is_refused_at_construction(int length) {
        // Exactement 32, et pas « au moins 32 ». JCE n'accepte que 16, 24 ou 32 octets : une
        // cle de 33 construirait l'objet sans broncher puis ferait echouer chaque
        // chiffrement. 16 et 24 sont valides pour JCE mais refuses ici, TAS imposant AES-256.
        assertThatThrownBy(() -> new AesGcmSecretCipher(new byte[length]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SECRET_CIPHER_KEY_MUST_BE_32_BYTES");
    }

    @Test
    void given_no_key_then_the_cipher_is_refused_at_construction() {
        assertThatThrownBy(() -> new AesGcmSecretCipher(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SECRET_CIPHER_KEY_MUST_BE_32_BYTES");
    }

    @Test
    void given_an_oversized_key_then_it_is_refused_before_any_encryption_is_attempted() {
        // Le defaut que ce controle ferme : sans lui, l'objet se construit et l'echec
        // n'apparait qu'au premier secret a proteger, deguise en erreur de chiffrement.
        assertThatThrownBy(() -> new AesGcmSecretCipher(new byte[33]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContainingAny("ENCRYPT_FAILED");
    }

    // ---------- Indistinction des echecs ----------

    @Test
    void given_every_failure_cause_then_the_message_never_varies() {
        // Les cinq causes, et non deux qui empruntent la meme branche : chiffre vide,
        // illisible, tronque, altere, ou produit avec une autre cle. Distinguer ces cas
        // donnerait a qui sonde un oracle sur la structure de son entree.
        SecretCipher other = new AesGcmSecretCipher(aKey(2));

        byte[] raw = Base64.getDecoder().decode(cipher.encrypt(A_PRIVATE_KEY));
        raw[raw.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(raw);

        String blank = "   ";
        String notBase64 = "*** pas du base64 ***";
        String truncated = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        String foreign = other.encrypt(A_PRIVATE_KEY);

        assertThat(List.of(
                messageOf(blank),
                messageOf(notBase64),
                messageOf(truncated),
                messageOf(tampered),
                messageOf(foreign)))
                .containsOnly("SECRET_CIPHER_DECRYPT_FAILED");
    }

    @Test
    void given_a_failure_then_the_underlying_cause_remains_available_for_diagnosis() {
        // Le message est muet vers l'exterieur ; la cause reste chainee pour l'exploitant.
        assertThatThrownBy(() -> cipher.decrypt("*** pas du base64 ***"))
                .isInstanceOf(SecretDecryptionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    private String messageOf(String ciphertext) {
        try {
            cipher.decrypt(ciphertext);
            throw new AssertionError("le dechiffrement aurait du echouer");
        } catch (SecretDecryptionException e) {
            return e.getMessage();
        }
    }

    /** Cle deterministe par graine, pour que deux cles distinctes le restent. */
    private static byte[] aKey(long seed) {
        byte[] key = new byte[32];
        new SecureRandom(String.valueOf(seed).getBytes(StandardCharsets.UTF_8)).nextBytes(key);
        return key;
    }
}
