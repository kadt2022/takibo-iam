package com.takibo.authorizationserver.infrastructure.jwks;

import com.takibo.authorizationserver.infrastructure.jwks.InetAddressClassifier.AddressClass;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;

class InetAddressClassifierTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            // IPv4 toujours interdites
            "0.0.0.0,            ALWAYS_FORBIDDEN",
            "0.1.2.3,            ALWAYS_FORBIDDEN",
            "169.254.169.254,    ALWAYS_FORBIDDEN",
            "169.254.0.1,        ALWAYS_FORBIDDEN",
            "192.0.0.1,          ALWAYS_FORBIDDEN",
            "192.0.2.10,         ALWAYS_FORBIDDEN",
            "198.51.100.10,      ALWAYS_FORBIDDEN",
            "203.0.113.10,       ALWAYS_FORBIDDEN",
            "224.0.0.1,          ALWAYS_FORBIDDEN",
            "239.255.255.250,    ALWAYS_FORBIDDEN",
            "240.0.0.1,          ALWAYS_FORBIDDEN",
            "255.255.255.255,    ALWAYS_FORBIDDEN",
            // IPv4 privées
            "127.0.0.1,          PRIVATE",
            "127.255.255.254,    PRIVATE",
            "10.0.0.5,           PRIVATE",
            "172.16.0.1,         PRIVATE",
            "172.31.255.254,     PRIVATE",
            "192.168.65.254,     PRIVATE",
            "100.64.0.1,         PRIVATE",
            "198.18.0.1,         PRIVATE",
            // IPv4 publiques, y compris les bords des plages privées
            "8.8.8.8,            PUBLIC",
            "172.15.255.255,     PUBLIC",
            "172.32.0.0,         PUBLIC",
            "100.128.0.0,        PUBLIC",
            "169.255.0.1,        PUBLIC",
            // IPv6 toujours interdites
            "::,                 ALWAYS_FORBIDDEN",
            "fe80::1,            ALWAYS_FORBIDDEN",
            "fec0::1,            ALWAYS_FORBIDDEN",
            "ff02::1,            ALWAYS_FORBIDDEN",
            "2001::1,            ALWAYS_FORBIDDEN",
            "2001:db8::1,        ALWAYS_FORBIDDEN",
            "64:ff9b:1::1,       ALWAYS_FORBIDDEN",
            "fd00:ec2::254,      ALWAYS_FORBIDDEN",
            // IPv6 privées
            "::1,                PRIVATE",
            "fc00::1,            PRIVATE",
            "fd12:3456::1,       PRIVATE",
            // IPv4 portée par IPv6 : classée selon l'IPv4 qu'elle transporte
            "::127.0.0.1,        PRIVATE",
            "::169.254.169.254,  ALWAYS_FORBIDDEN",
            "::8.8.8.8,          PUBLIC",
            "::ffff:127.0.0.1,   PRIVATE",
            "::ffff:169.254.169.254, ALWAYS_FORBIDDEN",
            "::ffff:0:a9fe:a9fe, ALWAYS_FORBIDDEN",
            "64:ff9b::7f00:1,    PRIVATE",
            "64:ff9b::a9fe:a9fe, ALWAYS_FORBIDDEN",
            "64:ff9b::808:808,   PUBLIC",
            "2002:7f00:1::,      PRIVATE",
            "2002:a9fe:a9fe::1,  ALWAYS_FORBIDDEN",
            "2002:808:808::1,    PUBLIC",
            // IPv6 publique
            "2606:4700:4700::1111, PUBLIC"
    })
    void classifies_each_resolved_address(String literal, AddressClass expected) throws Exception {
        assertThat(InetAddressClassifier.classify(InetAddress.getByName(literal))).isEqualTo(expected);
    }
}
