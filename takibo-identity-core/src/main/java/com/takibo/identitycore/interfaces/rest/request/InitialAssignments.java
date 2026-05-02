// ─────────────────────────────────────────────────────────────────────────────
// FILE 3 — interfaces/rest/request/InitialAssignments.java
// ─────────────────────────────────────────────────────────────────────────────
package com.takibo.identitycore.interfaces.rest.request;


import java.util.List;


public record InitialAssignments(
        List<String> roleNames,
        List<String> groupCodes
) {
}