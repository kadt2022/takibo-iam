package com.takibo.authorizationserver.domain.keys;

import com.takibo.authorizationserver.domain.keys.model.GeneratedSigningKeyMaterial;
import com.takibo.authorizationserver.domain.keys.model.NewSigningKey;
import com.takibo.authorizationserver.domain.keys.port.SecretCipher;
import com.takibo.authorizationserver.domain.keys.port.SecretContext;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyMaterialGenerator;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyWriter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Amorçage et rotation des clés de signature de plateforme (TAS-GRANTS-02A).
 * <p>
 * Deux opérations, pas une : {@link #initializeFirstIssuer()} pour une installation qui n'a
 * encore aucune émettrice, {@link #rotate(Duration)} pour en remplacer une qui existe déjà.
 * Les confondre a un coût réel — un chevauchement nul retirerait l'ancienne émettrice avant
 * l'expiration des JWT qu'elle a signés, les rendant soudain invérifiables. C'est pourquoi
 * {@link #rotate(Duration)} exige un chevauchement strictement positif là où l'amorçage n'a
 * rien à retirer et n'en a pas besoin.
 * <p>
 * Ce service ne connaît ni Nimbus ni RSA : {@link SigningKeyMaterialGenerator} est le seul
 * point d'entrée de la matière neuve, ce qui rend la génération remplaçable par un KMS ou un
 * HSM sans toucher ce service.
 * <p>
 * Ce service ne sait pas non plus combien de temps durent les tokens signés avec l'ancienne
 * clé — TAS n'a qu'un seul {@code JwtEncoder}, partagé entre tokens humains et machine, aux
 * durées de vie distinctes. C'est pourquoi le chevauchement est un paramètre de l'appel et
 * non une constante : au périmètre de ce récit, désigner cette durée reste une décision de
 * l'appelant, pas de ce service.
 */
public class SigningKeyRotationService {

    private final SigningKeyMaterialGenerator generator;
    private final SigningKeyWriter writer;
    private final SecretCipher cipher;
    private final Clock clock;

    public SigningKeyRotationService(SigningKeyMaterialGenerator generator, SigningKeyWriter writer,
                                      SecretCipher cipher, Clock clock) {
        this.generator = generator;
        this.writer = writer;
        this.cipher = cipher;
        this.clock = clock;
    }

    /**
     * Amorce une installation qui n'a encore aucune émettrice de plateforme.
     * <p>
     * S'il en existe déjà une, l'écriture échoue — voir
     * {@link SigningKeyWriter#activateFirstIssuer}. Utiliser {@link #rotate(Duration)} pour
     * remplacer une émettrice existante.
     *
     * @return le {@code kid} de la clé activée
     */
    public String initializeFirstIssuer() {
        NewSigningKey newKey = sealedNewKey();
        writer.activateFirstIssuer(newKey);
        return newKey.kid();
    }

    /**
     * Remplace l'émettrice de plateforme actuellement active par une clé neuve.
     * <p>
     * S'il n'en existe aucune, l'écriture échoue — voir
     * {@link SigningKeyWriter#activateNewIssuer}. Utiliser {@link #initializeFirstIssuer()}
     * pour une première installation.
     *
     * @param overlap doit couvrir la durée de vie maximale d'un token que l'ancienne
     *                émettrice a pu signer, et doit être strictement positif : un
     *                chevauchement nul ou négatif retirerait l'ancienne émettrice avant que
     *                les JWT encore valides qu'elle a signés n'expirent
     * @return le {@code kid} de la clé nouvellement activée
     */
    public String rotate(Duration overlap) {
        if (overlap == null || !overlap.isPositive()) {
            throw new IllegalArgumentException("SIGNING_KEY_ROTATION_REQUIRES_A_POSITIVE_OVERLAP");
        }

        NewSigningKey newKey = sealedNewKey();
        Instant now = clock.instant();
        writer.activateNewIssuer(newKey, now.plus(overlap));
        return newKey.kid();
    }

    private NewSigningKey sealedNewKey() {
        GeneratedSigningKeyMaterial material = generator.generate();
        String encryptedPrivateMaterial = cipher.encrypt(
                SecretContext.signingKeyMaterial(material.kid()), material.privateKeyMaterial());
        return new NewSigningKey(
                material.kid(),
                material.alg(),
                material.kty(),
                material.keyUse(),
                material.publicJwkJson(),
                encryptedPrivateMaterial);
    }
}
