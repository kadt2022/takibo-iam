package com.takibo.authorizationserver.infrastructure.jwks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CidrTest {

    @Test
    void contains_addresses_of_its_range_only() throws Exception {
        Cidr range = Cidr.parse("172.16.0.0/12");

        assertThat(range.contains(InetAddress.getByName("172.16.0.0"))).isTrue();
        assertThat(range.contains(InetAddress.getByName("172.31.255.255"))).isTrue();
        assertThat(range.contains(InetAddress.getByName("172.32.0.0"))).isFalse();
        assertThat(range.contains(InetAddress.getByName("::ffff:172.16.0.1"))).isTrue();
        assertThat(range.contains(InetAddress.getByName("fc00::1"))).isFalse();
    }

    @Test
    void a_single_address_is_a_full_length_prefix() throws Exception {
        Cidr single = Cidr.parse("10.0.0.7");

        assertThat(single.prefixLength()).isEqualTo(32);
        assertThat(single.contains(InetAddress.getByName("10.0.0.7"))).isTrue();
        assertThat(single.contains(InetAddress.getByName("10.0.0.8"))).isFalse();
    }

    @Test
    void ipv6_ranges_are_supported() throws Exception {
        Cidr range = Cidr.parse("fd00:1234::/32");

        assertThat(range.isIpv4()).isFalse();
        assertThat(range.contains(InetAddress.getByName("fd00:1234:ffff::1"))).isTrue();
        assertThat(range.contains(InetAddress.getByName("fd00:1235::1"))).isFalse();
    }

    @Test
    void overlap_is_detected_in_both_directions() {
        assertThat(Cidr.parse("169.0.0.0/8").overlaps(Cidr.parse("169.254.0.0/16"))).isTrue();
        assertThat(Cidr.parse("169.254.10.0/24").overlaps(Cidr.parse("169.254.0.0/16"))).isTrue();
        assertThat(Cidr.parse("10.0.0.0/8").overlaps(Cidr.parse("169.254.0.0/16"))).isFalse();
        assertThat(Cidr.parse("10.0.0.0/8").overlaps(Cidr.parse("::/128"))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "10.0.0.0/33", "10.0.0.0/-1", "10.0.0.0/x", "keycloak/32", "010.0.0.0/8", "::/129"})
    void invalid_ranges_are_refused(String value) {
        assertThatThrownBy(() -> Cidr.parse(value)).isInstanceOf(IllegalArgumentException.class);
    }
}
