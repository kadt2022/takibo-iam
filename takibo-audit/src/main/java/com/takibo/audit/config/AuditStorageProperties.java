package com.takibo.audit.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "takibo.audit")
@Validated
public record AuditStorageProperties(
        @NotNull Mode mode,
        @NotEmpty @Valid List<StoreConfig> stores
) {
    public record StoreConfig(
            @NotBlank String name,
            boolean enabled,
            Map<String, String> params
    ) {}

    public enum Mode { COMPOSITE, FALLBACK, DIRECT }
}

