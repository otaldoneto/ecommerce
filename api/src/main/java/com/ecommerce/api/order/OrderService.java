package com.ecommerce.api.order;

import com.ecommerce.api.cart.CartService;
import com.ecommerce.api.catalog.Product;
import com.ecommerce.api.catalog.ProductRepository;
import com.ecommerce.api.shared.error.BadRequestException;
import com.ecommerce.api.shared.error.ConflictException;
import com.ecommerce.api.shared.error.ResourceNotFoundException;
import com.ecommerce.api.shared.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Optional;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CartService cartService;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository, CartService cartService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.cartService = cartService;
    }

    @Transactional
    public CheckoutResult checkout(UUID userId, UUID idempotencyKey) {
        Optional<Order> previous = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (previous.isPresent()) {
            return new CheckoutResult(OrderResponse.from(previous.get()), false);
        }

        // Sorted by product id so concurrent checkouts lock rows in the same order and can't deadlock.
        Map<UUID, Integer> cart = new TreeMap<>(cartService.items(userId));
        if (cart.isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }

        Map<UUID, Product> products = productRepository.findAllById(cart.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        Order order = new Order(userId, idempotencyKey);
        cart.forEach((productId, quantity) -> {
            Product product = products.get(productId);
            if (product == null || !product.isActive()) {
                throw new ConflictException("A product in the cart is no longer available");
            }
            if (productRepository.decrementStock(productId, quantity) == 0) {
                throw new ConflictException("Not enough stock for " + product.getName());
            }
            order.addItem(productId, product.getName(), product.getPrice(), quantity);
        });

        Order saved;
        try {
            saved = orderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("A checkout with this Idempotency-Key is already in progress");
        }

        clearCartAfterCommit(userId);
        return new CheckoutResult(OrderResponse.from(saved), true);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> list(UUID userId, Pageable pageable) {
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(orderRepository.findByUserId(userId, newestFirst).map(OrderSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID userId, UUID orderId) {
        return orderRepository.findByIdAndUserId(orderId, userId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    @Transactional
    public OrderResponse cancel(UUID userId, UUID orderId) {
        Order order = orderRepository.findLockedByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        order.cancel();
        order.getItems().stream()
                .sorted(Comparator.comparing(OrderItem::getProductId))
                .forEach(item -> productRepository.incrementStock(item.getProductId(), item.getQuantity()));
        return OrderResponse.from(order);
    }

    public record CheckoutResult(OrderResponse order, boolean created) {
    }

    private void clearCartAfterCommit(UUID userId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cartService.clear(userId);
            }
        });
    }
}
