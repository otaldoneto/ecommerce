package com.ecommerce.api.order;

import com.ecommerce.api.TestcontainersConfiguration;
import com.ecommerce.api.catalog.Category;
import com.ecommerce.api.catalog.CategoryRepository;
import com.ecommerce.api.catalog.Product;
import com.ecommerce.api.catalog.ProductRepository;
import com.ecommerce.api.user.Role;
import com.ecommerce.api.user.User;
import com.ecommerce.api.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import java.util.UUID;

import java.math.BigDecimal;

import static com.ecommerce.api.support.AuthTestSupport.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrderTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redis;

    private String ana;
    private String bruno;
    private Product shirt;
    private Product sneakers;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();

        userRepository.save(new User("Ana", "ana@example.com", passwordEncoder.encode("secret123"), Role.CUSTOMER));
        userRepository.save(new User("Bruno", "bruno@example.com", passwordEncoder.encode("secret123"), Role.CUSTOMER));
        ana = bearer(mockMvc, "ana@example.com", "secret123");
        bruno = bearer(mockMvc, "bruno@example.com", "secret123");

        Category category = categoryRepository.save(new Category("Roupas", "roupas"));
        shirt = productRepository.save(new Product("Camiseta Preta", "camiseta-preta", "Algodão", new BigDecimal("59.90"), 5, category));
        sneakers = productRepository.save(new Product("Tênis Preto", "tenis-preto", "Couro", new BigDecimal("299.90"), 2, category));
    }

    // Orders reference users and products, so other test classes couldn't delete those while orders remain.
    @AfterEach
    void cleanUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void checkoutRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void checkoutCreatesOrderDecrementsStockAndClearsCart() throws Exception {
        addToCart(ana, shirt, 2);
        addToCart(ana, sneakers, 1);

        checkout(ana)
                .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.total").value(419.70))
                .andExpect(jsonPath("$.items.length()").value(2));

        assertThat(stockOf(shirt)).isEqualTo(3);
        assertThat(stockOf(sneakers)).isEqualTo(1);

        mockMvc.perform(get("/cart").header(HttpHeaders.AUTHORIZATION, ana))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void checkoutRequiresAValidIdempotencyKey() throws Exception {
        addToCart(ana, shirt, 1);

        mockMvc.perform(post("/orders").header(HttpHeaders.AUTHORIZATION, ana))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/orders").header(HttpHeaders.AUTHORIZATION, ana).header("Idempotency-Key", "abc"))
                .andExpect(status().isBadRequest());

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void retryWithSameKeyReturnsTheSameOrder() throws Exception {
        UUID key = UUID.randomUUID();
        addToCart(ana, shirt, 2);
        String orderId = orderId(checkout(ana, key));

        addToCart(ana, sneakers, 1);
        checkout(ana, key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.items.length()").value(1));

        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(stockOf(shirt)).isEqualTo(3);
        assertThat(stockOf(sneakers)).isEqualTo(2);

        checkout(ana, UUID.randomUUID()).andExpect(status().isCreated());
        assertThat(orderRepository.count()).isEqualTo(2);
    }

    @Test
    void sameKeyFromDifferentUsersCreatesSeparateOrders() throws Exception {
        UUID key = UUID.randomUUID();
        addToCart(ana, shirt, 1);
        addToCart(bruno, shirt, 1);

        String anaOrder = orderId(checkout(ana, key));
        String brunoOrder = orderId(checkout(bruno, key));

        assertThat(anaOrder).isNotEqualTo(brunoOrder);
        assertThat(stockOf(shirt)).isEqualTo(3);
    }

    @Test
    void orderKeepsPriceFromTheMomentOfPurchase() throws Exception {
        addToCart(ana, shirt, 1);
        String orderId = orderId(checkout(ana));

        Product current = productRepository.findById(shirt.getId()).orElseThrow();
        current.update("Camiseta Preta Premium", "Algodão", new BigDecimal("99.90"), current.getStock(), current.getCategory());
        productRepository.save(current);

        mockMvc.perform(get("/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productName").value("Camiseta Preta"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(59.90))
                .andExpect(jsonPath("$.total").value(59.90));
    }

    @Test
    void checkoutRejectsEmptyCart() throws Exception {
        checkout(ana).andExpect(status().isBadRequest());
    }

    @Test
    void checkoutIsAllOrNothing() throws Exception {
        addToCart(ana, shirt, 2);
        addToCart(ana, sneakers, 2);

        Product current = productRepository.findById(sneakers.getId()).orElseThrow();
        current.update(current.getName(), current.getDescription(), current.getPrice(), 1, current.getCategory());
        productRepository.save(current);

        checkout(ana).andExpect(status().isConflict());

        assertThat(stockOf(shirt)).isEqualTo(5);
        assertThat(orderRepository.count()).isZero();
        mockMvc.perform(get("/cart").header(HttpHeaders.AUTHORIZATION, ana))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void checkoutRejectsProductDeactivatedAfterBeingAdded() throws Exception {
        addToCart(ana, shirt, 1);

        Product current = productRepository.findById(shirt.getId()).orElseThrow();
        current.deactivate();
        productRepository.save(current);

        checkout(ana).andExpect(status().isConflict());
        assertThat(stockOf(shirt)).isEqualTo(5);
    }

    @Test
    void ordersAreVisibleOnlyToTheirOwner() throws Exception {
        addToCart(ana, shirt, 1);
        String orderId = orderId(checkout(ana));

        mockMvc.perform(get("/orders").header(HttpHeaders.AUTHORIZATION, ana))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(orderId));

        mockMvc.perform(get("/orders").header(HttpHeaders.AUTHORIZATION, bruno))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, bruno))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/orders/{id}/cancel", orderId).header(HttpHeaders.AUTHORIZATION, bruno))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelRestoresStockOnlyOnce() throws Exception {
        addToCart(ana, shirt, 2);
        String orderId = orderId(checkout(ana));
        assertThat(stockOf(shirt)).isEqualTo(3);

        mockMvc.perform(post("/orders/{id}/cancel", orderId).header(HttpHeaders.AUTHORIZATION, ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(stockOf(shirt)).isEqualTo(5);

        mockMvc.perform(post("/orders/{id}/cancel", orderId).header(HttpHeaders.AUTHORIZATION, ana))
                .andExpect(status().isConflict());
        assertThat(stockOf(shirt)).isEqualTo(5);
    }

    private void addToCart(String token, Product product, int quantity) throws Exception {
        mockMvc.perform(put("/cart/items/{productId}", product.getId()).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": %d}
                                """.formatted(quantity)))
                .andExpect(status().isOk());
    }

    private ResultActions checkout(String token) throws Exception {
        return checkout(token, UUID.randomUUID());
    }

    private ResultActions checkout(String token, UUID idempotencyKey) throws Exception {
        return mockMvc.perform(post("/orders").header(HttpHeaders.AUTHORIZATION, token)
                .header("Idempotency-Key", idempotencyKey.toString()));
    }

    private static String orderId(ResultActions result) throws Exception {
        return JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private int stockOf(Product product) {
        return productRepository.findById(product.getId()).orElseThrow().getStock();
    }
}
