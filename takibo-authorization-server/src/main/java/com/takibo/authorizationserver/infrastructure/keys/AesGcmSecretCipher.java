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
    private static final int MIN_KEY_LENGTH_BYTES = 32;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param key cle brute, 32 octets au minimum (AES-256)
     */
    public AesGcmSecretCipher(byte[] key) {
        if (key == null || key.length < MIN_KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException(
                    "SECRET_CIPHER_KEY_TOO_SHORT: at least " + MIN_KEY_LENGTH_BYTES
                            + " bytes are required");
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

    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new SecretDecryptionException("SECRET_CIPHER_REQUIRES_CIPHERTEXT");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(ciphertext);
        } catch (IllegalArgumentException e) {
            throw new SecretDecryptionException("SECRET_CIPHER_CIPHERTEXT_NOT_READABLE", e);
        }
        if (decoded.length <= IV_LENGTH_BYTES) {
            throw new SecretDecryptionException("SECRET_CIPHER_CIPHERTEXT_TRUNCATED");
        }
        try {
            byte[] iv = Arrays.copyOfRange(decoded, 0, IV_LENGTH_BYTES);
            byte[] sealed = Arrays.copyOfRange(decoded, IV_LENGTH_BYTES, decoded.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(sealed), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Chiffre altere, tronque, ou produit avec une autre cle : indistinguables,
            // et c'est voulu.
            throw new SecretDecryptionException("SECRET_CIPHER_DECRYPT_FAILED", e);
        }
    }
}
