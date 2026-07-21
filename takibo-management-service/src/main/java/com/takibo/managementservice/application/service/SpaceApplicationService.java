package com.takibo.managementservice.application.service;

import com.takibo.managementservice.application.command.CreateSpaceCommand;
import com.takibo.managementservice.application.command.CreateSpaceResult;
import com.takibo.managementservice.domain.model.SpaceRegistrationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
@RequiredArgsConstructor
public class SpaceApplicationService {

    private final SpaceRegistrationOrchestrator spaceRegistrationOrchestrator;

    @Transactional
    public CreateSpaceResult createSpace(CreateSpaceCommand cmd) {
        Assert.notNull(cmd, "CreateSpaceCommand must not be null");

        SpaceRegistrationResult result = spaceRegistrationOrchestrator.registerSpace(cmd);
        return CreateSpaceResult.from(result.space());
    }
}
