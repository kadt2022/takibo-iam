package com.takibo.identitycore.config;

import com.takibo.identitycore.domain.catalogrbac.DefaultTechnicalRbacCatalog;
import com.takibo.identitycore.domain.catalogrbac.TechnicalRbacCatalog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

    // Force par défaut = 12 (robuste, mais ajuste si besoin)
    @Bean
    public PasswordEncoder passwordEncoder(
            @Value("${security.password-encoder.bcrypt-strength:12}") int strength
    ) {
        return new BCryptPasswordEncoder(strength);
    }


}
