package com.takibo.authorizationserver.infrastructure.jwks;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;

/**
 * Seul client HTTP par lequel Takibo récupère un JWK Set distant.
 * <ul>
 *   <li>Connexion uniquement aux adresses validées par {@link PolicyDnsResolver}.</li>
 *   <li>TLS du système, vérification du certificat et du nom intactes.</li>
 *   <li>Aucun proxy : un proxy résoudrait le DNS lui-même et rendrait le contrôle inopérant.</li>
 *   <li>Aucune redirection, aucune réutilisation de connexion, aucune nouvelle tentative.</li>
 *   <li>Réponse non 2xx refusée ; taille et durée bornées.</li>
 * </ul>
 */
public final class JwksFetchClient {

    private static final int MAX_LINE_LENGTH = 8 * 1024;
    private static final int MAX_HEADER_COUNT = 50;

    private final OutboundDestinationPolicy policy;
    private final RestOperations restOperations;

    public JwksFetchClient(OutboundDestinationPolicy policy, Limits limits, SSLContext sslContext) {
        this(policy, limits, sslContext, InetAddress::getAllByName, Clock.systemUTC());
    }

    JwksFetchClient(OutboundDestinationPolicy policy, Limits limits, SSLContext sslContext,
                    PolicyDnsResolver.Lookup lookup, Clock clock) {
        this.policy = policy;
        RestTemplate template = new RestTemplate(
                new HttpComponentsClientHttpRequestFactory(httpClient(policy, limits, sslContext, lookup)));
        template.getInterceptors().add(new BoundedResponseInterceptor(limits.maxResponseBytes(),
                limits.totalTimeout(), clock));
        template.setErrorHandler(new Non2xxIsAnError());
        this.restOperations = template;
    }

    /** Juge l'URI avant toute connexion ; la résolution DNS est jugée à la connexion. */
    public void checkUri(URI uri) {
        policy.checkUri(uri);
    }

    public RestOperations restOperations() {
        return restOperations;
    }

    private static CloseableHttpClient httpClient(OutboundDestinationPolicy policy, Limits limits,
                                                  SSLContext sslContext, PolicyDnsResolver.Lookup lookup) {
        var connections = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(new PolicyDnsResolver(policy, lookup))
                // Vérification du nom par défaut (HostnameVerificationPolicy.BOTH).
                .setTlsSocketStrategy(new DefaultClientTlsStrategy(sslContext))
                .setConnectionFactory(ManagedHttpClientConnectionFactory.builder()
                        .http1Config(Http1Config.custom()
                                .setMaxLineLength(MAX_LINE_LENGTH)
                                .setMaxHeaderCount(MAX_HEADER_COUNT)
                                .build())
                        .build())
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.of(limits.connectTimeout()))
                        .setSocketTimeout(Timeout.of(limits.readTimeout()))
                        .build())
                .build();
        return HttpClients.custom()
                .setConnectionManager(connections)
                .setRoutePlanner(new DefaultRoutePlanner(DefaultSchemePortResolver.INSTANCE))
                .disableRedirectHandling()
                .disableContentCompression()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .disableAuthCaching()
                // Chaque récupération ouvre sa propre connexion, résolue et jugée à nouveau.
                .setConnectionReuseStrategy((request, response, context) -> false)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.of(limits.readTimeout()))
                        .build())
                .build();
    }

    /** Limites d'une récupération JWKS. */
    public record Limits(Duration connectTimeout, Duration readTimeout, Duration totalTimeout,
                         long maxResponseBytes) {
    }

    private static final class Non2xxIsAnError implements ResponseErrorHandler {

        @Override
        public boolean hasError(ClientHttpResponse response) throws IOException {
            return !response.getStatusCode().is2xxSuccessful();
        }

        @Override
        public void handleError(URI url, HttpMethod method, ClientHttpResponse response)
                throws IOException {
            // Une redirection n'est jamais suivie : la cible n'a pas été jugée.
            throw new RestClientException("JWK Set URL answered HTTP " + response.getStatusCode().value());
        }
    }
}
