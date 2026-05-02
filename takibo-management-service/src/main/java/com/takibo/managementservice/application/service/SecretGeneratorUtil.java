package com.takibo.managementservice.application.service;

import java.security.SecureRandom;
import java.util.Base64;

final class SecretGeneratorUtil {
    private static final SecureRandom RND = new SecureRandom();
    static String randomUrlSafe(int bytes) {
        byte[] buf = new byte[bytes];
        RND.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
    private SecretGeneratorUtil() {}
}
