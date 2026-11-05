package com.takibo.authorizationserver.infrastructure.jwks;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsServer;
import org.apache.hc.core5.ssl.SSLContexts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Client JWKS contre de vrais serveurs HTTP(S) locaux. Les réponses DNS sont simulées : le
 * nom {@code jwks.test} se résout vers l'adresse de boucle choisie par chaque test, ce qui
 * permet de mesurer le rebinding, les réponses mixtes et la vérification du nom TLS.
 */
class JwksFetchClientTest {

    private static final String JWKS = "{\"keys\":[]}";
    private static final JwksFetchClient.Limits LIMITS = new JwksFetchClient.Limits(
            Duration.ofMillis(500), Duration.ofMillis(300), Duration.ofMillis(800), 50 * 1024);

    private static TestTls jwksTestCertificate;
    private static TestTls otherNameCertificate;

    private final FakeDns dns = new FakeDns();

    @BeforeAll
    static void certificates() {
        // Le certificat ne porte QUE le nom : une connexion à 127.0.0.1 n'est acceptée que
        // si le nom demandé, et non l'adresse validée, est vérifié.
        jwksTestCertificate = TestTls.selfSigned("dns:jwks.test");
        otherNameCertificate = TestTls.selfSigned("dns:other.test");
    }

    // ---------- Chemin autorisé ----------

    @Test
    void fetches_from_a_trusted_name_over_tls_verified_against_that_name() throws Exception {
        HttpsServer server = jwksTestCertificate.server("127.0.0.1", ok(JWKS));
        try {
            dns.answer("jwks.test", "127.0.0.1");
            JwksFetchClient client = client(trusting("jwks.test"), jwksTestCertificate.context());

            String body = client.restOperations().getForObject(url(server, "/jwks"), String.class);

            assertThat(body).isEqualTo(JWKS);
            assertThat(dns.lookups("jwks.test")).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    // ---------- DNS rebinding et réutilisation ----------

    @Test
    void every_fetch_resolves_again_and_a_rebound_answer_is_never_connected() throws Exception {
        HttpsServer server = jwksTestCertificate.server("127.0.0.1", ok(JWKS));
        try (ConnectionProbe internal = new ConnectionProbe("127.0.0.2", server.getAddress().getPort())) {
            dns.answer("jwks.test", "127.0.0.1");
            dns.answer("jwks.test", "127.0.0.2");
            // Seule 127.0.0.1 est de confiance : la seconde réponse DNS désigne un autre service.
            JwksFetchClient client = client(
                    new OutboundDestinationPolicy(List.of(), List.of("127.0.0.1/32"), false),
                    jwksTestCertificate.context());

            assertThat(client.restOperations().getForObject(url(server, "/jwks"), String.class)).isEqualTo(JWKS);
            assertThatThrownBy(() -> client.restOperations().getForObject(url(server, "/jwks"), String.class))
                    .isInstanceOf(RestClientException.class)
                    .hasRootCauseInstanceOf(DestinationRefusedException.class);

            assertThat(dns.lookups("jwks.test"))
                    .as("une résolution par récupération : aucune connexion n'est réutilisée")
                    .isEqualTo(2);
            assertThat(internal.acceptedConnections()).as("connexions vers l'adresse substituée").isZero();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void a_mixed_dns_answer_is_refused_before_any_connection() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpsServer server = jwksTestCertificate.server("127.0.0.1", exchange -> {
            requests.incrementAndGet();
            ok(JWKS).handle(exchange);
        });
        try {
            dns.answer("jwks.test", "127.0.0.1", "10.0.0.5");
            JwksFetchClient client = client(
                    new OutboundDestinationPolicy(List.of(), List.of("127.0.0.1/32"), false),
                    jwksTestCertificate.context());

            assertThatThrownBy(() -> client.restOperations().getForObject(url(server, "/jwks"), String.class))
                    .hasRootCauseInstanceOf(DestinationRefusedException.class)
                    .hasStackTraceContaining("10.0.0.5");
            assertThat(requests).hasValue(0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void a_trusted_name_resolving_to_a_forbidden_address_is_refused() {
        dns.answer("jwks.test", "169.254.169.254");
        JwksFetchClient client = client(trusting("jwks.test"), jwksTestCertificate.context());

        assertThatThrownBy(() -> client.restOperations().getForObject(URI.create("https://jwks.test/jwks"), String.class))
                .hasRootCauseInstanceOf(DestinationRefusedException.class)
                .hasStackTraceContaining("forbidden");
    }

    // ---------- Redirections ----------

    @Test
    void a_redirect_is_not_followed_even_towards_a_trusted_destination() throws Exception {
        try (ConnectionProbe target = new ConnectionProbe("127.0.0.1", 0)) {
            HttpsServer server = jwksTestCertificate.server("127.0.0.1", exchange -> {
                exchange.getResponseHeaders().add("Location", "https://jwks.test:" + target.port() + "/jwks");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            });
            try {
                dns.answer("jwks.test", "127.0.0.1");
                JwksFetchClient client = client(trusting("jwks.test"), jwksTestCertificate.context());

                assertThatThrownBy(() -> client.restOperations().getForObject(url(server, "/jwks"), String.class))
                        .isInstanceOf(RestClientException.class)
                        .hasMessageContaining("302");
                assertThat(target.acceptedConnections()).as("connexions vers la cible de la redirection").isZero();
            } finally {
                server.stop(0);
            }
        }
    }

    // ---------- TLS ----------

    @Test
    void a_certificate_for_another_name_is_refused() throws Exception {
        HttpsServer server = otherNameCertificate.server("127.0.0.1", ok(JWKS));
        try {
            dns.answer("jwks.test", "127.0.0.1");
            // Le client fait confiance à ce certificat : seul le nom peut le faire refuser.
            JwksFetchClient client = client(trusting("jwks.test"), otherNameCertificate.context());

            assertThatThrownBy(() -> client.restOperations().getForObject(url(server, "/jwks"), String.class))
                    .satisfies(JwksFetchClientTest::failedDuringTls);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void a_certificate_outside_the_trust_store_is_refused() throws Exception {
        HttpsServer server = jwksTestCertificate.server("127.0.0.1", ok(JWKS));
        try {
            dns.answer("jwks.test", "127.0.0.1");
            JwksFetchClient client = client(trusting("jwks.test"), SSLContexts.createSystemDefault());

            assertThatThrownBy(() -> client.restOperations().getForObject(url(server, "/jwks"), String.class))
                    .satisfies(JwksFetchClientTest::failedDuringTls);
        } finally {
            server.stop(0);
        }
    }

    // ---------- HTTP ----------

    @Test
    void plain_http_reaches_a_trusted_name_only_when_explicitly_allowed() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/", ok(JWKS));
        server.start();
        try {
            dns.answer("jwks.test", "127.0.0.1");
            URI uri = URI.create("http://jwks.test:" + server.getAddress().getPort() + "/certs");
            JwksFetchClient httpOff = client(trusting("jwks.test"), jwksTestCertificate.context());
            JwksFetchClient httpOn = client(new OutboundDestinationPolicy(List.of("jwks.test"), List.of(), true),
                    jwksTestCertificate.context());

            assertThatThrownBy(() -> httpOff.checkUri(uri)).isInstanceOf(DestinationRefusedException.class);
            httpOn.checkUri(uri);
            assertThat(httpOn.restOperations().getForObject(uri, String.class)).isEqualTo(JWKS);
        } finally {
            server.stop(0);
        }
    }

    // ---------- Proxy ----------

    @Test
    void system_proxy_settings_are_ignored() throws Exception {
        HttpsServer server = jwksTestCertificate.server("127.0.0.1", ok(JWKS));
        String previousHost = System.getProperty("https.proxyHost");
        String previousPort = System.getProperty("https.proxyPort");
        String previousNonProxy = System.getProperty("http.nonProxyHosts");
        ProxySelector previousSelector = ProxySelector.getDefault();
        try (ConnectionProbe proxy = new ConnectionProbe("127.0.0.1", 0)) {
            System.setProperty("https.proxyHost", "127.0.0.1");
            System.setProperty("https.proxyPort", String.valueOf(proxy.port()));
            System.clearProperty("http.nonProxyHosts");
            ProxySelector.setDefault(new ProxySelector() {
                @Override
                public List<Proxy> select(URI uri) {
                    return List.of(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", proxy.port())));
                }

                @Override
                public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                    // Sans objet : le proxy ne doit jamais être utilisé.
                }
            });
            dns.answer("jwks.test", "127.0.0.1");
            JwksFetchClient client = client(trusting("jwks.test"), jwksTestCertificate.context());

            assertThat(client.restOperations().getForObject(url(server, "/jwks"), String.class)).isEqualTo(JWKS);
            assertThat(proxy.acceptedConnections()).as("connexions vers le proxy du système").isZero();
        } finally {
            ProxySelector.setDefault(previousSelector);
            restore("https.proxyHost", previousHost);
            restore("https.proxyPort", previousPort);
            restore("http.nonProxyHosts", previousNonProxy);
            server.stop(0);
        }
    }

    // ---------- Taille ----------

    @Test
    void a_response_within_the_limit_is_read_entirely() throws Exception {
        String body = "{\"keys\":[],\"pad\":\"" + "a".repeat(40 * 1024) + "\"}";
        HttpsServer server = jwksTestCertificate.server("127.0.0.1", ok(body));
        try {
            dns.answer("jwks.test", "127.0.0.1");

            assertThat(client(trusting("jwks.test"), jwksTestCertificate.context()).restOperations()
                    .getForObject(url(server, "/jwks"), String.class)).hasSize(body.length());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void a_declared_oversized_response_is_refused() throws Exception {
        HttpsServer server = jwksTestCertificate.server("127.0.0.1", ok("a".repeat(60 * 1024)));
        try {
            dns.answer("jwks.test", "127.0.0.1");

            assertThatThrownBy(() -> client(trusting("jwks.test"), jwksTestCertificate.context()).restOperations()
                    .getForObject(url(server, "/jwks"), String.class))
                    .hasStackTraceContaining("exceeds " + 50 * 1024 + " bytes");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void an_undeclared_endless_response_is_cut_at_the_limit() throws Exception {
        AtomicInteger written = new AtomicInteger();
        HttpsServer server = jwksTestCertificate.server("127.0.0.1", exchange -> {
            exchange.sendResponseHeaders(200, 0); // chunked, sans longueur annoncée
            byte[] chunk = new byte[4096];
            try (OutputStream out = exchange.getResponseBody()) {
                while (written.get() < 10 * 1024 * 1024) {
                    out.write(chunk);
                    written.addAndGet(chunk.length);
                }
            } catch (IOException clientWentAway) {
                // Attendu : le client coupe la lecture à la limite.
            }
        });
        try {
            dns.answer("jwks.test", "127.0.0.1");

            assertThatThrownBy(() -> client(trusting("jwks.test"), jwksTestCertificate.context()).restOperations()
                    .getForObject(url(server, "/jwks"), String.class))
                    .hasStackTraceContaining("exceeds " + 50 * 1024 + " bytes");
            assertThat(written.get()).as("octets servis avant la coupure").isLessThan(10 * 1024 * 1024);
        } finally {
            server.stop(0);
        }
    }

    // ---------- Délais ----------

    @Test
    void a_silent_server_is_cut_by_the_read_timeout() throws Exception {
        HttpsServer server = jwksTestCertificate.server("127.0.0.1", exchange -> sleep(5_000));
        try {
            dns.answer("jwks.test", "127.0.0.1");
            JwksFetchClient client = client(trusting("jwks.test"), jwksTestCertificate.context());

            long start = System.nanoTime();
            assertThatThrownBy(() -> client.restOperations().getForObject(url(server, "/jwks"), String.class))
                    .hasRootCauseInstanceOf(SocketTimeoutException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(2_000));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void a_server_trickling_bytes_is_cut_by_the_total_timeout() throws Exception {
        HttpsServer server = jwksTestCertificate.server("127.0.0.1", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                for (int i = 0; i < 100; i++) {
                    // Jamais muet plus de 100 ms : le délai de lecture (300 ms) ne se déclenche pas.
                    out.write(' ');
                    out.flush();
                    sleep(100);
                }
            } catch (IOException clientWentAway) {
                // Attendu : le client abandonne à l'échéance.
            }
        });
        try {
            dns.answer("jwks.test", "127.0.0.1");
            JwksFetchClient client = client(trusting("jwks.test"), jwksTestCertificate.context());

            long start = System.nanoTime();
            // L'annulation de la requête à l'échéance agit la première ; le contrôle d'échéance
            // à chaque lecture du corps reste une seconde barrière.
            assertThatThrownBy(() -> client.restOperations().getForObject(url(server, "/jwks"), String.class))
                    .isInstanceOf(RestClientException.class);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            // Échéance (800 ms), avec une marge de planification.
            assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(800)).isLessThan(Duration.ofMillis(2_000));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void a_server_trickling_its_response_headers_is_cut_by_the_total_timeout() throws Exception {
        // L'en-tête lui-même arrive octet par octet, jamais muet plus de 100 ms : ni le délai
        // de lecture (300 ms) ni la lecture du corps ne peuvent l'arrêter. Seule l'échéance
        // totale (800 ms) appliquée à tout l'échange le peut ; sans elle, ~20 s ici.
        byte[] head = ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nX-Pad: " + "a".repeat(150)
                + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        try (ServerSocket server = jwksTestCertificate.context().getServerSocketFactory()
                .createServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            Thread trickler = new Thread(() -> {
                try (Socket socket = server.accept(); OutputStream out = socket.getOutputStream()) {
                    socket.getInputStream().read(new byte[4096]);
                    for (byte b : head) {
                        out.write(b);
                        out.flush();
                        sleep(100);
                    }
                } catch (IOException clientWentAway) {
                    // Attendu : le client abandonne à l'échéance.
                }
            }, "header-trickler");
            trickler.setDaemon(true);
            trickler.start();
            dns.answer("jwks.test", "127.0.0.1");
            JwksFetchClient client = client(trusting("jwks.test"), jwksTestCertificate.context());

            long start = System.nanoTime();
            assertThatThrownBy(() -> client.restOperations()
                    .getForObject(URI.create("https://jwks.test:" + server.getLocalPort() + "/jwks"), String.class))
                    .isInstanceOf(RestClientException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start))
                    .isGreaterThanOrEqualTo(Duration.ofMillis(800))
                    .isLessThan(Duration.ofMillis(2_000));
        }
    }

    @Test
    void the_connect_timeout_cuts_a_connection_that_never_completes() throws Exception {
        // File d'attente TCP saturée et jamais servie : le noyau ignore les nouveaux SYN, la
        // connexion reste en suspens jusqu'au délai de connexion (500 ms).
        try (ServerSocket saturated = new ServerSocket()) {
            saturated.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 1);
            try (Fillers fillers = new Fillers()) {
                fillers.saturate(saturated.getLocalSocketAddress());
                dns.answer("jwks.test", "127.0.0.1");
                JwksFetchClient client = client(trusting("jwks.test"), jwksTestCertificate.context());
                URI uri = URI.create("https://jwks.test:" + saturated.getLocalPort() + "/jwks");

                long start = System.nanoTime();
                assertThatThrownBy(() -> client.restOperations().getForObject(uri, String.class))
                        .hasRootCauseInstanceOf(SocketTimeoutException.class);
                assertThat(Duration.ofNanos(System.nanoTime() - start))
                        .isGreaterThanOrEqualTo(Duration.ofMillis(500))
                        .isLessThan(Duration.ofMillis(2_000));
            }
        }
    }

    // ---------- Outils ----------

    private JwksFetchClient client(OutboundDestinationPolicy policy, SSLContext sslContext) {
        return new JwksFetchClient(policy, LIMITS, sslContext, dns, Clock.systemUTC());
    }

    private static OutboundDestinationPolicy trusting(String host) {
        return new OutboundDestinationPolicy(List.of(host), List.of(), false);
    }

    private static URI url(HttpServer server, String path) {
        return URI.create("https://jwks.test:" + server.getAddress().getPort() + path);
    }

    private static com.sun.net.httpserver.HttpHandler ok(String body) {
        return exchange -> respond(exchange, body);
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void failedDuringTls(Throwable thrown) {
        List<Throwable> chain = new ArrayList<>();
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            chain.add(cause);
        }
        assertThat(chain).as("chaîne des causes").hasAtLeastOneElementOfType(SSLException.class);
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    /** Connexions qui remplissent la file d'attente d'une socket jamais servie ; toutes fermées à la fin. */
    private static final class Fillers implements AutoCloseable {

        private final List<Socket> sockets = new ArrayList<>();

        void saturate(SocketAddress target) throws IOException {
            for (int i = 0; i < 8; i++) {
                Socket filler = new Socket();
                sockets.add(filler);
                try {
                    filler.connect(target, 200);
                } catch (SocketTimeoutException queueFull) {
                    return;
                }
            }
        }

        @Override
        public void close() throws IOException {
            for (Socket socket : sockets) {
                socket.close();
            }
        }
    }

    /** DNS simulé : chaque nom rend ses réponses dans l'ordre, la dernière se répète. */
    private static final class FakeDns implements PolicyDnsResolver.Lookup {

        private final Map<String, Deque<InetAddress[]>> answers = new HashMap<>();
        private final Map<String, AtomicInteger> lookups = new HashMap<>();

        void answer(String host, String... addresses) {
            List<InetAddress> resolved = new ArrayList<>();
            for (String address : addresses) {
                try {
                    resolved.add(InetAddress.getByName(address));
                } catch (UnknownHostException e) {
                    throw new IllegalArgumentException(e);
                }
            }
            answers.computeIfAbsent(host, h -> new ArrayDeque<>()).add(resolved.toArray(InetAddress[]::new));
        }

        int lookups(String host) {
            return lookups.getOrDefault(host, new AtomicInteger()).get();
        }

        @Override
        public synchronized InetAddress[] lookup(String host) throws UnknownHostException {
            lookups.computeIfAbsent(host, h -> new AtomicInteger()).incrementAndGet();
            Deque<InetAddress[]> queue = answers.get(host);
            if (queue == null || queue.isEmpty()) {
                throw new UnknownHostException(host);
            }
            return queue.size() > 1 ? queue.poll() : queue.peek();
        }
    }
}
