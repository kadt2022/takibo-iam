package com.takibo.authorizationserver.infrastructure.keys;

import com.takibo.authorizationserver.domain.keys.port.UserCodeHmac;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * Implémentation HMAC-SHA256 de {@link UserCodeHmac}.
 * <p>
 * Aucun envelope ni identifiant de clé, contrairement à {@link AesGcmSecretCipher} : un
 * {@code user_code} expire en quelques minutes (RFC 8628), bien avant qu'une rotation de clé
 * n'ait à distinguer quelle génération l'a haché. Ajouter cette machinerie pour une valeur
 * aussi éphémère protégerait contre un risque qui n'existe pas dans sa fenêtre de vie.
 */
public class HmacSha256UserCodeHmac implements UserCodeHmac {

    private static final String ALGORITHM = "HmacSHA256";

    static final int REQUIRED_KEY_LENGTH_BYTES = 32;

    private final SecretKeySpec key;

    /**
     * @param keyMaterial exactement 32 octets, comme {@link SecretCipherKey}. {@link SecretKeySpec}
     *                    accepterait sans broncher n'importe quelle longueur non nulle — HMAC
     *                    est defini pour toute taille de cle — mais une cle plus courte que la
     *                    sortie de SHA-256 reduit d'autant la resistance recherchee, et une cle
     *                    plus longue n'ajoute rien : HMAC-SHA256 la complete par des zeros
     *                    jusqu'au bloc de 64 octets (et ne la hache qu'au-dela de ce bloc).
     *                    N'admettre qu'une taille, refusee ici, est le seul endroit ou l'ecart
     *                    de configuration se voit.
     */
    public HmacSha256UserCodeHmac(byte[] keyMaterial) {
        if (keyMaterial == null || keyMaterial.length != REQUIRED_KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException(
                    "USER_CODE_HMAC_KEY_MUST_BE_" + REQUIRED_KEY_LENGTH_BYTES + "_BYTES");
        }
        this.key = new SecretKeySpec(keyMaterial, ALGORITHM);
    }

    @Override
    public String hmacHex(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            // La cle est validee a la construction (SecretKeySpec) ; un echec ici denoterait
            // un environnement d'execution casse (algorithme HMAC-SHA256 absent), jamais une
            // entree utilisateur invalide.
            throw new IllegalStateException("HMAC_SHA256_UNAVAILABLE", e);
        }
    }
}
