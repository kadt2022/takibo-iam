package com.takibo.authorizationserver.infrastructure.keys;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.takibo.authorizationserver.domain.keys.model.KeyStatus;
import com.takibo.authorizationserver.domain.keys.model.TasSigningKey;
import com.takibo.authorizationserver.domain.keys.port.SecretCipher;
import com.takibo.authorizationserver.domain.keys.port.SecretContext;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyRepository;
import org.springframework.beans.factory.InitializingBean;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Source de cles adossee a {@code tas_signing_keys} (TAS-GRANTS-02A).
 * <p>
 * Remplace la generation ephemere, qui regenerait une paire RSA a chaque demarrage et
 * invalidait donc tous les JWT en circulation a chaque deploiement.
 *
 * <h2>Une seule cle porte la matiere privee</h2>
 * Celle que {@link SigningKeyRepository#findActivePlatformIssuer(Instant)} designe, reconnue
 * par son <b>identite</b>. Toutes les autres sortent en public seul : elles verifient les JWT
 * deja emis sans jamais pouvoir en produire.
 * <p>
 * La distinction est subtile et elle est le coeur de la rotation. Le drapeau
 * {@code is_issuer} ne suffit pas : une cle <b>retiree conserve {@code is_issuer = true}</b>,
 * puisqu'elle a bel et bien emis. Pendant un chevauchement, l'ancienne et la nouvelle le
 * portent donc toutes deux. S'y fier sortirait deux cles privees, et
 * {@code NimbusJwtEncoder} — qui refuse toute ambiguite — cesserait de signer. C'est le
 * statut, croise avec la fenetre temporelle, qui departage ; le drapeau seul ne dit que
 * « a vocation a emettre ».
 *
 * <h2>Ce qui est verifie au demarrage</h2>
 * L'absence de cle emettrice, une matiere privee indechiffrable ou un JWK incoherent
 * empechent le demarrage. Les decouvrir a la premiere demande de token produirait des refus
 * incomprehensibles en service ; mieux vaut un demarrage refuse avec un diagnostic net.
 *
 * <h2>Forme du secret stocke</h2>
 * {@code private_key_encrypted} contient le <b>JWK complet</b> serialise puis chiffre, et non
 * un PEM : le dechiffrement rend une cle directement utilisable, sans reconstruction ni
 * hypothese sur l'encodage. {@code public_jwk_json} reste la seule source de ce que le JWKS
 * publie — et les deux sont confrontes, sans quoi TAS pourrait signer avec une cle
 * differente de celle qu'il annonce.
 *
 * <h2>Pas de cache, volontairement</h2>
 * Chaque appel relit la base. Un cache exigerait une invalidation, donc une conception, et
 * celle-ci appartient a la tranche de rotation : c'est elle qui sait quand les cles changent.
 */
public class PersistentJwkSource implements JWKSource<SecurityContext>, InitializingBean {

    private final SigningKeyRepository signingKeys;
    private final SecretCipher cipher;
    private final Clock clock;

    public PersistentJwkSource(SigningKeyRepository signingKeys, SecretCipher cipher, Clock clock) {
        this.signingKeys = signingKeys;
        this.cipher = cipher;
        this.clock = clock;
    }

    /**
     * Fail-closed au demarrage, et non a la premiere requete : le contexte refuse de se
     * charger si TAS ne peut pas signer, ou si une cle qu'il publierait est illisible.
     * <p>
     * Toutes les cles publiables sont validees ici, pas seulement l'emettrice : une matiere
     * privee dechiffrable mais un JWK public incoherent sur une cle retiree ne se
     * decouvrirait sinon qu'a la premiere requete JWKS ou au premier decodage qui la
     * rencontre, transformant un demarrage cense echouer net en panne de service en
     * production.
     */
    @Override
    public void afterPropertiesSet() {
        Instant now = clock.instant();
        List<TasSigningKey> publishable = signingKeys.findPublishable(now);
        TasSigningKey issuer = activeIssuerAmong(publishable)
                .orElseThrow(() -> new SigningKeyUnavailableException(
                        "NO_ACTIVE_PLATFORM_SIGNING_KEY: TAS cannot issue tokens"));

        for (TasSigningKey key : publishable) {
            if (key.id().equals(issuer.id())) {
                signingJwkOf(key);
            } else {
                publicJwkOf(key);
            }
        }
    }

    @Override
    public List<JWK> get(JWKSelector selector, SecurityContext context) {
        Instant now = clock.instant();

        // Une seule lecture : deux requetes separees s'exposeraient a une rotation qui
        // commettrait entre les deux, l'une voyant l'ancien etat et l'autre le nouveau. La
        // condition de findActivePlatformIssuer est un sous-ensemble de celle de
        // findPublishable, l'emettrice active — si elle existe — figure donc toujours dans
        // cette liste.
        List<TasSigningKey> publishable = signingKeys.findPublishable(now);
        Optional<UUID> issuerId = activeIssuerAmong(publishable).map(TasSigningKey::id);

        List<JWK> jwks = new ArrayList<>(publishable.size());
        for (TasSigningKey key : publishable) {
            // L'identite, pas le drapeau : une cle retiree conserve is_issuer = true.
            boolean signs = issuerId.filter(id -> id.equals(key.id())).isPresent();
            jwks.add(signs ? signingJwkOf(key) : publicJwkOf(key));
        }
        // Volontairement sans exception si la liste est vide : la verification des JWT deja
        // emis ne doit pas dependre de l'existence d'une emettrice. Signer, si.
        return selector.select(new JWKSet(jwks));
    }

    /**
     * L'emettrice active parmi des cles deja lues, sans nouvelle requete. Le schema garantit
     * qu'il n'y en a jamais plus d'une — index partiel {@code uk_tas_sk_platform_issuer_active}.
     */
    private static Optional<TasSigningKey> activeIssuerAmong(List<TasSigningKey> keys) {
        return keys.stream()
                .filter(TasSigningKey::issuer)
                .filter(key -> key.status() == KeyStatus.ACTIVE)
                .findFirst();
    }

    /** La cle emettrice, avec sa matiere privee, confrontee a ce que le JWKS annonce. */
    private JWK signingJwkOf(TasSigningKey key) {
        if (key.privateKeyEncrypted() == null || key.privateKeyEncrypted().isBlank()) {
            throw new SigningKeyUnavailableException(
                    "ISSUER_KEY_HAS_NO_PRIVATE_MATERIAL: " + key.kid());
        }
        JWK privateJwk = parse(cipher.decrypt(
                SecretContext.signingKeyMaterial(key.kid()), key.privateKeyEncrypted()));
        if (!privateJwk.isPrivate()) {
            throw new SigningKeyUnavailableException(
                    "ISSUER_KEY_MATERIAL_IS_NOT_A_PRIVATE_JWK: " + key.kid());
        }
        assertAnnouncesWhatItSigns(key, privateJwk);
        return privateJwk;
    }

    private JWK publicJwkOf(TasSigningKey key) {
        JWK jwk = parse(publicJson(key));
        if (jwk.isPrivate()) {
            // public_jwk_json ne doit jamais contenir de parametre prive : il est publie tel
            // quel par l'endpoint JWKS.
            throw new SigningKeyUnavailableException(
                    "PUBLIC_JWK_CONTAINS_PRIVATE_PARAMETERS: " + key.kid());
        }
        return jwk;
    }

    /**
     * La cle qui signe doit etre exactement celle que le JWKS publie.
     * <p>
     * Sans ce controle, une divergence entre {@code private_key_encrypted} et
     * {@code public_jwk_json} passerait inapercue : TAS signerait avec une cle et en
     * annoncerait une autre, rendant chaque token invalide pour tous ses consommateurs. La
     * comparaison porte sur la partie publique complete, ce qui couvre la matiere ainsi que
     * {@code kid}, {@code kty}, {@code alg} et {@code use}.
     */
    private void assertAnnouncesWhatItSigns(TasSigningKey key, JWK privateJwk) {
        JWK announced = parse(publicJson(key));
        if (!privateJwk.toPublicJWK().equals(announced)) {
            throw new SigningKeyUnavailableException(
                    "ISSUER_KEY_DOES_NOT_MATCH_ITS_PUBLISHED_JWK: " + key.kid());
        }
    }

    private static String publicJson(TasSigningKey key) {
        if (key.publicJwkJson() == null || key.publicJwkJson().isEmpty()) {
            throw new SigningKeyUnavailableException("SIGNING_KEY_HAS_NO_PUBLIC_JWK: " + key.kid());
        }
        return JSONObjectUtils.toJSONString(key.publicJwkJson());
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
