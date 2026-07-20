package com.takibo.managementservice.application.service;

import com.takibo.managementservice.application.command.CreateSpaceCommand;
import com.takibo.managementservice.domain.mapper.SpaceMapper;
import com.takibo.managementservice.domain.model.SpaceRegistrationResult;
import com.takibo.managementservice.interfaces.rest.response.SpaceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
@RequiredArgsConstructor
public class SpaceApplicationService {

    // DDD mode (Controller -> Command -> Response)
    private final SpaceRegistrationOrchestrator spaceRegistrationOrchestrator;

    private final SpaceMapper spaceMapper;

//    @Transactional
//    public CreateSpaceResult createSpace(UUID orgId, String name, String codeInput, String description) {
//        CreateSpaceCommand cmd = CreateSpaceCommand.builder()
//                .orgId(orgId)
//                .name(name)
//                .code(codeInput)
//                .description(description)
//                .ownerAccountId(null)
//                .source(ActorSource.SYSTEM)
//                .build();
//
//        SpaceRegistrationResult result = spaceRegistrationOrchestrator.registerSpace(cmd);
//        Space saved = result.space();
//
//        return new CreateSpaceResult(saved.getId().value(), saved.getCode(), saved.getName());
//    }

    @Transactional
    public SpaceResponse createSpace(CreateSpaceCommand cmd) {
        Assert.notNull(cmd, "CreateSpaceCommand must not be null");

        SpaceRegistrationResult result = spaceRegistrationOrchestrator.registerSpace(cmd);
        return spaceMapper.toSpaceResponse(result.space());
    }
}
