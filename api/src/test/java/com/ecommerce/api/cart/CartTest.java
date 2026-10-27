package com.ecommerce.api.cart;

import com.ecommerce.api.TestcontainersConfiguration;
import com.ecommerce.api.catalog.Category;
import com.ecommerce.api.catalog.CategoryRepository;
import com.ecommerce.api.catalog.Product;
import com.ecommerce.api.catalog.ProductRepository;
import com.ecommerce.api.user.Role;
import com.ecommerce.api.user.User;
import com.ecommerce.api.user.UserRepository;
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

import java.math.BigDecimal;
import java.util.UUID;

import static com.ecommerce.api.support.AuthTestSupport.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CartTest {

    @Autowired
    private MockMvc mockMvc;

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
    private UUID anaId;
    private Product shirt;
    private Product sneakers;

    @BeforeEach
    void setUp() throws Exception {
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();

        anaId = userRepository.save(new User("Ana", "ana@example.com", passwordEncoder.encode("secret123"), Role.CUSTOMER))
                .getId();
        userRepository.save(new User("Bruno", "bruno@example.com", passwordEncoder.encode("secret123"), Role.CUSTOMER));
        ana = bearer(mockMvc, "ana@example.com", "secret123");

        Category category = categoryRepository.save(new Category("Roupas", "roupas"));
        shirt = productRepository.save(new Product("Camiseta Preta", "camiseta-preta", "Algodão", new BigDecimal("59.90"), 5, category));
        sneakers = productRepository.save(new Product("Tênis Preto", "tenis-preto", "Couro", new BigDecimal("299.90"), 2, category));
    }

    @Test
    void cartRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/cart")).andExpect(status().isUnauthorized());
    }

    @Test
    void newCartIsEmpty() throws Exception {
        mockMvc.perform(get("/cart").header(HttpHeaders.AUTHORIZATION, ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void setsQuantityAndComputesTotal() throws Exception {
        setQuantity(ana, shirt.getId(), 2).andExpect(status().isOk());
        setQuantity(ana, sneakers.getId(), 1).andExpect(status().isOk());

        setQuantity(ana, shirt.getId(), 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].slug").value("camiseta-preta"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].subtotal").value(119.80))
                .andExpect(jsonPath("$.total").value(419.70));
    }

    @Test
    void rejectsInvalidQuantities() throws Exception {
        setQuantity(ana, shirt.getId(), 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").exists());

        setQuantity(ana, shirt.getId(), CartService.MAX_QUANTITY + 1).andExpect(status().isBadRequest());

        setQuantity(ana, sneakers.getId(), 3).andExpect(status().isConflict());
    }

    @Test
    void rejectsUnknownAndInactiveProducts() throws Exception {
        setQuantity(ana, UUID.randomUUID(), 1).andExpect(status().isNotFound());

        shirt.deactivate();
        productRepository.save(shirt);
        setQuantity(ana, shirt.getId(), 1).andExpect(status().isNotFound());
    }

    @Test
    void removesItemsAndClearsCart() throws Exception {
        setQuantity(ana, shirt.getId(), 1);
        setQuantity(ana, sneakers.getId(), 1);

        mockMvc.perform(delete("/cart/items/{productId}", shirt.getId()).header(HttpHeaders.AUTHORIZATION, ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("tenis-preto"));

        mockMvc.perform(delete("/cart").header(HttpHeaders.AUTHORIZATION, ana))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/cart").header(HttpHeaders.AUTHORIZATION, ana))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void eachUserSeesOnlyTheirOwnCart() throws Exception {
        setQuantity(ana, shirt.getId(), 3);

        String bruno = bearer(mockMvc, "bruno@example.com", "secret123");
        mockMvc.perform(get("/cart").header(HttpHeaders.AUTHORIZATION, bruno))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void showsCurrentPriceAndDropsDeactivatedProducts() throws Exception {
        setQuantity(ana, shirt.getId(), 1);
        setQuantity(ana, sneakers.getId(), 1);

        sneakers.update("Tênis Preto", "Couro", new BigDecimal("249.90"), 2, sneakers.getCategory());
        productRepository.save(sneakers);
        shirt.deactivate();
        productRepository.save(shirt);

        mockMvc.perform(get("/cart").header(HttpHeaders.AUTHORIZATION, ana))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].unitPrice").value(249.90))
                .andExpect(jsonPath("$.total").value(249.90));

        assertThat(redis.opsForHash().hasKey(CartService.key(anaId), shirt.getId().toString())).isFalse();
    }

    @Test
    void cartExpiresAfterInactivity() throws Exception {
        setQuantity(ana, shirt.getId(), 1);

        Long ttl = redis.getExpire(CartService.key(anaId));
        assertThat(ttl).isPositive().isLessThanOrEqualTo(CartService.TTL.toSeconds());
    }

    private ResultActions setQuantity(String token, UUID productId, int quantity) throws Exception {
        return mockMvc.perform(put("/cart/items/{productId}", productId).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"quantity": %d}
                        """.formatted(quantity)));
    }
}
