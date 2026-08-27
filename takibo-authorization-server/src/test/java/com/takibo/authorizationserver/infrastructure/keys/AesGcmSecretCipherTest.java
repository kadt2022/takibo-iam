package com.takibo.authorizationserver.infrastructure.keys;

import com.takibo.authorizationserver.domain.keys.port.SecretCipher;
import com.takibo.authorizationserver.domain.keys.port.SecretDecryptionException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat du chiffrement au repos (TAS-GRANTS-02A).
 * <p>
 * Ce port protege deux familles de secrets que TAS doit pouvoir <b>relire</b> : la matiere
 * privee des cles de signature, et bientot les valeurs de codes et de tokens du recit 02.
 * Quatre proprietes, dont aucune n'est decorative :
 * <ul>
 *   <li><b>authentification</b> — sans elle, une cle privee alteree produirait des signatures
 *       invalides sans le moindre diagnostic ;</li>
 *   <li><b>non-determinisme</b> — sans lui, deux chiffres egaux trahiraient deux secrets
 *       egaux ;</li>
 *   <li><b>format versionne et identifie</b> — sans version le format ne peut plus evoluer,
 *       sans identifiant de cle la cle de chiffrement ne peut plus tourner ;</li>
 *   <li><b>indistinction des echecs</b> — toutes les causes donnent la meme erreur, pour ne
 *       rien apprendre a qui sonde.</li>
 * </ul>
 */
class AesGcmSecretCipherTest {

    private static final String A_PRIVATE_KEY = """
            -----BEGIN PRIVATE KEY-----
            MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC7VJTUt9Us8cKj
            -----END PRIVATE KEY-----""";

    private static final SecretCipherKey ACTIVE = aKey("platform-2026-08", 1);
    private static final SecretCipherKey RETIRED = aKey("platform-2026-02", 2);
    private static final SecretCipherKey FOREIGN = aKey("ailleurs", 3);

    private final AesGcmSecretCipher cipher = new AesGcmSecretCipher(ACTIVE);

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

    // ---------- Format versionne et identifie ----------

    @Test
    void given_a_ciphertext_then_it_carries_the_format_version_and_the_key_identifier() {
        String sealed = cipher.encrypt(A_PRIVATE_KEY);

        assertThat(sealed).startsWith("v1$platform-2026-08$");
        assertThat(sealed.split("\\$")).hasSize(3);
    }

    @Test
    void given_a_ciphertext_then_it_is_storable_as_text() {
        // La colonne private_key_encrypted est un VARCHAR : la sortie doit y tenir telle
        // quelle, sans encodage supplementaire.
        assertThat(cipher.encrypt(A_PRIVATE_KEY))
                .matches("^v1\\$[A-Za-z0-9_-]{1,64}\\$[A-Za-z0-9+/]+={0,2}$");
    }

    @Test
    void given_a_cipher_then_it_names_the_key_that_seals_now() {
        assertThat(cipher.activeKeyId()).isEqualTo("platform-2026-08");
    }

    // ---------- Rotation de la cle de chiffrement ----------

    @Test
    void given_a_secret_sealed_by_a_retired_key_then_it_is_still_readable() {
        // Ce que l'identifiant de cle rend possible : la nouvelle cle chiffre, l'ancienne
        // reste acceptee en lecture, et les lignes migrent a leur rythme plutot que d'un bloc.
        String sealedBefore = new AesGcmSecretCipher(RETIRED).encrypt(A_PRIVATE_KEY);
        SecretCipher afterRotation = new AesGcmSecretCipher(ACTIVE, RETIRED);

        assertThat(afterRotation.decrypt(sealedBefore)).isEqualTo(A_PRIVATE_KEY);
    }

    @Test
    void given_a_rotated_cipher_then_new_secrets_are_sealed_by_the_active_key() {
        AesGcmSecretCipher afterRotation = new AesGcmSecretCipher(ACTIVE, RETIRED);

        assertThat(afterRotation.encrypt(A_PRIVATE_KEY)).startsWith("v1$platform-2026-08$");
    }

    @Test
    void given_a_retired_key_no_longer_accepted_then_its_secrets_become_unreadable() {
        // Le retrait effectif d'une cle : ses chiffres cessent d'etre lisibles, ce qui est le
        // but recherche une fois toutes les lignes rechiffrees.
        String sealedBefore = new AesGcmSecretCipher(RETIRED).encrypt(A_PRIVATE_KEY);

        assertThatThrownBy(() -> cipher.decrypt(sealedBefore))
                .isInstanceOf(SecretDecryptionException.class);
    }

    @Test
    void given_two_keys_sharing_an_identifier_then_the_cipher_is_refused_at_construction() {
        // Deux matieres sous le meme identifiant rendraient indechiffrables les chiffres de
        // l'une des deux, sans qu'on sache laquelle.
        SecretCipherKey homonym = aKey("platform-2026-08", 9);

        assertThatThrownBy(() -> new AesGcmSecretCipher(ACTIVE, homonym))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SECRET_CIPHER_DUPLICATE_KEY_ID");
    }

    @Test
    void given_no_active_key_then_the_cipher_is_refused_at_construction() {
        assertThatThrownBy(() -> new AesGcmSecretCipher(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SECRET_CIPHER_REQUIRES_AN_ACTIVE_KEY");
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
    void given_a_tampered_payload_then_decryption_is_refused() {
        // Un mode non authentifie, comme CBC, rendrait ici un clair corrompu sans erreur.
        String tampered = tamperedPayload(cipher.encrypt(A_PRIVATE_KEY));

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(SecretDecryptionException.class);
    }

    @Test
    void given_a_tampered_initialisation_vector_then_decryption_is_refused() {
        String[] parts = cipher.encrypt(A_PRIVATE_KEY).split("\\$");
        byte[] payload = Base64.getDecoder().decode(parts[2]);
        payload[0] ^= 0x01;
        String tampered = parts[0] + "$" + parts[1] + "$"
                + Base64.getEncoder().encodeToString(payload);

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(SecretDecryptionException.class);
    }

    @Test
    void given_the_same_key_identifier_but_another_material_then_decryption_is_refused() {
        // Identifiant declare identique, matiere differente : GCM le detecte.
        SecretCipher impostor = new AesGcmSecretCipher(aKey("platform-2026-08", 9));
        String sealedByImpostor = impostor.encrypt(A_PRIVATE_KEY);

        assertThatThrownBy(() -> cipher.decrypt(sealedByImpostor))
                .isInstanceOf(SecretDecryptionException.class);
    }

    @Test
    void given_a_ciphertext_sealed_by_an_unknown_key_then_decryption_is_refused() {
        String sealedElsewhere = new AesGcmSecretCipher(FOREIGN).encrypt(A_PRIVATE_KEY);

        assertThatThrownBy(() -> cipher.decrypt(sealedElsewhere))
                .isInstanceOf(SecretDecryptionException.class);
    }

    // ---------- Entrees malformees ----------

    @Test
    void given_a_null_plaintext_then_encryption_is_refused() {
        assertThatThrownBy(() -> cipher.encrypt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SECRET_CIPHER_REQUIRES_PLAINTEXT");
    }

    @Test
    void given_a_ciphertext_of_an_unknown_format_version_then_decryption_is_refused() {
        // Ce que la version protege : un format futur ne doit pas etre lu par erreur avec les
        // regles d'aujourd'hui.
        String fromTheFuture = "v2" + cipher.encrypt(A_PRIVATE_KEY).substring(2);

        assertThatThrownBy(() -> cipher.decrypt(fromTheFuture))
                .isInstanceOf(SecretDecryptionException.class);
    }

    // ---------- Indistinction des echecs ----------

    @Test
    void given_every_failure_cause_then_the_message_never_varies() {
        // Toutes les causes, et non deux qui empruntent la meme branche : absence, structure,
        // version, cle inconnue, base64 illisible, troncature, alteration, matiere etrangere.
        String sealed = cipher.encrypt(A_PRIVATE_KEY);
        String prefix = "v1$" + ACTIVE.id() + "$";

        assertThat(List.of(
                messageOf("   "),
                messageOf("sans-separateur"),
                messageOf("v1$une-seule-partie"),
                messageOf("v1$k$payload$de$trop"),
                messageOf("v2" + sealed.substring(2)),
                messageOf("v1$inconnue$" + sealed.split("\\$")[2]),
                messageOf(prefix + "*** pas du base64 ***"),
                messageOf(prefix + Base64.getEncoder().encodeToString(new byte[]{1, 2, 3})),
                messageOf(tamperedPayload(sealed)),
                messageOf(new AesGcmSecretCipher(FOREIGN).encrypt(A_PRIVATE_KEY))))
                .containsOnly("SECRET_CIPHER_DECRYPT_FAILED");
    }

    @Test
    void given_a_failure_then_the_underlying_cause_remains_available_for_diagnosis() {
        // Le message est muet vers l'exterieur ; la cause reste chainee pour l'exploitant.
        assertThatThrownBy(() -> cipher.decrypt("v1$" + ACTIVE.id() + "$*** pas du base64 ***"))
                .isInstanceOf(SecretDecryptionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    // ---------- Fixtures ----------

    private static String tamperedPayload(String sealed) {
        String[] parts = sealed.split("\\$");
        byte[] payload = Base64.getDecoder().decode(parts[2]);
        payload[payload.length - 1] ^= 0x01;
        return parts[0] + "$" + parts[1] + "$" + Base64.getEncoder().encodeToString(payload);
    }

    private String messageOf(String ciphertext) {
        try {
            cipher.decrypt(ciphertext);
            throw new AssertionError("le dechiffrement aurait du echouer : " + ciphertext);
        } catch (SecretDecryptionException e) {
            return e.getMessage();
        }
    }

    /** Matiere deterministe par graine, pour que deux cles distinctes le restent. */
    private static SecretCipherKey aKey(String id, long seed) {
        byte[] material = new byte[32];
        new SecureRandom(String.valueOf(seed).getBytes(StandardCharsets.UTF_8))
                .nextBytes(material);
        return new SecretCipherKey(id, material);
    }
}
