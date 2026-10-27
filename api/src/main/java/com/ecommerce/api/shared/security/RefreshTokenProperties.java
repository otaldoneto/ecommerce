package com.ecommerce.api.shared.security;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.refresh-token")
public record RefreshTokenProperties(
        @NotNull Duration ttl,
        @NotNull Duration sessionMaxLifetime
) {
}
