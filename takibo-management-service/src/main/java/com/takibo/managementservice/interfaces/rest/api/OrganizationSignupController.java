package com.takibo.managementservice.interfaces.rest.api;

import com.takibo.audit.annotations.Audit;
import com.takibo.audit.annotations.LogAction;
import com.takibo.audit.annotations.TriggerAlertOnFailure;
import com.takibo.audit.domain.AuditType;
import com.takibo.identitycore.domain.exception.UserNotFoundException;
import com.takibo.managementservice.application.service.OrganizationSignupService;
import com.takibo.managementservice.interfaces.rest.request.OrganizationSignupRequest;
import com.takibo.managementservice.interfaces.rest.response.OrganizationSignupResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/orgs")
@RequiredArgsConstructor
public class OrganizationSignupController {

    private final OrganizationSignupService service;

    @LogAction("Registration of a new user")
    @Audit(
            type = AuditType.CREATE,
            entityType = "USER",
            entityIdParam = "#result.body.userId"

    )
    @TriggerAlertOnFailure(
            triggerOn = {UserNotFoundException.class, IllegalArgumentException.class },
            threshold = 3,
            auditType = AuditType.MEDIUM
    )
    @PostMapping("/signup")
    public ResponseEntity<OrganizationSignupResponse> signup(@Valid @RequestBody OrganizationSignupRequest req) {
        OrganizationSignupResponse signupResponse = service.signup(req);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/orgs/{id}")
                .buildAndExpand(signupResponse.organizationId())
                .toUri();
        return ResponseEntity.created(location).body(signupResponse);
    }
}
