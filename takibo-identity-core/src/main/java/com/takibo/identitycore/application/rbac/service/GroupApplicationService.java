package com.takibo.identitycore.application.rbac.service;

import com.takibo.identitycore.integration.space.port.SpaceStatusCheckerCase;
import com.takibo.identitycore.domain.model.Group;
import com.takibo.identitycore.domain.model.GroupNature;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;


@Service
@RequiredArgsConstructor
public class GroupApplicationService {
    private final GroupRepository groupRepository;
    private final SpaceStatusCheckerCase spaceStatusCheckerCase;

    @Transactional
    public void ensure(SpaceId spaceId, String code, String name, String description) {
        spaceStatusCheckerCase.assertSpaceExistsAndActive(spaceId.getValue());
        if (groupRepository.findBySpaceIdAndCode(spaceId, code).isPresent()) {
            return;
        }

        Group group = Group.createNew(spaceId, code, name, description, GroupNature.GOVERNANCE);

        try {
            groupRepository.save(group);
        } catch (DataIntegrityViolationException e) {
            if (groupRepository.findBySpaceIdAndCode(spaceId, code).isEmpty()) throw e;
        }
    }

    public UUID ensureGroup(UUID spaceId, String code, String name, String desc) {
        var existingId = groupRepository.findIdBySpaceIdAndCode(SpaceId.of(spaceId), code);
        if (existingId.isPresent()) return existingId.get().value();

        Group group = Group.createNew(
                SpaceId.of(spaceId),
                code,
                name != null ? name : code,
                desc,
                GroupNature.GOVERNANCE
        );

        Group saved = groupRepository.save(group);
        return saved.getId().getValue();
    }
}
