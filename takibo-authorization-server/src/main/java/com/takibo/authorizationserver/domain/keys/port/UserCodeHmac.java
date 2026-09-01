package com.takibo.authorizationserver.domain.keys.port;

/**
 * HMAC d'installation pour {@code user_code} (TAS-GRANTS-02).
 * <p>
 * {@code user_code} est, par conception, une valeur de faible entropie — quelques caractères
 * qu'un humain saisit à l'écran d'approbation du flux device (RFC 8628), typiquement tirée
 * d'un alphabet restreint pour rester facile à recopier. Un SHA-256 non clé ({@code TokenHash})
 * sur une valeur de cet espace resterait énumérable hors ligne dès la seule fuite de la
 * colonne {@code user_code_hash} : il suffit de hacher chaque valeur possible et de comparer,
 * sans jamais toucher au chiffrement. Les cinq autres colonnes ({@code authorization_code},
 * {@code access_token}, {@code refresh_token}, {@code oidc_id_token}, {@code device_code})
 * portent une valeur à haute entropie, hors de portée d'une énumération hors ligne, et restent
 * sur ce SHA-256 non clé.
 * <p>
 * Une clé d'installation ferme cette attaque : sans elle, aucune énumération ni table
 * arc-en-ciel ne retrouve la valeur en clair à partir du seul hash. Cette clé est distincte de
 * celle de {@link SecretCipher} — mélanger les usages d'une même matière entre chiffrement et
 * authentification par hash est un anti-pattern cryptographique, pas une simplification.
 */
public interface UserCodeHmac {

    /** @return l'empreinte HMAC-SHA256 de {@code value}, en hexadécimal minuscule (64 caractères) */
    String hmacHex(String value);
}
