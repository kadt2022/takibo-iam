package com.takibo.authorizationserver.infrastructure.jwks;

import org.apache.hc.client5.http.DnsResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * Résolution DNS filtrée : HttpClient se connecte exclusivement aux adresses que renvoie ce
 * résolveur ({@code DefaultHttpClientConnectionOperator}). Une résolution a lieu par tentative
 * de connexion, ses adresses sont jugées par la politique, et ce sont ces mêmes adresses que
 * la socket utilise : aucune seconde résolution ne peut substituer une adresse interne
 * (DNS rebinding). Le nom demandé reste celui du SNI et de la vérification du certificat.
 */
final class PolicyDnsResolver implements DnsResolver {

    /** Résolution système, isolée pour que les tests simulent les réponses DNS. */
    @FunctionalInterface
    interface Lookup {
        InetAddress[] lookup(String host) throws UnknownHostException;
    }

    private static final Logger log = LoggerFactory.getLogger(PolicyDnsResolver.class);

    private final OutboundDestinationPolicy policy;
    private final Lookup lookup;

    PolicyDnsResolver(OutboundDestinationPolicy policy, Lookup lookup) {
        this.policy = policy;
        this.lookup = lookup;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] resolved = lookup.lookup(host);
        try {
            return policy.filterResolved(host, resolved);
        } catch (DestinationRefusedException refused) {
            log.warn("JWKS : résolution de {} refusée : {}", host, refused.getMessage());
            UnknownHostException failure = new UnknownHostException(refused.getMessage());
            failure.initCause(refused);
            throw failure;
        }
    }

    /**
     * Redéfini pour qu'aucune adresse « non résolue » ne puisse jamais être rendue : la
     * méthode par défaut le ferait si {@link #resolve(String)} renvoyait {@code null}.
     */
    @Override
    public List<InetSocketAddress> resolve(String host, int port) throws UnknownHostException {
        return Arrays.stream(resolve(host)).map(address -> new InetSocketAddress(address, port)).toList();
    }

    @Override
    public String resolveCanonicalHostname(String host) {
        // Utilisé seulement par l'authentification SPNEGO, absente ici : aucune requête DNS.
        return host;
    }
}
