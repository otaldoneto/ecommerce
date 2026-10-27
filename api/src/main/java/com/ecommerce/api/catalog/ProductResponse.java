package com.ecommerce.api.catalog;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String slug,
        String description,
        BigDecimal price,
        int stock,
        boolean active,
        CategoryResponse category
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getSlug(), product.getDescription(),
                product.getPrice(), product.getStock(), product.isActive(), CategoryResponse.from(product.getCategory()));
    }
}
