package com.takibo.authorizationserver.domain.keys.port;

/**
 * Chiffrement reversible des secrets au repos.
 * <p>
 * Port unique du lot pour tout ce que TAS doit pouvoir relire : la matiere privee des cles de
 * signature (TAS-GRANTS-02A) et, ensuite, les valeurs de codes et de tokens (TAS-GRANTS-02).
 * Un seul mecanisme, defini ici une fois, plutot que deux constructions paralleles qui
 * divergeraient.
 * <p>
 * Volontairement distinct d'un hachage. Un hachage protege une valeur qu'on ne relira jamais ;
 * ce port protege des valeurs qu'il faut restituer telles quelles — une cle privee doit
 * signer, un code d'autorisation doit etre rejoue par Spring Authorization Server. Confondre
 * les deux casse le flux, comme le stockage hash-only initialement prevu pour les device codes
 * l'aurait fait.
 * <p>
 * <b>Le {@link SecretContext} n'est pas optionnel.</b> Il lie le chiffre a sa place : sans lui,
 * un chiffre valide le reste partout, et pourrait etre recopie d'un enregistrement a un autre
 * ou d'un usage a un autre. Le meme contexte doit etre fourni au chiffrement et au
 * dechiffrement ; toute divergence fait echouer la lecture.
 * <p>
 * Contrat attendu de toute implementation :
 * <ul>
 *   <li><b>authentifie</b> — une alteration du chiffre ou de son enveloppe doit etre detectee
 *       au dechiffrement, jamais silencieusement toleree ;</li>
 *   <li><b>lie au contexte</b> — un chiffre deplace cesse de se dechiffrer ;</li>
 *   <li><b>non deterministe</b> — deux chiffrements du meme clair produisent des sorties
 *       differentes, sans quoi l'egalite des chiffres trahirait l'egalite des clairs ;</li>
 *   <li><b>autoportant</b> — la sortie contient ce qu'il faut pour etre dechiffree, hors la
 *       cle et le contexte, tous deux fournis par l'appelant.</li>
 * </ul>
 * L'implementation est remplacable par un KMS ou un HSM sans que le domaine change.
 */
public interface SecretCipher {

    /** @return le chiffre, encode en texte, stockable tel quel en base */
    String encrypt(SecretContext context, String plaintext);

    /**
     * @param context exactement celui fourni au chiffrement, reconstruit par l'appelant
     * @return le clair d'origine
     * @throws SecretDecryptionException si le chiffre est illisible, altere, chiffre avec une
     *                                   autre cle, ou scelle pour un autre contexte
     */
    String decrypt(SecretContext context, String ciphertext);
}
