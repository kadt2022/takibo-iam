package com.takibo.identitycore.application.auth.service;

import com.takibo.identitycore.application.auth.model.AuthenticationMethod;
import com.takibo.identitycore.application.auth.model.HumanCredentials;
import com.takibo.identitycore.application.auth.model.TrustedOrganizationContext;
import com.takibo.identitycore.application.auth.model.VerifiedHuman;
import com.takibo.identitycore.domain.exception.AuthenticationFailedException;
import com.takibo.identitycore.domain.model.Account;
import com.takibo.identitycore.domain.model.AccountCredentials;
import com.takibo.identitycore.domain.model.EmailAddress;
import com.takibo.identitycore.domain.model.FailedAttemptReservation;
import com.takibo.identitycore.domain.model.TakiboIdentity;
import com.takibo.identitycore.domain.repository.AccountCredentialsRepository;
import com.takibo.identitycore.domain.repository.AccountRepository;
import com.takibo.identitycore.domain.repository.TakiboIdentityRepository;
import com.takibo.identitycore.domain.security.port.PasswordHasherCase;
import com.takibo.identitycore.domain.vo.OrganizationId;
import com.takibo.identitycore.domain.vo.PasswordHash;
import com.takibo.identitycore.domain.vo.TakiboIdentityId;
import com.takibo.identitycore.integration.space.port.ResolvedOrgKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * TAS-GRANTS-03-A : le cœur unique d'authentification humaine. Il authentifie un Account
 * dans une Organization déjà résolue, et seulement dans celle-ci ; il ne signe rien et ne
 * calcule aucun pouvoir. Toutes les causes d'échec convergent vers la même réponse externe.
 */
@ExtendWith(MockitoExtension.class)
class HumanAuthenticationServiceTest {

    private static final UUID ORG_A = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000a");
    private static final UUID ORG_B = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000b");
    private static final String EMAIL = "pi001@email.com";
    private static final String PASSWORD_A = "Passw0rd!OfA";
    private static final String HASH_A = "$2a$hash-of-a";
    private static final String HASH_B = "$2a$hash-of-b";
    private static final String DUMMY_HASH = "$2a$dummy-timing-hash";
    private static final FailedAttemptReservation RESERVED = new FailedAttemptReservation(1, false);
    private static final String UNIFORM_MESSAGE = "Impossible de valider cette connexion.";
    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");

    @Mock private AccountRepository accountRepository;
    @Mock private AccountCredentialsRepository accountCredentialsRepository;
    @Mock private PasswordHasherCase passwordHasher;
    @Mock private TakiboIdentityRepository takiboIdentityRepository;

    private HumanAuthenticationService service;
    private Account accountA;
    private Account accountB;

    @BeforeEach
    void setUp() {
        service = new HumanAuthenticationService(accountRepository, accountCredentialsRepository, passwordHasher,
                takiboIdentityRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "maxFailedAttempts", 5);
        ReflectionTestUtils.setField(service, "lockSeconds", 900L);
        ReflectionTestUtils.setField(service, "dummyPasswordHash", DUMMY_HASH);

        accountA = Account.create(new EmailAddress(EMAIL), null, null, Map.of()).withOrgId(ORG_A);
        accountB = Account.create(new EmailAddress(EMAIL), null, null, Map.of()).withOrgId(ORG_B);
    }

    // ---------- Succès ----------

    @Test
    void valid_credentials_in_the_trusted_organization_yield_a_verified_human_situated_in_it() {
        givenAccount(ORG_A, accountA, credentials(accountA, HASH_A));
        givenIdentity(ORG_A, accountA, active(ORG_A, accountA));
        when(passwordHasher.matches(PASSWORD_A, HASH_A)).thenReturn(true);

        VerifiedHuman verified = service.authenticate(contextOf(ORG_A), new HumanCredentials(EMAIL, PASSWORD_A));

        assertThat(verified.orgId()).isEqualTo(ORG_A);
        assertThat(verified.accountId()).isEqualTo(accountA.getId().getValue());
        assertThat(verified.method()).isEqualTo(AuthenticationMethod.PASSWORD);
        assertThat(verified.authenticatedAt()).isEqualTo(NOW);
        verify(accountCredentialsRepository).clearFailedAttempts(new OrganizationId(ORG_A), accountA.getId(), NOW);
        verify(accountCredentialsRepository, never()).save(any(), any());
    }

    @Test
    void a_success_after_previous_failures_resets_the_failure_counter_of_this_organization() {
        givenAccount(ORG_A, accountA, credentials(accountA, HASH_A).toBuilder().failedAttempts(2).build());
        givenIdentity(ORG_A, accountA, active(ORG_A, accountA));
        when(passwordHasher.matches(PASSWORD_A, HASH_A)).thenReturn(true);

        service.authenticate(contextOf(ORG_A), new HumanCredentials(EMAIL, PASSWORD_A));

        verify(accountCredentialsRepository).clearFailedAttempts(new OrganizationId(ORG_A), accountA.getId(), NOW);
        verify(accountCredentialsRepository, never()).cancelFailedAttempt(any(), any(), any(), anyInt(), any());
    }

    // ---------- Échecs uniformes ----------

    @Test
    void a_bad_password_counts_a_failure_in_this_organization_and_fails_uniformly() {
        givenAccount(ORG_A, accountA, credentials(accountA, HASH_A));
        when(passwordHasher.matches("wrong", HASH_A)).thenReturn(false);

        assertUniformFailure(() -> service.authenticate(contextOf(ORG_A), new HumanCredentials(EMAIL, "wrong")));

        // SEC-HUNT-02 : la tentative a été comptée avant la comparaison, et n'est ni annulée ni soldée.
        verify(accountCredentialsRepository).reserveFailedAttempt(eq(new OrganizationId(ORG_A)), eq(accountA.getId()),
                eq(5), eq(NOW.plusSeconds(900)), eq(NOW));
        verify(accountCredentialsRepository, never()).cancelFailedAttempt(any(), any(), any(), anyInt(), any());
        verify(accountCredentialsRepository, never()).clearFailedAttempts(any(), any(), any());
        verify(accountCredentialsRepository, never()).save(any(), any());
    }

    @Test
    void an_unknown_account_in_the_organization_pays_the_hash_cost_and_fails_uniformly() {
        when(accountRepository.findByEmail(new OrganizationId(ORG_A), new EmailAddress(EMAIL)))
                .thenReturn(Optional.empty());

        assertUniformFailure(() -> service.authenticate(contextOf(ORG_A), new HumanCredentials(EMAIL, PASSWORD_A)));

        verify(passwordHasher).matches(PASSWORD_A, DUMMY_HASH);
    }

    @Test
    void missing_credentials_pay_the_hash_cost_and_fail_uniformly() {
        when(accountRepository.findByEmail(new OrganizationId(ORG_A), new EmailAddress(EMAIL)))
                .thenReturn(Optional.of(accountA));
        when(accountCredentialsRepository.find(new OrganizationId(ORG_A), accountA.getId()))
                .thenReturn(Optional.empty());

        assertUniformFailure(() -> service.authenticate(contextOf(ORG_A), new HumanCredentials(EMAIL, PASSWORD_A)));

        verify(passwordHasher).matches(PASSWORD_A, DUMMY_HASH);
    }

    @Test
    void a_locked_account_fails_uniformly_without_checking_the_password() {
        givenAccount(ORG_A, accountA, credentials(accountA, HASH_A).toBuilder()
                .failedAttempts(5).lockedUntil(Instant.now().plusSeconds(600)).build());

        assertUniformFailure(() -> service.authenticate(contextOf(ORG_A), new HumanCredentials(EMAIL, PASSWORD_A)));

        verify(passwordHasher, never()).matches(any(), any());
        verify(accountCredentialsRepository, never()).reserveFailedAttempt(any(), any(), anyInt(), any(), any());
    }

    @Test
    void an_account_locked_by_a_concurrent_attempt_fails_uniformly_without_checking_the_password() {
        givenAccount(ORG_A, accountA, credentials(accountA, HASH_A).toBuilder().failedAttempts(4).build());
        when(accountCredentialsRepository.reserveFailedAttempt(any(), any(), anyInt(), any(), any()))
                .thenReturn(Optional.empty());

        assertUniformFailure(() -> service.authenticate(contextOf(ORG_A), new HumanCredentials(EMAIL, PASSWORD_A)));

        verify(passwordHasher, never()).matches(any(), any());
    }

    @Test
    void without_a_lock_duration_no_lock_is_ever_requested() {
        ReflectionTestUtils.setField(service, "lockSeconds", 0L);
        givenAccount(ORG_A, accountA, credentials(accountA, HASH_A));
        when(passwordHasher.matches("wrong", HASH_A)).thenReturn(false);

        assertUniformFailure(() -> service.authenticate(contextOf(ORG_A), new HumanCredentials(EMAIL, "wrong")));

        verify(accountCredentialsRepository).reserveFailedAttempt(eq(new OrganizationId(ORG_A)), eq(accountA.getId()),
                eq(5), isNull(), eq(NOW));
    }

    @Test
    void a_failure_without_a_trusted_context_pays_the_same_hash_cost() {
        service.equalizeFailedAttempt(new HumanCredentials(EMAIL, PASSWORD_A));

        verify(passwordHasher).matches(PASSWORD_A, DUMMY_HASH);
        verifyNoMoreInteractions(passwordHasher);
    }

    // ---------- Frontière Organization ----------

    @Test
    void the_account_is_only_ever_searched_in_the_trusted_organization() {
        givenAccount(ORG_A, accountA, credentials(accountA, HASH_A));
        givenIdentity(ORG_A, accountA, active(ORG_A, accountA));
        when(passwordHasher.matches(PASSWORD_A, HASH_A)).thenReturn(true);

        service.authenticate(contextOf(ORG_A), new HumanCredentials(EMAIL, PASSWORD_A));

        verify(accountRepository).findByEmail(new OrganizationId(ORG_A), new EmailAddress(EMAIL));
        verify(accountCredentialsRepository).find(new OrganizationId(ORG_A), accountA.getId());
        verifyNoMoreInteractions(accountRepository);
    }

    @Test
    void the_password_of_account_a_never_authenticates_account_b_with_the_same_email() {
        givenAccount(ORG_B, accountB, credentials(accountB, HASH_B));
        when(passwordHasher.matches(PASSWORD_A, HASH_B)).thenReturn(false);

        assertUniformFailure(() -> service.authenticate(contextOf(ORG_B), new HumanCredentials(EMAIL, PASSWORD_A)));

        verify(accountRepository).findByEmail(new OrganizationId(ORG_B), new EmailAddress(EMAIL));
        verifyNoMoreInteractions(accountRepository);
        verify(passwordHasher, never()).matches(PASSWORD_A, HASH_A);
    }

    @Test
    void the_same_email_authenticates_each_account_only_in_its_own_organization() {
        givenAccount(ORG_A, accountA, credentials(accountA, HASH_A));
        givenAccount(ORG_B, accountB, credentials(accountB, HASH_B));
        givenIdentity(ORG_A, accountA, active(ORG_A, accountA));
        givenIdentity(ORG_B, accountB, active(ORG_B, accountB));
        when(passwordHasher.matches(PASSWORD_A, HASH_A)).thenReturn(true);
        when(passwordHasher.matches("Passw0rd!OfB", HASH_B)).thenReturn(true);

        VerifiedHuman inA = service.authenticate(contextOf(ORG_A), new HumanCredentials(EMAIL, PASSWORD_A));
        VerifiedHuman inB = service.authenticate(contextOf(ORG_B), new HumanCredentials(EMAIL, "Passw0rd!OfB"));

        assertThat(inA.orgId()).isEqualTo(ORG_A);
        assertThat(inA.accountId()).isEqualTo(accountA.getId().getValue());
        assertThat(inB.orgId()).isEqualTo(ORG_B);
        assertThat(inB.accountId()).isEqualTo(accountB.getId().getValue());
        assertThat(inA.accountId()).isNotEqualTo(inB.accountId());
    }

    // ---------- IAM-REVOKE-01 : identité révoquée ou absente ----------

    @Test
    void a_suspended_identity_with_the_right_password_fails_uniformly_and_leaves_the_counter_untouched() {
        givenAccount(ORG_A, accountA, credentials(accountA, HASH_A).toBuilder().failedAttempts(2).build());
        givenIdentity(ORG_A, accountA, active(ORG_A, accountA).suspend());
        when(passwordHasher.matches(PASSWORD_A, HASH_A)).thenReturn(true);

        assertUniformFailure(() -> service.authenticate(contextOf(ORG_A), new HumanCredentials(EMAIL, PASSWORD_A)));

        verify(passwordHasher).matches(PASSWORD_A, HASH_A);
        verify(accountCredentialsRepository).cancelFailedAttempt(new OrganizationId(ORG_A), accountA.getId(), RESERVED, 5, NOW);
        verify(accountCredentialsRepository, never()).clearFailedAttempts(any(), any(), any());
        verify(accountCredentialsRepository, never()).save(any(), any());
    }

    @Test
    void a_disabled_identity_with_the_right_password_fails_uniformly() {
        givenAccount(ORG_A, accountA, credentials(accountA, HASH_A));
        givenIdentity(ORG_A, accountA, active(ORG_A, accountA).disable());
        when(passwordHasher.matches(PASSWORD_A, HASH_A)).thenReturn(true);

        assertUniformFailure(() -> service.authenticate(contextOf(ORG_A), new HumanCredentials(EMAIL, PASSWORD_A)));

        verify(passwordHasher).matches(PASSWORD_A, HASH_A);
        verify(accountCredentialsRepository).cancelFailedAttempt(new OrganizationId(ORG_A), accountA.getId(), RESERVED, 5, NOW);
        verify(accountCredentialsRepository, never()).clearFailedAttempts(any(), any(), any());
        verify(accountCredentialsRepository, never()).save(any(), any());
    }

    @Test
    void an_absent_identity_with_the_right_password_fails_uniformly() {
        givenAccount(ORG_A, accountA, credentials(accountA, HASH_A));
        when(takiboIdentityRepository.findByOrgIdAndAccountId(ORG_A, accountA.getId().getValue()))
                .thenReturn(Optional.empty());
        when(passwordHasher.matches(PASSWORD_A, HASH_A)).thenReturn(true);

        assertUniformFailure(() -> service.authenticate(contextOf(ORG_A), new HumanCredentials(EMAIL, PASSWORD_A)));

        verify(passwordHasher).matches(PASSWORD_A, HASH_A);
        verify(accountCredentialsRepository).cancelFailedAttempt(new OrganizationId(ORG_A), accountA.getId(), RESERVED, 5, NOW);
        verify(accountCredentialsRepository, never()).clearFailedAttempts(any(), any(), any());
        verify(accountCredentialsRepository, never()).save(any(), any());
    }

    @Test
    void a_wrong_password_is_still_counted_whatever_the_identity_status() {
        givenAccount(ORG_A, accountA, credentials(accountA, HASH_A));
        lenientIdentity(ORG_A, accountA, active(ORG_A, accountA).disable());
        when(passwordHasher.matches("wrong", HASH_A)).thenReturn(false);

        assertUniformFailure(() -> service.authenticate(contextOf(ORG_A), new HumanCredentials(EMAIL, "wrong")));

        // SEC-HUNT-02 : la tentative a été comptée avant la comparaison, et n'est ni annulée ni soldée.
        verify(accountCredentialsRepository).reserveFailedAttempt(eq(new OrganizationId(ORG_A)), eq(accountA.getId()),
                eq(5), eq(NOW.plusSeconds(900)), eq(NOW));
        verify(accountCredentialsRepository, never()).cancelFailedAttempt(any(), any(), any(), anyInt(), any());
        verify(accountCredentialsRepository, never()).clearFailedAttempts(any(), any(), any());
        verify(accountCredentialsRepository, never()).save(any(), any());
    }

    @Test
    void the_identity_is_only_read_in_the_trusted_organization() {
        givenAccount(ORG_A, accountA, credentials(accountA, HASH_A));
        givenIdentity(ORG_A, accountA, active(ORG_A, accountA));
        when(passwordHasher.matches(PASSWORD_A, HASH_A)).thenReturn(true);

        service.authenticate(contextOf(ORG_A), new HumanCredentials(EMAIL, PASSWORD_A));

        verify(takiboIdentityRepository).findByOrgIdAndAccountId(ORG_A, accountA.getId().getValue());
        verifyNoMoreInteractions(takiboIdentityRepository);
    }

    // ---------- Contrat des valeurs ----------

    @Test
    void credentials_never_print_the_password() {
        assertThat(new HumanCredentials(EMAIL, PASSWORD_A).toString()).doesNotContain(PASSWORD_A);
    }

    @Test
    void a_trusted_context_comes_from_a_server_side_resolution_of_the_organization() {
        assertThat(TrustedOrganizationContext.from(new ResolvedOrgKey(ORG_A, "org-a")).orgId()).isEqualTo(ORG_A);
    }

    @Test
    void the_dummy_hash_is_generated_once_at_startup_when_not_configured() {
        ReflectionTestUtils.setField(service, "dummyPasswordHash", "");
        when(passwordHasher.hash(any())).thenReturn("$2a$generated-once");

        service.initDummyPasswordHash();

        assertThat(ReflectionTestUtils.getField(service, "dummyPasswordHash")).isEqualTo("$2a$generated-once");
    }

    // ---------- Outils ----------

    private static TrustedOrganizationContext contextOf(UUID orgId) {
        return TrustedOrganizationContext.from(new ResolvedOrgKey(orgId, "org"));
    }

    private static AccountCredentials credentials(Account account, String hash) {
        return AccountCredentials.create(account.getId(), PasswordHash.of(hash, "bcrypt", 1));
    }

    private static TakiboIdentity active(UUID orgId, Account account) {
        return TakiboIdentity.createHuman(TakiboIdentityId.of(account.getId().getValue()),
                new OrganizationId(orgId), account.getId());
    }

    private void givenIdentity(UUID orgId, Account account, TakiboIdentity identity) {
        when(takiboIdentityRepository.findByOrgIdAndAccountId(orgId, account.getId().getValue()))
                .thenReturn(Optional.of(identity));
    }

    /** Identité présente, mais que le parcours testé n'a pas besoin de lire. */
    private void lenientIdentity(UUID orgId, Account account, TakiboIdentity identity) {
        org.mockito.Mockito.lenient().when(takiboIdentityRepository.findByOrgIdAndAccountId(orgId, account.getId().getValue()))
                .thenReturn(Optional.of(identity));
    }

    private void givenAccount(UUID orgId, Account account, AccountCredentials credentials) {
        when(accountRepository.findByEmail(new OrganizationId(orgId), new EmailAddress(EMAIL)))
                .thenReturn(Optional.of(account));
        when(accountCredentialsRepository.find(new OrganizationId(orgId), account.getId()))
                .thenReturn(Optional.of(credentials));
        lenient().when(accountCredentialsRepository.reserveFailedAttempt(
                        eq(new OrganizationId(orgId)), eq(account.getId()), anyInt(), any(), any()))
                .thenReturn(Optional.of(RESERVED));
    }

    private static void assertUniformFailure(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(AuthenticationFailedException.class).hasMessage(UNIFORM_MESSAGE);
    }
}
