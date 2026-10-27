package com.ecommerce.api.cart;

import com.ecommerce.api.catalog.Product;
import com.ecommerce.api.catalog.ProductRepository;
import com.ecommerce.api.shared.error.ConflictException;
import com.ecommerce.api.shared.error.ResourceNotFoundException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CartService {

    static final int MAX_QUANTITY = 20;
    static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;
    private final ProductRepository productRepository;

    public CartService(StringRedisTemplate redis, ProductRepository productRepository) {
        this.redis = redis;
        this.productRepository = productRepository;
    }

    public CartResponse get(UUID userId) {
        String key = key(userId);
        Map<String, String> entries = hash().entries(key);

        List<UUID> ids = entries.keySet().stream().map(UUID::fromString).toList();
        Map<UUID, Product> products = productRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<CartItemResponse> items = new ArrayList<>();
        entries.forEach((productId, quantity) -> {
            Product product = products.get(UUID.fromString(productId));
            // Products deactivated or deleted after being added are dropped, so the cart never shows what can't be bought.
            if (product == null || !product.isActive()) {
                hash().delete(key, productId);
            } else {
                items.add(CartItemResponse.from(product, Integer.parseInt(quantity)));
            }
        });
        items.sort(Comparator.comparing(CartItemResponse::name));

        return CartResponse.of(items);
    }

    public Map<UUID, Integer> items(UUID userId) {
        return hash().entries(key(userId)).entrySet().stream()
                .collect(Collectors.toMap(entry -> UUID.fromString(entry.getKey()),
                        entry -> Integer.parseInt(entry.getValue())));
    }

    public CartResponse setQuantity(UUID userId, UUID productId, int quantity) {
        Product product = productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (product.getStock() < quantity) {
            throw new ConflictException("Not enough stock");
        }

        String key = key(userId);
        hash().put(key, productId.toString(), String.valueOf(quantity));
        redis.expire(key, TTL);
        return get(userId);
    }

    public CartResponse removeItem(UUID userId, UUID productId) {
        String key = key(userId);
        hash().delete(key, productId.toString());
        redis.expire(key, TTL);
        return get(userId);
    }

    public void clear(UUID userId) {
        redis.delete(key(userId));
    }

    private HashOperations<String, String, String> hash() {
        return redis.opsForHash();
    }

    static String key(UUID userId) {
        return "cart:" + userId;
    }
}
