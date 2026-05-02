package com.takibo.managementservice.domain.service;

import com.takibo.managementservice.application.command.CreateSpaceCommand;
import com.takibo.managementservice.application.service.SpaceCodeGenerator;
import com.takibo.managementservice.domain.model.OrganizationContext;
import com.takibo.managementservice.domain.model.Space;
import com.takibo.managementservice.domain.vo.SpaceId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class SpaceCreationDomainService {

    private final Clock clock;
    private final SpaceCodeGenerator codeGenerator;

    public String initialCode(CreateSpaceCommand cmd) {
        return codeGenerator.normalizeOrGenerate(cmd.code(), cmd.name());
    }

    public String nextCodeCandidate(String current) {
        return codeGenerator.nextCandidate(current);
    }

    public Space createSpace(CreateSpaceCommand cmd,
                             OrganizationContext ctx,
                             String finalCode,
                             SpaceId spaceId) {

        return Space.createNew(
                spaceId,
                ctx.orgId(),
                cmd.ownerAccountId(),   // nouveau champ
                finalCode,
                cmd.name(),
                cmd.description(),
                clock.instant()
        );
    }
}
