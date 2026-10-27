package com.ecommerce.api.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummaryResponse(UUID id, OrderStatus status, BigDecimal total, Instant createdAt) {

    static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(order.getId(), order.getStatus(), order.getTotal(), order.getCreatedAt());
    }
}
