package com.ecommerce.api.catalog;

import com.ecommerce.api.TestcontainersConfiguration;
import com.ecommerce.api.user.Role;
import com.ecommerce.api.user.User;
import com.ecommerce.api.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static com.ecommerce.api.support.AuthTestSupport.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminCatalogTest {

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

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(new User("Admin", "admin@example.com", passwordEncoder.encode("secret123"), Role.ADMIN));
        userRepository.save(new User("Ana", "ana@example.com", passwordEncoder.encode("secret123"), Role.CUSTOMER));
        admin = bearer(mockMvc, "admin@example.com", "secret123");
    }

    @Test
    void adminRoutesRequireAdminRole() throws Exception {
        String customer = bearer(mockMvc, "ana@example.com", "secret123");
        String body = """
                {"name": "Camisetas"}
                """;

        mockMvc.perform(post("/admin/categories").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/admin/categories").header(HttpHeaders.AUTHORIZATION, customer)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        createCategory("Camisetas").andExpect(status().isCreated());
    }

    @Test
    void createsCategoryWithSlugAndRejectsDuplicates() throws Exception {
        createCategory("Roupas de Inverno")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("roupas-de-inverno"));

        createCategory("roupas DE inverno")
                .andExpect(status().isConflict());
    }

    @Test
    void createsProduct() throws Exception {
        String categoryId = categoryId("Camisetas");

        createProduct("Camiseta Básica Preta", "59.90", 10, categoryId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("camiseta-basica-preta"))
                .andExpect(jsonPath("$.price").value(59.90))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.category.slug").value("camisetas"));
    }

    @Test
    void rejectsInvalidPrices() throws Exception {
        String categoryId = categoryId("Camisetas");

        createProduct("Camiseta", "0", 10, categoryId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.price").exists());

        createProduct("Camiseta", "19.999", 10, categoryId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.price").exists());
    }

    @Test
    void rejectsUnknownCategoryAndDuplicateProduct() throws Exception {
        createProduct("Camiseta", "59.90", 10, "00000000-0000-0000-0000-000000000000")
                .andExpect(status().isNotFound());

        String categoryId = categoryId("Camisetas");
        createProduct("Camiseta Preta", "59.90", 10, categoryId).andExpect(status().isCreated());
        createProduct("Camiseta PRETA", "49.90", 5, categoryId).andExpect(status().isConflict());
    }

    @Test
    void updateKeepsSlugEvenWhenNameChanges() throws Exception {
        String categoryId = categoryId("Camisetas");
        String productId = productId(createProduct("Camiseta Preta", "59.90", 10, categoryId));

        mockMvc.perform(put("/admin/products/{id}", productId).header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Camiseta Preta Premium", "79.90", 3, categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Camiseta Preta Premium"))
                .andExpect(jsonPath("$.price").value(79.90))
                .andExpect(jsonPath("$.slug").value("camiseta-preta"));
    }

    @Test
    void deleteDeactivatesInsteadOfRemoving() throws Exception {
        String categoryId = categoryId("Camisetas");
        String productId = productId(createProduct("Camiseta Preta", "59.90", 10, categoryId));

        mockMvc.perform(delete("/admin/products/{id}", productId).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/admin/products/{id}", productId).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    private ResultActions createCategory(String name) throws Exception {
        return mockMvc.perform(post("/admin/categories").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s"}
                        """.formatted(name)));
    }

    private String categoryId(String name) throws Exception {
        return JsonPath.read(createCategory(name).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions createProduct(String name, String price, int stock, String categoryId) throws Exception {
        return mockMvc.perform(post("/admin/products").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(productJson(name, price, stock, categoryId)));
    }

    private static String productId(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static String productJson(String name, String price, int stock, String categoryId) {
        return """
                {"name": "%s", "description": "Algodão", "price": %s, "stock": %d, "categoryId": "%s"}
                """.formatted(name, price, stock, categoryId);
    }
}
