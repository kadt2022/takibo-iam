package com.takibo.installkeys;

import com.takibo.authorizationserver.domain.keys.port.SecretContext;
import com.takibo.authorizationserver.infrastructure.keys.AesGcmSecretCipher;
import com.takibo.authorizationserver.infrastructure.keys.HmacSha256UserCodeHmac;
import com.takibo.authorizationserver.infrastructure.keys.SecretCipherKey;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La preuve d'interopérabilité : ce que la CLI produit est accepté par le serveur qui le
 * consommera (TAKIBO-INSTALL-KEYS-01).
 * <p>
 * Le critère du récit est « satisfaire les invariants de {@code SecretCipherKey} et de
 * {@code HmacSha256UserCodeHmac} <b>sans aucune modification de ces types</b> ». La CLI ne les
 * importe donc pas en production — elle ne dépend de rien — et c'est ici, en dépendance de
 * test, que les deux mondes se rencontrent. Le jour où l'un de ces types durcit sa règle, ce
 * module casse, à cet endroit précis, avant qu'une installation ne produise des valeurs que le
 * serveur refuserait au démarrage.
 * <p>
 * Aucun démarrage de TAS, aucune base : seules les classes qui portent les invariants sont
 * sollicitées.
 */
class InstallKeysContractTest {

    private final InstallKeys keys = InstallKeys.generate(new SecureRandom());

    @Test
    void given_generated_values_then_the_server_cipher_key_accepts_them() {
        SecretCipherKey key = new SecretCipherKey(
                keys.cipherKeyId(), Base64.getDecoder().decode(keys.cipherKeyBase64()));

        assertThat(key.id()).isEqualTo(keys.cipherKeyId());
    }

    @Test
    void given_a_generated_cipher_key_then_it_seals_and_unseals_a_secret() {
        // Au-dela des invariants de construction : la matiere produite chiffre et dechiffre
        // reellement, ce qu'une cle de longueur valide mais mal encodee ne ferait pas.
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(new SecretCipherKey(
                keys.cipherKeyId(), Base64.getDecoder().decode(keys.cipherKeyBase64())));
        SecretContext context = SecretContext.signingKeyMaterial("kid-probe");

        assertThat(cipher.decrypt(context, cipher.encrypt(context, "probe"))).isEqualTo("probe");
    }

    @Test
    void given_a_generated_hmac_key_then_the_server_hmac_accepts_it_and_hashes() {
        HmacSha256UserCodeHmac hmac = new HmacSha256UserCodeHmac(
                Base64.getDecoder().decode(keys.userCodeHmacKeyBase64()));

        assertThat(hmac.hmacHex("WDJB-MJHT")).hasSize(64);
    }

    @Test
    void given_the_generated_pair_then_the_two_keys_are_never_interchangeable() {
        // La separation de roles, vue depuis les consommateurs : deux cles distinctes
        // produisent deux HMAC distincts du meme code.
        HmacSha256UserCodeHmac fromHmacKey = new HmacSha256UserCodeHmac(
                Base64.getDecoder().decode(keys.userCodeHmacKeyBase64()));
        HmacSha256UserCodeHmac fromCipherKey = new HmacSha256UserCodeHmac(
                Base64.getDecoder().decode(keys.cipherKeyBase64()));

        assertThat(fromHmacKey.hmacHex("WDJB-MJHT"))
                .isNotEqualTo(fromCipherKey.hmacHex("WDJB-MJHT"));
    }
}
