package com.takibo.authorizationserver.infrastructure.jwks;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Destination qui ne doit jamais être jointe : compte chaque connexion TCP acceptée, avant
 * tout TLS, et la ferme aussitôt.
 */
final class ConnectionProbe implements AutoCloseable {

    private final ServerSocket server;
    private final AtomicInteger accepted = new AtomicInteger();

    ConnectionProbe(String loopbackAddress, int port) throws IOException {
        server = new ServerSocket();
        server.bind(new InetSocketAddress(InetAddress.getByName(loopbackAddress), port));
        Thread acceptor = new Thread(this::acceptLoop, "jwks-connection-probe");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    int port() {
        return server.getLocalPort();
    }

    int acceptedConnections() {
        return accepted.get();
    }

    private void acceptLoop() {
        while (!server.isClosed()) {
            try (Socket ignored = server.accept()) {
                accepted.incrementAndGet();
            } catch (IOException closed) {
                return;
            }
        }
    }

    @Override
    public void close() throws IOException {
        server.close();
    }
}
