package com.takibo.authorizationserver.infrastructure.springauthserver.keys;

import com.takibo.authorizationserver.domain.keys.port.SecretCipher;
import com.takibo.authorizationserver.infrastructure.keys.AesGcmSecretCipher;
import com.takibo.authorizationserver.infrastructure.keys.SecretCipherKey;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Clé de chiffrement au repos éphémère, régénérée à chaque démarrage (TAS-GRANTS-02).
 * <p>
 * <b>Développement et tests uniquement.</b> Chaque redémarrage produit une clé neuve, donc
 * rend illisible tout ce que la précédente avait chiffré — matière privée de clé de signature
 * ({@code tas_signing_keys.private_key_encrypted}) comme valeurs de tokens/codes
 * ({@code oauth2_authorization.*_value}). C'est acceptable sur un poste ou en CI, jamais en
 * service : voir {@link SigningKeysConfiguration#secretCipher} pour la source persistante,
 * activée par défaut.
 * <p>
 * Miroir exact de {@link DevJwkSourceConfiguration} pour {@link SecretCipher} : même
 * activation explicite ({@code takibo.tas.keys.ephemeral=true}), même défaut protecteur.
 * Sans ce bean, {@code JpaOAuth2AuthorizationService} (qui exige un {@link SecretCipher} pour
 * chiffrer chaque valeur de token) ne pourrait démarrer dans aucun profil éphémère — c'est-à-
 * dire ni en {@code dev}, ni en {@code test}, ni dans le BVT de la CI, qui active tous les
 * trois ce profil précisément pour éviter d'y provisionner une vraie clé.
 */
@Configuration
@ConditionalOnProperty(name = "takibo.tas.keys.ephemeral", havingValue = "true")
public class DevSecretCipherConfiguration {

    private static final int AES_256_KEY_LENGTH_BYTES = 32;

    @Bean
    public SecretCipher secretCipher() {
        byte[] material = new byte[AES_256_KEY_LENGTH_BYTES];
        new SecureRandom().nextBytes(material);
        return new AesGcmSecretCipher(new SecretCipherKey("ephemeral-" + UUID.randomUUID(), material));
    }
}
