package com.takibo.installkeys;

/**
 * Le contrat de sortie : trois lignes {@code CLE=valeur}, et rien d'autre
 * (TAKIBO-INSTALL-KEYS-01).
 *
 * <h2>Pourquoi ce format, et pourquoi si nu</h2>
 * C'est le seul que {@code source}, {@code docker --env-file},
 * {@code oc create secret --from-env-file} et un coffre acceptent tous sans retouche. Chaque
 * ornement lui coûterait cette universalité :
 * <ul>
 *   <li><b>aucun commentaire</b> — un en-tête expliquant la provenance des valeurs rendrait le
 *       fichier plus lisible et moins importable ;</li>
 *   <li><b>aucun guillemet</b> — les uns les retirent, les autres les gardent dans la valeur,
 *       et la clé décodée ne serait alors plus de 32 octets ;</li>
 *   <li><b>fins de ligne LF</b>, y compris sous Windows — un {@code \r} final entrerait dans la
 *       valeur et casserait le décodage base64 côté serveur ;</li>
 *   <li><b>saut de ligne final</b> — une dernière ligne sans terminaison est ignorée par
 *       certains analyseurs, et c'est alors une clé qui disparaît en silence.</li>
 * </ul>
 * Le padding base64 introduit un {@code =} dans la valeur : sans effet, tous ces analyseurs
 * découpant sur le <b>premier</b> {@code =} de la ligne.
 */
final class EnvFileContent {

    static final String CIPHER_KEY_ID_VARIABLE = "TAKIBO_TAS_CIPHER_KEY_ID";
    static final String CIPHER_KEY_VARIABLE = "TAKIBO_TAS_CIPHER_KEY";
    static final String USER_CODE_HMAC_KEY_VARIABLE = "TAKIBO_TAS_USER_CODE_HMAC_KEY";

    private EnvFileContent() {
    }

    /**
     * Rend les trois lignes. L'ordre — identifiant, clé de chiffrement, clé HMAC — n'a aucune
     * portée technique ; il suit celui du contrat de sortie du récit, pour qu'un lecteur
     * retrouve dans le fichier ce que la documentation lui décrit.
     */
    static String render(InstallKeys keys) {
        return line(CIPHER_KEY_ID_VARIABLE, keys.cipherKeyId())
                + line(CIPHER_KEY_VARIABLE, keys.cipherKeyBase64())
                + line(USER_CODE_HMAC_KEY_VARIABLE, keys.userCodeHmacKeyBase64());
    }

    private static String line(String variable, String value) {
        // '\n' litteral, jamais System.lineSeparator() : le format ne depend pas de la
        // machine qui l'ecrit, mais de celles qui le liront.
        return variable + "=" + value + "\n";
    }
}
