package com.takibo.authorizationserver.infrastructure.springauthserver.keys;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.takibo.authorizationserver.domain.keys.PlatformSigningKeyBootstrap;
import com.takibo.authorizationserver.domain.keys.SigningKeyRotationService;
import com.takibo.authorizationserver.domain.keys.port.SecretCipher;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyMaterialGenerator;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyRepository;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyWriter;
import com.takibo.authorizationserver.domain.keys.port.UserCodeHmac;
import com.takibo.authorizationserver.infrastructure.keys.AesGcmSecretCipher;
import com.takibo.authorizationserver.infrastructure.keys.HmacSha256UserCodeHmac;
import com.takibo.authorizationserver.infrastructure.keys.PersistentJwkSource;
import com.takibo.authorizationserver.infrastructure.keys.RsaSigningKeyGenerator;
import com.takibo.authorizationserver.infrastructure.keys.SecretCipherKey;
import com.takibo.authorizationserver.infrastructure.keys.SigningKeyBootstrapInitializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Clock;
import java.util.Base64;
import java.util.List;

/**
 * Cablage des cles de signature et du chiffrement au repos (TAS-GRANTS-02A).
 * <p>
 * L'encodeur et le decodeur sont declares ici, une seule fois, quelle que soit l'origine des
 * cles : la source ephemere de developpement et la source persistante fournissent toutes deux
 * un {@code JWKSource}, et rien d'autre ne change. C'est aussi ce qui garantit que les tokens
 * humains et machine restent signes par la meme cle — propriete que ce recit ne doit pas
 * rompre.
 */
@Configuration
public class SigningKeysConfiguration {

    /**
     * Source persistante, active par defaut. La source ephemere exige un opt-in explicite,
     * de sorte qu'une configuration oubliee ne puisse pas ressusciter des cles jetables.
     */
    @Bean
    @ConditionalOnProperty(name = "takibo.tas.keys.ephemeral", havingValue = "false",
            matchIfMissing = true)
    public JWKSource<SecurityContext> persistentJwkSource(
            SigningKeyRepository signingKeys,
            SecretCipher secretCipher,
            Clock clock,
            SigningKeyBootstrapInitializer bootstrapped) {
        // Le parametre bootstrapped n'est pas utilise : il est la dependance elle-meme. Spring
        // n'injecte un bean qu'une fois completement initialise, donc l'amorcage a deja eu lieu
        // quand cette source valide qu'une emettrice existe. L'exprimer par un type plutot que
        // par @DependsOn("nom") le fait verifier a la compilation, et le fait survivre a un
        // renommage.
        return new PersistentJwkSource(signingKeys, secretCipher, clock);
    }

    /**
     * Amorcage de la premiere cle de signature (TAS-KEYS-BOOTSTRAP-01), sous la meme condition
     * que la source persistante : en mode ephemere, il n'y a rien a amorcer puisque rien n'est
     * conserve.
     */
    @Bean
    @ConditionalOnProperty(name = "takibo.tas.keys.ephemeral", havingValue = "false",
            matchIfMissing = true)
    public SigningKeyBootstrapInitializer signingKeyBootstrapInitializer(
            SigningKeyRepository signingKeys,
            SigningKeyWriter signingKeyWriter,
            SigningKeyMaterialGenerator generator,
            SecretCipher secretCipher,
            Clock clock) {
        return new SigningKeyBootstrapInitializer(new PlatformSigningKeyBootstrap(
                signingKeys, signingKeyWriter, generator, secretCipher, clock));
    }

    /**
     * Chiffrement au repos de la matiere privee, et bientot des codes et tokens du recit 02.
     * <p>
     * La cle vient de l'environnement et n'a <b>aucune valeur par defaut</b> : son absence
     * empeche le demarrage. C'est voulu — un defaut integre serait un secret publie dans le
     * depot, et le fail-closed vaut mieux qu'un chiffrement que tout le monde peut defaire.
     */
    @Bean
    @ConditionalOnProperty(name = "takibo.tas.keys.ephemeral", havingValue = "false",
            matchIfMissing = true)
    public SecretCipher secretCipher(
            @Value("${takibo.tas.keys.cipher.active-key-id}") String activeKeyId,
            @Value("${takibo.tas.keys.cipher.active-key}") String activeKeyBase64) {
        return new AesGcmSecretCipher(new SecretCipherKey(activeKeyId, decode(activeKeyBase64)));
    }

    /**
     * Clé distincte de celle de {@link #secretCipher} : mélanger les usages d'une même
     * matière entre chiffrement et HMAC est un anti-pattern cryptographique, pas une
     * simplification. Voir {@link UserCodeHmac} pour ce que cette clé protège
     * ({@code user_code}, seule valeur de faible entropie parmi les six colonnes chiffrées de
     * TAS-GRANTS-02) et pourquoi les cinq autres n'en ont pas besoin.
     */
    @Bean
    @ConditionalOnProperty(name = "takibo.tas.keys.ephemeral", havingValue = "false",
            matchIfMissing = true)
    public UserCodeHmac userCodeHmac(
            @Value("${takibo.tas.keys.user-code-hmac.key}") String hmacKeyBase64) {
        return new HmacSha256UserCodeHmac(decode(hmacKeyBase64));
    }

    private static byte[] decode(String base64) {
        try {
            return Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            // Le message ne reproduit jamais la valeur fournie.
            throw new IllegalStateException("SECRET_CIPHER_KEY_IS_NOT_VALID_BASE64", e);
        }
    }

    /**
     * RSA local aujourd'hui ; seul ce bean change le jour où la génération vient d'un KMS ou
     * d'un HSM — {@code SigningKeyRotationService} ne connaît que le port.
     */
    @Bean
    public SigningKeyMaterialGenerator signingKeyMaterialGenerator() {
        return new RsaSigningKeyGenerator();
    }

    /**
     * Absent en profil éphémère : la rotation n'a de sens que pour des clés persistées, et
     * l'exposer quand même laisserait croire qu'elle survivrait à un redémarrage.
     */
    @Bean
    @ConditionalOnProperty(name = "takibo.tas.keys.ephemeral", havingValue = "false",
            matchIfMissing = true)
    public SigningKeyRotationService signingKeyRotationService(
            SigningKeyMaterialGenerator signingKeyMaterialGenerator,
            SigningKeyWriter signingKeyWriter,
            SecretCipher secretCipher,
            Clock clock) {
        return new SigningKeyRotationService(
                signingKeyMaterialGenerator, signingKeyWriter, secretCipher, clock);
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /**
     * Meme cle que les tokens machine emis par SAS : un token humain signe par
     * {@code HumanTokenSigner} est valide par le decodeur ci-dessus sans configuration
     * supplementaire.
     * <p>
     * Le selecteur explicite est ce qui rendra la rotation possible. {@code NimbusJwtEncoder}
     * leve une exception des que plusieurs cles repondent au selecteur, et son filtre RSA ne
     * discrimine pas sur la presence de matiere privee : pendant un chevauchement, l'ancienne
     * cle publique et la nouvelle repondraient toutes deux. Retenir celle qui porte une partie
     * privee designe l'emettrice sans ambiguite, puisque la source persistante n'en expose
     * qu'une.
     * <p>
     * Ce selecteur n'est toutefois consulte que si <b>plus d'une</b> cle correspond a
     * l'algorithme demande : {@code NimbusJwtEncoder} court-circuite tout selecteur des qu'un
     * seul candidat matche. Avec une seule cle publiee et aucune emettrice — retrait sans
     * nouvelle activation — c'est donc l'erreur generique de Nimbus qui remonte, pas
     * {@link IllegalStateException} ci-dessous ; l'echec reste net dans les deux cas.
     */
    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(jwkSource);
        encoder.setJwkSelector(SigningKeysConfiguration::theOneThatCanSign);
        return encoder;
    }

    private static JWK theOneThatCanSign(List<JWK> candidates) {
        List<JWK> signing = candidates.stream().filter(JWK::isPrivate).toList();
        if (signing.size() != 1) {
            throw new IllegalStateException(
                    "EXPECTED_EXACTLY_ONE_SIGNING_KEY_BUT_FOUND_" + signing.size());
        }
        return signing.get(0);
    }
}
