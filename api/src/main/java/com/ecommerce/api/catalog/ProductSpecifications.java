package com.ecommerce.api.catalog;

import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Locale;

final class ProductSpecifications {

    private ProductSpecifications() {
    }

    static Specification<Product> matching(ProductFilter filter, boolean onlyActive) {
        Specification<Product> spec = (root, query, cb) -> cb.conjunction();

        if (onlyActive) {
            spec = spec.and((root, query, cb) -> cb.isTrue(root.get("active")));
        }
        if (filter.category() != null && !filter.category().isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category").get("slug"), filter.category()));
        }
        if (filter.q() != null && !filter.q().isBlank()) {
            String pattern = "%" + escapeLike(filter.q().trim().toLowerCase(Locale.ROOT)) + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, '\\'));
        }
        if (filter.minPrice() != null) {
            BigDecimal min = filter.minPrice();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), min));
        }
        if (filter.maxPrice() != null) {
            BigDecimal max = filter.maxPrice();
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), max));
        }
        return spec;
    }

    // Without escaping, a search for "%" or "_" would act as a wildcard and match every product.
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
