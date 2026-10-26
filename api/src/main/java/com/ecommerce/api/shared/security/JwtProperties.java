package com.ecommerce.api.shared.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        // HS256 needs a key of at least 256 bits (32 bytes).
        @NotBlank @Size(min = 32) String secret,
        @NotBlank String issuer,
        @NotNull Duration expiration
) {
}
