package com.takibo.installkeys;

/**
 * Les issues de la CLI, distinctes parce qu'elles appellent des conduites différentes
 * (TAKIBO-INSTALL-KEYS-01).
 * <p>
 * Un script d'installation doit pouvoir agir sur le code seul, sans lire un message :
 * {@link #TARGET_EXISTS} est la seconde exécution d'un installateur idempotent — souvent
 * bénigne — tandis que {@link #UNSAFE_FILESYSTEM} dit qu'aucune installation n'est possible
 * ici tant que rien ne change. Les confondre sous un unique code 1 obligerait à analyser du
 * texte pour les séparer.
 */
enum ExitCode {

    /** Les trois valeurs ont été écrites. */
    SUCCESS(0),

    /** Commande inconnue, argument manquant ou inattendu. Rien n'a été tenté. */
    USAGE(2),

    /**
     * Le fichier de sortie existe déjà. Rien n'a été écrit, rien n'a été remplacé — voir la
     * décision du récit : la conduite est de restaurer la sauvegarde, jamais de régénérer.
     */
    TARGET_EXISTS(3),

    /**
     * Le système de fichiers ne peut garantir ni la restriction des permissions, ni une
     * publication atomique. Refus <b>avant</b> qu'un secret n'existe sur le disque.
     */
    UNSAFE_FILESYSTEM(4),

    /** Échec d'entrée-sortie : répertoire absent, disque plein, droits insuffisants. */
    IO_FAILURE(5);

    private final int value;

    ExitCode(int value) {
        this.value = value;
    }

    int value() {
        return value;
    }
}
