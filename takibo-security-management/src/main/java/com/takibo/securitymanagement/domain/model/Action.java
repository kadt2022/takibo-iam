package com.takibo.securitymanagement.domain.model;

public enum Action {
    READ,
    CREATE,
    UPDATE,
    DELETE,
    OTHER;

    public static Action fromHttpMethod(String method) {
        if (method == null) {
            return OTHER;
        }
        return switch (method.toUpperCase()) {
            case "GET", "HEAD" -> READ;
            case "POST" -> CREATE;
            case "PUT", "PATCH" -> UPDATE;
            case "DELETE" -> DELETE;
            default -> OTHER;
        };
    }
}
