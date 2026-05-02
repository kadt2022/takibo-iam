package com.takibo.identitycore.domain.security.policy.password;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "security.password-policy")
public class PasswordPolicyProperties {
    private int minLength = 8;
    private boolean requireUppercase = true;
    private boolean requireLowercase = true;
    private boolean requireDigit = true;
    private boolean requireSpecialChar = true;
    private int maxAgeDays = 90;
    private int historySize = 5;

    @Bean
    public PasswordPolicy passwordPolicy() {
        return new PasswordPolicy(UUID.randomUUID(), "Default Password Policy", minLength, requireUppercase, requireLowercase, requireDigit, requireSpecialChar, maxAgeDays, historySize, true);
    }
}
