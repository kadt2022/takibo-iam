package com.takibo.iamboot.oauth;

import com.takibo.authorizationserver.infrastructure.jwks.DestinationRefusedException;
import com.takibo.authorizationserver.infrastructure.jwks.OutboundDestinationPolicy;
import com.takibo.managementservice.application.port.JwksUriAdmissionPort;
import com.takibo.managementservice.domain.exception.InvalidClientConfigurationException;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Branche l'enregistrement d'un client (management-service) sur la politique de destination
 * qui gouverne la récupération des clés (authorization-server) : une seule table de plages,
 * une seule règle de transport, aucune dépendance entre les deux modules.
 */
@Component
public class JwksUriAdmissionAdapter implements JwksUriAdmissionPort {

    private final OutboundDestinationPolicy policy;

    public JwksUriAdmissionAdapter(OutboundDestinationPolicy policy) {
        this.policy = policy;
    }

    @Override
    public void requireAllowed(URI jwksUri) {
        try {
            policy.checkUri(jwksUri);
        } catch (DestinationRefusedException refused) {
            throw new InvalidClientConfigurationException("jwksUri is not an allowed destination: " + refused.getMessage());
        }
    }
}
