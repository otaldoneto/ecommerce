package com.ecommerce.api.catalog;

import java.math.BigDecimal;

public record ProductFilter(String category, String q, BigDecimal minPrice, BigDecimal maxPrice) {
}
