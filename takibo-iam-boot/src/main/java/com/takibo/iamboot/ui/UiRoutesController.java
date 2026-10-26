package com.takibo.iamboot.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Rafraîchissement direct d'une route de l'application monopage : la route est rendue par
 * {@code index.html}, le routeur du navigateur fait le reste. Les routes sont listées
 * explicitement (pas de {@code /**}) pour ne jamais capter {@code /api/**} ni {@code /actuator/**}.
 */
@Controller
class UiRoutesController {

    @GetMapping({"/login", "/app", "/app/**"})
    String index() {
        return "forward:/index.html";
    }
}
