package com.takibo.securitymanagement.infrastructure.security;

import com.takibo.securitycontext.validation.TakiboSecurityContextValidators;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Proxys de confiance ({@code takibo.security.trusted-proxies}) : seules les connexions venant de
 * ces adresses peuvent faire valoir X-Forwarded-For. Liste vide par défaut, donc l'en-tête, fourni
 * par le client, n'est jamais cru. Seules des adresses IP littérales sont admises : aucune
 * résolution DNS, et une valeur invalide empêche le démarrage plutôt que d'être ignorée.
 */
@Component
public class TrustedProxies {

    private static final TrustedProxies NONE = new TrustedProxies(List.of());

    private final Set<String> addresses;

    public TrustedProxies(@Value("${takibo.security.trusted-proxies:}") List<String> addresses) {
        this.addresses = addresses.stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(TrustedProxies::requireCanonical)
                .collect(Collectors.toUnmodifiableSet());
    }

    static TrustedProxies none() {
        return NONE;
    }

    boolean contains(String address) {
        String canonical = canonical(address);
        return canonical != null && addresses.contains(canonical);
    }

    private static String requireCanonical(String address) {
        String canonical = canonical(address);
        if (canonical == null) {
            throw new IllegalStateException("takibo.security.trusted-proxies accepts only IP addresses: " + address);
        }
        return canonical;
    }

    /** Forme canonique d'une IP littérale (IPv6 compressée ou non), ou null si ce n'en est pas une. */
    static String canonical(String address) {
        String literal = address == null ? null : address.trim();
        if (!TakiboSecurityContextValidators.isIpLiteral(literal)) {
            return null;
        }
        if (literal.startsWith("[") && literal.endsWith("]")) {
            literal = literal.substring(1, literal.length() - 1);
        }
        try {
            // Sans risque de DNS : la valeur est une IP littérale, déjà vérifiée ci-dessus.
            return InetAddress.getByName(literal).getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
