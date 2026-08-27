package com.takibo.authorizationserver.infrastructure.keys;

import com.takibo.authorizationserver.domain.keys.port.SecretCipher;
import com.takibo.authorizationserver.domain.keys.port.SecretContext;
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
 * Cinq proprietes, dont aucune n'est decorative :
 * <ul>
 *   <li><b>authentification</b> — sans elle, une cle privee alteree produirait des signatures
 *       invalides sans le moindre diagnostic ;</li>
 *   <li><b>liaison au contexte</b> — sans elle, un chiffre valide le reste partout, et se
 *       recopie d'un enregistrement a un autre ou d'un usage a un autre ;</li>
 *   <li><b>enveloppe authentifiee</b> — la version et le {@code keyId} voyagent en clair mais
 *       sont couverts par le tag, donc immuables de fait ;</li>
 *   <li><b>non-determinisme</b> — sans lui, deux chiffres egaux trahiraient deux secrets
 *       egaux ;</li>
 *   <li><b>indistinction des echecs</b> — toutes les causes donnent la meme erreur.</li>
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

    private static final SecretContext CONTEXT = SecretContext.signingKeyMaterial("kid-alpha");
    private static final SecretContext ANOTHER_RECORD = SecretContext.signingKeyMaterial("kid-beta");
    private static final SecretContext ANOTHER_TYPE =
            new SecretContext("oauth2_authorization.refresh_token", "kid-alpha");

    private final AesGcmSecretCipher cipher = new AesGcmSecretCipher(ACTIVE);

    // ---------- Aller-retour ----------

    @Test
    void given_a_secret_when_encrypted_then_it_is_restored_identically() {
        String sealed = cipher.encrypt(CONTEXT, A_PRIVATE_KEY);

        assertThat(sealed).isNotEqualTo(A_PRIVATE_KEY);
        assertThat(cipher.decrypt(CONTEXT, sealed)).isEqualTo(A_PRIVATE_KEY);
    }

    @Test
    void given_an_empty_secret_then_it_still_round_trips() {
        assertThat(cipher.decrypt(CONTEXT, cipher.encrypt(CONTEXT, ""))).isEmpty();
    }

    @Test
    void given_a_secret_with_non_ascii_characters_then_it_round_trips() {
        String secret = "clé-de-signature-éàü-é中文";

        assertThat(cipher.decrypt(CONTEXT, cipher.encrypt(CONTEXT, secret))).isEqualTo(secret);
    }

    // ---------- Liaison au contexte : le chiffre ne se deplace pas ----------

    @Test
    void given_a_ciphertext_moved_to_another_record_then_it_no_longer_decrypts() {
        // La faille que l'AAD ferme : sans elle, la matiere privee chiffree d'une cle
        // pourrait etre recopiee dans la ligne d'une autre, et se dechiffrerait.
        String sealedForAlpha = cipher.encrypt(CONTEXT, A_PRIVATE_KEY);

        assertThatThrownBy(() -> cipher.decrypt(ANOTHER_RECORD, sealedForAlpha))
                .isInstanceOf(SecretDecryptionException.class);
    }

    @Test
    void given_a_ciphertext_moved_to_another_usage_then_it_no_longer_decrypts() {
        // Meme enregistrement, autre colonne : un token chiffre glisse dans la colonne d'une
        // cle de signature doit etre refuse.
        String sealedAsSigningKey = cipher.encrypt(CONTEXT, A_PRIVATE_KEY);

        assertThatThrownBy(() -> cipher.decrypt(ANOTHER_TYPE, sealedAsSigningKey))
                .isInstanceOf(SecretDecryptionException.class);
    }

    @Test
    void given_the_right_context_then_the_same_ciphertext_decrypts() {
        // Contre-epreuve des deux precedents : seul le contexte les distingue.
        String sealed = cipher.encrypt(CONTEXT, A_PRIVATE_KEY);

        assertThat(cipher.decrypt(CONTEXT, sealed)).isEqualTo(A_PRIVATE_KEY);
    }

    // ---------- Enveloppe authentifiee ----------

    @Test
    void given_two_keys_sharing_material_when_the_key_identifier_is_rewritten_then_it_is_refused() {
        // Deux chiffreurs separes permettent de prouver que le keyId appartient bien a l'AAD,
        // sans autoriser un faux trousseau qui reutiliserait la meme matiere.
        byte[] shared = material(7);
        AesGcmSecretCipher alphaCipher =
                new AesGcmSecretCipher(new SecretCipherKey("alpha", shared));
        AesGcmSecretCipher betaCipher =
                new AesGcmSecretCipher(new SecretCipherKey("beta", shared));

        String sealedUnderAlpha = alphaCipher.encrypt(CONTEXT, A_PRIVATE_KEY);
        String relabelledAsBeta = sealedUnderAlpha.replaceFirst("\\$alpha\\$", "\\$beta\\$");

        assertThat(relabelledAsBeta).startsWith("v1$beta$");
        assertThatThrownBy(() -> betaCipher.decrypt(CONTEXT, relabelledAsBeta))
                .isInstanceOf(SecretDecryptionException.class);
        assertThat(alphaCipher.decrypt(CONTEXT, sealedUnderAlpha)).isEqualTo(A_PRIVATE_KEY);
    }

    @Test
    void given_a_ciphertext_relabelled_with_another_format_version_then_it_is_refused() {
        String fromTheFuture = "v2" + cipher.encrypt(CONTEXT, A_PRIVATE_KEY).substring(2);

        assertThatThrownBy(() -> cipher.decrypt(CONTEXT, fromTheFuture))
                .isInstanceOf(SecretDecryptionException.class);
    }

    // ---------- Format versionne et identifie ----------

    @Test
    void given_a_ciphertext_then_it_carries_the_format_version_and_the_key_identifier() {
        String sealed = cipher.encrypt(CONTEXT, A_PRIVATE_KEY);

        assertThat(sealed).startsWith("v1$platform-2026-08$");
        assertThat(sealed.split("\\$")).hasSize(3);
    }

    @Test
    void given_a_ciphertext_then_it_is_storable_as_text() {
        // La colonne private_key_encrypted est un VARCHAR : la sortie doit y tenir telle
        // quelle, sans encodage supplementaire.
        assertThat(cipher.encrypt(CONTEXT, A_PRIVATE_KEY))
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
        String sealedBefore = new AesGcmSecretCipher(RETIRED).encrypt(CONTEXT, A_PRIVATE_KEY);
        SecretCipher afterRotation = new AesGcmSecretCipher(ACTIVE, RETIRED);

        assertThat(afterRotation.decrypt(CONTEXT, sealedBefore)).isEqualTo(A_PRIVATE_KEY);
    }

    @Test
    void given_a_rotated_cipher_then_new_secrets_are_sealed_by_the_active_key() {
        AesGcmSecretCipher afterRotation = new AesGcmSecretCipher(ACTIVE, RETIRED);

        assertThat(afterRotation.encrypt(CONTEXT, A_PRIVATE_KEY))
                .startsWith("v1$platform-2026-08$");
    }

    @Test
    void given_a_retired_key_no_longer_accepted_then_its_secrets_become_unreadable() {
        // Le retrait effectif d'une cle : ses chiffres cessent d'etre lisibles, ce qui est le
        // but recherche une fois toutes les lignes rechiffrees.
        String sealedBefore = new AesGcmSecretCipher(RETIRED).encrypt(CONTEXT, A_PRIVATE_KEY);

        assertThatThrownBy(() -> cipher.decrypt(CONTEXT, sealedBefore))
                .isInstanceOf(SecretDecryptionException.class);
    }

    @Test
    void given_two_keys_sharing_an_identifier_then_the_cipher_is_refused_at_construction() {
        SecretCipherKey homonym = aKey("platform-2026-08", 9);

        assertThatThrownBy(() -> new AesGcmSecretCipher(ACTIVE, homonym))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SECRET_CIPHER_DUPLICATE_KEY_ID");
    }

    @Test
    void given_two_identifiers_sharing_material_then_the_cipher_is_refused_at_construction() {
        byte[] shared = material(11);

        assertThatThrownBy(() -> new AesGcmSecretCipher(
                new SecretCipherKey("old-key", shared),
                new SecretCipherKey("new-key", shared)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SECRET_CIPHER_DUPLICATE_KEY_MATERIAL");
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
        String first = cipher.encrypt(CONTEXT, A_PRIVATE_KEY);
        String second = cipher.encrypt(CONTEXT, A_PRIVATE_KEY);

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(CONTEXT, first)).isEqualTo(cipher.decrypt(CONTEXT, second));
    }

    // ---------- Authentification du clair ----------

    @Test
    void given_a_tampered_payload_then_decryption_is_refused() {
        // Un mode non authentifie, comme CBC, rendrait ici un clair corrompu sans erreur.
        String tampered = tamperedPayload(cipher.encrypt(CONTEXT, A_PRIVATE_KEY));

        assertThatThrownBy(() -> cipher.decrypt(CONTEXT, tampered))
                .isInstanceOf(SecretDecryptionException.class);
    }

    @Test
    void given_a_tampered_initialisation_vector_then_decryption_is_refused() {
        String[] parts = cipher.encrypt(CONTEXT, A_PRIVATE_KEY).split("\\$");
        byte[] payload = Base64.getDecoder().decode(parts[2]);
        payload[0] ^= 0x01;
        String tampered = parts[0] + "$" + parts[1] + "$"
                + Base64.getEncoder().encodeToString(payload);

        assertThatThrownBy(() -> cipher.decrypt(CONTEXT, tampered))
                .isInstanceOf(SecretDecryptionException.class);
    }

    @Test
    void given_the_same_key_identifier_but_another_material_then_decryption_is_refused() {
        SecretCipher impostor = new AesGcmSecretCipher(aKey("platform-2026-08", 9));
        String sealedByImpostor = impostor.encrypt(CONTEXT, A_PRIVATE_KEY);

        assertThatThrownBy(() -> cipher.decrypt(CONTEXT, sealedByImpostor))
                .isInstanceOf(SecretDecryptionException.class);
    }

    @Test
    void given_a_ciphertext_sealed_by_an_unknown_key_then_decryption_is_refused() {
        String sealedElsewhere = new AesGcmSecretCipher(FOREIGN).encrypt(CONTEXT, A_PRIVATE_KEY);

        assertThatThrownBy(() -> cipher.decrypt(CONTEXT, sealedElsewhere))
                .isInstanceOf(SecretDecryptionException.class);
    }

    // ---------- Entrees malformees ----------

    @Test
    void given_a_null_plaintext_then_encryption_is_refused() {
        assertThatThrownBy(() -> cipher.encrypt(CONTEXT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SECRET_CIPHER_REQUIRES_PLAINTEXT");
    }

    @Test
    void given_no_context_then_both_operations_are_refused() {
        // Le contexte n'est pas optionnel : l'omettre reviendrait a rendre le chiffre
        // deplacable, ce que ce port refuse par construction.
        assertThatThrownBy(() -> cipher.encrypt(null, A_PRIVATE_KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SECRET_CIPHER_REQUIRES_CONTEXT");
        assertThatThrownBy(() -> cipher.decrypt(null, "peu importe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SECRET_CIPHER_REQUIRES_CONTEXT");
    }

    // ---------- Indistinction des echecs ----------

    @Test
    void given_every_failure_cause_then_the_message_never_varies() {
        // Toutes les causes, et non deux qui empruntent la meme branche : absence, structure,
        // version, cle inconnue, base64 illisible, troncature, alteration, matiere etrangere,
        // et desormais contexte errone.
        String sealed = cipher.encrypt(CONTEXT, A_PRIVATE_KEY);
        String prefix = "v1$" + ACTIVE.id() + "$";

        assertThat(List.of(
                messageOf(CONTEXT, "   "),
                messageOf(CONTEXT, "sans-separateur"),
                messageOf(CONTEXT, "v1$une-seule-partie"),
                messageOf(CONTEXT, "v1$k$payload$de$trop"),
                messageOf(CONTEXT, "v2" + sealed.substring(2)),
                messageOf(CONTEXT, "v1$inconnue$" + sealed.split("\\$")[2]),
                messageOf(CONTEXT, prefix + "*** pas du base64 ***"),
                messageOf(CONTEXT, prefix + Base64.getEncoder().encodeToString(new byte[]{1, 2, 3})),
                messageOf(CONTEXT, tamperedPayload(sealed)),
                messageOf(CONTEXT, new AesGcmSecretCipher(FOREIGN).encrypt(CONTEXT, A_PRIVATE_KEY)),
                messageOf(ANOTHER_RECORD, sealed),
                messageOf(ANOTHER_TYPE, sealed)))
                .containsOnly("SECRET_CIPHER_DECRYPT_FAILED");
    }

    @Test
    void given_a_failure_then_the_underlying_cause_remains_available_for_diagnosis() {
        // Le message est muet vers l'exterieur ; la cause reste chainee pour l'exploitant.
        assertThatThrownBy(() ->
                cipher.decrypt(CONTEXT, "v1$" + ACTIVE.id() + "$*** pas du base64 ***"))
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

    private String messageOf(SecretContext context, String ciphertext) {
        try {
            cipher.decrypt(context, ciphertext);
            throw new AssertionError("le dechiffrement aurait du echouer : " + ciphertext);
        } catch (SecretDecryptionException e) {
            return e.getMessage();
        }
    }

    private static SecretCipherKey aKey(String id, long seed) {
        return new SecretCipherKey(id, material(seed));
    }

    /** Matiere deterministe par graine, pour que deux cles distinctes le restent. */
    private static byte[] material(long seed) {
        byte[] material = new byte[32];
        new SecureRandom(String.valueOf(seed).getBytes(StandardCharsets.UTF_8))
                .nextBytes(material);
        return material;
    }
}
