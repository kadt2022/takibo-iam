package com.takibo.securitymanagement.infrastructure.adp;

import com.takibo.securitymanagement.domain.model.Resource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat d'API (RBAC-06) : seule une route /api/v1/orgs/{UUID}/... désigne une organisation.
 */
class PolicyResourceResolverTest {

    private static final String ORG = "aaaaaaaa-0000-0000-0000-000000000001";

    @Test
    void orgUuidInPath_isTheTargetOrganization() {
        assertThat(PolicyResourceResolver.resolve("/api/v1/orgs/" + ORG + "/spaces").orgId()).isEqualTo(ORG);
        assertThat(PolicyResourceResolver.resolve("/api/v1/orgs/" + ORG).orgId()).isEqualTo(ORG);
        assertThat(PolicyResourceResolver.resolve("/api/v1/orgs/" + ORG + "/dashboard/summary").orgId()).isEqualTo(ORG);
    }

    @Test
    void orgUuidInPath_isNormalized() {
        assertThat(PolicyResourceResolver.resolve("/api/v1/orgs/" + ORG.toUpperCase() + "/spaces").orgId())
                .isEqualTo(ORG);
    }

    @Test
    void pathsWithoutOrganizationUuid_designateNoOrganization() {
        for (String path : new String[]{
                "/api/v1/orgs/signup",
                "/api/v1/orgs/takibo-iam/spaces/finance/users",
                "/api/v1/me/spaces",
                "/api/spaces/" + ORG + "/users",
                "/api/v2/orgs/" + ORG + "/spaces",
                "/api/v1/orgs",
                "/"}) {
            assertThat(PolicyResourceResolver.resolve(path).orgId()).as(path).isNull();
        }
        assertThat(PolicyResourceResolver.resolve(null).orgId()).isNull();
    }

    @Test
    void pathIsKept_andSpaceIsNeverRead() {
        String path = "/api/v1/orgs/" + ORG + "/spaces/bbbbbbbb-0000-0000-0000-000000000002";

        Resource resource = PolicyResourceResolver.resolve(path);

        assertThat(resource.path()).isEqualTo(path);
        assertThat(resource.spaceId()).isNull();
    }
}
