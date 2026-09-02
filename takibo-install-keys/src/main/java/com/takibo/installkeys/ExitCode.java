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
    IO_FAILURE(5),

    /**
     * Un fichier {@code .pending} est là : une initialisation précédente a été interrompue,
     * ou une autre est en cours. Rien n'a été régénéré et rien n'a été effacé — ce fichier
     * peut porter des clés déjà utilisées ailleurs, et lui seul peut les rendre.
     */
    INTERRUPTED_INSTALLATION(6),

    /**
     * Le tirage des clés a échoué — fournisseur cryptographique indisponible, par exemple.
     * Rien n'a été écrit, et le fichier de travail a été retiré : relancer la commande suffit.
     * <p>
     * Distinct de {@link #INTERRUPTED_INSTALLATION} pour une raison qui compte : ce dernier dit
     * « des clés existent peut-être déjà, ne régénérez pas », alors qu'ici aucune clé n'a
     * jamais existé. Les confondre enverrait l'opérateur chercher une sauvegarde qui n'a pas
     * lieu d'être.
     */
    GENERATION_FAILURE(7);

    private final int value;

    ExitCode(int value) {
        this.value = value;
    }

    int value() {
        return value;
    }
}
