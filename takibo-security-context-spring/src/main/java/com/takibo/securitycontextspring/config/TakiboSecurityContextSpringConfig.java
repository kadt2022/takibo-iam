package com.takibo.securitycontextspring.config;

import com.takibo.securitycontext.spi.CurrentTakiboSecurityContextProvider;
import com.takibo.securitycontextspring.provider.SpringCurrentTakiboSecurityContextProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TakiboSecurityContextSpringConfig {

    @Bean
    public CurrentTakiboSecurityContextProvider currentTakiboSecurityContextProvider() {
        return new SpringCurrentTakiboSecurityContextProvider();
    }
}
