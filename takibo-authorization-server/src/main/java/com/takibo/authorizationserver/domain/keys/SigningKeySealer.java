package com.takibo.authorizationserver.domain.keys;

import com.takibo.authorizationserver.domain.keys.model.GeneratedSigningKeyMaterial;
import com.takibo.authorizationserver.domain.keys.model.NewSigningKey;
import com.takibo.authorizationserver.domain.keys.port.SecretCipher;
import com.takibo.authorizationserver.domain.keys.port.SecretContext;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyMaterialGenerator;

/**
 * Tire une clé neuve et scelle sa matière privée, en un seul geste.
 * <p>
 * Ce geste est le même pour l'amorçage ({@link PlatformSigningKeyBootstrap}) et pour la
 * rotation ({@link SigningKeyRotationService}) — seule diffère la façon dont la clé obtenue
 * est ensuite activée. L'isoler ici évite que les deux services n'entretiennent chacun leur
 * copie de la même séquence, où une divergence — un contexte de chiffrement oublié, par
 * exemple — ne se verrait que sur l'un des deux chemins.
 * <p>
 * La matière privée en clair ne quitte jamais cette méthode autrement que scellée : elle est
 * chiffrée avant d'entrer dans {@link NewSigningKey}, donc avant d'atteindre quoi que ce soit
 * qui persiste.
 */
class SigningKeySealer {

    private final SigningKeyMaterialGenerator generator;
    private final SecretCipher cipher;

    SigningKeySealer(SigningKeyMaterialGenerator generator, SecretCipher cipher) {
        this.generator = generator;
        this.cipher = cipher;
    }

    NewSigningKey seal() {
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
