package com.ecommerce.api.auth;

import com.ecommerce.api.TestcontainersConfiguration;
import com.ecommerce.api.user.User;
import com.ecommerce.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RegisterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void registersCustomerWithoutExposingPassword() throws Exception {
        register("""
                {"name": "Ana", "email": "ana@example.com", "password": "secret123"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void storesPasswordAsHash() throws Exception {
        register("""
                {"name": "Ana", "email": "ana@example.com", "password": "secret123"}
                """);

        User user = userRepository.findByEmail("ana@example.com").orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo("secret123");
        assertThat(passwordEncoder.matches("secret123", user.getPasswordHash())).isTrue();
    }

    @Test
    void ignoresRoleSentByClient() throws Exception {
        register("""
                {"name": "Eve", "email": "eve@example.com", "password": "secret123", "role": "ADMIN"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() throws Exception {
        register("""
                {"name": "Ana", "email": "ana@example.com", "password": "secret123"}
                """);

        register("""
                {"name": "Ana 2", "email": "ANA@Example.com", "password": "secret123"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email already registered"));
    }

    @Test
    void rejectsInvalidFields() throws Exception {
        register("""
                {"name": "", "email": "not-an-email", "password": "short"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    private ResultActions register(String json) throws Exception {
        return mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
