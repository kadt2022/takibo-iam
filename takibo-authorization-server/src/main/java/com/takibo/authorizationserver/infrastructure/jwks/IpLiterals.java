package com.takibo.authorizationserver.infrastructure.jwks;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Lecture des adresses IP littérales sous leur seule forme canonique, sans jamais interroger
 * le DNS.
 * <p>
 * Les autres écritures numériques ({@code 2130706433}, {@code 127.1}, {@code 0177.0.0.1},
 * {@code 0x7f000001}) sont interprétées différemment selon les bibliothèques : Java lit
 * {@code 0177.0.0.1} comme {@code 177.0.0.1}, d'autres outils comme {@code 127.0.0.1}. Elles
 * sont reconnues comme « numériques non canoniques » pour être refusées, jamais devinées.
 */
final class IpLiterals {

    private static final Pattern CANONICAL_IPV4 =
            Pattern.compile("(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}");
    private static final Pattern IPV6_CHARACTERS = Pattern.compile("[0-9A-Fa-f:.]+");
    private static final Pattern NUMERIC_LOOKING = Pattern.compile("(0[xX][0-9A-Fa-f]*|\\d+)(\\.(0[xX][0-9A-Fa-f]*|\\d+))*\\.?");

    private IpLiterals() {
    }

    /** L'adresse si {@code host} est une IPv4 canonique ou une IPv6 (avec ou sans crochets). */
    static Optional<InetAddress> parse(String host) {
        if (host == null || host.isEmpty()) {
            return Optional.empty();
        }
        String candidate = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1)
                : host;
        boolean ipv4 = CANONICAL_IPV4.matcher(candidate).matches();
        boolean ipv6 = candidate.indexOf(':') >= 0 && IPV6_CHARACTERS.matcher(candidate).matches();
        if (!ipv4 && !ipv6) {
            return Optional.empty();
        }
        try {
            // Un littéral validé ci-dessus n'est jamais résolu par le DNS.
            return Optional.of(InetAddress.getByName(candidate));
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    }

    /** Vrai pour toute écriture numérique qui n'est pas une IPv4 canonique. */
    static boolean isNonCanonicalNumeric(String host) {
        return NUMERIC_LOOKING.matcher(host).matches() && !CANONICAL_IPV4.matcher(host).matches();
    }
}
