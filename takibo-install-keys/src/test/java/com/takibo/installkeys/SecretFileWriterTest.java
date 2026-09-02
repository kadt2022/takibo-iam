package com.takibo.installkeys;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
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
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        SecretFileWriter.write(target, CONTENT);

        assertThat(target).content(StandardCharsets.UTF_8).isEqualTo(CONTENT);
    }

    @Test
    void given_a_written_file_then_only_its_owner_can_read_it() throws Exception {
        Path target = directory.resolve("secrets.env");

        SecretFileWriter.write(target, CONTENT);

        assertThat(readableByOwnerOnly(target))
                .as("le fichier de secrets doit rester illisible pour les autres")
                .isTrue();
    }

    @Test
    void given_a_written_file_then_no_temporary_remains() {
        Path target = directory.resolve("secrets.env");

        SecretFileWriter.write(target, CONTENT);

        assertThat(fileNamesIn(directory)).containsExactly("secrets.env");
    }

    @Test
    void given_an_existing_target_then_the_write_is_refused_and_the_content_is_untouched() {
        Path target = directory.resolve("secrets.env");
        writeFile(target, "ancien\n");

        assertThatThrownBy(() -> SecretFileWriter.write(target, CONTENT))
                .isInstanceOf(SecretFileException.class)
                .extracting(e -> ((SecretFileException) e).exitCode())
                .isEqualTo(ExitCode.TARGET_EXISTS);

        assertThat(target).content(StandardCharsets.UTF_8).isEqualTo("ancien\n");
        assertThat(fileNamesIn(directory)).containsExactly("secrets.env");
    }

    @Test
    void given_a_missing_directory_then_the_write_is_refused() {
        Path target = directory.resolve("absent").resolve("secrets.env");

        assertThatThrownBy(() -> SecretFileWriter.write(target, CONTENT))
                .isInstanceOf(SecretFileException.class)
                .extracting(e -> ((SecretFileException) e).exitCode())
                .isEqualTo(ExitCode.IO_FAILURE);
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
                            SecretFileWriter.write(target, content);
                            return content;
                        } catch (SecretFileException e) {
                            assertThat(e.exitCode()).isEqualTo(ExitCode.TARGET_EXISTS);
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
                .allMatch(code -> code == ExitCode.TARGET_EXISTS.value());
        assertThat(fileNamesIn(directory)).containsExactly("secrets.env");
    }

    // ---------- Fixtures ----------

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
