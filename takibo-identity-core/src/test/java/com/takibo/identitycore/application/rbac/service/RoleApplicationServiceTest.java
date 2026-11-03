package com.takibo.identitycore.application.rbac.service;

import com.takibo.identitycore.domain.exception.ReservedTenantRoleCodeException;
import com.takibo.identitycore.domain.exception.SpaceNotActiveException;
import com.takibo.identitycore.domain.model.Role;
import com.takibo.identitycore.domain.model.RoleNature;
import com.takibo.identitycore.domain.repository.RoleRepository;
import com.takibo.identitycore.domain.vo.RoleId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.integration.space.port.SpaceStatusCheckerCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleApplicationServiceTest {

    private static final UUID SPACE_ID =
            UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID ROLE_UUID =
            UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final SpaceId SPACE = SpaceId.of(SPACE_ID);

    @Mock private RoleRepository roleRepository;
    @Mock private SpaceStatusCheckerCase spaceStatusCheckerCase;

    @InjectMocks
    private RoleApplicationService service;

    private static Role existingRole(String code) {
        Instant now = Instant.now();
        return Role.builder()
                .id(RoleId.of(ROLE_UUID)).spaceId(SPACE)
                .code(code).name(code).nature(RoleNature.BUSINESS)
                .createdAt(now).updatedAt(now).version(0L)
                .build();
    }

    // ─────────────────────────── ensure ───────────────────────────

    @Test
    void ensure_reservedCodeFailsBeforeExistingRoleLookup() {
        SpaceId spaceId = SpaceId.of(SPACE_ID);

        assertThatThrownBy(() ->
                service.ensure(spaceId, "R_ORG_CUSTOM", "Custom", null))
                .isInstanceOf(ReservedTenantRoleCodeException.class);

        verifyNoInteractions(roleRepository, spaceStatusCheckerCase);
    }

    @Test
    void ensure_inactiveSpace_isRejectedBeforeAnyLookup() {
        doThrow(new SpaceNotActiveException(SPACE_ID))
                .when(spaceStatusCheckerCase).assertSpaceExistsAndActive(SPACE_ID);

        assertThatThrownBy(() -> service.ensure(SPACE, "B_APPROVER", "Approver", null))
                .isInstanceOf(SpaceNotActiveException.class);

        verifyNoInteractions(roleRepository);
    }

    @Test
    void ensure_existingRole_isIdempotent() {
        when(roleRepository.findBySpaceIdAndCode(SPACE, "B_APPROVER"))
                .thenReturn(Optional.of(existingRole("B_APPROVER")));

        service.ensure(SPACE, "B_APPROVER", "Approver", null);

        verify(roleRepository, never()).save(any());
    }

    @Test
    void ensure_newRole_isCreatedAsBusinessRoleOfTheSpace() {
        when(roleRepository.findBySpaceIdAndCode(SPACE, "B_APPROVER")).thenReturn(Optional.empty());

        service.ensure(SPACE, "B_APPROVER", "Approver", "Approves invoices");

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(captor.capture());
        Role saved = captor.getValue();
        assertThat(saved.getNature()).isEqualTo(RoleNature.BUSINESS);
        assertThat(saved.getSpaceId()).isEqualTo(SPACE);
        assertThat(saved.getCode()).isEqualTo("B_APPROVER");
        assertThat(saved.getName()).isEqualTo("Approver");
        assertThat(saved.getDescription()).isEqualTo("Approves invoices");
        verify(spaceStatusCheckerCase).assertSpaceExistsAndActive(SPACE_ID);
    }

    @Test
    void ensure_concurrentInsertOfSameRole_isTreatedAsIdempotent() {
        when(roleRepository.findBySpaceIdAndCode(SPACE, "B_APPROVER"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingRole("B_APPROVER")));
        when(roleRepository.save(any())).thenThrow(new DataIntegrityViolationException("uk_role_space_code"));

        assertThatCode(() -> service.ensure(SPACE, "B_APPROVER", "Approver", null))
                .doesNotThrowAnyException();
    }

    @Test
    void ensure_integrityViolationWithoutConcurrentRole_isPropagated() {
        when(roleRepository.findBySpaceIdAndCode(SPACE, "B_APPROVER")).thenReturn(Optional.empty());
        when(roleRepository.save(any())).thenThrow(new DataIntegrityViolationException("fk_space"));

        assertThatThrownBy(() -> service.ensure(SPACE, "B_APPROVER", "Approver", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ─────────────────────────── ensureRole ───────────────────────────

    @Test
    void ensureRole_reservedCodeFailsBeforeExistingRoleLookup() {
        assertThatThrownBy(() ->
                service.ensureRole(SPACE_ID, "PLATFORM_ADMIN", "Admin", null))
                .isInstanceOf(ReservedTenantRoleCodeException.class);

        verifyNoInteractions(roleRepository, spaceStatusCheckerCase);
    }

    @Test
    void ensureRole_existingRole_returnsItsIdWithoutSaving() {
        when(roleRepository.findIdBySpaceIdAndCode(SPACE, "B_APPROVER")).thenReturn(Optional.of(ROLE_UUID));

        assertThat(service.ensureRole(SPACE_ID, "B_APPROVER", "Approver", null)).isEqualTo(ROLE_UUID);

        verify(roleRepository, never()).save(any());
    }

    @Test
    void ensureRole_newRoleWithoutName_usesCodeAsName_andReturnsPersistedId() {
        when(roleRepository.findIdBySpaceIdAndCode(SPACE, "B_APPROVER")).thenReturn(Optional.empty());
        when(roleRepository.save(any())).thenReturn(existingRole("B_APPROVER"));

        UUID id = service.ensureRole(SPACE_ID, "B_APPROVER", null, "desc");

        assertThat(id).isEqualTo(ROLE_UUID);
        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("B_APPROVER");
        assertThat(captor.getValue().getNature()).isEqualTo(RoleNature.BUSINESS);
        assertThat(captor.getValue().getSpaceId()).isEqualTo(SPACE);
    }
}
