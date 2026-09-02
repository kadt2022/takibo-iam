package com.takibo.installkeys;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
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
    static Durability write(Path target, Supplier<String> content) {
        Path directory = parentOf(target);
        Path pending = target.resolveSibling(target.getFileName() + PENDING_SUFFIX);

        // Avant d'interroger les capacites du volume : ce que cette installation a deja laisse
        // derriere elle prime sur ce que le systeme de fichiers sait faire. Dans l'ordre
        // inverse, une cible existante ou un amorcage interrompu sur un volume sans POSIX ni
        // ACL sortirait en UNSAFE_FILESYSTEM, et l'operateur perdrait le diagnostic qui
        // comptait — celui qui lui dit de restaurer plutot que de regenerer.
        resolveLeftovers(target, pending);

        Restriction restriction = Restriction.of(directory);
        restriction.createRestrictedFile(pending);
        // Le temoin de reprise ne sert a rien s'il ne survit pas a une coupure : son entree de
        // repertoire doit atteindre le disque avant que des cles n'y soient ecrites.
        boolean pendingEntryFlushed = syncDirectory(directory);
        // Le tirage a sa propre phase de nettoyage : une exception du fournisseur laisserait
        // sinon un .pending vide, que la prochaine execution lirait comme un amorcage
        // interrompu — donc comme des cles peut-etre deja en service. Un faux diagnostic de
        // cette gravite enverrait l'operateur chercher une sauvegarde inexistante.
        String generated = generate(content, pending);

        boolean targetEntryFlushed;
        try {
            writeFully(pending, generated);
            publish(pending, target);
            // Avant de retirer le second nom et d'annoncer le succes : sans cela, une coupure
            // pourrait faire disparaitre les DEUX entrees alors que les cles ont deja ete
            // remises a l'operateur, et le demarrage suivant en fabriquerait d'incompatibles.
            targetEntryFlushed = syncDirectory(directory);
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
        return pendingEntryFlushed && targetEntryFlushed
                ? Durability.FLUSHED_TO_DISK : Durability.BEST_EFFORT;
    }

    /**
     * Le niveau de garantie réellement atteint, rendu à l'appelant plutôt que taire.
     * <p>
     * Toute l'architecture du {@code .pending} vise un scénario précis : un succès annoncé,
     * des clés déjà mises en service, puis une coupure de courant qui ferait disparaître les
     * deux noms — et une installation suivante qui en fabriquerait d'incompatibles. Cette
     * protection repose sur la possibilité de forcer les entrées de répertoire sur le disque,
     * que tous les systèmes n'offrent pas.
     * <p>
     * La doctrine du récit : la garantie est <b>déclarée</b>, jamais silencieusement dégradée.
     * Là où elle ne peut pas être tenue, la CLI le dit — c'est le rôle de cette valeur.
     */
    enum Durability {

        /** Les entrées de répertoire ont atteint le disque : la coupure ne peut plus effacer. */
        FLUSHED_TO_DISK,

        /**
         * Le volume n'expose pas ses répertoires — Windows notamment, où l'API standard ne
         * permet pas d'ouvrir un répertoire en canal. Le fichier est écrit et publié, mais sa
         * survie à une coupure immédiate dépend alors du système de fichiers seul.
         */
        BEST_EFFORT
    }

    /**
     * Tire les valeurs, en retirant le fichier de travail si le tirage échoue.
     * <p>
     * Le fournisseur est du code arbitraire du point de vue de cette classe : il peut lever
     * n'importe quelle {@link RuntimeException} — un fournisseur cryptographique absent, par
     * exemple. Laisser cette exception traverser abandonnerait un {@code .pending} vide et
     * transformerait une panne passagère, réparable par une simple relance, en soupçon de clés
     * perdues.
     */
    private static String generate(Supplier<String> content, Path pending) {
        try {
            return content.get();
        } catch (RuntimeException e) {
            deleteQuietly(pending);
            throw new SecretFileException(ExitCode.GENERATION_FAILURE,
                    "Failed to generate the installation secrets; nothing was written", e);
        }
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

    /**
     * Force les entrées du répertoire sur le disque.
     * <p>
     * {@code force(true)} sur le fichier garantit son <b>contenu</b>, jamais son <b>nom</b> :
     * après une coupure de courant, un fichier parfaitement écrit peut n'être rattaché à aucun
     * répertoire. Ici, la conséquence serait grave — la CLI aurait annoncé un succès, ses clés
     * seraient déjà en service, et le démarrage suivant, ne trouvant plus ni cible ni témoin,
     * en fabriquerait d'incompatibles.
     * <p>
     * Tous les systèmes ne permettent pas d'ouvrir un répertoire en canal — Windows le refuse.
     * L'échec est donc admis : c'est une garantie supplémentaire là où elle existe, jamais une
     * condition de fonctionnement.
     */
    private static boolean syncDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
            return true;
        } catch (IOException | UnsupportedOperationException e) {
            // Ni exception ni silence : l'echec remonte comme un niveau de garantie moindre,
            // que l'appelant annonce a l'operateur.
            return false;
        }
    }

    private static void writeFully(Path pending, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(pending, StandardOpenOption.WRITE)) {
            writeFully(channel, bytes);
            // Forcer avant de publier : le nom final ne doit jamais designer un contenu que le
            // disque n'a pas encore recu.
            channel.force(true);
        }
    }

    /**
     * Écrit <b>tout</b> le tampon, quel que soit le nombre d'appels nécessaires.
     * <p>
     * {@link WritableByteChannel#write} ne s'engage pas à consommer le tampon entier en une
     * fois. Un unique appel publierait, dans le cas d'une écriture partielle, un fichier
     * tronqué — une clé coupée en son milieu — puis le forcerait sur le disque et annoncerait
     * un succès. Le contenu est donc bouclé jusqu'à épuisement.
     * <p>
     * Le canal est reçu en paramètre plutôt qu'ouvert ici : c'est ce qui permet à un test de
     * simuler l'écriture partielle, qu'un fichier local ne produit pratiquement jamais — donc
     * jamais au moment où on l'observerait.
     */
    static void writeFully(WritableByteChannel channel, byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
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
