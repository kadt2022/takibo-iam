package com.takibo.authorizationserver.domain.keys;

import com.takibo.authorizationserver.domain.keys.model.NewSigningKey;
import com.takibo.authorizationserver.domain.keys.model.TasSigningKey;
import com.takibo.authorizationserver.domain.keys.port.SecretCipher;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyMaterialGenerator;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyRepository;
import com.takibo.authorizationserver.domain.keys.port.SigningKeyWriter;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Amorçage automatique de la première clé de signature de plateforme
 * (TAS-KEYS-BOOTSTRAP-01).
 * <p>
 * Une installation neuve doit démarrer sans qu'un opérateur ait à fabriquer quoi que ce soit.
 * La clé RSA n'est pas un secret que le client fournit : c'est un <b>état cryptographique
 * interne persistant</b> — tirée au hasard, non reproductible, jamais manipulée directement,
 * et déjà protégée au repos par la clé AES externe que l'installation, elle, fournit. Exiger
 * une commande d'amorçage n'ajouterait aucune sécurité ; seulement une étape ratable, dont
 * l'oubli ne se découvrirait qu'au démarrage refusé.
 * <p>
 * Ce que cela n'assouplit pas : la clé AES et la base se sauvegardent <b>ensemble</b>.
 * Restaurer l'une sans l'autre ne rend pas la clé de signature — l'une est indéchiffrable
 * sans l'autre.
 *
 * <h2>Trois situations, trois issues</h2>
 * <ul>
 *   <li><b>Aucune ligne de clé de plateforme</b> — installation vierge : on amorce ;</li>
 *   <li><b>une émettrice active</b> — rien à faire, on repart avec son {@code kid} ;</li>
 *   <li><b>un historique sans émettrice active</b> — échec fermé.</li>
 * </ul>
 * La troisième est la seule qui demande un mot d'explication. Y générer une clé neuve
 * « réparerait » le démarrage en masquant ce qui l'a cassé : une restauration partielle, une
 * rotation interrompue, une suppression manuelle. TAS se remettrait à signer avec une clé que
 * personne n'a décidée, à côté d'un historique que personne n'aurait examiné. La condition
 * d'amorçage porte donc sur l'<b>absence totale d'historique</b>, jamais sur la seule absence
 * d'émettrice active.
 */
public class PlatformSigningKeyBootstrap {

    private final SigningKeyRepository keys;
    private final SigningKeyWriter writer;
    private final SigningKeySealer sealer;
    private final Clock clock;

    public PlatformSigningKeyBootstrap(SigningKeyRepository keys,
                                       SigningKeyWriter writer,
                                       SigningKeyMaterialGenerator generator,
                                       SecretCipher cipher,
                                       Clock clock) {
        this.keys = keys;
        this.writer = writer;
        this.sealer = new SigningKeySealer(generator, cipher);
        this.clock = clock;
    }

    /**
     * Garantit qu'une émettrice de plateforme active existe, et rend son {@code kid}.
     * <p>
     * Idempotent : un second appel — un redémarrage — ne crée rien et rend le même
     * {@code kid}.
     *
     * @return l'émettrice active et la façon dont elle a été obtenue
     * @throws IllegalStateException si un historique de clés existe sans émettrice active
     */
    public Outcome ensurePlatformIssuer() {
        Instant now = clock.instant();

        Optional<TasSigningKey> alreadyActive = keys.findActivePlatformIssuer(now);
        if (alreadyActive.isPresent()) {
            return Outcome.alreadyThere(alreadyActive.get().kid());
        }

        if (keys.hasPlatformKeyHistory()) {
            // Nos deux lectures ne partagent aucune transaction — JpaSigningKeyRepository est
            // annoté au niveau classe, et cette méthode n'en ouvre pas — donc en READ
            // COMMITTED une instance concurrente a pu activer son émettrice entre les deux.
            // L'historique vu ici serait alors le sien, et non la corruption que ce refus
            // décrit : relire avant de refuser, sinon un amorçage concurrent réussi ferait
            // échouer ce démarrage en annonçant un historique cassé qui n'existe pas.
            return adoptConcurrentWinnerOrThrow(
                    "PLATFORM_SIGNING_KEY_HISTORY_WITHOUT_ACTIVE_ISSUER: "
                            + "refusing to bootstrap over an existing key history");
        }

        NewSigningKey candidate = sealer.seal();
        if (writer.tryActivateFirstIssuer(candidate)) {
            return Outcome.created(candidate.kid());
        }

        // Course perdue : une autre instance a inséré son émettrice entre notre lecture et
        // notre écriture. Sa ligne est nécessairement visible ici — l'insertion en conflit a
        // attendu la fin de la transaction concurrente avant de rendre la main — donc cette
        // relecture aboutit, et les deux instances repartent avec le même kid.
        return adoptConcurrentWinnerOrThrow(
                "PLATFORM_SIGNING_KEY_BOOTSTRAP_LOST_ITS_RACE_AND_FOUND_NO_ISSUER");
    }

    /**
     * Relit l'émettrice active et l'adopte, ou échoue fermé avec le diagnostic fourni.
     * <p>
     * Les deux appelants perdent la même course, à deux endroits différents — l'un entre ses
     * deux lectures, l'autre entre sa lecture et son écriture — et en tirent la même
     * conclusion : si une émettrice active est visible maintenant, une instance concurrente
     * l'a créée, et il faut repartir avec son {@code kid}. Seul le diagnostic diffère lorsque
     * la relecture ne trouve toujours rien, et c'est pourquoi il est passé en paramètre.
     */
    private Outcome adoptConcurrentWinnerOrThrow(String failure) {
        return keys.findActivePlatformIssuer(clock.instant())
                .map(TasSigningKey::kid)
                .map(Outcome::alreadyThere)
                .orElseThrow(() -> new IllegalStateException(failure));
    }

    /**
     * Le résultat de l'amorçage, et la seule façon de distinguer une clé qui vient de naître
     * d'une clé retrouvée. La distinction n'a aucun effet sur la suite du démarrage — elle
     * existe pour le journal : la naissance d'une clé de plateforme est le seul évènement
     * cryptographique que TAS déclenche sans qu'un opérateur l'ait demandé, et elle doit se
     * lire comme tel plutôt que se confondre avec un démarrage ordinaire.
     *
     * @param created {@code true} si <b>cette</b> instance a inséré la clé ; {@code false} si
     *                elle était déjà là, y compris lorsqu'une instance concurrente vient de la
     *                créer
     */
    public record Outcome(String kid, boolean created) {

        static Outcome created(String kid) {
            return new Outcome(kid, true);
        }

        static Outcome alreadyThere(String kid) {
            return new Outcome(kid, false);
        }
    }
}
