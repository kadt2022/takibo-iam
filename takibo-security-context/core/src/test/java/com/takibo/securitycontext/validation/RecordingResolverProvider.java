package com.takibo.securitycontext.validation;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * Résolveur DNS espion, enregistré pour les tests via META-INF/services : il note chaque nom
 * réellement envoyé à la résolution, puis délègue au résolveur du JDK. Les adresses littérales
 * ne passent jamais par un résolveur.
 */
public final class RecordingResolverProvider extends InetAddressResolverProvider {

    static final List<String> LOOKUPS = new CopyOnWriteArrayList<>();

    @Override
    public InetAddressResolver get(Configuration configuration) {
        InetAddressResolver builtin = configuration.builtinResolver();
        return new InetAddressResolver() {
            @Override
            public Stream<InetAddress> lookupByName(String host, LookupPolicy lookupPolicy) throws UnknownHostException {
                LOOKUPS.add(host);
                return builtin.lookupByName(host, lookupPolicy);
            }

            @Override
            public String lookupByAddress(byte[] addr) throws UnknownHostException {
                return builtin.lookupByAddress(addr);
            }
        };
    }

    @Override
    public String name() {
        return "takibo-test-recording-resolver";
    }
}
