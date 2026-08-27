package com.takibo.authorizationserver.infrastructure.keys;

import com.takibo.authorizationserver.domain.keys.port.SecretCipher;
import com.takibo.authorizationserver.domain.keys.port.SecretDecryptionException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Implementation AES-GCM du {@link SecretCipher}.
 * <p>
 * GCM est choisi pour son authentification integree : toute alteration du chiffre est
 * detectee au dechiffrement plutot que de produire un clair corrompu. Un mode sans
 * authentification, comme CBC, rendrait une cle privee silencieusement fausse — et une
 * signature invalide sans diagnostic.
 * <p>
 * Format de sortie : {@code base64(iv || chiffre || tag)}. Le vecteur d'initialisation est
 * tire au hasard a chaque appel et prefixe au resultat. Deux consequences voulues : la sortie
 * est autoportante, et deux chiffrements du meme clair different — sans quoi l'egalite de
 * deux chiffres trahirait l'egalite de deux secrets.
 * <p>
 * La cle n'est jamais journalisee, ni incluse dans un message d'erreur.
 */
public class AesGcmSecretCipher implements SecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ALGORITHM = "AES";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int REQUIRED_KEY_LENGTH_BYTES = 32;

    /**
     * Message unique de tout echec de dechiffrement. Voir {@link #decrypt(String)} : la cause
     * exacte ne doit pas transparaitre.
     */
    private static final String DECRYPT_FAILED = "SECRET_CIPHER_DECRYPT_FAILED";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param key cle brute de <b>exactement</b> 32 octets (AES-256)
     * @throws IllegalArgumentException si la longueur differe
     */
    public AesGcmSecretCipher(byte[] key) {
        // Exactement 32, pas « au moins 32 ». JCE n'accepte que 16, 24 ou 32 octets : une cle
        // de 33 octets construirait l'objet sans broncher, puis ferait echouer chaque
        // chiffrement avec InvalidKeyException. Une configuration fautive doit tomber ici,
        // au demarrage, pas au premier secret a proteger.
        if (key == null || key.length != REQUIRED_KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException(
                    "SECRET_CIPHER_KEY_MUST_BE_" + REQUIRED_KEY_LENGTH_BYTES + "_BYTES");
        }
        this.key = new SecretKeySpec(key, ALGORITHM);
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("SECRET_CIPHER_REQUIRES_PLAINTEXT");
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] output = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, output, 0, iv.length);
            System.arraycopy(sealed, 0, output, iv.length, sealed.length);
            return Base64.getEncoder().encodeToString(output);
        } catch (Exception e) {
            // Le message ne porte ni le clair ni la cle.
            throw new IllegalStateException("SECRET_CIPHER_ENCRYPT_FAILED", e);
        }
    }

    /**
     * Tout echec porte le meme message, quelle qu'en soit la cause : chiffre absent, illisible,
     * tronque, altere, ou produit avec une autre cle. Distinguer ces cas donnerait a qui sonde
     * un oracle sur la structure de l'entree — il saurait si son chiffre forge est bien forme
     * avant meme de s'attaquer a la cle. La cause reelle reste disponible dans l'exception
     * chainee, pour le diagnostic serveur.
     */
    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new SecretDecryptionException(DECRYPT_FAILED);
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(ciphertext);
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
            return new String(cipher.doFinal(sealed), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new SecretDecryptionException(DECRYPT_FAILED, e);
        }
    }
}
