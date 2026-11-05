package com.takibo.authorizationserver.infrastructure.jwks;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Classe une adresse effectivement résolue. Les fonctions du JDK ({@code isLoopbackAddress},
 * {@code isSiteLocalAddress}) ne suffisent pas : elles ignorent {@code ::127.0.0.1},
 * {@code 64:ff9b::7f00:1} ou {@code 2002:7f00:1::}, et ne connaissent pas {@code fc00::/7}.
 * Les plages sont donc explicites, et l'IPv4 portée par une adresse IPv6 est classée selon
 * les règles IPv4 ; la classe la plus restrictive l'emporte.
 */
// java:S1313 vise les adresses de destination codées en dur. Ici, les littéraux sont la table
// des plages que Takibo refuse de joindre : c'est la protection elle-même, pas une destination.
@SuppressWarnings("java:S1313")
final class InetAddressClassifier {

    enum AddressClass {
        /** Jamais joignable, aucune liste de confiance ne lève ce refus. */
        ALWAYS_FORBIDDEN,
        /** Refusée par défaut ; joignable seulement par exception déclarée. */
        PRIVATE,
        PUBLIC
    }

    private static final List<Cidr> FORBIDDEN_V4 = cidrs(
            "0.0.0.0/8", "169.254.0.0/16", "192.0.0.0/24", "192.0.2.0/24", "198.51.100.0/24",
            "203.0.113.0/24", "224.0.0.0/4", "240.0.0.0/4");
    private static final List<Cidr> PRIVATE_V4 = cidrs(
            "127.0.0.0/8", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "100.64.0.0/10",
            "198.18.0.0/15");
    private static final List<Cidr> FORBIDDEN_V6 = cidrs(
            "::/128", "fe80::/10", "fec0::/10", "ff00::/8", "2001::/32", "2001:db8::/32",
            "64:ff9b:1::/48", "fd00:ec2::254/128");
    private static final Cidr LOOPBACK_V6 = Cidr.parse("::1/128");
    private static final List<Cidr> PRIVATE_V6 = cidrs("fc00::/7");

    /**
     * Préfixes IPv6 qui transportent une IPv4 dans leurs 32 derniers bits. {@code ::ffff:0:0/96}
     * n'y figure pas : le JDK convertit toujours une adresse IPv4-mappée en {@code Inet4Address}.
     */
    private static final List<Cidr> EMBEDDED_V4_LOW_BITS = cidrs(
            "::/96", "::ffff:0:0:0/96", "64:ff9b::/96");
    private static final Cidr SIX_TO_FOUR = Cidr.parse("2002::/16");

    private InetAddressClassifier() {
    }

    static AddressClass classify(InetAddress address) {
        if (address instanceof Inet6Address) {
            return classifyV6(address);
        }
        return classifyV4(address);
    }

    /** Toutes les plages toujours interdites, pour refuser une exception qui les chevauche. */
    static List<Cidr> alwaysForbiddenRanges() {
        return concat(FORBIDDEN_V4, FORBIDDEN_V6);
    }

    private static AddressClass classifyV4(InetAddress address) {
        if (FORBIDDEN_V4.stream().anyMatch(cidr -> cidr.contains(address))) {
            return AddressClass.ALWAYS_FORBIDDEN;
        }
        if (PRIVATE_V4.stream().anyMatch(cidr -> cidr.contains(address))) {
            return AddressClass.PRIVATE;
        }
        return AddressClass.PUBLIC;
    }

    private static AddressClass classifyV6(InetAddress address) {
        if (FORBIDDEN_V6.stream().anyMatch(cidr -> cidr.contains(address))) {
            return AddressClass.ALWAYS_FORBIDDEN;
        }
        // ::1 appartient aussi à ::/96 : traité avant, sans quoi il serait lu comme 0.0.0.1.
        if (LOOPBACK_V6.contains(address)) {
            return AddressClass.PRIVATE;
        }
        AddressClass own = PRIVATE_V6.stream().anyMatch(cidr -> cidr.contains(address))
                ? AddressClass.PRIVATE
                : AddressClass.PUBLIC;
        byte[] bytes = address.getAddress();
        if (EMBEDDED_V4_LOW_BITS.stream().anyMatch(cidr -> cidr.contains(address))) {
            return mostRestrictive(own, classifyV4(v4(Arrays.copyOfRange(bytes, 12, 16))));
        }
        if (SIX_TO_FOUR.contains(address)) {
            return mostRestrictive(own, classifyV4(v4(Arrays.copyOfRange(bytes, 2, 6))));
        }
        return own;
    }

    private static AddressClass mostRestrictive(AddressClass a, AddressClass b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }

    private static InetAddress v4(byte[] bytes) {
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static List<Cidr> cidrs(String... values) {
        return Arrays.stream(values).map(Cidr::parse).toList();
    }

    private static List<Cidr> concat(List<Cidr> a, List<Cidr> b) {
        return Stream.concat(a.stream(), b.stream()).toList();
    }
}
