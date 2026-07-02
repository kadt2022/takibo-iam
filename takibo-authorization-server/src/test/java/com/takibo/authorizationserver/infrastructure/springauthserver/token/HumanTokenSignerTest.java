package com.takibo.authorizationserver.infrastructure.springauthserver.token;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.takibo.authorizationserver.infrastructure.springauthserver.properties.TasAuthorizationServerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HumanTokenSignerTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ACCOUNT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID USER_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    private static final UUID STUB_ORG = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID STUB_SPACE = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private HumanTokenSigner signer;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID("test-key")
                .build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));

        TasAuthorizationServerProperties properties = new TasAuthorizationServerProperties();
        properties.setIssuer("http://localhost:8081");

        signer = new HumanTokenSigner(new NimbusJwtEncoder(jwkSource), properties, 300);
    }

    private HumanTokenCommand command() {
        return new HumanTokenCommand(ORG_ID, SPACE_ID, ACCOUNT_ID, USER_ID,
                List.of("R_ORG_OWNER", "R_SPACE_ADMIN"));
    }

    @Test
    void sign_emitsCanonicalHumanClaims() throws Exception {
        SignedHumanToken signed = signer.sign(command());

        assertThat(signed.expiresInSeconds()).isEqualTo(300);

        JWTClaimsSet claims = SignedJWT.parse(signed.tokenValue()).getJWTClaimsSet();

        assertThat(claims.getIssuer()).isEqualTo("http://localhost:8081");
        assertThat(claims.getSubject()).isEqualTo(ACCOUNT_ID.toString());
        assertThat(claims.getStringClaim(TakiboTokenClaims.SUBJECT_TYPE)).isEqualTo("HUMAN");
        assertThat(claims.getStringClaim(TakiboTokenClaims.AUTH_METHOD)).isEqualTo("PASSWORD");
        assertThat(claims.getStringClaim(TakiboTokenClaims.SCOPE_LEVEL)).isEqualTo("SPACE");
        assertThat(claims.getStringClaim(TakiboTokenClaims.TENANT_SOURCE)).isEqualTo("human_login");
        assertThat(claims.getStringClaim(TakiboTokenClaims.ORG_ID)).isEqualTo(ORG_ID.toString());
        assertThat(claims.getStringClaim(TakiboTokenClaims.SPACE_ID)).isEqualTo(SPACE_ID.toString());
        assertThat(claims.getStringClaim(TakiboTokenClaims.ACCOUNT_ID)).isEqualTo(ACCOUNT_ID.toString());
        assertThat(claims.getStringClaim(TakiboTokenClaims.USER_ID)).isEqualTo(USER_ID.toString());
        assertThat(claims.getStringListClaim(TakiboTokenClaims.ROLES))
                .containsExactly("R_ORG_OWNER", "R_SPACE_ADMIN");

        assertThat(claims.getExpirationTime()).isNotNull();
        assertThat(claims.getIssueTime()).isNotNull();
        assertThat(claims.getJWTID()).isNotBlank();
    }

    @Test
    void sign_neverEmitsMachineVocabulary() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(signer.sign(command()).tokenValue()).getJWTClaimsSet();

        assertThat(claims.getStringClaim(TakiboTokenClaims.SUBJECT_TYPE))
                .isNotEqualTo(TakiboTokenClaims.SUBJECT_CLIENT_APP);
        assertThat(claims.getStringClaim(TakiboTokenClaims.AUTH_METHOD))
                .isNotEqualTo(TakiboTokenClaims.AUTH_CLIENT_CREDENTIALS);
        assertThat(claims.getStringClaim(TakiboTokenClaims.TENANT_SOURCE))
                .isNotEqualTo(TakiboTokenClaims.SOURCE_PLATFORM);
    }

    @Test
    void sign_neverEmitsStubTenantIds() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(signer.sign(command()).tokenValue()).getJWTClaimsSet();

        assertThat(claims.getStringClaim(TakiboTokenClaims.ORG_ID)).isNotEqualTo(STUB_ORG.toString());
        assertThat(claims.getStringClaim(TakiboTokenClaims.SPACE_ID)).isNotEqualTo(STUB_SPACE.toString());
    }

    @Test
    void sign_failsClosed_whenTenantIdentityIncomplete() {
        assertThatThrownBy(() -> signer.sign(
                new HumanTokenCommand(ORG_ID, SPACE_ID, ACCOUNT_ID, null, List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HUMAN_TOKEN_REQUIRES_FULL_TENANT_IDENTITY");

        assertThatThrownBy(() -> signer.sign(
                new HumanTokenCommand(null, SPACE_ID, ACCOUNT_ID, USER_ID, List.of())))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> signer.sign(
                new HumanTokenCommand(ORG_ID, null, ACCOUNT_ID, USER_ID, List.of())))
                .isInstanceOf(IllegalStateException.class);
    }
}
