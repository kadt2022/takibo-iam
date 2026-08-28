package com.takibo.authorizationserver.infrastructure.keys;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/**
 * Génère la matière RSA d'une nouvelle clé de signature de plateforme (TAS-GRANTS-02A,
 * rotation).
 * <p>
 * RSA-2048 avec {@code alg=RS256} et {@code use=sig} explicites : {@code PersistentJwkSource}
 * confronte ensuite la partie publique de cette clé à {@code public_jwk_json}, et une clé sans
 * algorithme ni usage déclarés produirait une comparaison qui ne dit rien de ce que la clé sert
 * réellement à faire.
 * <p>
 * Concrète et non un port : contrairement au stockage, remplaçable par un KMS sans changer le
 * domaine (voir {@link com.takibo.authorizationserver.domain.keys.port.SigningKeyWriter}), la
 * génération elle-même sort du périmètre de ce récit — un KMS générerait la matière côté KMS,
 * ce qui est une conception différente de « générer ici puis chiffrer avant stockage ».
 */
public final class RsaSigningKeyGenerator {

    private static final int KEY_SIZE_BITS = 2048;

    private RsaSigningKeyGenerator() {
    }

    public static GeneratedSigningKeyMaterial generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(KEY_SIZE_BITS);
            KeyPair pair = generator.generateKeyPair();

            RSAKey jwk = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .algorithm(JWSAlgorithm.RS256)
                    .keyUse(KeyUse.SIGNATURE)
                    .build();

            return new GeneratedSigningKeyMaterial(jwk);
        } catch (NoSuchAlgorithmException e) {
            // RSA est garanti par toute JVM conforme : n'atteindrait ce chemin que sur une
            // installation cassée, jamais en fonctionnement normal.
            throw new IllegalStateException("RSA_ALGORITHM_UNAVAILABLE", e);
        }
    }
}
