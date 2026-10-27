package com.takibo.securitymanagement.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat d'API (RBAC-06) : seule une route /api/v1/orgs/{UUID}/... désigne une organisation.
 */
class ResourceTest {

    private static final String ORG = "aaaaaaaa-0000-0000-0000-000000000001";

    @Test
    void orgUuidInPath_isTheTargetOrganization() {
        assertThat(Resource.fromPath("/api/v1/orgs/" + ORG + "/spaces").orgId()).isEqualTo(ORG);
        assertThat(Resource.fromPath("/api/v1/orgs/" + ORG).orgId()).isEqualTo(ORG);
        assertThat(Resource.fromPath("/api/v1/orgs/" + ORG + "/dashboard/summary").orgId()).isEqualTo(ORG);
    }

    @Test
    void orgUuidInPath_isNormalized() {
        assertThat(Resource.fromPath("/api/v1/orgs/" + ORG.toUpperCase() + "/spaces").orgId()).isEqualTo(ORG);
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
                "api/v1/orgs/" + ORG,
                "/"}) {
            assertThat(Resource.fromPath(path).orgId()).as(path).isNull();
        }
        assertThat(Resource.fromPath(null).orgId()).isNull();
    }

    @Test
    void spaceIsNeverReadFromThePath() {
        Resource resource = Resource.fromPath(
                "/api/v1/orgs/" + ORG + "/spaces/bbbbbbbb-0000-0000-0000-000000000002");

        assertThat(resource.spaceId()).isNull();
        assertThat(resource.path()).isEqualTo(
                "/api/v1/orgs/" + ORG + "/spaces/bbbbbbbb-0000-0000-0000-000000000002");
    }
}
