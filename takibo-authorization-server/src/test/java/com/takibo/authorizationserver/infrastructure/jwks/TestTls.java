package com.takibo.authorizationserver.infrastructure.jwks;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Certificat auto-signé de test produit à l'exécution par le {@code keytool} du JDK : aucune
 * clé versionnée. Le contexte sert le certificat côté serveur et ne fait confiance qu'à lui
 * côté client ; la vérification du nom reste celle de HttpClient et du JDK.
 */
public final class TestTls {

    private static final char[] PASSWORD = "test-only".toCharArray();

    private final SSLContext context;

    private TestTls(SSLContext context) {
        this.context = context;
    }

    /** {@code san} au format keytool, par exemple {@code dns:jwks.test}. */
    public static TestTls selfSigned(String san) {
        try {
            Path keyStore = Files.createTempDirectory("takibo-tls").resolve("server.p12");
            Process process = new ProcessBuilder(List.of(
                    Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                    "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048",
                    "-dname", "CN=takibo-test", "-ext", "SAN=" + san, "-validity", "2",
                    "-storetype", "PKCS12", "-keystore", keyStore.toString(), "-storepass", new String(PASSWORD)))
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().transferTo(OutputStream.nullOutputStream());
            if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException("keytool n'a pas produit le certificat de test");
            }
            KeyStore store = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(keyStore)) {
                store.load(in, PASSWORD);
            }
            KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(store, PASSWORD);
            TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init(store);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keys.getKeyManagers(), trust.getTrustManagers(), null);
            return new TestTls(context);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Certificat de test indisponible", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Génération du certificat interrompue", e);
        }
    }

    public SSLContext context() {
        return context;
    }

    /** Serveur HTTPS sur l'adresse de boucle donnée, port libre. */
    public HttpsServer server(String loopbackAddress, HttpHandler handler) throws IOException {
        return server(loopbackAddress, 0, handler);
    }

    public HttpsServer server(String loopbackAddress, int port, HttpHandler handler) throws IOException {
        HttpsServer server = HttpsServer.create(new InetSocketAddress(InetAddress.getByName(loopbackAddress), port), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(context));
        server.createContext("/", handler);
        // Un gestionnaire lent ne bloque ni les autres échanges ni l'arrêt du serveur.
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "test-https");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        return server;
    }
}
