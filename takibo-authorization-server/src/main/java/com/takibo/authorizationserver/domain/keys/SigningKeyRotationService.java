package com.takibo.authorizationserver.domain.keys;

import com.nimbusds.jose.jwk.JWK;
import com.takibo.authorizationserver.domain.keys.model.NewSigningKey;
import com.takibo.authorizationserver.domain.keys.port.SecretCipher;
import com.takibo.authorizationserver.domain.keys.port.SecretContext;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyWriter;
import com.takibo.authorizationserver.infrastructure.keys.GeneratedSigningKeyMaterial;
import com.takibo.authorizationserver.infrastructure.keys.RsaSigningKeyGenerator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rotation des clés de signature de plateforme (TAS-GRANTS-02A).
 * <p>
 * Une seule opération : générer une clé neuve, chiffrer sa matière privée, l'activer. Le reste
 * — publier l'ancienne le temps qu'il faut, refuser de signer avec plus d'une clé à la fois —
 * est déjà porté par {@code PersistentJwkSource} et le schéma ; ce service ne fait qu'y
 * introduire la clé neuve.
 * <p>
 * Ce service ne sait pas combien de temps durent les tokens signés avec l'ancienne clé — TAS
 * n'a qu'un seul {@code JwtEncoder}, partagé entre tokens humains et machine, aux durées de
 * vie distinctes. C'est pourquoi {@code retirementGracePeriod} est un paramètre de l'appel et
 * non une constante : au périmètre de ce récit, désigner cette durée reste une décision de
 * l'appelant, pas de ce service.
 */
public class SigningKeyRotationService {

    private final SigningKeyWriter writer;
    private final SecretCipher cipher;
    private final Clock clock;

    public SigningKeyRotationService(SigningKeyWriter writer, SecretCipher cipher, Clock clock) {
        this.writer = writer;
        this.cipher = cipher;
        this.clock = clock;
    }

    /**
     * @param retirementGracePeriod doit couvrir la durée de vie maximale d'un token que
     *                              l'ancienne émettrice a pu signer ; trop court, un JWT
     *                              encore valide cesserait de se vérifier avant son expiration
     *                              propre
     * @return le {@code kid} de la clé nouvellement activée
     */
    public String rotate(Duration retirementGracePeriod) {
        if (retirementGracePeriod == null || retirementGracePeriod.isNegative()) {
            throw new IllegalArgumentException("SIGNING_KEY_ROTATION_REQUIRES_A_GRACE_PERIOD");
        }

        GeneratedSigningKeyMaterial generated = RsaSigningKeyGenerator.generate();
        JWK privateJwk = generated.privateJwk();
        String kid = privateJwk.getKeyID();

        String encryptedPrivateMaterial = cipher.encrypt(
                SecretContext.signingKeyMaterial(kid), privateJwk.toJSONString());

        NewSigningKey newKey = new NewSigningKey(
                kid,
                privateJwk.getAlgorithm().getName(),
                privateJwk.getKeyType().getValue(),
                privateJwk.getKeyUse().getValue(),
                publicJsonOf(privateJwk),
                encryptedPrivateMaterial);

        Instant now = clock.instant();
        writer.activateNewIssuer(newKey, now.plus(retirementGracePeriod));
        return kid;
    }

    /**
     * {@code toPublicJWK().toJSONObject()}, et non {@code toJSONObject()} directement : la
     * seconde inclurait la matière privée. C'est exactement l'asymétrie que
     * {@code PersistentJwkSource} vérifie à la lecture — la produire correctement ici évite
     * qu'elle ne le découvre au premier démarrage.
     */
    private static Map<String, Object> publicJsonOf(JWK privateJwk) {
        return new LinkedHashMap<>(privateJwk.toPublicJWK().toJSONObject());
    }
}
