package com.takibo.securitymanagement.domain.service.policy;

import java.util.List;
import java.util.UUID;

/**
 * Segments d'un chemin de requête, découpés comme par {@link String#split(String)} :
 * ["", "api", "v1", "orgs", {org}, {surface}, ...]. Le segment org est un UUID sur les
 * surfaces TMS et un code lisible sur les surfaces TIS-CORE.
 */
record RequestPath(List<String> segments) {

    private static final List<String> ORGANIZATIONS_PREFIX = List.of("", "api", "v1", "orgs");
    private static final int ORGANIZATION = 4;

    static RequestPath of(String path) {
        return new RequestPath(List.of(path.split("/")));
    }

    /** /api/v1/orgs/{org}... avec au moins {@code minSegments} segments. */
    boolean isOrganizationRoute(int minSegments) {
        return size() >= minSegments
                && segments.subList(0, ORGANIZATIONS_PREFIX.size()).equals(ORGANIZATIONS_PREFIX);
    }

    boolean hasUuidOrganization() {
        return isUuidAt(ORGANIZATION);
    }

    String organization() {
        return segment(ORGANIZATION);
    }

    String segment(int index) {
        return segments.get(index);
    }

    boolean segmentIs(int index, String value) {
        return value.equals(segment(index));
    }

    boolean isUuidAt(int index) {
        return isUuid(segment(index));
    }

    List<String> from(int index) {
        return segments.subList(index, size());
    }

    int size() {
        return segments.size();
    }

    static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException notUuid) {
            return false;
        }
    }

    static boolean sameUuid(String expected, String actual) {
        return actual != null && isUuid(expected) && isUuid(actual)
                && UUID.fromString(expected).equals(UUID.fromString(actual));
    }
}
