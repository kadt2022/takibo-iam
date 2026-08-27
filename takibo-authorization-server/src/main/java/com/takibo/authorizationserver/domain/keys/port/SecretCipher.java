package com.takibo.authorizationserver.domain.keys.port;

/**
 * Chiffrement reversible des secrets au repos.
 * <p>
 * Port unique du lot pour tout ce que TAS doit pouvoir relire : la matiere privee des cles
 * de signature (TAS-GRANTS-02A) et, ensuite, les valeurs de codes et de tokens
 * (TAS-GRANTS-02). Un seul mecanisme, defini ici une fois, plutot que deux constructions
 * paralleles qui divergeraient.
 * <p>
 * Volontairement distinct d'un hachage. Un hachage protege une valeur qu'on ne relira jamais
 * ; ce port protege des valeurs qu'il faut restituer telles quelles — une cle privee doit
 * signer, un code d'autorisation doit etre compare puis rejoue par Spring Authorization
 * Server. Confondre les deux casse le flux, comme le stockage hash-only initialement prevu
 * pour les device codes l'aurait fait.
 * <p>
 * Contrat attendu de toute implementation :
 * <ul>
 *   <li><b>authentifie</b> — une alteration du chiffre doit etre detectee au dechiffrement,
 *       jamais silencieusement toleree ;</li>
 *   <li><b>non deterministe</b> — deux chiffrements du meme clair produisent des sorties
 *       differentes, sans quoi l'egalite des chiffres trahirait l'egalite des clairs ;</li>
 *   <li><b>autoportant</b> — la sortie contient ce qu'il faut pour etre dechiffree, hors la
 *       cle elle-meme.</li>
 * </ul>
 * L'implementation est remplacable par un KMS ou un HSM sans que le domaine change.
 */
public interface SecretCipher {

    /** @return le chiffre, encode en texte, stockable tel quel en base */
    String encrypt(String plaintext);

    /**
     * @return le clair d'origine
     * @throws SecretDecryptionException si le chiffre est illisible, altere, ou chiffre
     *                                   avec une autre cle
     */
    String decrypt(String ciphertext);
}
