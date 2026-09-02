package com.takibo.authorizationserver.infrastructure.keys;

import com.takibo.authorizationserver.domain.keys.PlatformSigningKeyBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * Declenche l'amorcage de la premiere cle de signature au demarrage du contexte
 * (TAS-KEYS-BOOTSTRAP-01).
 * <p>
 * Adaptateur, et non logique : la decision — amorcer, ne rien faire, ou echouer — appartient
 * entierement a {@link PlatformSigningKeyBootstrap}, que rien de Spring n'atteint. Cette
 * classe ne fait que choisir <b>quand</b> cette decision est prise, et l'inscrire au journal.
 *
 * <h2>Pourquoi {@code InitializingBean}</h2>
 * {@link PersistentJwkSource} valide au demarrage qu'une emettrice existe et refuse le
 * contexte sinon. L'amorcage doit donc etre <b>termine</b> avant cette validation, et pas
 * seulement commence. Spring garantit qu'un bean injecte est completement initialise avant
 * d'etre remis a celui qui le demande : faire porter l'amorcage par {@code afterPropertiesSet}
 * et passer cet initialiseur en parametre de la methode {@code @Bean} de
 * {@code PersistentJwkSource} suffit donc a l'ordonner — par une dependance de type, verifiee
 * a la compilation, plutot que par un {@code @DependsOn} sur un nom de bean en chaine de
 * caracteres, qui casserait en silence au premier renommage.
 */
public class SigningKeyBootstrapInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(SigningKeyBootstrapInitializer.class);

    private final PlatformSigningKeyBootstrap bootstrap;

    public SigningKeyBootstrapInitializer(PlatformSigningKeyBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    /**
     * Le {@code kid} est journalise, jamais la matiere — ni en clair, ni chiffree. C'est le
     * seul instant ou une cle de plateforme nait sans qu'un operateur l'ait demandee : la
     * trace doit permettre de dater cette naissance et de la rattacher a une cle precise.
     */
    @Override
    public void afterPropertiesSet() {
        PlatformSigningKeyBootstrap.Outcome outcome = bootstrap.ensurePlatformIssuer();
        if (outcome.created()) {
            log.info("TAS bootstrapped its first platform signing key: kid={}", outcome.kid());
        } else {
            log.info("TAS platform signing key already present: kid={}", outcome.kid());
        }
    }
}
