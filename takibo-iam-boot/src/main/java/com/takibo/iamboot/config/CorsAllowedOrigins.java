package com.takibo.iamboot.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Origines croisées acceptées par l'installation, validées au démarrage (SEC-TMS-05).
 *
 * <p>Une origine croisée n'est acceptée que si l'installation l'a nommée. Hors du profil
 * {@code dev}, aucun joker, aucun motif, aucun reflet d'origine : une seule valeur invalide
 * empêche le démarrage. Une liste vide n'accepte aucune origine croisée.
 *
 * <p>Les règles de forme sont celles de {@code ClientCorsOrigin} côté TMS — HTTPS hors
 * loopback, ni chemin, ni requête, ni fragment, ni information d'utilisateur. Elles sont
 * reprises ici plutôt qu'importées : le contrat d'installation n'appartient pas au domaine
 * TMS, et {@code takibo-iam-boot} n'importe aucune classe de ce domaine.
 */
final class CorsAllowedOrigins {

    static final String PROPERTY = "takibo.cors.allowed-origins";

    private final List<String> exactOrigins;
    private final List<String> originPatterns;

    private CorsAllowedOrigins(Set<String> exactOrigins, List<String> originPatterns) {
        this.exactOrigins = List.copyOf(exactOrigins);
        this.originPatterns = List.copyOf(originPatterns);
    }

    /**
     * @param rawValues  valeurs de {@value #PROPERTY}, éventuellement nulles
     * @param devProfile vrai seulement sous le profil {@code dev}, seul autorisé à déclarer
     *                   un motif ou le joker
     * @throws IllegalStateException à la première valeur refusée, nommant la propriété et
     *                               la valeur
     */
    static CorsAllowedOrigins validate(List<String> rawValues, boolean devProfile) {
        Set<String> exact = new LinkedHashSet<>();
        List<String> patterns = new ArrayList<>();
        List<String> values = rawValues == null ? List.of() : rawValues;

        for (String raw : values) {
            String value = raw == null ? "" : raw.trim();
            if (value.contains("*")) {
                patterns.add(requirePatternAllowed(value, devProfile));
            } else if (!value.isEmpty()) {
                exact.add(normalizeExactOrigin(value));
            }
        }
        return new CorsAllowedOrigins(exact, patterns);
    }

    List<String> exactOrigins() {
        return exactOrigins;
    }

    List<String> originPatterns() {
        return originPatterns;
    }

    boolean isEmpty() {
        return exactOrigins.isEmpty() && originPatterns.isEmpty();
    }

    private static String requirePatternAllowed(String value, boolean devProfile) {
        if (!devProfile) {
            throw refused(value, "un joker ou un motif n'est accepté que sous le profil dev");
        }
        return value;
    }

    /**
     * Forme sérialisée d'une origine, celle qu'envoie un navigateur : schéma et hôte en
     * minuscules, port par défaut omis, sans slash final. La comparaison de Spring est exacte
     * après cette normalisation — aucune correspondance par préfixe ni par suffixe.
     */
    private static String normalizeExactOrigin(String value) {
        if ("null".equalsIgnoreCase(value)) {
            throw refused(value, "une origine opaque ne désigne aucun site");
        }
        URI uri = parse(value);
        String scheme = requireHttpScheme(value, uri);
        requireBareAuthority(value, uri);

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if ("http".equals(scheme) && !isLoopbackHost(host)) {
            throw refused(value, "HTTP n'est accepté que pour une adresse de loopback");
        }
        return scheme + "://" + host + portSuffix(scheme, uri.getPort());
    }

    private static URI parse(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException e) {
            throw refused(value, "ce n'est pas une origine valide");
        }
    }

    private static String requireHttpScheme(String value, URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            throw refused(value, "seuls les schémas https et http sont acceptés");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw refused(value, "l'hôte est obligatoire");
        }
        return scheme;
    }

    private static void requireBareAuthority(String value, URI uri) {
        if (uri.getRawUserInfo() != null) {
            throw refused(value, "une origine ne porte pas d'information d'utilisateur");
        }
        String path = uri.getRawPath();
        if (path != null && !path.isEmpty() && !"/".equals(path)) {
            throw refused(value, "une origine ne porte pas de chemin");
        }
        if (uri.getRawQuery() != null) {
            throw refused(value, "une origine ne porte pas de requête");
        }
        if (uri.getRawFragment() != null) {
            throw refused(value, "une origine ne porte pas de fragment");
        }
    }

    private static String portSuffix(String scheme, int port) {
        boolean defaultPort = ("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80);
        return port == -1 || defaultPort ? "" : ":" + port;
    }

    private static boolean isLoopbackHost(String host) {
        if ("localhost".equals(host) || "[::1]".equals(host)) {
            return true;
        }
        String[] octets = host.split("\\.", -1);
        if (octets.length != 4 || !"127".equals(octets[0])) {
            return false;
        }
        for (int index = 1; index < octets.length; index++) {
            if (!isIpv4Octet(octets[index])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIpv4Octet(String value) {
        return !value.isEmpty() && value.length() <= 3
                && value.chars().allMatch(Character::isDigit)
                && Integer.parseInt(value) <= 255;
    }

    private static IllegalStateException refused(String value, String reason) {
        return new IllegalStateException(
                PROPERTY + " : valeur refusée « " + value + " » — " + reason + ".");
    }
}
