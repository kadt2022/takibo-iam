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
 * <b>Développement et tests uniquement.</b> Sert exclusivement à
 * {@code oauth2_authorization.*_value} : en profil éphémère, la matière privée de signature
 * ne passe pas par ce chiffrement — {@link DevJwkSourceConfiguration} génère une paire RSA
 * en mémoire, sans jamais écrire dans {@code tas_signing_keys}. Chaque redémarrage produit
 * donc une clé neuve pour ce bean, ce qui rend illisible toute ligne
 * {@code oauth2_authorization} chiffrée par l'instance précédente : acceptable sur un poste
 * ou en CI, où la base repart généralement à vide, jamais en service. Voir
 * {@link SigningKeysConfiguration#secretCipher} pour la source persistante, activée par
 * défaut.
 * <p>
 * Un déchiffrement qui échoue ainsi après un redémarrage en {@code dev} remonte tel quel
 * ({@link com.takibo.authorizationserver.domain.keys.port.SecretDecryptionException}) — ni
 * {@code JpaOAuth2AuthorizationService} ni ce bean ne le rattrapent. Sur un poste avec une
 * base Postgres qui, elle, survit au redémarrage, une ancienne autorisation redevient donc
 * illisible plutôt que silencieusement absente ; en pratique sans effet pour
 * {@code client_credentials} (chaque requête crée sa propre ligne), mais à garder en tête si
 * un flux relisant une autorisation plus ancienne (introspection, révocation) est testé
 * localement après un redémarrage.
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

    // Instance partagee, jamais recreee a la volee : SecureRandom est thread-safe et concue
    // pour etre reutilisee (Sonar S2119) ; en construire une nouvelle a chaque appel degraderait
    // aussi la qualite de l'aleatoire produit par des instances creees en succession rapide.
    private static final SecureRandom RANDOM = new SecureRandom();

    @Bean
    public SecretCipher secretCipher() {
        byte[] material = new byte[AES_256_KEY_LENGTH_BYTES];
        RANDOM.nextBytes(material);
        return new AesGcmSecretCipher(new SecretCipherKey("ephemeral-" + UUID.randomUUID(), material));
    }
}
