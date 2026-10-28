package com.ecommerce.api.user;

import com.ecommerce.api.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static com.ecommerce.api.support.AuthTestSupport.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminBootstrapTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void createsAdminThatCanUseAdminRoutes() throws Exception {
        bootstrap(" Boss@Example.com ", "secret123").run(null);

        String admin = bearer(mockMvc, "boss@example.com", "secret123");
        mockMvc.perform(get("/admin/products").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());
    }

    @Test
    void runningAgainDoesNotDuplicateOrResetTheAdmin() throws Exception {
        bootstrap("boss@example.com", "secret123").run(null);
        bootstrap("boss@example.com", "another-password").run(null);

        assertThat(userRepository.count()).isEqualTo(1);
        bearer(mockMvc, "boss@example.com", "secret123");
    }

    @Test
    void skipsWhenNotConfigured() {
        bootstrap("", "").run(null);
        bootstrap(null, null).run(null);

        assertThat(userRepository.count()).isZero();
    }

    private AdminBootstrap bootstrap(String email, String password) {
        return new AdminBootstrap(userRepository, passwordEncoder, new AdminProperties(email, password));
    }
}
