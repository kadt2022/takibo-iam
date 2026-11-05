package com.takibo.authorizationserver.infrastructure.jwks;

import org.apache.hc.core5.http.io.EofSensorInputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Borne chaque réponse JWKS sur les octets réellement lus et sur une échéance totale.
 * <p>
 * La décompression est désactivée sur le client : les octets comptés sont ceux reçus. Le
 * délai de lecture du client coupe une socket muette ; l'échéance, vérifiée à chaque lecture,
 * coupe un serveur qui distille sa réponse octet par octet sans jamais rester muet assez
 * longtemps. Une lecture bloquée attend au plus ce délai de lecture : la durée totale est
 * bornée par l'échéance plus un délai de lecture.
 */
final class BoundedResponseInterceptor implements ClientHttpRequestInterceptor {

    private final long maxBytes;
    private final Duration totalTimeout;
    private final Clock clock;

    BoundedResponseInterceptor(long maxBytes, Duration totalTimeout, Clock clock) {
        this.maxBytes = maxBytes;
        this.totalTimeout = totalTimeout;
        this.clock = clock;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        Instant deadline = clock.instant().plus(totalTimeout);
        ClientHttpResponse response = execution.execute(request, body);
        long declared = response.getHeaders().getContentLength();
        if (declared > maxBytes) {
            abortAndClose(response);
            throw new IOException("JWK Set response exceeds " + maxBytes + " bytes");
        }
        return new BoundedResponse(response, deadline);
    }

    /**
     * Ferme sans jamais lire le reste du corps. Spring, en fermant une réponse HttpClient,
     * consomme le flux jusqu'à sa fin pour réutiliser la connexion : un serveur hostile
     * annulerait ainsi la limite de taille et l'échéance. La connexion est donc abandonnée
     * ({@link EofSensorInputStream#abort()} ferme la socket sans la vider) ; elle n'est de
     * toute façon jamais réutilisée.
     */
    private static void abortAndClose(ClientHttpResponse response) {
        try {
            if (response.getBody() instanceof EofSensorInputStream sensor) {
                sensor.abort();
            }
        } catch (IOException ignored) {
            // La connexion est déjà fermée : rien à abandonner.
        } finally {
            response.close();
        }
    }

    private final class BoundedResponse implements ClientHttpResponse {

        private final ClientHttpResponse delegate;
        private final Instant deadline;
        private InputStream body;

        BoundedResponse(ClientHttpResponse delegate, Instant deadline) {
            this.delegate = delegate;
            this.deadline = deadline;
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() throws IOException {
            if (body == null) {
                body = new BoundedStream(delegate.getBody(), deadline);
            }
            return body;
        }

        @Override
        public void close() {
            abortAndClose(delegate);
        }
    }

    private final class BoundedStream extends FilterInputStream {

        private final Instant deadline;
        private long count;

        BoundedStream(InputStream in, Instant deadline) {
            super(in);
            this.deadline = deadline;
        }

        @Override
        public int read() throws IOException {
            checkDeadline();
            int value = super.read();
            if (value >= 0) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            checkDeadline();
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                count(read);
            }
            return read;
        }

        @Override
        public long skip(long n) throws IOException {
            throw new IOException("Skipping is not supported on a bounded JWK Set response");
        }

        private void count(int read) throws IOException {
            count += read;
            if (count > maxBytes) {
                throw new IOException("JWK Set response exceeds " + maxBytes + " bytes");
            }
            checkDeadline();
        }

        private void checkDeadline() throws IOException {
            if (clock.instant().isAfter(deadline)) {
                throw new IOException("JWK Set response exceeded its total timeout of " + totalTimeout);
            }
        }
    }
}
