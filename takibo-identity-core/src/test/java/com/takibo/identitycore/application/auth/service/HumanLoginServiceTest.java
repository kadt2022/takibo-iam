package com.takibo.identitycore.application.auth.service;

import com.takibo.identitycore.application.auth.command.LoginCommand;
import com.takibo.identitycore.application.auth.mapper.AuthMapper;
import com.takibo.identitycore.application.auth.model.HumanTokenRequest;
import com.takibo.identitycore.application.auth.model.LoginToken;
import com.takibo.identitycore.application.auth.port.HumanAccessTokenIssuer;
import com.takibo.identitycore.application.rbac.effective.model.EffectiveRbac;
import com.takibo.identitycore.application.rbac.effective.port.in.EffectiveRbacQueryCase;
import com.takibo.identitycore.domain.exception.AuthenticationFailedException;
import com.takibo.identitycore.domain.exception.OrganizationNotActiveException;
import com.takibo.identitycore.domain.exception.OrganizationNotFoundException;
import com.takibo.identitycore.domain.exception.SpaceNotFoundException;
import com.takibo.identitycore.domain.model.Account;
import com.takibo.identitycore.domain.model.AccountCredentials;
import com.takibo.identitycore.domain.model.EmailAddress;
import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.domain.repository.AccountCredentialsRepository;
import com.takibo.identitycore.domain.repository.AccountRepository;
import com.takibo.identitycore.domain.repository.UserRepository;
import com.takibo.identitycore.domain.security.port.PasswordHasherCase;
import com.takibo.identitycore.domain.status.UserStatus;
import com.takibo.identitycore.domain.vo.PasswordHash;
import com.takibo.identitycore.domain.vo.UserId;
import com.takibo.identitycore.integration.space.SpaceContextVerifier;
import com.takibo.identitycore.integration.space.port.ResolvedOrgKey;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import com.takibo.identitycore.integration.space.port.SpaceKeyResolutionCase;
import com.takibo.identitycore.interfaces.rest.response.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * IAM 31 : la surface de login ne raconte rien — toutes les causes d'échec
 * convergent vers {@link AuthenticationFailedException} (message unique), et
 * seuls les effets de bord internes (compteur d'échecs, audit) diffèrent.
 */
@ExtendWith(MockitoExtension.class)
class HumanLoginServiceTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SPACE_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final String EMAIL = "founder@takibo.io";
    private static final String PASSWORD = "Str0ng!Passw0rd";
    private static final String UNIFORM_MESSAGE = "Impossible de valider cette connexion.";
    private static final ResolvedSpaceKey KEY =
            new ResolvedSpaceKey(ORG_ID, SPACE_ID, "takibo-iam", "finance");

    @Mock private SpaceKeyResolutionCase spaceKeyResolution;
    @Mock private SpaceContextVerifier spaceContextVerifier;
    @Mock private AccountRepository accountRepository;
    @Mock private AccountCredentialsRepository accountCredentialsRepository;
    @Mock private PasswordHasherCase passwordHasher;
    @Mock private UserRepository userRepository;
    @Mock private EffectiveRbacQueryCase effectiveRbacQuery;
    @Mock private HumanAccessTokenIssuer tokenIssuer;
    @Mock private AuthMapper authMapper;

    @InjectMocks
    private HumanLoginService service;

    private Account account;
    private AccountCredentials credentials;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxFailedAttempts", 5);
        ReflectionTestUtils.setField(service, "lockSeconds", 900L);

        account = Account.create(new EmailAddress(EMAIL), null, null, Map.of()).withOrgId(ORG_ID);
        credentials = AccountCredentials.create(account.getId(), PasswordHash.of("$2a$hash", "bcrypt", 1));
    }

    private LoginCommand command() {
        return new LoginCommand(EMAIL, PASSWORD, "takibo-iam", "finance");
    }

    private LoginCommand orgCommand() {
        return new LoginCommand(EMAIL, PASSWORD, "takibo-iam", null);
    }

    private void givenResolvedSpace() {
        when(spaceKeyResolution.resolve("takibo-iam", "finance")).thenReturn(KEY);
    }

    private void givenResolvedActiveOrg() {
        when(spaceKeyResolution.resolveActiveOrganization("takibo-iam"))
                .thenReturn(new ResolvedOrgKey(ORG_ID, "takibo-iam"));
    }

    private void givenAccountWithCredentials() {
        when(accountRepository.findByEmail(any(), eq(new EmailAddress(EMAIL))))
                .thenReturn(Optional.of(account));
        when(accountCredentialsRepository.find(any(), eq(account.getId())))
                .thenReturn(Optional.of(credentials));
    }

    private User localUser() {
        return localUser(UserStatus.ACTIVE);
    }

    private User localUser(UserStatus status) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(new UserId(USER_ID));
        when(user.getStatus()).thenReturn(status);
        return user;
    }

    // ───────────────────────── chemin SPACE (transitoire) ─────────────────────────

    @Test
    void loginSuccess_issuesSituatedToken_withRealIds_andFounderRoles() {
        givenResolvedSpace();
        givenAccountWithCredentials();
        when(passwordHasher.matches(PASSWORD, "$2a$hash")).thenReturn(true);
        User user = localUser();
        when(userRepository.findBySpaceAndAccount(any(), eq(account.getId())))
                .thenReturn(Optional.of(user));
        when(effectiveRbacQuery.effectiveFor(ORG_ID, SPACE_ID, account.getId().getValue()))
                .thenReturn(new EffectiveRbac(
                        List.of("R_ORG_OWNER", "R_SPACE_ADMIN"),
                        List.of("G_ORG_ADMINS", "G_SPACE_ADMINS"),
                        List.of("P_ASSIGN_ROLES", "P_MANAGE_USERS")));

        LoginToken token = new LoginToken("jwt-value", "Bearer", 300);
        when(tokenIssuer.issue(any())).thenReturn(token);
        LoginResponse expected = mock(LoginResponse.class);
        when(authMapper.toLoginResponse(eq(token), any())).thenReturn(expected);

        LoginResponse response = service.login(command());

        assertThat(response).isSameAs(expected);

        ArgumentCaptor<HumanTokenRequest> captor = ArgumentCaptor.forClass(HumanTokenRequest.class);
        verify(tokenIssuer).issue(captor.capture());
        HumanTokenRequest request = captor.getValue();
        assertThat(request.orgId()).isEqualTo(ORG_ID);
        assertThat(request.spaceId()).isEqualTo(SPACE_ID);
        assertThat(request.accountId()).isEqualTo(account.getId().getValue());
        assertThat(request.userId()).isEqualTo(USER_ID);
        assertThat(request.isOrganizationScoped()).isFalse();
        assertThat(request.roles()).containsExactly("R_ORG_OWNER", "R_SPACE_ADMIN");
        assertThat(request.groups()).containsExactly("G_ORG_ADMINS", "G_SPACE_ADMINS");
        assertThat(request.permissions()).containsExactly("P_ASSIGN_ROLES", "P_MANAGE_USERS");

        // Aucun échec préalable : pas de reset superflu des credentials.
        verify(accountCredentialsRepository, never()).save(any(), any());
    }

    @Test
    void unknownEmail_uniformFailure_withoutRevealingCause() {
        givenResolvedSpace();
        when(accountRepository.findByEmail(any(), eq(new EmailAddress(EMAIL))))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(command()))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage(UNIFORM_MESSAGE);

        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    void missingCredentials_uniformFailure_sameAsUnknownEmail() {
        givenResolvedSpace();
        when(accountRepository.findByEmail(any(), eq(new EmailAddress(EMAIL))))
                .thenReturn(Optional.of(account));
        when(accountCredentialsRepository.find(any(), eq(account.getId())))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(command()))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage(UNIFORM_MESSAGE);

        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    void badPassword_registersFailure_andFailsUniformly() {
        givenResolvedSpace();
        givenAccountWithCredentials();
        when(passwordHasher.matches(PASSWORD, "$2a$hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(command()))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage(UNIFORM_MESSAGE);

        // L'échec est compté même si la réponse externe reste muette.
        ArgumentCaptor<AccountCredentials> captor = ArgumentCaptor.forClass(AccountCredentials.class);
        verify(accountCredentialsRepository).save(captor.capture(), eq(ORG_ID));
        assertThat(captor.getValue().getFailedAttempts()).isEqualTo(1);

        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    void lockedAccount_failsUniformly_beforePasswordCheck() {
        givenResolvedSpace();
        credentials = credentials.toBuilder()
                .failedAttempts(5)
                .lockedUntil(Instant.now().plusSeconds(600))
                .build();
        givenAccountWithCredentials();

        assertThatThrownBy(() -> service.login(command()))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage(UNIFORM_MESSAGE);

        verify(passwordHasher, never()).matches(any(), any());
        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    void successAfterPreviousFailures_resetsFailedAttempts() {
        givenResolvedSpace();
        credentials = credentials.toBuilder().failedAttempts(2).build();
        givenAccountWithCredentials();
        when(passwordHasher.matches(PASSWORD, "$2a$hash")).thenReturn(true);
        User user = localUser();
        when(userRepository.findBySpaceAndAccount(any(), eq(account.getId())))
                .thenReturn(Optional.of(user));
        when(effectiveRbacQuery.effectiveFor(any(), any(), any()))
                .thenReturn(new EffectiveRbac(List.of("R_SPACE_ADMIN"), List.of(), List.of()));
        when(tokenIssuer.issue(any())).thenReturn(new LoginToken("jwt", "Bearer", 300));
        when(authMapper.toLoginResponse(any(), any())).thenReturn(mock(LoginResponse.class));

        service.login(command());

        ArgumentCaptor<AccountCredentials> captor = ArgumentCaptor.forClass(AccountCredentials.class);
        verify(accountCredentialsRepository).save(captor.capture(), eq(ORG_ID));
        assertThat(captor.getValue().getFailedAttempts()).isZero();
        assertThat(captor.getValue().getLockedUntil()).isNull();
    }

    @Test
    void accountWithoutLocalUserInSpace_failsUniformly_noTokenIssued() {
        givenResolvedSpace();
        givenAccountWithCredentials();
        when(passwordHasher.matches(PASSWORD, "$2a$hash")).thenReturn(true);
        when(userRepository.findBySpaceAndAccount(any(), eq(account.getId())))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(command()))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage(UNIFORM_MESSAGE);

        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    void unknownSpaceCode_failsUniformly_withoutTouchingAccounts() {
        when(spaceKeyResolution.resolve("takibo-iam", "finance"))
                .thenThrow(new SpaceNotFoundException("Space not found: finance"));

        assertThatThrownBy(() -> service.login(command()))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage(UNIFORM_MESSAGE);

        verifyNoInteractions(accountRepository, accountCredentialsRepository, passwordHasher, tokenIssuer);
    }

    @Test
    void nonActiveUser_cannotObtainToken_evenWithValidPassword() {
        // Le statut du user est une frontière d'accès : suspendre, c'est retirer
        // la capacité de recevoir une nouvelle preuve. Réponse externe : uniforme.
        for (UserStatus status : new UserStatus[]{
                UserStatus.SUSPENDED, UserStatus.LOCKED, UserStatus.DEACTIVATED,
                UserStatus.PASSWORD_RESET, UserStatus.PENDING_ACTIVATION}) {

            reset(spaceKeyResolution, accountRepository, accountCredentialsRepository,
                    passwordHasher, userRepository, tokenIssuer);
            givenResolvedSpace();
            givenAccountWithCredentials();
            when(passwordHasher.matches(PASSWORD, "$2a$hash")).thenReturn(true);
            User user = localUser(status);
            when(userRepository.findBySpaceAndAccount(any(), eq(account.getId())))
                    .thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.login(command()))
                    .as("status " + status)
                    .isInstanceOf(AuthenticationFailedException.class)
                    .hasMessage(UNIFORM_MESSAGE);

            verify(tokenIssuer, never()).issue(any());
        }
    }

    // ───────────────────────── chemin ORGANIZATION (IAM 31) ─────────────────────────

    @Test
    void orgLogin_issuesOrganizationScopedToken_withOrgAuthoritiesOnly() {
        givenResolvedActiveOrg();
        givenAccountWithCredentials();
        when(passwordHasher.matches(PASSWORD, "$2a$hash")).thenReturn(true);
        when(effectiveRbacQuery.effectiveOrgFor(ORG_ID, account.getId().getValue()))
                .thenReturn(new EffectiveRbac(
                        List.of("R_ORG_ADMIN", "R_ORG_OWNER"),
                        List.of("G_ORG_ADMINS"),
                        List.of("P_READ_ORG", "P_UPDATE_ORG_SETTINGS")));

        LoginToken token = new LoginToken("jwt-org", "Bearer", 300);
        when(tokenIssuer.issue(any())).thenReturn(token);
        LoginResponse expected = mock(LoginResponse.class);
        when(authMapper.toLoginResponse(eq(token), any())).thenReturn(expected);

        LoginResponse response = service.login(orgCommand());

        assertThat(response).isSameAs(expected);

        ArgumentCaptor<HumanTokenRequest> captor = ArgumentCaptor.forClass(HumanTokenRequest.class);
        verify(tokenIssuer).issue(captor.capture());
        HumanTokenRequest request = captor.getValue();
        assertThat(request.isOrganizationScoped()).isTrue();
        assertThat(request.orgId()).isEqualTo(ORG_ID);
        assertThat(request.accountId()).isEqualTo(account.getId().getValue());
        assertThat(request.spaceId()).isNull();
        assertThat(request.userId()).isNull();
        assertThat(request.roles()).containsExactly("R_ORG_ADMIN", "R_ORG_OWNER");

        // Le pouvoir vient d'effectiveOrgFor : jamais du calcul situé d'un space.
        verify(effectiveRbacQuery, never()).effectiveFor(any(), any(), any());
        verifyNoInteractions(userRepository, spaceContextVerifier);
    }

    @Test
    void orgLogin_blankSpaceCode_takesOrganizationPath() {
        givenResolvedActiveOrg();
        givenAccountWithCredentials();
        when(passwordHasher.matches(PASSWORD, "$2a$hash")).thenReturn(true);
        when(effectiveRbacQuery.effectiveOrgFor(any(), any()))
                .thenReturn(new EffectiveRbac(List.of(), List.of(), List.of()));
        when(tokenIssuer.issue(any())).thenReturn(new LoginToken("jwt", "Bearer", 300));
        when(authMapper.toLoginResponse(any(), any())).thenReturn(mock(LoginResponse.class));

        service.login(new LoginCommand(EMAIL, PASSWORD, "takibo-iam", "   "));

        verify(spaceKeyResolution).resolveActiveOrganization("takibo-iam");
        verify(spaceKeyResolution, never()).resolve(any(), any());
    }

    @Test
    void orgLogin_accountWithoutOrgAuthority_stillLogsIn_withEmptyClaims() {
        // AC-11 : claims vides = login OK — c'est IAM 32 qui montrera ses spaces.
        givenResolvedActiveOrg();
        givenAccountWithCredentials();
        when(passwordHasher.matches(PASSWORD, "$2a$hash")).thenReturn(true);
        when(effectiveRbacQuery.effectiveOrgFor(ORG_ID, account.getId().getValue()))
                .thenReturn(new EffectiveRbac(List.of(), List.of(), List.of()));
        when(tokenIssuer.issue(any())).thenReturn(new LoginToken("jwt", "Bearer", 300));
        when(authMapper.toLoginResponse(any(), any())).thenReturn(mock(LoginResponse.class));

        service.login(orgCommand());

        ArgumentCaptor<HumanTokenRequest> captor = ArgumentCaptor.forClass(HumanTokenRequest.class);
        verify(tokenIssuer).issue(captor.capture());
        assertThat(captor.getValue().roles()).isEmpty();
        assertThat(captor.getValue().groups()).isEmpty();
        assertThat(captor.getValue().permissions()).isEmpty();
    }

    @Test
    void orgLogin_unknownOrganization_failsUniformly_withoutTouchingAccounts() {
        when(spaceKeyResolution.resolveActiveOrganization("takibo-iam"))
                .thenThrow(new OrganizationNotFoundException("Organization not found: takibo-iam"));

        assertThatThrownBy(() -> service.login(orgCommand()))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage(UNIFORM_MESSAGE);

        verifyNoInteractions(accountRepository, accountCredentialsRepository, passwordHasher, tokenIssuer);
    }

    @Test
    void orgLogin_inactiveOrganization_failsUniformly() {
        when(spaceKeyResolution.resolveActiveOrganization("takibo-iam"))
                .thenThrow(new OrganizationNotActiveException("Organization not active: takibo-iam"));

        assertThatThrownBy(() -> service.login(orgCommand()))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage(UNIFORM_MESSAGE);

        verify(tokenIssuer, never()).issue(any());
    }
}
