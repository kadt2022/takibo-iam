package com.takibo.authorizationserver.infrastructure.jwks;

import com.takibo.authorizationserver.infrastructure.jwks.InetAddressClassifier.AddressClass;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Destinations que Takibo accepte de joindre pour récupérer un JWK Set.
 * <p>
 * Sécurité par défaut, exceptions maîtrisées :
 * <ul>
 *   <li>HTTPS seulement ; HTTP uniquement vers un nom de confiance, si l'installation l'a
 *       explicitement permis (profil dev, vérifié au démarrage).</li>
 *   <li>Adresses privées et boucle locale refusées, sauf pour un nom de confiance ou une
 *       plage de confiance.</li>
 *   <li>Adresses toujours interdites refusées sans exception, même pour un nom de confiance.</li>
 * </ul>
 * {@link #checkUri(URI)} juge l'URI sans DNS ; {@link #filterResolved(String, InetAddress[])}
 * juge les adresses que la connexion utilisera réellement. Le second fait foi.
 */
public final class OutboundDestinationPolicy {

    private static final String HTTPS = "https";
    private static final String HTTP = "http";

    private final Set<String> trustedHosts;
    private final List<Cidr> trustedCidrs;
    private final boolean httpAllowedForTrustedHosts;

    public OutboundDestinationPolicy(List<String> trustedHosts, List<String> trustedCidrs,
                                     boolean httpAllowedForTrustedHosts) {
        this.trustedHosts = trustedHosts.stream()
                .map(OutboundDestinationPolicy::requireTrustedHostName)
                .collect(Collectors.toUnmodifiableSet());
        this.trustedCidrs = trustedCidrs.stream()
                .map(OutboundDestinationPolicy::requireTrustableCidr)
                .toList();
        this.httpAllowedForTrustedHosts = httpAllowedForTrustedHosts;
    }

    /** Juge l'URI telle qu'écrite, sans résolution DNS. */
    public void checkUri(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new DestinationRefusedException("The JWK Set URL must name a host");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new DestinationRefusedException("The JWK Set URL must not include user-info or a fragment");
        }
        if (HTTP.equals(scheme)) {
            if (!(httpAllowedForTrustedHosts && trustedHosts.contains(normalizedHost))) {
                throw new DestinationRefusedException("The JWK Set URL must use HTTPS");
            }
        } else if (!HTTPS.equals(scheme)) {
            throw new DestinationRefusedException("The JWK Set URL must use HTTPS");
        }
        if (IpLiterals.isNonCanonicalNumeric(normalizedHost)) {
            throw new DestinationRefusedException("The JWK Set URL host is a non-canonical IP address form");
        }
        IpLiterals.parse(normalizedHost).ifPresentOrElse(
                address -> requireAllowed(normalizedHost, address),
                () -> requireAllowedName(normalizedHost));
    }

    /**
     * Les adresses que la connexion utilisera. Une seule adresse refusée, et toute la réponse
     * l'est : une réponse DNS mixte ne laisse jamais passer sa partie interne.
     */
    public InetAddress[] filterResolved(String host, InetAddress[] resolved) {
        if (resolved == null || resolved.length == 0) {
            throw new DestinationRefusedException("The JWK Set URL host did not resolve");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        for (InetAddress address : resolved) {
            requireAllowed(normalizedHost, address);
        }
        return resolved.clone();
    }

    private void requireAllowedName(String host) {
        // RFC 6761 : localhost et ses sous-domaines désignent toujours la boucle locale.
        boolean loopbackName = "localhost".equals(host) || host.endsWith(".localhost");
        if (loopbackName && !trustedHosts.contains(host)) {
            throw new DestinationRefusedException("The JWK Set URL targets the loopback interface");
        }
    }

    private void requireAllowed(String host, InetAddress address) {
        AddressClass addressClass = InetAddressClassifier.classify(address);
        if (addressClass == AddressClass.ALWAYS_FORBIDDEN) {
            throw new DestinationRefusedException(
                    "The JWK Set URL resolves to a forbidden address: " + address.getHostAddress());
        }
        if (addressClass == AddressClass.PRIVATE
                && !trustedHosts.contains(host)
                && trustedCidrs.stream().noneMatch(cidr -> cidr.contains(address))) {
            throw new DestinationRefusedException(
                    "The JWK Set URL resolves to an internal address: " + address.getHostAddress());
        }
    }

    /** Plages de confiance plus larges que /24 (IPv4) ou /64 (IPv6), à signaler au démarrage. */
    public List<Cidr> broadTrustedCidrs() {
        return trustedCidrs.stream()
                .filter(cidr -> cidr.prefixLength() < (cidr.isIpv4() ? 24 : 64))
                .toList();
    }

    public boolean httpAllowedForTrustedHosts() {
        return httpAllowedForTrustedHosts;
    }

    private static String requireTrustedHostName(String raw) {
        String host = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (host.isEmpty() || host.contains("*") || host.endsWith(".") || host.contains("/")
                || host.contains(":") || IpLiterals.parse(host).isPresent()
                || IpLiterals.isNonCanonicalNumeric(host)) {
            throw new IllegalArgumentException(
                    "trusted-hosts accepts exact host names only (no wildcard, no IP, no trailing dot): " + raw);
        }
        return host;
    }

    private static Cidr requireTrustableCidr(String raw) {
        Cidr cidr = Cidr.parse(raw);
        if (InetAddressClassifier.alwaysForbiddenRanges().stream().anyMatch(cidr::overlaps)) {
            throw new IllegalArgumentException(
                    "trusted-cidrs must not overlap an always-forbidden range: " + raw);
        }
        return cidr;
    }
}
