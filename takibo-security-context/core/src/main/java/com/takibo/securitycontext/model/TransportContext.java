package com.takibo.securitycontext.model;

import static com.takibo.securitycontext.validation.TakiboAsserts.maxLength;
import static com.takibo.securitycontext.validation.TakiboSecurityContextValidators.*;

public record TransportContext(
        String requestId,
        String ipAddress,
        String forwardedFor,
        String protocol,
        String tlsVersion,
        String cipherSuite,
        String host,
        int port
) {

    public TransportContext {
        requestId = normalizeToNull(requestId);
        ipAddress = normalizeToNull(ipAddress);
        forwardedFor = normalizeToNull(forwardedFor);
        protocol = normalizeToNull(protocol);
        tlsVersion = normalizeToNull(tlsVersion);
        cipherSuite = normalizeToNull(cipherSuite);
        host = normalizeToNull(host);

        maxLength(ipAddress, 128, "ipAddress too long");
        maxLength(requestId, 128, "requestId too long");
        maxLength(host, 255, "host too long");

        validateIpAddressOptional(ipAddress, "ipAddress");
        validatePortOptional(port, "port");
    }
}
