package com.takibo.identitycore.config;

import com.takibo.identitycore.domain.catalogrbac.DefaultTechnicalRbacCatalog;
import com.takibo.identitycore.domain.catalogrbac.TechnicalRbacCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TechnicalRbacConfig {

    @Bean
    public TechnicalRbacCatalog technicalRbacCatalog() {
        return new DefaultTechnicalRbacCatalog();
    }
}