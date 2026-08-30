package com.takibo.authorizationserver.domain.authorization;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Hash SHA-256 hexadécimal minuscule d'une valeur de token/code (TAS-GRANTS-02).
 * <p>
 * Distinct du chiffrement : ce hash sert à <b>retrouver</b> une ligne par sa valeur — jamais à
 * la restituer. C'est pourquoi {@code oauth2_authorization} porte les deux, jamais l'un à la
 * place de l'autre : le hash indexe {@code findByToken(...)}, la valeur chiffrée (voir
 * {@link EncryptedTokenValue}) est ce que Spring Authorization Server doit relire.
 * <p>
 * Le format — 64 caractères hexadécimaux minuscules — correspond exactement aux contraintes
 * {@code CHECK (... ~ '^[a-f0-9]{64}$')} posées par la migration d'origine
 * (V202601091233) sur chaque colonne {@code *_hash}.
 */
public final class TokenHash {

    private TokenHash() {
    }

    /** @return le hash SHA-256 de {@code plaintext}, en hexadécimal minuscule (64 caractères) */
    public static String sha256Hex(String plaintext) {
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plaintext.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est garanti present par toute JVM conforme (JLS, algorithmes standards) :
            // une absence ici denoterait un environnement d'execution casse, pas une entree
            // invalide - il n'y a rien de plus specifique a faire que de le signaler tel quel.
            throw new IllegalStateException("SHA-256 unavailable in this JVM", e);
        }
    }
}
