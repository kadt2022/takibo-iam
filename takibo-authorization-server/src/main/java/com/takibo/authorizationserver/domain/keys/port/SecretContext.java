package com.takibo.authorizationserver.domain.keys.port;

import java.util.regex.Pattern;

/**
 * Ou vit un secret, et ce qu'il est. Lie le chiffre a sa place.
 * <p>
 * Sans ce contexte, un chiffre valide reste valide <b>partout</b>. Un attaquant capable
 * d'ecrire en base pourrait recopier la matiere privee chiffree d'une cle dans la ligne d'une
 * autre, ou glisser un refresh token chiffre dans la colonne d'une cle de signature : le
 * dechiffrement reussirait, puisque le chiffre est authentique — simplement pas pour cet
 * emplacement. AES-GCM authentifie ce qu'il scelle, pas l'endroit ou on le range.
 * <p>
 * Le contexte comble ce trou : il est lie au chiffre comme donnee authentifiee additionnelle
 * (AAD). Un chiffre deplace d'un enregistrement a un autre, ou d'un usage a un autre, cesse
 * de se dechiffrer.
 * <p>
 * Le contexte n'est pas un secret. Il n'est pas stocke avec le chiffre : l'appelant le
 * reconstruit a la lecture a partir de ce qu'il sait deja — la ligne qu'il vient de charger.
 * C'est precisement ce qui fait la garantie : si sa reconstruction differe, la lecture echoue.
 *
 * @param type     nature du secret, stable dans le temps. Un renommage rendrait illisible
 *                 tout ce qui a ete scelle sous l'ancien nom.
 * @param recordId identifiant de l'enregistrement qui porte le secret
 */
public record SecretContext(String type, String recordId) {

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");

    public SecretContext {
        // Le contexte est serialise dans l'AAD, champs separes par '$'. Aucun champ ne peut
        // contenir le separateur, sans quoi deux contextes distincts pourraient produire la
        // meme AAD et la garantie tomberait.
        if (type == null || !SAFE.matcher(type).matches()) {
            throw new IllegalArgumentException("SECRET_CONTEXT_TYPE_INVALID");
        }
        if (recordId == null || !SAFE.matcher(recordId).matches()) {
            throw new IllegalArgumentException("SECRET_CONTEXT_RECORD_ID_INVALID");
        }
    }

    /** Matiere privee d'une cle de signature, identifiee par son {@code kid}. */
    public static SecretContext signingKeyMaterial(String kid) {
        return new SecretContext("tas_signing_keys.private_key_encrypted", kid);
    }
}
