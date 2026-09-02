package com.takibo.installkeys;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Écrit un secret sur le disque, ou refuse (TAKIBO-INSTALL-KEYS-01).
 *
 * <h2>Un témoin déterministe, et non un temporaire anonyme</h2>
 * Le fichier de travail s'appelle {@code <cible>.pending} — un nom prévisible, adjacent à la
 * cible. Un temporaire au nom aléatoire ferait le même travail tant que tout se passe bien,
 * mais laisserait, après un arrêt brutal, un fichier de secrets que plus rien ne rattache à
 * une installation : ni la CLI relancée, ni un opérateur ne sauraient dire s'il est le résidu
 * d'une tentative interrompue ou un fichier étranger. Un nom déterministe rend l'interruption
 * <b>lisible</b>.
 * <p>
 * Il est créé en {@code CREATE_NEW} <b>avant</b> même que les clés ne soient tirées : c'est
 * lui, et non la cible, qui arbitre la concurrence. Deux initialisations simultanées se
 * départagent donc avant qu'aucun secret n'existe, et la perdante n'aura pas consommé
 * d'entropie pour rien.
 *
 * <h2>Les deux instants où un arrêt brutal peut frapper</h2>
 * <ul>
 *   <li><b>avant la publication</b> — {@code .pending} porte des secrets, aucune cible
 *       n'existe. L'installation a été interrompue : refus avec
 *       {@link ExitCode#INTERRUPTED_INSTALLATION}, et surtout aucune régénération. Le
 *       {@code .pending} n'est jamais effacé automatiquement — il peut être la seule copie de
 *       clés déjà utilisées ailleurs ;</li>
 *   <li><b>après la publication, avant le nettoyage</b> — {@code .pending} et la cible sont
 *       deux noms du même inode. L'installation a en réalité réussi : {@link Files#isSameFile}
 *       le confirme, le second nom est retiré, et le résultat est
 *       {@link ExitCode#TARGET_EXISTS}.</li>
 * </ul>
 * Un {@code .pending} qui existe à côté d'une cible <b>différente</b> ne relève d'aucun des
 * deux cas : deux jeux de clés distincts coexistent, personne d'autre que l'opérateur ne peut
 * dire lequel fait foi, et rien n'est effacé.
 *
 * <h2>Pourquoi un lien, et non un déplacement</h2>
 * Il n'existe pas, dans l'API standard, de déplacement à la fois atomique et non destructeur.
 * {@code Files.move} avec {@code ATOMIC_MOVE} appelle {@code rename}, qui <b>écrase</b>
 * silencieusement une cible existante ; sans {@code ATOMIC_MOVE}, l'implémentation vérifie
 * l'existence puis renomme, laissant une fenêtre entre le test et l'écriture.
 * <p>
 * {@link Files#createLink} n'a pas ce défaut : le lien est créé ou échoue, en une seule
 * opération, et il échoue si la cible existe. Le fichier publié est le même inode que le
 * {@code .pending}, donc il porte déjà son contenu complet et ses permissions restreintes :
 * rien n'est jamais visible sous le nom final dans un état partiel.
 *
 * <h2>Le secret n'existe jamais sans protection</h2>
 * Sous POSIX, les permissions sont fournies comme attributs <b>de création</b>. Sous Windows,
 * l'API standard n'offre aucun attribut de création ACL : le fichier est alors créé
 * <b>vide</b>, son ACL est restreinte, puis relue pour vérification, et ce n'est qu'ensuite
 * que le contenu est écrit. Ce qui existe brièvement sous les permissions héritées est un
 * fichier vide, jamais un secret.
 */
final class SecretFileWriter {

    static final String PENDING_SUFFIX = ".pending";

    private SecretFileWriter() {
    }

    /**
     * @param target  chemin final, qui ne doit pas exister
     * @param content contenu à écrire, produit <b>après</b> que la concurrence a été arbitrée :
     *                une initialisation perdante ne doit pas avoir tiré de clés
     * @throws SecretFileException porteur du code de sortie décrivant le refus
     */
    static void write(Path target, Supplier<String> content) {
        Path directory = parentOf(target);
        Restriction restriction = Restriction.of(directory);
        Path pending = target.resolveSibling(target.getFileName() + PENDING_SUFFIX);

        resolveLeftovers(target, pending);

        restriction.createRestrictedFile(pending);
        try {
            writeFully(pending, content.get());
            publish(pending, target);
        } catch (SecretFileException e) {
            // Notre propre .pending, non publie : l'effacer est sans risque, et le laisser
            // serait abandonner un secret dans un repertoire.
            deleteQuietly(pending);
            throw e;
        } catch (IOException e) {
            deleteQuietly(pending);
            throw new SecretFileException(ExitCode.IO_FAILURE,
                    "Failed to write the secrets file: " + target, e);
        }
        deleteQuietly(pending);
    }

    /**
     * Lit ce qu'une exécution précédente a laissé, et en tire la seule conclusion défendable.
     * Aucune écriture ici : cette étape ne fait que décider si la suivante a le droit d'avoir
     * lieu.
     */
    private static void resolveLeftovers(Path target, Path pending) {
        boolean pendingExists = Files.exists(pending);
        boolean targetExists = Files.exists(target);

        if (pendingExists && targetExists) {
            if (sameFile(pending, target)) {
                // La publication avait reussi ; seul le nettoyage manquait. On le termine, et
                // on rend le meme verdict qu'une installation deja faite.
                deleteQuietly(pending);
                throw targetExists(target);
            }
            throw new SecretFileException(ExitCode.INTERRUPTED_INSTALLATION,
                    "Found " + pending + " next to a different " + target + "; two distinct key "
                            + "sets coexist and only an operator can tell which one is in use. "
                            + "Nothing was deleted.");
        }
        if (pendingExists) {
            throw new SecretFileException(ExitCode.INTERRUPTED_INSTALLATION,
                    "Found " + pending + " with no installed secrets file; a previous "
                            + "initialization was interrupted. Refusing to regenerate: those "
                            + "keys may already be in use. Nothing was deleted.");
        }
        if (targetExists) {
            throw targetExists(target);
        }
    }

    /**
     * Publication : le lien porte le nom final, ou rien ne se passe. Aucune vérification
     * d'existence préalable — c'est le système de fichiers qui arbitre, dans la même logique
     * que l'{@code ON CONFLICT} de l'amorçage des clés de signature.
     */
    private static void publish(Path pending, Path target) throws IOException {
        try {
            Files.createLink(target, pending);
        } catch (FileAlreadyExistsException e) {
            throw targetExists(target);
        } catch (UnsupportedOperationException e) {
            throw new SecretFileException(ExitCode.UNSAFE_FILESYSTEM,
                    "This filesystem cannot publish a file atomically without replacing an "
                            + "existing one; refusing to write secrets to " + target, e);
        }
    }

    private static void writeFully(Path pending, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(pending, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(bytes));
            // Forcer avant de publier : le nom final ne doit jamais designer un contenu que le
            // disque n'a pas encore recu.
            channel.force(true);
        }
    }

    private static boolean sameFile(Path pending, Path target) {
        try {
            return Files.isSameFile(pending, target);
        } catch (IOException e) {
            // Dans le doute, ne rien effacer : le cas ambigu est traite comme deux jeux
            // distincts, ce qui laisse la decision a l'operateur.
            return false;
        }
    }

    private static Path parentOf(Path target) {
        Path directory = target.toAbsolutePath().getParent();
        if (directory == null || !Files.isDirectory(directory)) {
            throw new SecretFileException(ExitCode.IO_FAILURE,
                    "The output directory does not exist: " + directory);
        }
        return directory;
    }

    private static SecretFileException targetExists(Path target) {
        return new SecretFileException(ExitCode.TARGET_EXISTS,
                "Refusing to overwrite an existing secrets file: " + target);
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Le fichier est deja parti, ou le repertoire ne se laisse pas modifier ; signaler
            // cet echec masquerait la cause reelle, et il n'y a rien a corriger ici.
        }
    }

    /**
     * La façon dont ce système de fichiers sait restreindre un fichier — déduite de ses
     * capacités déclarées, jamais du système d'exploitation.
     */
    private enum Restriction {

        /** Permissions fournies à la création : le fichier n'existe jamais autrement. */
        POSIX {
            @Override
            void createRestrictedFile(Path file) {
                Set<PosixFilePermission> ownerOnly =
                        EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
                FileAttribute<Set<PosixFilePermission>> attribute =
                        PosixFilePermissions.asFileAttribute(ownerOnly);
                createExclusively(file, attribute);
            }
        },

        /**
         * ACL posée sur un fichier vide, puis relue : aucun secret n'existe entre les deux.
         * La logique de restriction et de vérification vit dans {@link OwnerOnlyAcl}, où un
         * test la couvre quel que soit le système — sans quoi elle ne serait exercée que sur
         * un poste Windows, jamais en intégration continue.
         */
        WINDOWS_ACL {
            @Override
            void createRestrictedFile(Path file) {
                createExclusively(file);
                try {
                    OwnerOnlyAcl.restrict(
                            Files.getFileAttributeView(file, AclFileAttributeView.class),
                            Files.getOwner(file), file);
                } catch (IOException | RuntimeException e) {
                    deleteQuietly(file);
                    throw e instanceof SecretFileException secretFileException
                            ? secretFileException
                            : new SecretFileException(ExitCode.UNSAFE_FILESYSTEM,
                                    "Failed to restrict " + file + " to its owner", e);
                }
            }
        };

        abstract void createRestrictedFile(Path file);

        /**
         * Création exclusive : {@code CREATE_NEW} échoue si le nom est déjà pris, et c'est
         * cette exclusivité qui arbitre deux initialisations simultanées — avant qu'aucune clé
         * ne soit tirée.
         */
        static void createExclusively(Path file, FileAttribute<?>... attributes) {
            try {
                Files.createFile(file, attributes);
            } catch (FileAlreadyExistsException e) {
                throw new SecretFileException(ExitCode.INTERRUPTED_INSTALLATION,
                        "Another initialization holds " + file + "; if no other process is "
                                + "running, a previous one was interrupted. Nothing was written.",
                        e);
            } catch (IOException e) {
                throw new SecretFileException(ExitCode.IO_FAILURE,
                        "Failed to create " + file, e);
            }
        }

        static Restriction of(Path directory) {
            if (supports(directory, "posix")) {
                return POSIX;
            }
            if (supports(directory, "acl")) {
                return WINDOWS_ACL;
            }
            throw new SecretFileException(ExitCode.UNSAFE_FILESYSTEM,
                    "This filesystem supports neither POSIX permissions nor ACLs; "
                            + "refusing to write secrets in " + directory);
        }

        /**
         * Interroge le volume qui porte ce répertoire, et non le système de fichiers par
         * défaut : c'est le montage qui décide, et deux répertoires de la même machine
         * peuvent répondre différemment.
         */
        private static boolean supports(Path directory, String view) {
            try {
                return Files.getFileStore(directory).supportsFileAttributeView(view);
            } catch (IOException e) {
                throw new SecretFileException(ExitCode.IO_FAILURE,
                        "Failed to inspect the filesystem hosting " + directory, e);
            }
        }
    }
}
