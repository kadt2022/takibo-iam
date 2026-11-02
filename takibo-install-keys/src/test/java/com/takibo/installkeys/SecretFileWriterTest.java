package com.takibo.installkeys;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.takibo.installkeys.InterceptingFileSystem.Operation;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * L'écriture protégée du fichier de secrets (TAKIBO-INSTALL-KEYS-01).
 * <p>
 * Deux propriétés portent tout le reste : le secret n'est jamais lisible par un autre que son
 * propriétaire, et deux initialisations simultanées ne produisent jamais deux fichiers ni un
 * fichier écrasé.
 */
class SecretFileWriterTest {

    private static final String CONTENT = "TAKIBO_TAS_CIPHER_KEY_ID=k-probe\n";

    @TempDir
    Path directory;

    @Test
    void given_a_free_target_then_the_content_is_written_whole() {
        Path target = directory.resolve("secrets.env");

        SecretFileWriter.write(target, () -> CONTENT);

        assertThat(target).content(StandardCharsets.UTF_8).isEqualTo(CONTENT);
    }

    @Test
    void given_a_written_file_then_only_its_owner_can_read_it() throws Exception {
        Path target = directory.resolve("secrets.env");

        SecretFileWriter.write(target, () -> CONTENT);

        assertThat(readableByOwnerOnly(target))
                .as("le fichier de secrets doit rester illisible pour les autres")
                .isTrue();
    }

    @Test
    void given_a_written_file_then_no_temporary_remains() {
        Path target = directory.resolve("secrets.env");

        SecretFileWriter.write(target, () -> CONTENT);

        assertThat(fileNamesIn(directory)).containsExactly("secrets.env");
    }

    @Test
    void given_an_existing_target_then_the_write_is_refused_and_the_content_is_untouched() {
        Path target = directory.resolve("secrets.env");
        writeFile(target, "ancien\n");

        assertThatThrownBy(() -> SecretFileWriter.write(target, () -> CONTENT))
                .isInstanceOf(SecretFileException.class)
                .extracting(e -> ((SecretFileException) e).exitCode())
                .isEqualTo(ExitCode.TARGET_EXISTS);

        assertThat(target).content(StandardCharsets.UTF_8).isEqualTo("ancien\n");
        assertThat(fileNamesIn(directory)).containsExactly("secrets.env");
    }

    @Test
    void given_a_missing_directory_then_the_write_is_refused() {
        Path target = directory.resolve("absent").resolve("secrets.env");

        assertThatThrownBy(() -> SecretFileWriter.write(target, () -> CONTENT))
                .isInstanceOf(SecretFileException.class)
                .extracting(e -> ((SecretFileException) e).exitCode())
                .isEqualTo(ExitCode.IO_FAILURE);
    }

    // ---------- Ecriture complete ----------

    @Test
    void given_a_channel_that_writes_in_small_chunks_then_every_byte_still_reaches_it()
            throws Exception {
        // write() ne s'engage pas a consommer tout le tampon en une fois. Un unique appel
        // publierait un fichier tronque — une cle coupee en son milieu — puis le forcerait sur
        // le disque et annoncerait un succes. Un canal local ne produit pratiquement jamais
        // d'ecriture partielle, donc seule une simulation peut le prouver.
        byte[] content = ("TAKIBO_TAS_CIPHER_KEY_ID=k-partiel\n"
                + "TAKIBO_TAS_CIPHER_KEY=" + "A".repeat(44) + "\n").getBytes(StandardCharsets.UTF_8);
        ChunkedChannel channel = new ChunkedChannel(7);

        SecretFileWriter.writeFully(channel, content);

        assertThat(channel.written.toByteArray()).isEqualTo(content);
        assertThat(channel.calls).isGreaterThan(1);
    }

    @Test
    void given_an_empty_content_then_the_write_loop_terminates_immediately() throws Exception {
        ChunkedChannel channel = new ChunkedChannel(7);

        SecretFileWriter.writeFully(channel, new byte[0]);

        assertThat(channel.calls).isZero();
    }

    @Test
    void given_a_failing_key_generation_then_nothing_is_left_behind() {
        // Une panne du fournisseur cryptographique ne doit pas se deguiser en amorcage
        // interrompu : sans nettoyage, la prochaine execution lirait un .pending vide comme
        // des cles peut-etre deja en service, et enverrait l'operateur chercher une
        // sauvegarde inexistante.
        Path target = directory.resolve("secrets.env");

        assertThatThrownBy(() -> SecretFileWriter.write(target, () -> {
            throw new IllegalStateException("fournisseur indisponible");
        }))
                .isInstanceOf(SecretFileException.class)
                .extracting(e -> ((SecretFileException) e).exitCode())
                .isEqualTo(ExitCode.GENERATION_FAILURE);

        assertThat(fileNamesIn(directory)).isEmpty();
    }

    @Test
    void given_a_failing_generation_then_a_later_run_still_succeeds() {
        // La preuve qui compte pour l'operateur : relancer suffit.
        Path target = directory.resolve("secrets.env");
        try {
            SecretFileWriter.write(target, () -> {
                throw new IllegalStateException("fournisseur indisponible");
            });
        } catch (SecretFileException expected) {
            // Le cas nominal de ce test est l'echec ; la suite verifie qu'il ne laisse rien.
        }

        SecretFileWriter.write(target, () -> CONTENT);

        assertThat(target).content(StandardCharsets.UTF_8).isEqualTo(CONTENT);
        assertThat(fileNamesIn(directory)).containsExactly("secrets.env");
    }

    // ---------- Concurrence ----------

    @Test
    void given_simultaneous_initialisations_then_exactly_one_file_wins() throws Exception {
        // Le coeur de la garde : la publication est arbitree par le systeme de fichiers, pas
        // par une verification prealable. Huit ecritures, une seule doit aboutir, et le
        // fichier final doit etre celui de la gagnante — complet, jamais un melange.
        int writers = 8;
        Path target = directory.resolve("secrets.env");
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        CountDownLatch ready = new CountDownLatch(writers);
        CountDownLatch go = new CountDownLatch(1);

        List<Future<String>> results;
        try {
            List<Callable<String>> attempts = IntStream.range(0, writers)
                    .<Callable<String>>mapToObj(index -> () -> {
                        String content = "TAKIBO_TAS_CIPHER_KEY_ID=k-writer-" + index + "\n";
                        ready.countDown();
                        go.await();
                        try {
                            SecretFileWriter.write(target, () -> content);
                            return content;
                        } catch (SecretFileException e) {
                            // Deux refus possibles, et tous deux corrects : le .pending de la
                            // gagnante tient encore (INTERRUPTED_INSTALLATION) ou elle a deja
                            // publie et nettoye (TARGET_EXISTS). Aucun autre code n'est admis.
                            assertThat(e.exitCode()).isIn(
                                    ExitCode.INTERRUPTED_INSTALLATION, ExitCode.TARGET_EXISTS);
                            return null;
                        }
                    })
                    .toList();
            results = attempts.stream().map(pool::submit).toList();
            ready.await();
            go.countDown();
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        List<String> winners = results.stream().map(SecretFileWriterTest::get)
                .filter(java.util.Objects::nonNull).toList();

        assertThat(winners).hasSize(1);
        // Le contenu publie est exactement celui de la gagnante : aucune ecriture concurrente
        // n'a pu s'y melanger, chacune ayant travaille dans son propre temporaire.
        assertThat(target).content(StandardCharsets.UTF_8).isEqualTo(winners.get(0));
        assertThat(fileNamesIn(directory)).containsExactly("secrets.env");
    }

    @Test
    void given_a_cli_race_then_only_one_process_reports_success() throws Exception {
        // La meme course, vue depuis la commande : un seul code 0, les autres en TARGET_EXISTS,
        // et jamais un code d'echec inattendu.
        int writers = 4;
        Path target = directory.resolve("secrets.env");
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        CountDownLatch go = new CountDownLatch(1);
        PrintStream silence = new PrintStream(OutputStream.nullOutputStream(), true,
                StandardCharsets.UTF_8);

        List<Future<Integer>> results;
        try {
            results = IntStream.range(0, writers)
                    .<Callable<Integer>>mapToObj(index -> () -> {
                        go.await();
                        return TakiboInstallKeysCli.run(
                                new String[] {"init", "--out", target.toString()},
                                silence, silence);
                    })
                    .map(pool::submit)
                    .toList();
            go.countDown();
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        List<Integer> codes = results.stream().map(SecretFileWriterTest::get).toList();
        assertThat(codes).filteredOn(code -> code == ExitCode.SUCCESS.value()).hasSize(1);
        assertThat(codes).filteredOn(code -> code != ExitCode.SUCCESS.value())
                .allMatch(code -> code == ExitCode.TARGET_EXISTS.value()
                        || code == ExitCode.INTERRUPTED_INSTALLATION.value());
        assertThat(fileNamesIn(directory)).containsExactly("secrets.env");
    }

    @Test
    void given_a_target_that_appears_during_the_write_then_it_is_not_overwritten() {
        // L'exigence la plus fine : la cible n'existait pas au depart, elle apparait pendant
        // que nous ecrivons. Aucune verification prealable ne peut couvrir ce cas — seule la
        // publication, arbitree par le systeme de fichiers, le refuse. Le fournisseur de
        // contenu sert ici d'instant precis ou l'intrus s'installe.
        Path target = directory.resolve("secrets.env");

        assertThatThrownBy(() -> SecretFileWriter.write(target, () -> {
            writeFile(target, "pose par un concurrent\n");
            return CONTENT;
        }))
                .isInstanceOf(SecretFileException.class)
                .extracting(e -> ((SecretFileException) e).exitCode())
                .isEqualTo(ExitCode.TARGET_EXISTS);

        assertThat(target).content(StandardCharsets.UTF_8).isEqualTo("pose par un concurrent\n");
        // Et notre propre .pending, jamais publie, ne reste pas derriere : c'est un secret.
        assertThat(fileNamesIn(directory)).containsExactly("secrets.env");
    }

    // ---------- Arret brutal ----------

    @Test
    void given_a_pending_file_without_a_target_then_nothing_is_regenerated() {
        // Premier instant : la machine s'arrete entre l'ecriture et la publication. Le
        // .pending porte des cles qui peuvent deja avoir servi ailleurs — les regenerer
        // rendrait indechiffrable ce qu'elles ont scelle.
        Path target = directory.resolve("secrets.env");
        Path pending = directory.resolve("secrets.env" + SecretFileWriter.PENDING_SUFFIX);
        writeFile(pending, "TAKIBO_TAS_CIPHER_KEY_ID=k-interrompu\n");

        assertThatThrownBy(() -> SecretFileWriter.write(target, () -> CONTENT))
                .isInstanceOf(SecretFileException.class)
                .extracting(e -> ((SecretFileException) e).exitCode())
                .isEqualTo(ExitCode.INTERRUPTED_INSTALLATION);

        assertThat(target).doesNotExist();
        // Jamais efface automatiquement : ce fichier est peut-etre la seule copie des cles.
        assertThat(pending).content(StandardCharsets.UTF_8)
                .isEqualTo("TAKIBO_TAS_CIPHER_KEY_ID=k-interrompu\n");
    }

    @Test
    void given_a_pending_file_linked_to_the_target_then_only_the_pending_is_removed()
            throws Exception {
        // Second instant : la publication a reussi, seul le nettoyage manquait. Les deux noms
        // designent le meme inode, l'installation est donc bel et bien faite.
        Path target = directory.resolve("secrets.env");
        Path pending = directory.resolve("secrets.env" + SecretFileWriter.PENDING_SUFFIX);
        writeFile(target, CONTENT);
        Files.createLink(pending, target);

        assertThatThrownBy(() -> SecretFileWriter.write(target, () -> "regenere\n"))
                .isInstanceOf(SecretFileException.class)
                .extracting(e -> ((SecretFileException) e).exitCode())
                .isEqualTo(ExitCode.TARGET_EXISTS);

        assertThat(pending).doesNotExist();
        assertThat(target).content(StandardCharsets.UTF_8).isEqualTo(CONTENT);
    }

    @Test
    void given_a_pending_file_distinct_from_the_target_then_nothing_is_deleted() {
        // Deux jeux de cles distincts : personne d'autre que l'operateur ne peut dire lequel
        // fait foi, donc rien n'est efface et rien n'est produit.
        Path target = directory.resolve("secrets.env");
        Path pending = directory.resolve("secrets.env" + SecretFileWriter.PENDING_SUFFIX);
        writeFile(target, "TAKIBO_TAS_CIPHER_KEY_ID=k-installe\n");
        writeFile(pending, "TAKIBO_TAS_CIPHER_KEY_ID=k-autre\n");

        assertThatThrownBy(() -> SecretFileWriter.write(target, () -> CONTENT))
                .isInstanceOf(SecretFileException.class)
                .extracting(e -> ((SecretFileException) e).exitCode())
                .isEqualTo(ExitCode.INTERRUPTED_INSTALLATION);

        assertThat(target).content(StandardCharsets.UTF_8)
                .isEqualTo("TAKIBO_TAS_CIPHER_KEY_ID=k-installe\n");
        assertThat(pending).content(StandardCharsets.UTF_8)
                .isEqualTo("TAKIBO_TAS_CIPHER_KEY_ID=k-autre\n");
    }

    @Test
    void given_a_losing_initialisation_then_it_never_drew_any_key() {
        // Le .pending arbitre avant le tirage : la perdante ne consomme pas d'entropie et ne
        // laisse aucune matiere derriere elle.
        Path target = directory.resolve("secrets.env");
        writeFile(directory.resolve("secrets.env" + SecretFileWriter.PENDING_SUFFIX), "occupe\n");
        java.util.concurrent.atomic.AtomicBoolean drawn =
                new java.util.concurrent.atomic.AtomicBoolean();

        assertThatThrownBy(() -> SecretFileWriter.write(target, () -> {
            drawn.set(true);
            return CONTENT;
        })).isInstanceOf(SecretFileException.class);

        assertThat(drawn).isFalse();
    }

    @Test
    void given_a_leftover_whose_identity_cannot_be_read_then_nothing_is_deleted()
            throws Exception {
        // Le doute ne vaut pas preuve : si le systeme ne sait pas dire que les deux noms
        // designent le meme fichier, ils sont traites comme deux jeux de cles distincts, et
        // aucun n'est efface — meme lorsqu'il s'agit en realite du meme inode.
        Path realTarget = directory.resolve("secrets.env");
        Path realPending = directory.resolve("secrets.env" + SecretFileWriter.PENDING_SUFFIX);
        writeFile(realTarget, CONTENT);
        Files.createLink(realPending, realTarget);
        Path target = filesystem((operation, path) -> {
            if (operation == Operation.SAME_FILE) {
                throw new IOException("stat refuse");
            }
        }).wrap(realTarget);

        assertThat(refusalOf(() -> SecretFileWriter.write(target, () -> CONTENT)).exitCode())
                .isEqualTo(ExitCode.INTERRUPTED_INSTALLATION);

        assertThat(realPending).content(StandardCharsets.UTF_8).isEqualTo(CONTENT);
        assertThat(realTarget).content(StandardCharsets.UTF_8).isEqualTo(CONTENT);
    }

    // ---------- Durabilite declaree ----------

    @Test
    void given_a_directory_that_can_be_flushed_then_the_durability_is_flushed_to_disk() {
        Path target = directory.resolve("secrets.env");

        assertThat(SecretFileWriter.write(target, () -> CONTENT))
                .isEqualTo(SecretFileWriter.Durability.FLUSHED_TO_DISK);
    }

    @Test
    void given_a_directory_that_refuses_to_be_flushed_then_the_write_succeeds_in_best_effort() {
        // Le cas Windows : le repertoire ne s'ouvre pas en canal. Ce n'est pas un echec — le
        // fichier est ecrit et publie — mais la garantie moindre doit etre rendue, jamais tue.
        Path realTarget = directory.resolve("secrets.env");
        Path target = filesystem((operation, path) -> {
            if (operation == Operation.OPEN_CHANNEL && path.equals(directory)) {
                throw new IOException("repertoire non ouvrable");
            }
        }).wrap(realTarget);

        assertThat(SecretFileWriter.write(target, () -> CONTENT))
                .isEqualTo(SecretFileWriter.Durability.BEST_EFFORT);

        assertThat(realTarget).content(StandardCharsets.UTF_8).isEqualTo(CONTENT);
        assertThat(fileNamesIn(directory)).containsExactly("secrets.env");
    }

    @Test
    void given_a_flush_lost_only_after_publication_then_the_lower_guarantee_is_still_declared() {
        // Les deux forcages comptent : un temoin bien force ne rachete pas une cible dont
        // l'entree de repertoire n'a pas atteint le disque.
        AtomicInteger directoryOpenings = new AtomicInteger();
        Path target = filesystem((operation, path) -> {
            if (operation == Operation.OPEN_CHANNEL && path.equals(directory)
                    && directoryOpenings.incrementAndGet() == 2) {
                throw new IOException("repertoire non ouvrable");
            }
        }).wrap(directory.resolve("secrets.env"));

        assertThat(SecretFileWriter.write(target, () -> CONTENT))
                .isEqualTo(SecretFileWriter.Durability.BEST_EFFORT);
        assertThat(directoryOpenings).hasValue(2);
    }

    // ---------- Pannes du systeme de fichiers ----------

    @Test
    void given_a_disk_failure_while_writing_then_io_failure_and_no_secret_is_left_behind() {
        // Disque plein au moment d'ecrire les cles : le .pending, deja rempli de leur tirage,
        // ne doit ni etre publie ni rester dans le repertoire.
        Path realTarget = directory.resolve("secrets.env");
        Path realPending = directory.resolve("secrets.env" + SecretFileWriter.PENDING_SUFFIX);
        Path target = filesystem((operation, path) -> {
            if (operation == Operation.OPEN_CHANNEL && path.equals(realPending)) {
                throw new IOException("No space left on device");
            }
        }).wrap(realTarget);

        SecretFileException refusal = refusalOf(() -> SecretFileWriter.write(target, () -> CONTENT));

        assertThat(refusal.exitCode()).isEqualTo(ExitCode.IO_FAILURE);
        assertThat(refusal).hasMessageContaining(realTarget.toString())
                .hasRootCauseMessage("No space left on device");
        assertThat(fileNamesIn(directory)).isEmpty();
    }

    @Test
    void given_a_creation_refused_by_the_filesystem_then_io_failure_and_no_key_is_drawn() {
        AtomicBoolean drawn = new AtomicBoolean();
        Path target = filesystem((operation, path) -> {
            if (operation == Operation.CREATE_FILE) {
                throw new AccessDeniedException(path.toString());
            }
        }).wrap(directory.resolve("secrets.env"));

        SecretFileException refusal = refusalOf(() -> SecretFileWriter.write(target, () -> {
            drawn.set(true);
            return CONTENT;
        }));

        assertThat(refusal.exitCode()).isEqualTo(ExitCode.IO_FAILURE);
        assertThat(refusal).hasCauseInstanceOf(AccessDeniedException.class);
        assertThat(drawn).isFalse();
        assertThat(fileNamesIn(directory)).isEmpty();
    }

    @Test
    void given_a_volume_that_cannot_be_inspected_then_io_failure_before_anything_exists() {
        AtomicBoolean drawn = new AtomicBoolean();
        Path target = filesystem((operation, path) -> {
            if (operation == Operation.FILE_STORE) {
                throw new IOException("montage inaccessible");
            }
        }).wrap(directory.resolve("secrets.env"));

        SecretFileException refusal = refusalOf(() -> SecretFileWriter.write(target, () -> {
            drawn.set(true);
            return CONTENT;
        }));

        assertThat(refusal.exitCode()).isEqualTo(ExitCode.IO_FAILURE);
        assertThat(refusal).hasMessageContaining(directory.toString());
        assertThat(drawn).isFalse();
        assertThat(fileNamesIn(directory)).isEmpty();
    }

    @Test
    void given_a_target_without_a_parent_directory_then_the_write_is_refused() {
        // La racine du systeme de fichiers n'a pas de repertoire parent : il n'y a nulle part
        // ou poser le temoin, donc rien n'est tente.
        Path root = directory.getRoot();

        assertThat(refusalOf(() -> SecretFileWriter.write(root, () -> CONTENT)).exitCode())
                .isEqualTo(ExitCode.IO_FAILURE);
    }

    @Test
    void given_a_cleanup_that_fails_then_the_original_cause_is_still_reported() {
        // Le nettoyage est accessoire : son echec ne doit pas remplacer le diagnostic qui
        // compte — ici, une panne du tirage, que l'operateur resout en relancant.
        Path target = filesystem((operation, path) -> {
            if (operation == Operation.DELETE) {
                throw new IOException("suppression refusee");
            }
        }).wrap(directory.resolve("secrets.env"));

        SecretFileException refusal = refusalOf(() -> SecretFileWriter.write(target, () -> {
            throw new IllegalStateException("fournisseur indisponible");
        }));

        assertThat(refusal.exitCode()).isEqualTo(ExitCode.GENERATION_FAILURE);
        assertThat(refusal).hasRootCauseMessage("fournisseur indisponible");
    }

    @Test
    void given_a_filesystem_without_hard_links_then_unsafe_filesystem_and_no_secret_is_left() {
        // Sans lien physique, la seule publication restante ecraserait une cible existante :
        // refus, et le .pending deja rempli de cles est retire.
        Path target = filesystem((operation, path) -> {
            if (operation == Operation.CREATE_LINK) {
                throw new UnsupportedOperationException("liens physiques non supportes");
            }
        }).wrap(directory.resolve("secrets.env"));

        SecretFileException refusal = refusalOf(() -> SecretFileWriter.write(target, () -> CONTENT));

        assertThat(refusal.exitCode()).isEqualTo(ExitCode.UNSAFE_FILESYSTEM);
        assertThat(refusal).hasCauseInstanceOf(UnsupportedOperationException.class);
        assertThat(fileNamesIn(directory)).isEmpty();
    }

    // ---------- Capacites du volume ----------

    @Test
    void given_a_volume_with_neither_posix_nor_acl_then_refused_before_any_key_is_drawn() {
        // FAT, exFAT, certains montages reseau : aucun moyen de restreindre le fichier. Le
        // refus intervient avant qu'un secret n'existe, meme vide.
        AtomicBoolean drawn = new AtomicBoolean();
        Path target = new InterceptingFileSystem(directory).declaringViews("basic")
                .wrap(directory.resolve("secrets.env"));

        SecretFileException refusal = refusalOf(() -> SecretFileWriter.write(target, () -> {
            drawn.set(true);
            return CONTENT;
        }));

        assertThat(refusal.exitCode()).isEqualTo(ExitCode.UNSAFE_FILESYSTEM);
        assertThat(drawn).isFalse();
        assertThat(fileNamesIn(directory)).isEmpty();
    }

    @Test
    void given_an_unsafe_volume_holding_an_installed_target_then_target_exists_is_reported() {
        // Ce que l'installation a deja laisse prime sur ce que le volume sait faire : le
        // diagnostic utile est « restaurez », pas « changez de disque ».
        writeFile(directory.resolve("secrets.env"), CONTENT);
        Path target = new InterceptingFileSystem(directory).declaringViews("basic")
                .wrap(directory.resolve("secrets.env"));

        assertThat(refusalOf(() -> SecretFileWriter.write(target, () -> CONTENT)).exitCode())
                .isEqualTo(ExitCode.TARGET_EXISTS);
    }

    @Test
    void given_an_unsafe_volume_holding_an_interrupted_installation_then_it_is_reported() {
        Path realPending = directory.resolve("secrets.env" + SecretFileWriter.PENDING_SUFFIX);
        writeFile(realPending, CONTENT);
        Path target = new InterceptingFileSystem(directory).declaringViews("basic")
                .wrap(directory.resolve("secrets.env"));

        assertThat(refusalOf(() -> SecretFileWriter.write(target, () -> CONTENT)).exitCode())
                .isEqualTo(ExitCode.INTERRUPTED_INSTALLATION);
        assertThat(realPending).content(StandardCharsets.UTF_8).isEqualTo(CONTENT);
    }

    // ---------- Volume a ACL ----------

    @Test
    void given_an_acl_volume_then_the_file_is_restricted_while_it_is_still_empty() {
        // Sous Windows, aucune ACL ne peut etre fournie a la creation : ce qui existe
        // brievement sous les droits herites doit etre un fichier vide, jamais un secret.
        Path realTarget = directory.resolve("secrets.env");
        Path realPending = directory.resolve("secrets.env" + SecretFileWriter.PENDING_SUFFIX);
        RecordingAclView view = new RecordingAclView(realPending, applied -> applied);
        Path target = aclVolume(view).wrap(realTarget);

        SecretFileWriter.write(target, () -> CONTENT);

        assertThat(realTarget).content(StandardCharsets.UTF_8).isEqualTo(CONTENT);
        assertThat(view.sizeWhenRestricted).isZero();
        assertThat(view.entries).singleElement().satisfies(entry -> {
            assertThat(entry.principal()).isEqualTo(RecordingAclView.OWNER);
            assertThat(entry.type()).isEqualTo(AclEntryType.ALLOW);
        });
        assertThat(fileNamesIn(directory)).containsExactly("secrets.env");
    }

    @Test
    void given_an_acl_volume_that_keeps_another_principal_then_refused_and_nothing_is_left() {
        // Le volume accepte setAcl sans erreur mais garde SYSTEM : la relecture le detecte,
        // et le fichier vide est retire avant qu'aucune cle ne soit tiree.
        AtomicBoolean drawn = new AtomicBoolean();
        RecordingAclView view = new RecordingAclView(
                directory.resolve("secrets.env" + SecretFileWriter.PENDING_SUFFIX),
                applied -> Stream.concat(applied.stream(), Stream.of(AclEntry.newBuilder()
                        .setType(AclEntryType.ALLOW)
                        .setPrincipal(new StubPrincipal("SYSTEM"))
                        .setPermissions(AclEntryPermission.READ_DATA)
                        .build())).toList());
        Path target = aclVolume(view).wrap(directory.resolve("secrets.env"));

        SecretFileException refusal = refusalOf(() -> SecretFileWriter.write(target, () -> {
            drawn.set(true);
            return CONTENT;
        }));

        assertThat(refusal.exitCode()).isEqualTo(ExitCode.UNSAFE_FILESYSTEM);
        assertThat(drawn).isFalse();
        assertThat(fileNamesIn(directory)).isEmpty();
    }

    @Test
    void given_an_acl_volume_that_cannot_apply_the_acl_then_refused_with_its_cause() {
        RecordingAclView view = new RecordingAclView(
                directory.resolve("secrets.env" + SecretFileWriter.PENDING_SUFFIX),
                applied -> {
                    throw new UncheckedIOException(new IOException("ACL refusee"));
                });
        Path target = aclVolume(view).wrap(directory.resolve("secrets.env"));

        SecretFileException refusal = refusalOf(() -> SecretFileWriter.write(target, () -> CONTENT));

        assertThat(refusal.exitCode()).isEqualTo(ExitCode.UNSAFE_FILESYSTEM);
        assertThat(refusal).hasRootCauseMessage("ACL refusee");
        assertThat(fileNamesIn(directory)).isEmpty();
    }

    // ---------- Fixtures ----------

    private InterceptingFileSystem filesystem(InterceptingFileSystem.Interceptor interceptor) {
        return new InterceptingFileSystem(directory).intercept(interceptor);
    }

    /** Un volume qui ne déclare que les ACL : le chemin emprunté sous Windows. */
    private InterceptingFileSystem aclVolume(AclFileAttributeView view) {
        return new InterceptingFileSystem(directory).declaringViews("basic", "owner", "acl")
                .withAclView(view);
    }

    private static SecretFileException refusalOf(Runnable write) {
        Throwable thrown = catchThrowable(write::run);
        assertThat(thrown).isInstanceOf(SecretFileException.class);
        return (SecretFileException) thrown;
    }

    /**
     * Vue ACL qui retient ce qu'on lui demande et la taille du fichier à cet instant ; le
     * volume simulé applique {@code applied} à la liste reçue, ce qui le rend docile,
     * désobéissant ou défaillant selon le test.
     */
    private static final class RecordingAclView implements AclFileAttributeView {

        static final UserPrincipal OWNER = new StubPrincipal("OWNER");

        private final Path file;
        private final UnaryOperator<List<AclEntry>> applied;
        private List<AclEntry> entries = List.of();
        private long sizeWhenRestricted = -1;

        RecordingAclView(Path file, UnaryOperator<List<AclEntry>> applied) {
            this.file = file;
            this.applied = applied;
        }

        @Override
        public void setAcl(List<AclEntry> acl) throws IOException {
            sizeWhenRestricted = Files.size(file);
            try {
                entries = applied.apply(acl);
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        }

        @Override
        public List<AclEntry> getAcl() {
            return entries;
        }

        @Override
        public UserPrincipal getOwner() {
            return OWNER;
        }

        @Override
        public void setOwner(UserPrincipal owner) {
            throw new UnsupportedOperationException("l'ecriture ne change jamais de proprietaire");
        }

        @Override
        public String name() {
            return "acl";
        }
    }

    private record StubPrincipal(String getName) implements UserPrincipal {
    }

    /** Canal qui n'accepte qu'un nombre borné d'octets par appel, comme le permet le contrat. */
    private static final class ChunkedChannel implements java.nio.channels.WritableByteChannel {

        private final int chunk;
        private final java.io.ByteArrayOutputStream written = new java.io.ByteArrayOutputStream();
        private int calls;

        ChunkedChannel(int chunk) {
            this.chunk = chunk;
        }

        @Override
        public int write(java.nio.ByteBuffer source) {
            calls++;
            int count = Math.min(chunk, source.remaining());
            byte[] slice = new byte[count];
            source.get(slice);
            written.write(slice, 0, count);
            return count;
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void close() {
            // Rien a liberer : ce double n'ouvre aucune ressource.
        }
    }

    /**
     * Vérifie la restriction avec les moyens du système de fichiers qui l'a posée : permissions
     * POSIX ici, ACL là. Interroger l'une sur un volume qui porte l'autre ne prouverait rien.
     */
    private static boolean readableByOwnerOnly(Path file) throws Exception {
        if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
            return PosixFilePermissions.toString(permissions).equals("rw-------");
        }
        AclFileAttributeView view = Files.getFileAttributeView(file, AclFileAttributeView.class);
        UserPrincipal owner = Files.getOwner(file);
        List<AclEntry> acl = view.getAcl();
        return !acl.isEmpty() && acl.stream().allMatch(
                entry -> entry.principal().equals(owner) && entry.type() == AclEntryType.ALLOW);
    }

    private static <T> T get(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void writeFile(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> fileNamesIn(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.map(path -> path.getFileName().toString()).toList();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
