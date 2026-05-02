// tis-core/application/service/RoleApplicationService.java
package com.takibo.identitycore.application.rbac.service;

import com.takibo.identitycore.integration.space.port.SpaceStatusCheckerCase;
import com.takibo.identitycore.domain.model.Role;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleApplicationService {

    private final RoleRepository roleRepository;
    private final SpaceStatusCheckerCase spaceStatusCheckerCase;

    @Transactional
    public void ensure(SpaceId spaceId, String code, String name, String description) {
       // spaceStatusPort.assertActive(spaceId.getValue());
        if (roleRepository.findBySpaceIdAndCode(spaceId, code).isPresent()) {
            return;
        }

        Role role = Role.createNew(spaceId, code, name, description);

        try {
            roleRepository.save(role);
        } catch (DataIntegrityViolationException e) {
            if (roleRepository.findBySpaceIdAndCode(spaceId, code).isEmpty()) throw e;
        }
    }

    public UUID ensureRole(UUID spaceId, String code, String name, String desc) {
        Optional<UUID> existingId = roleRepository.findIdBySpaceIdAndCode(SpaceId.of(spaceId), code);
        if (existingId.isPresent()) return existingId.get();

        Role role = Role.createNew(
                SpaceId.of(spaceId),
                code,
                (name != null ? name : code),
                desc
        );
        Role saved = roleRepository.save(role);
        return saved.getId().value();
    }
}
