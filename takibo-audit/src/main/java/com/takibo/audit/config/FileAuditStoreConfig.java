package com.takibo.audit.config;

import com.takibo.audit.core.AppendOnlyFileStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileAuditStoreConfig {

    @Bean
    public AppendOnlyFileStore appendOnlyFileStore(AuditStorageProperties props) {
        String path = props.stores().stream()
                .filter(s -> "file".equalsIgnoreCase(s.name()))
                .findFirst()
                .map(s -> s.params().get("path"))
                .orElse("./audit-log-tiscore.log");
        return new AppendOnlyFileStore(path);
    }
}
