package com.takibo.securitycontext.model;

import com.takibo.securitycontext.exception.InvalidTakiboSecurityContextException;

public record ClientContext(
        String userAgent,
        ClientChannel channel,
        String platform,
        String deviceId
) {

    public ClientContext {
        if (userAgent != null && userAgent.length() > 2048) {
            throw new InvalidTakiboSecurityContextException("userAgent too long");
        }
        if (platform != null && platform.length() > 128) {
            throw new InvalidTakiboSecurityContextException("platform too long");
        }
        if (deviceId != null && deviceId.length() > 256) {
            throw new InvalidTakiboSecurityContextException("deviceId too long");
        }
    }
}
