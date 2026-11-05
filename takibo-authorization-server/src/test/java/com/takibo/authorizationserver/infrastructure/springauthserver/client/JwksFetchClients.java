package com.takibo.authorizationserver.infrastructure.springauthserver.client;

import com.takibo.authorizationserver.infrastructure.jwks.JwksFetchClient;
import com.takibo.authorizationserver.infrastructure.jwks.OutboundDestinationPolicy;
import org.apache.hc.core5.ssl.SSLContexts;

import java.time.Duration;
import java.util.List;

/** Client JWKS de test, configuré exactement comme les valeurs par défaut de production. */
public final class JwksFetchClients {

    private JwksFetchClients() {
    }

    public static JwksFetchClient defaults() {
        return new JwksFetchClient(
                new OutboundDestinationPolicy(List.of(), List.of(), false),
                new JwksFetchClient.Limits(Duration.ofSeconds(2), Duration.ofSeconds(3), Duration.ofSeconds(5), 50 * 1024),
                SSLContexts.createSystemDefault());
    }
}
