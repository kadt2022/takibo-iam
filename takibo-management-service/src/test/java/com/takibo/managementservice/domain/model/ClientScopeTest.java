package com.takibo.managementservice.domain.model;

import com.takibo.managementservice.domain.exception.InvalidScopeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Un scope enregistré doit être un {@code scope-token} de la RFC 6749 §3.3 :
 * {@code 1*( %x21 / %x23-5B / %x5D-7E )}. C'est la règle que Spring Authorization Server
 * applique à la construction du client : la refuser ici évite d'enregistrer un client
 * qui ne pourrait jamais être chargé.
 */
class ClientScopeTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "api read",      // espace : séparateur de scopes dans les tokens
            "api\"read",     // %x22
            "api\\read",     // %x5C
            "api\tread",     // contrôle
            "api\nread",
            "api\u0000read",
            "api\u007Fread", // DEL
            "café",          // hors ASCII
            "api read"  // espace insécable
    })
    void a_scope_outside_the_rfc_6749_charset_is_refused(String scope) {
        Set<String> scopes = Set.of("openid", scope);

        assertThatThrownBy(() -> ClientScope.ofAll(scopes))
                .isInstanceOf(InvalidScopeException.class);
    }

    @Test
    void every_rfc_6749_scope_character_is_accepted() {
        String allAllowed = "!#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_`"
                + "abcdefghijklmnopqrstuvwxyz{|}~";

        assertThat(ClientScope.ofAll(Set.of(allAllowed, "api:read", "openid")))
                .extracting(ClientScope::getValue)
                .containsExactlyInAnyOrder(allAllowed, "api:read", "openid");
    }
}
