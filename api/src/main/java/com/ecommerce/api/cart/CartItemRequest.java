package com.ecommerce.api.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartItemRequest(@NotNull @Min(1) @Max(CartService.MAX_QUANTITY) Integer quantity) {
}
