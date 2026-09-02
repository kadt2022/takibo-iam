package com.takibo.installkeys;

import java.io.PrintStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.SecureRandom;

/**
 * La CLI d'initialisation cryptographique de TAKIBO (TAKIBO-INSTALL-KEYS-01).
 *
 * <pre>
 * java -jar takibo-install-keys.jar init --out takibo-secrets.env
 * </pre>
 *
 * <h2>Une seule commande, et aucun défaut</h2>
 * {@code --out} est obligatoire : un chemin implicite créerait un fichier de secrets là où
 * personne ne le cherche, et c'est ainsi qu'un secret survit à une désinstallation. Il n'existe
 * pas non plus de {@code --force} : l'option n'est pas absente par oubli, elle est refusée par
 * principe, pour qu'elle ne puisse pas être employée sous pression. Une cible existante
 * s'affronte en restaurant la sauvegarde, jamais en régénérant contre une base déjà chiffrée.
 * <p>
 * Tout argument inconnu, manquant ou en trop est refusé sans que rien ne soit tenté : une
 * faute de frappe sur {@code --out} ne doit pas produire un fichier au mauvais endroit.
 *
 * <h2>Ce qui sort, et par où</h2>
 * La sortie standard ne porte <b>jamais</b> de secret — ni en succès, ni en échec. Elle
 * confirme le chemin écrit, rien de plus. Les diagnostics vont sur la sortie d'erreur, et les
 * codes de sortie ({@link ExitCode}) distinguent les situations pour qu'un script n'ait pas à
 * lire du texte.
 */
public final class TakiboInstallKeysCli {

    private static final String COMMAND_INIT = "init";
    private static final String OPTION_OUT = "--out";

    private static final String USAGE =
            "usage: takibo-install-keys init --out <path>";

    private TakiboInstallKeysCli() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * Le corps réel de la commande, séparé de {@code main} pour que les flux et le code de
     * sortie soient observables par test sans arrêter la JVM.
     */
    static int run(String[] args, PrintStream out, PrintStream err) {
        Path target;
        try {
            target = parseInitTarget(args);
        } catch (SecretFileException e) {
            err.println(e.getMessage());
            err.println(USAGE);
            return e.exitCode().value();
        }

        try {
            // Les cles sont tirees par le writer, apres qu'il a arbitre la concurrence : une
            // initialisation perdante ne doit pas avoir consomme d'entropie pour rien.
            SecretFileWriter.write(target,
                    () -> EnvFileContent.render(InstallKeys.generate(new SecureRandom())));
        } catch (SecretFileException e) {
            err.println(e.getMessage());
            return e.exitCode().value();
        }

        // Le chemin, jamais le contenu. Cette ligne finit dans les journaux d'un installateur.
        out.println("Wrote TAKIBO installation secrets to " + target.toAbsolutePath());
        return ExitCode.SUCCESS.value();
    }

    private static Path parseInitTarget(String[] args) {
        if (args.length == 0) {
            throw usage("missing command");
        }
        if (!COMMAND_INIT.equals(args[0])) {
            throw usage("unknown command: " + args[0]);
        }
        if (args.length == 1) {
            throw usage("missing required option " + OPTION_OUT);
        }
        if (!OPTION_OUT.equals(args[1])) {
            throw usage("unknown option: " + args[1]);
        }
        if (args.length == 2) {
            throw usage(OPTION_OUT + " requires a path");
        }
        if (args.length > 3) {
            // Refuser plutot qu'ignorer : un argument en trop signale souvent une commande mal
            // citee par un shell, donc un chemin qui n'est pas celui qu'on croit.
            throw usage("unexpected argument: " + args[3]);
        }
        try {
            return Path.of(args[2]);
        } catch (InvalidPathException e) {
            throw usage("invalid path for " + OPTION_OUT);
        }
    }

    private static SecretFileException usage(String problem) {
        return new SecretFileException(ExitCode.USAGE, problem);
    }
}
