package com.takibo.iamboot.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.takibo.authorizationserver.infrastructure.springauthserver.properties.TasAuthorizationServerProperties;
import com.takibo.authorizationserver.infrastructure.springauthserver.token.HumanTokenCommand;
import com.takibo.authorizationserver.infrastructure.springauthserver.token.HumanTokenSigner;
import com.takibo.authorizationserver.infrastructure.springauthserver.token.TakiboTokenClaims;
import com.takibo.securitycontext.model.StandardAttributeKeys;
import com.takibo.securitycontext.spi.TakiboSecurityContextCarrier;
import com.takibo.securitymanagement.domain.exception.InvalidTokenException;
import com.takibo.securitymanagement.infrastructure.security.JwtAuthenticationFilter;
import com.takibo.securitymanagement.infrastructure.security.TrustedProxies;
import com.takibo.securitymanagement.infrastructure.security.boundary.BoundaryMembershipService;
import com.takibo.securitymanagement.infrastructure.token.TasJwtTokenValidatorAdapter;
import com.takibo.securitymanagement.sentinel.http.SentinelHttpErrorWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IAM-JWT-01 (J1-J3, J4/J5 par jeton signé) : signeur réel -> JWT signé -> décodeur -> adaptateur
 * -> filtre/factory -> Authentication -> contrôle d'appartenance. UUID tous distincts : jamais
 * userId = accountId, qui masquerait le défaut. Sans Docker : seule la base est simulée.
 */
class HumanIdentityChainTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_1 = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000011");
    private static final UUID SPACE_2 = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000022");
    private static final UUID ACCOUNT_A = UUID.fromString("cccccccc-0000-0000-0000-00000000000a");
    private static final UUID ACCOUNT_B = UUID.fromString("cccccccc-0000-0000-0000-00000000000b");
    private static final UUID USER_1 = UUID.fromString("dddddddd-0000-0000-0000-000000000001");
    private static final UUID USER_2 = UUID.fromString("dddddddd-0000-0000-0000-000000000002");
    private static final String ISSUER = "http://localhost:8081";

    private JwtEncoder encoder;
    private HumanTokenSigner signer;
    private JwtAuthenticationFilter filter;
    private SentinelHttpErrorWriter errorWriter;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate()).keyID("test").build();
        encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(jwk)));
        TasAuthorizationServerProperties properties = new TasAuthorizationServerProperties();
        properties.setIssuer(ISSUER);
        signer = new HumanTokenSigner(encoder, properties, 300, 600);
        errorWriter = mock(SentinelHttpErrorWriter.class);
        filter = new JwtAuthenticationFilter(
                new TasJwtTokenValidatorAdapter(NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build()),
                errorWriter,
                new TrustedProxies(List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void j1_spaceToken_subjectIsAccount_userIsAttribute() throws Exception {
        Authentication auth = authenticate(spaceToken(USER_1, SPACE_1));

        var ctx = ((TakiboSecurityContextCarrier) auth).getSecurityContext();
        assertThat(ctx.subject().subjectId()).isEqualTo(ACCOUNT_A.toString());
        assertThat(ctx.attributes().get(StandardAttributeKeys.ACCOUNT_ID, UUID.class)).contains(ACCOUNT_A);
        assertThat(ctx.attributes().get(StandardAttributeKeys.USER_ID, UUID.class)).contains(USER_1);
    }

    @Test
    void j2_organizationToken_subjectIsAccount_withoutUser() throws Exception {
        String token = signer.sign(new HumanTokenCommand(ORG_ID, null, ACCOUNT_A, null,
                TakiboTokenClaims.SOURCE_HUMAN_LOGIN, List.of(), List.of(), List.of())).tokenValue();

        var ctx = ((TakiboSecurityContextCarrier) authenticate(token)).getSecurityContext();

        assertThat(ctx.subject().subjectId()).isEqualTo(ACCOUNT_A.toString());
        assertThat(ctx.attributes().get(StandardAttributeKeys.USER_ID, UUID.class)).isEmpty();
        assertThat(ctx.tenant().spaceId()).isNull();
    }

    @Test
    void j3_switchingSpace_keepsTheSubject_andMovesTheUser() throws Exception {
        var first = ((TakiboSecurityContextCarrier) authenticate(spaceToken(USER_1, SPACE_1))).getSecurityContext();
        var second = ((TakiboSecurityContextCarrier) authenticate(spaceToken(USER_2, SPACE_2))).getSecurityContext();

        assertThat(second.subject().subjectId()).isEqualTo(first.subject().subjectId());
        assertThat(first.attributes().get(StandardAttributeKeys.USER_ID, UUID.class)).contains(USER_1);
        assertThat(second.attributes().get(StandardAttributeKeys.USER_ID, UUID.class)).contains(USER_2);
        assertThat(second.tenant().spaceId()).isEqualTo(SPACE_2.toString());
    }

    @Test
    void spaceAndOrganizationTokens_passTheMembershipCheck_throughTheirExplicitIdentifiers() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(SPACE_2))).thenReturn(ORG_ID);
        // Le User est cherché dans le Space du jeton (SPACE_1), pas dans le Space cible (SPACE_2).
        when(jdbc.queryForObject(contains("u.space_id = ?"), eq(Integer.class),
                eq(USER_1), eq(ACCOUNT_A), eq(SPACE_1), eq(ORG_ID))).thenReturn(1);
        when(jdbc.queryForObject(contains("WHERE u.account_id = ?"), eq(Integer.class), eq(ACCOUNT_A), eq(ORG_ID)))
                .thenReturn(1);
        BoundaryMembershipService boundary = new BoundaryMembershipService(jdbc);

        boundary.assertActorInSpaceOrg(SPACE_2, authenticate(spaceToken(USER_1, SPACE_1)));
        boundary.assertActorInSpaceOrg(SPACE_2, authenticate(signer.sign(new HumanTokenCommand(ORG_ID, null, ACCOUNT_A,
                null, TakiboTokenClaims.SOURCE_HUMAN_LOGIN, List.of(), List.of(), List.of())).tokenValue()));

        verify(jdbc, never()).queryForObject(contains("WHERE u.id = ?"), eq(Integer.class), eq(ACCOUNT_A), any());
    }

    @Test
    void j4_signedTokenWhoseSubDiffersFromAccountId_isRejectedAsInvalidToken() throws Exception {
        String token = encodeHuman(ACCOUNT_B, ACCOUNT_A, USER_1, SPACE_1);

        MockFilterChain chain = run(token);

        assertThat(chain.getRequest()).isNull();
        verify(errorWriter).write(any(InvalidTokenException.class), any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void j5_signedSpaceTokenWithoutSpace_isRejectedAsInvalidToken() throws Exception {
        String token = encodeHuman(ACCOUNT_A, ACCOUNT_A, USER_1, null);

        MockFilterChain chain = run(token);

        assertThat(chain.getRequest()).isNull();
        verify(errorWriter).write(any(InvalidTokenException.class), any(), any());
    }

    private String spaceToken(UUID userId, UUID spaceId) {
        return signer.sign(new HumanTokenCommand(ORG_ID, spaceId, ACCOUNT_A, userId,
                TakiboTokenClaims.SOURCE_HUMAN_SPACE_SELECTION, List.of(), List.of(), List.of())).tokenValue();
    }

    /** Signeur de fixture de confiance : produit une forme que le signeur de production refuse. */
    private String encodeHuman(UUID sub, UUID accountId, UUID userId, UUID spaceId) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER).subject(sub.toString()).issuedAt(now).expiresAt(now.plusSeconds(300))
                .claim(TakiboTokenClaims.SUBJECT_TYPE, TakiboTokenClaims.SUBJECT_HUMAN)
                .claim(TakiboTokenClaims.ORG_ID, ORG_ID.toString())
                .claim(TakiboTokenClaims.ACCOUNT_ID, accountId.toString())
                .claim(TakiboTokenClaims.USER_ID, userId.toString());
        if (spaceId != null) {
            claims.claim(TakiboTokenClaims.SPACE_ID, spaceId.toString());
        }
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build())).getTokenValue();
    }

    private Authentication authenticate(String token) throws Exception {
        MockFilterChain chain = run(token);
        assertThat(chain.getRequest()).as("filter chain continued").isNotNull();
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private MockFilterChain run(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/probe");
        request.addHeader("Authorization", "Bearer " + token);
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }
}
