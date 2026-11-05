package com.takibo.iamboot.tas;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Certificat auto-signé de test, généré à l'exécution par le {@code keytool} du JDK : aucune
 * clé n'est versionnée. Le contexte TLS obtenu présente ce certificat côté serveur et le
 * reconnaît côté client ; la vérification du nom reste celle du JDK.
 */
final class TasTlsMaterial {

    private static final char[] PASSWORD = "test-only".toCharArray();

    private final SSLContext context;

    private TasTlsMaterial(SSLContext context) {
        this.context = context;
    }

    /** {@code san} au format keytool, par exemple {@code dns:localhost,ip:127.0.0.1}. */
    static TasTlsMaterial selfSigned(String san) {
        try {
            Path directory = Files.createTempDirectory("takibo-tls");
            Path keyStore = directory.resolve("server.p12");
            Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
            Process process = new ProcessBuilder(List.of(
                    keytool.toString(), "-genkeypair", "-alias", "server", "-keyalg", "RSA",
                    "-keysize", "2048", "-dname", "CN=takibo-test", "-ext", "SAN=" + san,
                    "-validity", "2", "-storetype", "PKCS12", "-keystore", keyStore.toString(),
                    "-storepass", new String(PASSWORD)))
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
            return new TasTlsMaterial(context);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Certificat de test indisponible", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Génération du certificat interrompue", e);
        }
    }

    SSLContext context() {
        return context;
    }
}
