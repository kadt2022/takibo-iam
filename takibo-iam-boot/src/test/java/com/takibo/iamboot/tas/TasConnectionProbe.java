package com.takibo.iamboot.tas;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service « interne » qui ne doit jamais être joint : compte chaque connexion TCP acceptée,
 * avant toute négociation TLS, puis la ferme aussitôt. Une connexion comptée prouve que
 * Takibo a ouvert une socket vers cette destination, que le TLS ait abouti ou non.
 */
final class TasConnectionProbe implements AutoCloseable {

    private final ServerSocket server;
    private final AtomicInteger accepted = new AtomicInteger();

    TasConnectionProbe() throws IOException {
        server = new ServerSocket();
        server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        Thread acceptor = new Thread(this::acceptLoop, "tas-connection-probe");
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
