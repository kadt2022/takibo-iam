package com.takibo.iamboot.tas;

import com.takibo.securitymanagement.domain.model.Action;
import com.takibo.securitymanagement.domain.model.Environment;
import com.takibo.securitymanagement.domain.model.PolicyDecision;
import com.takibo.securitymanagement.domain.model.Resource;
import com.takibo.securitymanagement.domain.model.Subject;
import com.takibo.securitymanagement.domain.service.PolicyEvaluator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-TMS-07 — toute route servie par l'ADP est décidée par une politique explicite.
 * <p>
 * L'inventaire n'est pas écrit à la main : il est lu dans les mappings MVC de l'application
 * démarrée. Une nouvelle route qu'aucune politique ne gouverne fait échouer ce test, au lieu de
 * retomber silencieusement sur une autorisation par défaut. Seules les routes déclarées publiques
 * dans {@code SecurityConfig} échappent à l'ADP, et la liste en est fermée.
 */
@SpringBootTest(properties = {"management.health.mail.enabled=false", "security.password-encoder.bcrypt-strength=4"})
@ActiveProfiles("test")
@EnabledIf("dockerIsAvailable")
class ExplicitPolicyRouteInventoryPostgresIntegrationTest extends TasPostgresBaseline {

    /** Routes qui n'atteignent pas l'ADP : déclarées publiques dans SecurityConfig. */
    private static final Set<String> PUBLIC_ROUTES = Set.of("POST /api/v1/auth/login");
    /** Une route mappée décidée par défaut, dans un sens ou dans l'autre, n'est gouvernée par personne. */
    private static final Set<String> DEFAULT_DECISIONS = Set.of("POL_DEFAULT_ALLOW", "POL_DEFAULT_DENY");
    private static final Pattern UUID_ORG_ROUTE = Pattern.compile("^/api/v1/orgs/([0-9a-f-]{36})(/.*)?$");
    private static final String ORG = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String SPACE = "bbbbbbbb-0000-0000-0000-000000000002";

    @Autowired private ApplicationContext context;
    @Autowired private PolicyEvaluator policyEvaluator;

    @Test
    void every_mapped_route_is_decided_by_a_named_policy_never_by_default() {
        List<String> undecided = new ArrayList<>();
        for (String route : adpRoutes()) {
            String method = route.substring(0, route.indexOf(' '));
            String path = concrete(route.substring(route.indexOf(' ') + 1));
            for (Subject subject : List.of(spaceHuman(), organizationHuman(), service())) {
                PolicyDecision decision = policyEvaluator.evaluate(subject, resource(path), Action.fromHttpMethod(method),
                        new Environment(Instant.now(), "127.0.0.1", 0));
                if (DEFAULT_DECISIONS.contains(decision.getPolicyId())) {
                    undecided.add(route + " [" + subject.subjectType() + "/" + subject.scopeLevel() + "]");
                }
            }
        }

        assertThat(undecided).as("routes décidées par défaut, sans politique explicite").isEmpty();
    }

    @Test
    void the_inventory_really_covers_the_production_surface() {
        assertThat(adpRoutes()).as("inventaire lu à l'exécution")
                .anyMatch(r -> r.startsWith("GET /api/v1/orgs/{orgCode}/spaces/{spaceCode}/users"))
                .anyMatch(r -> r.startsWith("POST /api/v1/orgs/{orgId}/spaces/{spaceId}/clients"))
                .anyMatch(r -> r.startsWith("GET /api/v1/me/spaces"))
                .hasSizeGreaterThanOrEqualTo(28);
    }

    @Test
    void no_debug_endpoint_is_mapped() {
        assertThat(mappedPatterns()).as("aucune surface HTTP de diagnostic (SEC-TMS-07, D1)")
                .noneMatch(pattern -> pattern.startsWith("/debug"));
    }

    // ---------- Outils ----------

    private List<String> mappedPatterns() {
        List<String> patterns = new ArrayList<>();
        context.getBeansOfType(RequestMappingHandlerMapping.class).values().forEach(mapping ->
                mapping.getHandlerMethods().keySet().forEach(info -> patterns.addAll(info.getPatternValues())));
        return patterns;
    }

    private List<String> adpRoutes() {
        List<String> routes = new ArrayList<>();
        context.getBeansOfType(RequestMappingHandlerMapping.class).values().forEach(mapping ->
                mapping.getHandlerMethods().keySet().forEach(info -> info.getPatternValues().forEach(pattern -> {
                    if (!pattern.startsWith("/api/v1/") && !pattern.startsWith("/debug/")) {
                        return;
                    }
                    var methods = info.getMethodsCondition().getMethods();
                    (methods.isEmpty() ? Set.of(org.springframework.web.bind.annotation.RequestMethod.GET) : methods)
                            .forEach(m -> {
                                String route = m.name() + " " + pattern;
                                if (!PUBLIC_ROUTES.contains(route)) {
                                    routes.add(route);
                                }
                            });
                })));
        return routes;
    }

    private static String concrete(String pattern) {
        return pattern.replace("{orgId}", ORG).replace("{spaceId}", SPACE)
                .replace("{orgCode}", "takibo-iam").replace("{spaceCode}", "finance")
                .replaceAll("\\{[a-zA-Z]+}", UUID.randomUUID().toString());
    }

    /** Même résolution que l'ADP : seule une route /api/v1/orgs/{uuid}/... désigne une organisation. */
    private static Resource resource(String path) {
        var matcher = UUID_ORG_ROUTE.matcher(path);
        return new Resource(path, matcher.matches() ? matcher.group(1) : null, null);
    }

    private static Subject spaceHuman() {
        return new Subject("actor", Set.of(), Set.of(), ORG, SPACE, "HUMAN", "SPACE", "cccccccc-0000-0000-0000-000000000003");
    }

    private static Subject organizationHuman() {
        return new Subject("actor", Set.of(), Set.of(), ORG, null, "HUMAN", "ORGANIZATION", "cccccccc-0000-0000-0000-000000000003");
    }

    private static Subject service() {
        return new Subject("client", Set.of(), Set.of(), ORG, SPACE, "SERVICE", "SPACE", null);
    }
}
