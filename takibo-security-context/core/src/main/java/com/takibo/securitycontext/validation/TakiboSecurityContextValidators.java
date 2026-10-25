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
        if (!isIpLiteral(ip)) {
            throw new InvalidTakiboSecurityContextException("Invalid IP address for " + fieldName + ": " + ip);
        }
    }

    /**
     * Vrai si la valeur est une adresse IP littérale (IPv4 pointée ou IPv6, éventuellement entre
     * crochets). Ne déclenche jamais de résolution DNS : un nom d'hôte est simplement refusé.
     * La valeur peut venir d'un en-tête fourni par le client (X-Forwarded-For).
     */
    public static boolean isIpLiteral(String value) {
        if (value == null || value.isEmpty()) return false;
        String v = value;
        if (v.length() > 2 && v.charAt(0) == '[' && v.charAt(v.length() - 1) == ']') {
            v = v.substring(1, v.length() - 1);
        }
        return v.indexOf(':') >= 0 ? isIpv6Literal(v) : isIpv4Literal(v);
    }

    private static boolean isIpv4Literal(String v) {
        String[] octets = v.split("\\.", -1);
        if (octets.length != 4) return false;
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3 || (octet.length() > 1 && octet.charAt(0) == '0')) {
                return false;
            }
            for (int i = 0; i < octet.length(); i++) {
                if (!isAsciiDigit(octet.charAt(i))) return false;
            }
            if (Integer.parseInt(octet) > 255) return false;
        }
        return true;
    }

    private static boolean isIpv6Literal(String v) {
        char first = v.charAt(0);
        if (first != ':' && !isAsciiHexDigit(first)) return false;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c != ':' && c != '.' && !isAsciiHexDigit(c)) return false;
        }
        try {
            // Valeur contenant ':' et commençant par un chiffre hexadécimal ou ':' : le JDK
            // l'analyse comme littéral IPv6 et lève une exception si elle est invalide, sans DNS.
            InetAddress.getByName(v);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isAsciiHexDigit(char c) {
        return isAsciiDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
