package com.takibo.authorizationserver.domain.keys.port;

import com.takibo.authorizationserver.domain.keys.model.GeneratedSigningKeyMaterial;

/**
 * Génération de la matière d'une nouvelle clé de signature de plateforme (TAS-GRANTS-02A).
 * <p>
 * Port, et non appel statique direct à une bibliothèque cryptographique : le domaine décide
 * qu'une rotation a besoin d'une matière neuve, jamais comment elle est produite. Aujourd'hui
 * une paire RSA générée localement ({@code RsaSigningKeyGenerator}) ; demain, sans changer
 * {@link com.takibo.authorizationserver.domain.keys.SigningKeyRotationService}, un KMS ou un
 * HSM qui génère et garde la matière privée hors de portée de l'application.
 */
public interface SigningKeyMaterialGenerator {

    GeneratedSigningKeyMaterial generate();
}
