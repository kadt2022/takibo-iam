package com.takibo.authorizationserver.infrastructure.keys;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.takibo.authorizationserver.domain.keys.model.GeneratedSigningKeyMaterial;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyMaterialGenerator;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * Adaptateur RSA local du port {@link SigningKeyMaterialGenerator} (TAS-GRANTS-02A, rotation).
 * <p>
 * RSA-2048 avec {@code alg=RS256} et {@code use=sig} explicites : {@code PersistentJwkSource}
 * confronte ensuite la partie publique de cette clé à {@code public_jwk_json}, et une clé sans
 * algorithme ni usage déclarés produirait une comparaison qui ne dit rien de ce que la clé sert
 * réellement à faire.
 * <p>
 * Nimbus ({@link JWK}) ne sort jamais de cet adaptateur : {@link #generate()} traduit
 * immédiatement vers {@link GeneratedSigningKeyMaterial}, le type neutre que le domaine
 * connaît. C'est ce qui rend cette classe remplaçable par un KMS ou un HSM sans toucher
 * {@code SigningKeyRotationService} — seul le câblage dans
 * {@code SigningKeysConfiguration} changerait.
 */
public class RsaSigningKeyGenerator implements SigningKeyMaterialGenerator {

    private static final int KEY_SIZE_BITS = 2048;

    @Override
    public GeneratedSigningKeyMaterial generate() {
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

            return new GeneratedSigningKeyMaterial(
                    jwk.getKeyID(),
                    jwk.getAlgorithm().getName(),
                    jwk.getKeyType().getValue(),
                    jwk.getKeyUse().getValue(),
                    new LinkedHashMap<>(jwk.toPublicJWK().toJSONObject()),
                    jwk.toJSONString());
        } catch (NoSuchAlgorithmException e) {
            // RSA est garanti par toute JVM conforme : n'atteindrait ce chemin que sur une
            // installation cassée, jamais en fonctionnement normal.
            throw new IllegalStateException("RSA_ALGORITHM_UNAVAILABLE", e);
        }
    }
}
