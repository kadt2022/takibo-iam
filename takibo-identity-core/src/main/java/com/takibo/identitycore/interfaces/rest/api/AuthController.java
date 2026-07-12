package com.takibo.identitycore.interfaces.rest.api;

import com.takibo.audit.annotations.LogAction;
import com.takibo.identitycore.application.auth.mapper.AuthMapper;
import com.takibo.identitycore.application.auth.port.HumanLoginCase;
import com.takibo.identitycore.interfaces.rest.request.LoginRequest;
import com.takibo.identitycore.interfaces.rest.response.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Surface de login humain.
 * <p>
 * Route publique (permitAll côté security-management) : l'appelant ne possède encore
 * aucun token. La vérification d'identité est entièrement déléguée à
 * {@link HumanLoginCase} ; la signature du token appartient à TAS via le port
 * {@code HumanAccessTokenIssuer}.
 * <p>
 * IAM 31 : {@code orgCode + email + password} → token ORGANIZATION ; avec
 * {@code spaceCode} (transitoire) → token SPACE. Toute cause d'échec est indistincte
 * de l'extérieur : 401 uniforme, la cause réelle vit dans l'audit.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final HumanLoginCase humanLoginCase;
    private final AuthMapper authMapper;

    @PostMapping("/login")
    @LogAction("Human login (password, org- or space-scoped token)")
    @Operation(summary = "Authenticate a human with orgCode/email/password"
            + " (spaceCode optional and transitional: present -> SPACE token)")
    @ApiResponse(
            responseCode = "200",
            description = "Human token issued (ORGANIZATION scope, or SPACE when spaceCode is provided)",
            content = @Content(schema = @Schema(implementation = LoginResponse.class))
    )
    @ApiResponse(responseCode = "401",
            description = "Authentication failed — single undifferentiated response for every cause")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = humanLoginCase.login(authMapper.toCommand(request));
        return ResponseEntity.ok(response);
    }
}
