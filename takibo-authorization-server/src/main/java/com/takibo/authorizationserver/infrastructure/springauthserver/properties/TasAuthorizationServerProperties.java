package com.takibo.authorizationserver.infrastructure.springauthserver.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "takibo.tas")
public class TasAuthorizationServerProperties {

    private String issuer = "http://localhost:8081";

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
