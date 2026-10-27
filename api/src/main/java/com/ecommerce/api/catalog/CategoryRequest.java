package com.ecommerce.api.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = ".*[\\p{L}\\p{N}].*", message = "must contain a letter or digit")
        String name
) {
}
