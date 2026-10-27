package com.ecommerce.api.catalog;

import com.ecommerce.api.TestcontainersConfiguration;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        Category shirts = categoryRepository.save(new Category("Camisetas", "camisetas"));
        Category shoes = categoryRepository.save(new Category("Calçados", "calcados"));

        productRepository.save(product("Camiseta Preta", "camiseta-preta", "59.90", shirts));
        productRepository.save(product("Camiseta Branca", "camiseta-branca", "49.90", shirts));
        productRepository.save(product("Tênis Preto", "tenis-preto", "299.90", shoes));

        Product hidden = product("Camiseta Antiga", "camiseta-antiga", "19.90", shirts);
        hidden.deactivate();
        productRepository.save(hidden);
    }

    @Test
    void publicListHidesInactiveProducts() throws Exception {
        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].slug", containsInAnyOrder("camiseta-preta", "camiseta-branca", "tenis-preto")));

        mockMvc.perform(get("/products/camiseta-antiga")).andExpect(status().isNotFound());
        mockMvc.perform(get("/products/camiseta-preta")).andExpect(status().isOk());
    }

    @Test
    void filtersCanBeCombined() throws Exception {
        mockMvc.perform(get("/products").param("category", "camisetas"))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/products").param("q", "PRET"))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/products").param("category", "camisetas").param("q", "pret").param("maxPrice", "100"))
                .andExpect(jsonPath("$.content[*].slug", contains("camiseta-preta")));

        mockMvc.perform(get("/products").param("minPrice", "50").param("maxPrice", "300"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void likeWildcardsAreTreatedAsText() throws Exception {
        mockMvc.perform(get("/products").param("q", "%"))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/products").param("q", "_"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void sortsOnlyByAllowedFields() throws Exception {
        mockMvc.perform(get("/products").param("sort", "price,desc"))
                .andExpect(jsonPath("$.content[*].slug", contains("tenis-preto", "camiseta-preta", "camiseta-branca")));

        mockMvc.perform(get("/products").param("sort", "stock"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pageSizeIsCapped() throws Exception {
        mockMvc.perform(get("/products").param("size", "100000"))
                .andExpect(jsonPath("$.size").value(50));

        mockMvc.perform(get("/products").param("size", "2"))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void categoriesArePublicAndSortedByName() throws Exception {
        mockMvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug", contains("calcados", "camisetas")));
    }

    @Test
    void listingDoesNotTriggerNPlusOneQueries() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(get("/products")).andExpect(status().isOk());

        // At most one query for the page and one for the total count, however many categories appear.
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
    }

    private static Product product(String name, String slug, String price, Category category) {
        return new Product(name, slug, "Descrição", new BigDecimal(price), 10, category);
    }
}
