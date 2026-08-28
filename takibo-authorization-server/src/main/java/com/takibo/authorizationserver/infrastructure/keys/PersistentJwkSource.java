package com.takibo.authorizationserver.infrastructure.keys;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.takibo.authorizationserver.domain.keys.model.TasSigningKey;
import com.takibo.authorizationserver.domain.keys.port.SecretCipher;
import com.takibo.authorizationserver.domain.keys.port.SecretContext;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Source de cles adossee a {@code tas_signing_keys} (TAS-GRANTS-02A).
 * <p>
 * Remplace la generation ephemere, qui regenerait une paire RSA a chaque demarrage et
 * invalidait donc tous les JWT en circulation a chaque deploiement.
 *
 * <h2>Ce que chaque cle expose</h2>
 * <ul>
 *   <li><b>L'emettrice</b> sort avec sa matiere privee, dechiffree a la demande. Elle seule
 *       peut signer.</li>
 *   <li><b>Les autres</b> — retirees, ou actives sans etre emettrices — sortent en public
 *       seul. Elles verifient les JWT deja emis, sans jamais pouvoir en produire.</li>
 * </ul>
 * Cette asymetrie n'est pas qu'une precaution : c'est elle qui rend la rotation possible.
 * {@code NimbusJwtEncoder} leve une exception des que plusieurs cles repondent au selecteur,
 * et son filtre RSA ne discrimine pas sur la presence de matiere privee. Pendant un
 * chevauchement, c'est donc au {@code jwkSelector} de l'encodeur de retenir celle qui porte
 * une partie privee — voir {@code SigningKeysConfiguration}.
 *
 * <h2>Forme du secret stocke</h2>
 * {@code private_key_encrypted} contient le <b>JWK complet</b> serialise puis chiffre, et non
 * un PEM : le dechiffrement rend une cle directement utilisable, sans reconstruction ni
 * hypothese sur l'encodage. {@code public_jwk_json} reste la seule source de ce que le JWKS
 * publie.
 *
 * <h2>Pas de cache, volontairement</h2>
 * Chaque appel relit la base. Un cache exigerait une invalidation, donc une conception, et
 * celle-ci appartient a la tranche de rotation : c'est elle qui sait quand les cles changent.
 * Le cout est une requete indexee et un dechiffrement AES de quelques kilo-octets ; le
 * mesurer avant de l'optimiser.
 */
public class PersistentJwkSource implements JWKSource<SecurityContext> {

    private final SigningKeyRepository signingKeys;
    private final SecretCipher cipher;
    private final Clock clock;

    public PersistentJwkSource(SigningKeyRepository signingKeys, SecretCipher cipher, Clock clock) {
        this.signingKeys = signingKeys;
        this.cipher = cipher;
        this.clock = clock;
    }

    @Override
    public List<JWK> get(JWKSelector selector, SecurityContext context) {
        Instant now = clock.instant();
        List<TasSigningKey> publishable = signingKeys.findPublishable(now);

        if (publishable.isEmpty()) {
            // Ni signature ni verification possibles. Le dire ici plutot que de rendre une
            // liste vide, que l'appelant traduirait en « aucune cle ne correspond ».
            throw new SigningKeyUnavailableException("NO_PUBLISHABLE_SIGNING_KEY");
        }

        List<JWK> jwks = new ArrayList<>(publishable.size());
        for (TasSigningKey key : publishable) {
            jwks.add(key.issuer() ? withPrivateMaterial(key) : publicOnly(key));
        }
        return selector.select(new JWKSet(jwks));
    }

    private JWK withPrivateMaterial(TasSigningKey key) {
        if (key.privateKeyEncrypted() == null || key.privateKeyEncrypted().isBlank()) {
            // Une emettrice sans matiere privee ne peut pas signer. Fail-closed : mieux vaut
            // un demarrage refuse qu'un endpoint de token qui echoue a la premiere requete.
            throw new SigningKeyUnavailableException("ISSUER_KEY_HAS_NO_PRIVATE_MATERIAL");
        }
        String jwkJson = cipher.decrypt(
                SecretContext.signingKeyMaterial(key.kid()), key.privateKeyEncrypted());
        return parse(jwkJson);
    }

    private JWK publicOnly(TasSigningKey key) {
        return parse(toJson(key));
    }

    private static String toJson(TasSigningKey key) {
        if (key.publicJwkJson() == null || key.publicJwkJson().isEmpty()) {
            throw new SigningKeyUnavailableException("SIGNING_KEY_HAS_NO_PUBLIC_JWK");
        }
        return com.nimbusds.jose.util.JSONObjectUtils.toJSONString(key.publicJwkJson());
    }

    private static JWK parse(String json) {
        try {
            return JWK.parse(json);
        } catch (Exception e) {
            // Le message ne porte jamais le contenu : une matiere privee illisible ne doit
            // pas se retrouver dans une trace.
            throw new SigningKeyUnavailableException("SIGNING_KEY_IS_NOT_A_VALID_JWK", e);
        }
    }
}
