package com.ecommerce.api.order;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(UUID productId, String productName, BigDecimal unitPrice, int quantity,
                                BigDecimal subtotal) {

    static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getProductId(), item.getProductName(), item.getUnitPrice(),
                item.getQuantity(), item.getSubtotal());
    }
}
