package com.ecommerce.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 255) String email,
        // BCrypt ignores everything after 72 bytes, so longer passwords would give a false sense of security.
        @NotBlank @Size(min = 8, max = 72) String password
) {
}
