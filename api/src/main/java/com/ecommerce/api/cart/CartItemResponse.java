package com.ecommerce.api.cart;

import com.ecommerce.api.catalog.Product;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemResponse(
        UUID productId,
        String name,
        String slug,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal subtotal,
        boolean inStock
) {

    static CartItemResponse from(Product product, int quantity) {
        return new CartItemResponse(product.getId(), product.getName(), product.getSlug(), product.getPrice(), quantity,
                product.getPrice().multiply(BigDecimal.valueOf(quantity)), product.getStock() >= quantity);
    }
}
