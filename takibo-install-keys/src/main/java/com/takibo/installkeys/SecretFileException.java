package com.takibo.installkeys;

/**
 * Un échec d'écriture, porteur du code de sortie qui le distingue
 * (TAKIBO-INSTALL-KEYS-01).
 * <p>
 * Le code voyage avec l'échec plutôt que d'être décidé par l'appelant : un script
 * d'installation doit pouvoir distinguer « le fichier existe déjà » — situation normale d'une
 * seconde exécution — d'un système de fichiers incapable de protéger un secret. Traduire une
 * cause en code au moment de l'affichage reviendrait à la deviner.
 * <p>
 * Le message ne contient jamais de secret : il nomme un chemin et une cause, rien d'autre.
 */
class SecretFileException extends RuntimeException {

    private final transient ExitCode exitCode;

    SecretFileException(ExitCode exitCode, String message) {
        this(exitCode, message, null);
    }

    SecretFileException(ExitCode exitCode, String message, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
    }

    ExitCode exitCode() {
        return exitCode;
    }
}
