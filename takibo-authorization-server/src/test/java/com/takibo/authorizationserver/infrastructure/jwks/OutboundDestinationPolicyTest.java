package com.takibo.authorizationserver.infrastructure.jwks;

import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboundDestinationPolicyTest {

    private static final OutboundDestinationPolicy DEFAULTS =
            new OutboundDestinationPolicy(List.of(), List.of(), false);

    // ---------- URI, sans DNS ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "https://127.0.0.1/jwks",
            "https://[::1]/jwks",
            "https://[::ffff:127.0.0.1]/jwks",
            "https://[::127.0.0.1]/jwks",
            "https://[64:ff9b::7f00:1]/jwks",
            "https://169.254.169.254/latest/meta-data/",
            "https://[fd00:ec2::254]/",
            "https://10.0.0.5:8443/jwks",
            "https://192.168.1.10/jwks",
            "https://localhost/jwks",
            "https://LOCALHOST/jwks",
            "https://api.localhost/jwks",
            "https://2130706433/jwks",
            "https://0177.0.0.1/jwks",
            "https://0x7f000001/jwks",
            "https://0x7f.0.0.1/jwks",
            "https://127.000.000.001/jwks",
            "http://keys.example.com/jwks",
            "ftp://keys.example.com/jwks",
            "https://user@keys.example.com/jwks",
            "https://keys.example.com/jwks#frag"
    })
    void refuses_by_default(String uri) {
        assertThatThrownBy(checking(DEFAULTS, uri))
                .isInstanceOf(DestinationRefusedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://keys.example.com/.well-known/jwks.json",
            "https://8.8.8.8/jwks",
            "https://[2606:4700:4700::1111]/jwks",
            "https://keycloak:8443/realms/takibo/protocol/openid-connect/certs"
    })
    void accepts_by_default_what_dns_alone_can_judge(String uri) {
        // Un nom n'est jamais résolu à ce stade : seul filterResolved fait foi.
        assertThatCode(() -> DEFAULTS.checkUri(URI.create(uri))).doesNotThrowAnyException();
    }

    @Test
    void http_requires_both_the_switch_and_a_trusted_host() {
        OutboundDestinationPolicy httpOff = new OutboundDestinationPolicy(List.of("localhost"), List.of(), false);
        OutboundDestinationPolicy httpOn = new OutboundDestinationPolicy(List.of("localhost"), List.of(), true);

        assertThatThrownBy(checking(httpOff, "http://localhost:8080/certs"))
                .hasMessageContaining("HTTPS");
        assertThatCode(() -> httpOn.checkUri(URI.create("http://localhost:8080/certs")))
                .doesNotThrowAnyException();
        assertThatThrownBy(checking(httpOn, "http://keys.example.com/jwks"))
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(checking(httpOn, "http://127.0.0.1:8080/certs"))
                .hasMessageContaining("HTTPS");
    }

    @Test
    void a_trusted_name_lifts_the_loopback_refusal_for_that_name_only() {
        OutboundDestinationPolicy policy = new OutboundDestinationPolicy(List.of("localhost"), List.of(), false);

        assertThatCode(() -> policy.checkUri(URI.create("https://localhost:8443/certs"))).doesNotThrowAnyException();
        assertThatThrownBy(checking(policy, "https://api.localhost/certs"))
                .isInstanceOf(DestinationRefusedException.class);
        assertThatThrownBy(checking(policy, "https://127.0.0.1:8443/certs"))
                .isInstanceOf(DestinationRefusedException.class);
    }

    @Test
    void a_trusted_cidr_admits_its_ip_literals() {
        OutboundDestinationPolicy policy = new OutboundDestinationPolicy(List.of(), List.of("10.20.30.0/24"), false);

        assertThatCode(() -> policy.checkUri(URI.create("https://10.20.30.40/jwks"))).doesNotThrowAnyException();
        assertThatThrownBy(checking(policy, "https://10.20.31.40/jwks"))
                .isInstanceOf(DestinationRefusedException.class);
    }

    // ---------- Adresses réellement résolues ----------

    @Test
    void public_answers_pass_through_unchanged() throws Exception {
        InetAddress[] answers = {InetAddress.getByName("8.8.8.8"), InetAddress.getByName("2606:4700:4700::1111")};

        assertThat(DEFAULTS.filterResolved("keys.example.com", answers)).containsExactly(answers);
    }

    @Test
    void one_internal_answer_refuses_the_whole_response() throws Exception {
        InetAddress[] mixed = {InetAddress.getByName("8.8.8.8"), InetAddress.getByName("10.0.0.5")};
        InetAddress[] mixedFamilies = {InetAddress.getByName("8.8.8.8"), InetAddress.getByName("::1")};

        assertThatThrownBy(() -> DEFAULTS.filterResolved("keys.example.com", mixed))
                .hasMessageContaining("10.0.0.5");
        assertThatThrownBy(() -> DEFAULTS.filterResolved("keys.example.com", mixedFamilies))
                .isInstanceOf(DestinationRefusedException.class);
    }

    @Test
    void an_empty_answer_is_refused() {
        assertThatThrownBy(() -> DEFAULTS.filterResolved("keys.example.com", new InetAddress[0]))
                .isInstanceOf(DestinationRefusedException.class);
    }

    @Test
    void a_trusted_name_reaches_private_addresses_but_never_forbidden_ones() throws Exception {
        OutboundDestinationPolicy policy = new OutboundDestinationPolicy(List.of("keycloak"), List.of(), false);
        InetAddress dockerBridge = InetAddress.getByName("172.18.0.5");
        InetAddress metadata = InetAddress.getByName("169.254.169.254");

        assertThat(policy.filterResolved("keycloak", new InetAddress[]{dockerBridge})).containsExactly(dockerBridge);
        assertThat(policy.filterResolved("KEYCLOAK", new InetAddress[]{dockerBridge})).containsExactly(dockerBridge);
        assertThatThrownBy(() -> policy.filterResolved("keycloak", new InetAddress[]{metadata}))
                .hasMessageContaining("forbidden");
        assertThatThrownBy(() -> policy.filterResolved("keycloak-evil", new InetAddress[]{dockerBridge}))
                .hasMessageContaining("internal");
    }

    @Test
    void a_trusted_cidr_admits_private_answers_for_any_name_inside_it_only() throws Exception {
        OutboundDestinationPolicy policy = new OutboundDestinationPolicy(List.of(), List.of("172.18.0.0/16"), false);

        assertThat(policy.filterResolved("svc.internal", new InetAddress[]{InetAddress.getByName("172.18.0.5")}))
                .hasSize(1);
        InetAddress[] outsideTheRange = {InetAddress.getByName("172.19.0.5")};
        assertThatThrownBy(() -> policy.filterResolved("svc.internal", outsideTheRange))
                .isInstanceOf(DestinationRefusedException.class);
    }

    // ---------- Configuration ----------

    @ParameterizedTest
    @ValueSource(strings = {"", "*.example.com", "keycloak.", "127.0.0.1", "[::1]", "::1", "2130706433", "a/b"})
    void trusted_hosts_accept_exact_names_only(String host) {
        List<String> hosts = List.of(host);
        List<String> noCidr = List.of();
        assertThatThrownBy(() -> new OutboundDestinationPolicy(hosts, noCidr, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"169.254.0.0/16", "169.0.0.0/8", "0.0.0.0/0", "::/0", "fe80::/64", "224.0.0.0/8"})
    void trusted_cidrs_never_overlap_forbidden_ranges(String cidr) {
        List<String> noHost = List.of();
        List<String> cidrs = List.of(cidr);
        assertThatThrownBy(() -> new OutboundDestinationPolicy(noHost, cidrs, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("always-forbidden");
    }

    @Test
    void broad_trusted_cidrs_are_reported() {
        OutboundDestinationPolicy policy = new OutboundDestinationPolicy(
                List.of(), List.of("10.0.0.0/8", "10.1.2.0/24", "fd00::/48", "fd00:1::/64"), false);

        assertThat(policy.broadTrustedCidrs()).extracting(Cidr::toString)
                .containsExactlyInAnyOrder("10.0.0.0/8", "fd00:0:0:0:0:0:0:0/48");
    }

    /** Le contrôle seul, l'URI étant préparée hors de la lambda observée. */
    private static ThrowableAssert.ThrowingCallable checking(OutboundDestinationPolicy policy, String uri) {
        URI parsed = URI.create(uri);
        return () -> policy.checkUri(parsed);
    }
}
