package com.takibo.securitycontext.validation;

import com.takibo.securitycontext.exception.InvalidTakiboSecurityContextException;

import java.net.InetAddress;
import java.util.UUID;

public final class TakiboSecurityContextValidators {

    private TakiboSecurityContextValidators() {
    }

    public static String normalizeToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    public static void validateUuid(String value, String fieldName) {
        try {
            UUID.fromString(value);
        } catch (Exception e) {
            throw new InvalidTakiboSecurityContextException("Invalid UUID for " + fieldName + ": " + value, e);
        }
    }

    public static void validatePortOptional(int port, String fieldName) {
        if (port == 0) return; // 0 = non renseigné
        if (port < 1 || port > 65535) {
            throw new InvalidTakiboSecurityContextException(fieldName + " must be between 1 and 65535");
        }
    }

    public static void validateIpAddressOptional(String ip, String fieldName) {
        if (ip == null) return;
        try {
            InetAddress.getByName(ip);
        } catch (Exception e) {
            throw new InvalidTakiboSecurityContextException("Invalid IP address for " + fieldName + ": " + ip, e);
        }
    }
}
