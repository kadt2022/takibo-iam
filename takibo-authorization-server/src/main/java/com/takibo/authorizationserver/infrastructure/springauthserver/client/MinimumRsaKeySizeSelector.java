package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Key;
import java.security.interfaces.RSAKey;
import java.util.List;

/**
 * Écarte, au moment de la vérification, toute clé RSA de moins de {@value #MIN_RSA_BITS} bits
 * proposée par le sélecteur de Nimbus, qui n'impose aucune taille minimale côté vérification.
 * <p>
 * La règle est celle de l'enregistrement ({@code OAuthClientJwkSetValidator}) ; l'appliquer ici
 * couvre aussi les clés servies par un {@code jwksUri} et les JWK intégrés enregistrés avant elle.
 * Sans clé restante, Nimbus rejette l'assertion : l'authentification du client échoue.
 */
final class MinimumRsaKeySizeSelector implements JWSKeySelector<SecurityContext> {

    static final int MIN_RSA_BITS = 2048;

    private static final Logger log = LoggerFactory.getLogger(MinimumRsaKeySizeSelector.class);

    private final JWSKeySelector<SecurityContext> delegate;

    MinimumRsaKeySizeSelector(JWSKeySelector<SecurityContext> delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<Key> selectJWSKeys(JWSHeader header, SecurityContext context) throws KeySourceException {
        List<? extends Key> candidates = delegate.selectJWSKeys(header, context);
        List<Key> kept = candidates.stream().filter(MinimumRsaKeySizeSelector::isStrongEnough).map(Key.class::cast).toList();
        if (kept.size() < candidates.size()) {
            // Pas de kid : l'en-tête n'est pas encore vérifié, il ne doit pas entrer dans les journaux.
            log.warn("Assertion client : clé RSA de moins de {} bits écartée", MIN_RSA_BITS);
        }
        return kept;
    }

    private static boolean isStrongEnough(Key key) {
        return !(key instanceof RSAKey rsa) || rsa.getModulus().bitLength() >= MIN_RSA_BITS;
    }
}
