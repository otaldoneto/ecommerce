package com.ecommerce.api.order;

import com.ecommerce.api.TestcontainersConfiguration;
import com.ecommerce.api.cart.CartService;
import com.ecommerce.api.catalog.Category;
import com.ecommerce.api.catalog.CategoryRepository;
import com.ecommerce.api.catalog.Product;
import com.ecommerce.api.catalog.ProductRepository;
import com.ecommerce.api.shared.error.ConflictException;
import com.ecommerce.api.user.Role;
import com.ecommerce.api.user.User;
import com.ecommerce.api.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OrderConcurrencyTest {

    private static final int THREADS = 10;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate redis;

    private Product lastUnit;

    @BeforeEach
    void setUp() {
        cleanUp();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();

        Category category = categoryRepository.save(new Category("Roupas", "roupas"));
        lastUnit = productRepository.save(new Product("Camiseta Rara", "camiseta-rara", "Edição limitada", new BigDecimal("99.90"), 1, category));
    }

    @AfterEach
    void cleanUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void onlyOneBuyerGetsTheLastUnit() throws Exception {
        List<UUID> buyers = IntStream.range(0, THREADS)
                .mapToObj(i -> {
                    UUID userId = userRepository.save(new User("Buyer " + i, "buyer" + i + "@example.com", "unused", Role.CUSTOMER)).getId();
                    cartService.setQuantity(userId, lastUnit.getId(), 1);
                    return userId;
                })
                .toList();

        List<Boolean> results = runConcurrently(buyers.stream()
                .<Callable<Boolean>>map(userId -> () -> succeeded(() -> orderService.checkout(userId, UUID.randomUUID())))
                .toList());

        assertThat(results).containsOnlyOnce(true);
        assertThat(productRepository.findById(lastUnit.getId()).orElseThrow().getStock()).isZero();
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrentCancelsRestoreStockOnce() throws Exception {
        UUID userId = userRepository.save(new User("Ana", "ana@example.com", "unused", Role.CUSTOMER)).getId();
        cartService.setQuantity(userId, lastUnit.getId(), 1);
        UUID orderId = orderService.checkout(userId, UUID.randomUUID()).order().id();

        List<Boolean> results = runConcurrently(IntStream.range(0, THREADS)
                .<Callable<Boolean>>mapToObj(i -> () -> succeeded(() -> orderService.cancel(userId, orderId)))
                .toList());

        assertThat(results).containsOnlyOnce(true);
        assertThat(productRepository.findById(lastUnit.getId()).orElseThrow().getStock()).isEqualTo(1);
    }

    @Test
    void concurrentRetriesWithSameKeyCreateOneOrder() throws Exception {
        Product shirt = productRepository.save(new Product("Camiseta Preta", "camiseta-preta", "Algodão", new BigDecimal("59.90"), 10, lastUnit.getCategory()));
        UUID userId = userRepository.save(new User("Ana", "ana@example.com", "unused", Role.CUSTOMER)).getId();
        cartService.setQuantity(userId, shirt.getId(), 1);
        UUID key = UUID.randomUUID();

        List<Boolean> created = runConcurrently(IntStream.range(0, THREADS)
                .<Callable<Boolean>>mapToObj(i -> () -> {
                    try {
                        return orderService.checkout(userId, key).created();
                    } catch (ConflictException ex) {
                        return false;
                    }
                })
                .toList());

        assertThat(created).containsOnlyOnce(true);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(productRepository.findById(shirt.getId()).orElseThrow().getStock()).isEqualTo(9);
    }

    private static boolean succeeded(Runnable action) {
        try {
            action.run();
            return true;
        } catch (ConflictException ex) {
            return false;
        }
    }

    // The latch holds every thread at the starting line, so they all hit the database at the same moment.
    private static List<Boolean> runConcurrently(List<Callable<Boolean>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<Boolean> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();

            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
