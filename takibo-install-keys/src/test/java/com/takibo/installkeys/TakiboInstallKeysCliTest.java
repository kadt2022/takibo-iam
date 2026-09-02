package com.takibo.installkeys;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La commande, ses refus et ses codes de sortie (TAKIBO-INSTALL-KEYS-01).
 * <p>
 * Chaque refus d'argument correspond à une façon connue de se tromper de fichier : une
 * commande mal citée par un shell, une faute de frappe sur l'option, un chemin oublié. Aucune
 * ne doit produire un fichier de secrets ailleurs que là où l'opérateur croit l'écrire.
 */
class TakiboInstallKeysCliTest {

    @TempDir
    Path directory;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @Test
    void given_init_with_an_output_path_then_the_three_values_are_written() {
        Path target = directory.resolve("takibo-secrets.env");

        int exitCode = run("init", "--out", target.toString());

        assertThat(exitCode).isZero();
        assertThat(target).exists();
        assertThat(readLines(target)).hasSize(3)
                .anyMatch(line -> line.startsWith("TAKIBO_TAS_CIPHER_KEY_ID="))
                .anyMatch(line -> line.startsWith("TAKIBO_TAS_CIPHER_KEY="))
                .anyMatch(line -> line.startsWith("TAKIBO_TAS_USER_CODE_HMAC_KEY="));
    }

    @Test
    void given_a_successful_run_then_stdout_carries_the_path_and_no_secret() {
        Path target = directory.resolve("takibo-secrets.env");

        run("init", "--out", target.toString());

        String stdout = out.toString(StandardCharsets.UTF_8);
        assertThat(stdout).contains(target.toAbsolutePath().toString());
        for (String line : readLines(target)) {
            String value = line.substring(line.indexOf('=') + 1);
            assertThat(stdout).doesNotContain(value);
            assertThat(err.toString(StandardCharsets.UTF_8)).doesNotContain(value);
        }
    }

    @Test
    void given_an_existing_target_then_it_is_never_overwritten() {
        Path target = directory.resolve("takibo-secrets.env");
        writeFile(target, "TAKIBO_TAS_CIPHER_KEY_ID=k-existing\n");

        int exitCode = run("init", "--out", target.toString());

        assertThat(exitCode).isEqualTo(ExitCode.TARGET_EXISTS.value());
        assertThat(readLines(target)).containsExactly("TAKIBO_TAS_CIPHER_KEY_ID=k-existing");
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("Refusing to overwrite");
    }

    @Test
    void given_a_failure_then_no_temporary_file_is_left_behind() {
        // Un temporaire abandonne est un secret expose : l'echec de publication doit nettoyer.
        Path target = directory.resolve("takibo-secrets.env");
        writeFile(target, "deja la\n");

        run("init", "--out", target.toString());

        assertThat(filesIn(directory)).containsExactly(target.getFileName().toString());
    }

    @Test
    void given_no_argument_then_the_usage_is_refused() {
        assertThat(run()).isEqualTo(ExitCode.USAGE.value());
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("usage:");
    }

    @Test
    void given_an_unknown_command_then_it_is_refused_without_writing_anything() {
        int exitCode = run("bootstrap", "--out", directory.resolve("x.env").toString());

        assertThat(exitCode).isEqualTo(ExitCode.USAGE.value());
        assertThat(filesIn(directory)).isEmpty();
    }

    @Test
    void given_a_missing_out_option_then_it_is_refused() {
        assertThat(run("init")).isEqualTo(ExitCode.USAGE.value());
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("--out");
    }

    @Test
    void given_an_unknown_option_then_it_is_refused() {
        // Notamment --force, qui n'existe pas et ne doit surtout pas etre ignore en silence.
        int exitCode = run("init", "--force", directory.resolve("x.env").toString());

        assertThat(exitCode).isEqualTo(ExitCode.USAGE.value());
        assertThat(filesIn(directory)).isEmpty();
    }

    @Test
    void given_an_out_option_without_a_path_then_it_is_refused() {
        assertThat(run("init", "--out")).isEqualTo(ExitCode.USAGE.value());
    }

    @Test
    void given_an_extra_argument_then_it_is_refused() {
        // Souvent le signe d'un chemin non protege par des guillemets : le fichier serait ecrit
        // ailleurs que la ou l'operateur croit.
        int exitCode = run("init", "--out", directory.resolve("x.env").toString(), "en-trop");

        assertThat(exitCode).isEqualTo(ExitCode.USAGE.value());
        assertThat(filesIn(directory)).isEmpty();
    }

    @Test
    void given_a_path_the_filesystem_cannot_represent_then_it_is_refused() {
        // Le caractere nul est invalide dans un chemin sur tous les systemes vises : la CLI
        // doit le refuser comme un argument, sans laisser remonter une exception brute.
        int exitCode = run("init", "--out", "secrets\0.env");

        assertThat(exitCode).isEqualTo(ExitCode.USAGE.value());
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("invalid path");
    }

    @Test
    void given_a_missing_output_directory_then_the_failure_is_reported_without_creating_it() {
        Path target = directory.resolve("absent").resolve("takibo-secrets.env");

        int exitCode = run("init", "--out", target.toString());

        assertThat(exitCode).isEqualTo(ExitCode.IO_FAILURE.value());
        assertThat(target.getParent()).doesNotExist();
    }

    // ---------- Fixtures ----------

    private int run(String... args) {
        return TakiboInstallKeysCli.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private static List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
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

    private static List<String> filesIn(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.map(path -> path.getFileName().toString()).toList();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
