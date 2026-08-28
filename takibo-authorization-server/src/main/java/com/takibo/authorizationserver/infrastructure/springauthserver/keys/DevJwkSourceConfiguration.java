package com.takibo.authorizationserver.infrastructure.springauthserver.keys;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/**
 * Cle de signature ephemere, regeneree a chaque demarrage.
 * <p>
 * <b>Developpement et tests uniquement.</b> Chaque redemarrage produit une paire neuve, donc
 * invalide tous les JWT en circulation : personne ne reste connecte a travers un
 * deploiement. C'est acceptable sur un poste, jamais en service.
 * <p>
 * L'activation est explicite — {@code takibo.tas.keys.ephemeral=true} — et l'absence de
 * reglage donne la source persistante. Le defaut protege : oublier la configuration en
 * production ne peut pas silencieusement ressusciter les cles jetables.
 *
 * @see com.takibo.authorizationserver.infrastructure.keys.PersistentJwkSource
 */
@Configuration
@ConditionalOnProperty(name = "takibo.tas.keys.ephemeral", havingValue = "true")
public class DevJwkSourceConfiguration {

    @Bean
    public JWKSource<SecurityContext> jwkSource() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();

        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }
}
