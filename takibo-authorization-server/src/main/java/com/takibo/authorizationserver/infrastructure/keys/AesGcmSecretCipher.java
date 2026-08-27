package com.takibo.authorizationserver.infrastructure.keys;

import com.takibo.authorizationserver.domain.keys.port.SecretCipher;
import com.takibo.authorizationserver.domain.keys.port.SecretContext;
import com.takibo.authorizationserver.domain.keys.port.SecretDecryptionException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Implementation AES-GCM du {@link SecretCipher}.
 * <p>
 * GCM est choisi pour son authentification integree : toute alteration du chiffre est detectee
 * au dechiffrement plutot que de produire un clair corrompu. Un mode sans authentification,
 * comme CBC, rendrait une cle privee silencieusement fausse — et une signature invalide sans
 * diagnostic.
 *
 * <h2>Format du chiffre</h2>
 * <pre>{@code v1$<keyId>$<base64(iv || chiffre || tag)>}</pre>
 * Trois parties, et chacune repond a un manque precis.
 * <ul>
 *   <li><b>La version</b> permet au format d'evoluer. Sans elle, changer d'algorithme ou de
 *       taille d'IV rendrait illisible tout ce qui a deja ete stocke : rien ne distinguerait
 *       un ancien chiffre d'un nouveau.</li>
 *   <li><b>L'identifiant de cle</b> permet a la cle de chiffrement de tourner. Sans lui, on
 *       ignorerait quelle cle a scelle quelle ligne — il faudrait tout rechiffrer d'un seul
 *       tenant, ou ne jamais changer de cle. Avec lui, l'ancienne reste acceptee en lecture
 *       pendant que la nouvelle chiffre.</li>
 *   <li><b>L'IV, tire au hasard a chaque appel</b>, rend la sortie autoportante et non
 *       deterministe : sans cela, l'egalite de deux chiffres trahirait l'egalite de deux
 *       secrets.</li>
 * </ul>
 * Le separateur {@code $} est hors de l'alphabet base64 et interdit dans un identifiant de
 * cle : le decoupage est donc sans ambiguite.
 *
 * <h2>Ce que le tag couvre</h2>
 * L'enveloppe {@code v1$<keyId>$} est en clair, mais elle n'est pas pour autant modifiable :
 * elle est liee au chiffre comme donnee authentifiee additionnelle, avec le
 * {@link SecretContext}. AES-GCM authentifie alors {@code v1$<keyId>$<type>$<recordId>} en
 * plus du clair.
 * <p>
 * Sans cela, deux failles resteraient ouvertes. Reecrire le {@code keyId} de l'enveloppe
 * passerait inapercu des lors que deux cles partagent une matiere. Et surtout, un chiffre
 * valide resterait valide <b>partout</b> : on pourrait recopier la matiere privee chiffree
 * d'une cle dans la ligne d'une autre, ou glisser un token chiffre dans la colonne d'une cle
 * de signature. GCM authentifie ce qu'il scelle, jamais l'endroit ou on le range — l'AAD s'en
 * charge.
 *
 * <h2>Echecs</h2>
 * Tout echec de dechiffrement porte le meme message, quelle qu'en soit la cause. La cle n'est
 * jamais journalisee ni incluse dans un message d'erreur.
 */
public class AesGcmSecretCipher implements SecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ALGORITHM = "AES";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    /** Version du format produit aujourd'hui, et seule version acceptee en lecture. */
    static final String FORMAT_VERSION = "v1";
    static final String FORMAT_SEPARATOR = "$";
    private static final int FORMAT_PART_COUNT = 3;

    /**
     * Message unique de tout echec de dechiffrement : chiffre absent, mal forme, de version
     * inconnue, scelle par une cle inconnue, tronque, altere. Distinguer ces cas donnerait a
     * qui sonde un oracle sur la structure de son entree. La cause reelle reste chainee.
     */
    private static final String DECRYPT_FAILED = "SECRET_CIPHER_DECRYPT_FAILED";

    private final SecretCipherKey activeKey;
    private final Map<String, SecretKeySpec> keysById = new LinkedHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /**
     * @param activeKey       cle qui chiffre, et qui dechiffre ce qu'elle a scelle
     * @param acceptedForRead cles retirees, encore acceptees en lecture le temps que les
     *                        lignes qu'elles ont scellees soient rechiffrees
     */
    public AesGcmSecretCipher(SecretCipherKey activeKey, SecretCipherKey... acceptedForRead) {
        if (activeKey == null) {
            throw new IllegalArgumentException("SECRET_CIPHER_REQUIRES_AN_ACTIVE_KEY");
        }
        this.activeKey = activeKey;
        register(activeKey);
        for (SecretCipherKey key : acceptedForRead) {
            if (key == null) {
                throw new IllegalArgumentException("SECRET_CIPHER_REQUIRES_AN_ACTIVE_KEY");
            }
            register(key);
        }
    }

    private void register(SecretCipherKey key) {
        SecretKeySpec previous =
                keysById.put(key.id(), new SecretKeySpec(key.material(), ALGORITHM));
        if (previous != null) {
            // Deux matieres sous le meme identifiant : le chiffre deviendrait indechiffrable
            // pour l'une des deux, sans qu'on sache laquelle.
            throw new IllegalArgumentException("SECRET_CIPHER_DUPLICATE_KEY_ID: " + key.id());
        }
    }

    /** Identifiant inscrit dans les chiffres produits maintenant. */
    public String activeKeyId() {
        return activeKey.id();
    }

    @Override
    public String encrypt(SecretContext context, String plaintext) {
        if (context == null) {
            throw new IllegalArgumentException("SECRET_CIPHER_REQUIRES_CONTEXT");
        }
        if (plaintext == null) {
            throw new IllegalArgumentException("SECRET_CIPHER_REQUIRES_PLAINTEXT");
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keysById.get(activeKey.id()),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(associatedData(activeKey.id(), context));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(sealed, 0, payload, iv.length, sealed.length);

            return FORMAT_VERSION + FORMAT_SEPARATOR
                    + activeKey.id() + FORMAT_SEPARATOR
                    + Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            // Le message ne porte ni le clair ni la cle.
            throw new IllegalStateException("SECRET_CIPHER_ENCRYPT_FAILED", e);
        }
    }

    @Override
    public String decrypt(SecretContext context, String ciphertext) {
        if (context == null) {
            throw new IllegalArgumentException("SECRET_CIPHER_REQUIRES_CONTEXT");
        }
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new SecretDecryptionException(DECRYPT_FAILED);
        }
        String[] parts = ciphertext.split("\\" + FORMAT_SEPARATOR, -1);
        if (parts.length != FORMAT_PART_COUNT || !FORMAT_VERSION.equals(parts[0])) {
            throw new SecretDecryptionException(DECRYPT_FAILED);
        }
        SecretKeySpec key = keysById.get(parts[1]);
        if (key == null) {
            throw new SecretDecryptionException(DECRYPT_FAILED);
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new SecretDecryptionException(DECRYPT_FAILED, e);
        }
        if (decoded.length <= IV_LENGTH_BYTES) {
            throw new SecretDecryptionException(DECRYPT_FAILED);
        }
        try {
            byte[] iv = Arrays.copyOfRange(decoded, 0, IV_LENGTH_BYTES);
            byte[] sealed = Arrays.copyOfRange(decoded, IV_LENGTH_BYTES, decoded.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            // L'AAD est reconstruite depuis l'enveloppe lue et le contexte fourni par
            // l'appelant. Toute divergence — keyId reecrit, chiffre deplace vers un autre
            // enregistrement ou un autre usage — donne une AAD differente, et le tag GCM
            // refuse. C'est ce qui lie le chiffre a sa place.
            cipher.updateAAD(associatedData(parts[1], context));
            return new String(cipher.doFinal(sealed), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new SecretDecryptionException(DECRYPT_FAILED, e);
        }
    }

    /**
     * Donnee authentifiee additionnelle : {@code v1$<keyId>$<type>$<recordId>}.
     * <p>
     * Elle n'est pas chiffree et n'est pas stockee — l'enveloppe porte deja la version et le
     * {@code keyId}, l'appelant reconstruit le contexte. Elle est en revanche <b>couverte par
     * le tag</b>, donc immuable de fait : la modifier invalide le chiffre.
     * <p>
     * Aucun champ ne peut contenir le separateur, ce que garantissent {@link SecretCipherKey}
     * et {@link SecretContext}. Deux contextes distincts ne peuvent donc pas produire la meme
     * AAD.
     */
    private static byte[] associatedData(String keyId, SecretContext context) {
        return (FORMAT_VERSION + FORMAT_SEPARATOR
                + keyId + FORMAT_SEPARATOR
                + context.type() + FORMAT_SEPARATOR
                + context.recordId())
                .getBytes(StandardCharsets.UTF_8);
    }
}
