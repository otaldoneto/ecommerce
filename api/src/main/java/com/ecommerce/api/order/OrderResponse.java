package com.ecommerce.api.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(UUID id, OrderStatus status, BigDecimal total, Instant createdAt,
                            List<OrderItemResponse> items) {

    static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getStatus(), order.getTotal(), order.getCreatedAt(),
                order.getItems().stream().map(OrderItemResponse::from).toList());
    }
}
