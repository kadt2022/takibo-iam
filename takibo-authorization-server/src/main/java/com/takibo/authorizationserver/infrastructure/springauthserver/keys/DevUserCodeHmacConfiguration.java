package com.takibo.authorizationserver.infrastructure.springauthserver.keys;

import com.takibo.authorizationserver.domain.keys.port.UserCodeHmac;
import com.takibo.authorizationserver.infrastructure.keys.HmacSha256UserCodeHmac;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;

/**
 * Clé HMAC éphémère pour {@code user_code}, régénérée à chaque démarrage (TAS-GRANTS-02).
 * <p>
 * <b>Développement et tests uniquement.</b> Miroir exact de
 * {@link DevSecretCipherConfiguration} pour {@link UserCodeHmac} : même activation explicite
 * ({@code takibo.tas.keys.ephemeral=true}), même défaut protecteur, même conséquence — un
 * redémarrage rend introuvable, par son hash, tout {@code user_code} scellé par l'instance
 * précédente. Sans effet pratique : un {@code user_code} expire en quelques minutes (RFC
 * 8628), bien avant qu'un redémarrage de développement ne survienne d'ordinaire pendant sa
 * fenêtre de vie.
 */
@Configuration
@ConditionalOnProperty(name = "takibo.tas.keys.ephemeral", havingValue = "true")
public class DevUserCodeHmacConfiguration {

    private static final int HMAC_SHA256_KEY_LENGTH_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Bean
    public UserCodeHmac userCodeHmac() {
        byte[] material = new byte[HMAC_SHA256_KEY_LENGTH_BYTES];
        RANDOM.nextBytes(material);
        return new HmacSha256UserCodeHmac(material);
    }
}
