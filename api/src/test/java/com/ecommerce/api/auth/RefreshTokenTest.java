package com.ecommerce.api.auth;

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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RefreshTokenTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        userRepository.save(new User("Ana", "ana@example.com", passwordEncoder.encode("secret123"), Role.CUSTOMER));
    }

    @Test
    void refreshReturnsNewPairThatWorks() throws Exception {
        String refreshToken = refreshTokenOf(login());

        String body = refresh(refreshToken).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String newAccessToken = JsonPath.read(body, "$.accessToken");
        String newRefreshToken = JsonPath.read(body, "$.refreshToken");

        assertThat(newRefreshToken).isNotEqualTo(refreshToken);
        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccessToken))
                .andExpect(status().isOk());
    }

    @Test
    void refreshTokenWorksOnlyOnce() throws Exception {
        String refreshToken = refreshTokenOf(login());

        refresh(refreshToken).andExpect(status().isOk());
        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    void reuseRevokesTheWholeFamily() throws Exception {
        String first = refreshTokenOf(login());
        String second = refreshTokenOf(refresh(first).andReturn().getResponse().getContentAsString());

        refresh(first).andExpect(status().isUnauthorized());
        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesOnlyThatSession() throws Exception {
        String phone = refreshTokenOf(login());
        String laptop = refreshTokenOf(login());

        logout(phone).andExpect(status().isNoContent());

        refresh(phone).andExpect(status().isUnauthorized());
        refresh(laptop).andExpect(status().isOk());
    }

    @Test
    void logoutAllRevokesEverySession() throws Exception {
        String phoneBody = login();
        String laptop = refreshTokenOf(login());
        String accessToken = JsonPath.read(phoneBody, "$.accessToken");

        mockMvc.perform(post("/auth/logout-all").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        refresh(refreshTokenOf(phoneBody)).andExpect(status().isUnauthorized());
        refresh(laptop).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownTokenIsRejected() throws Exception {
        refresh("made-up-token").andExpect(status().isUnauthorized());
    }

    @Test
    void redisStoresOnlyTheHashWithTtl() throws Exception {
        String refreshToken = refreshTokenOf(login());

        assertThat(redis.keys("*" + refreshToken + "*")).isEmpty();

        String key = "refresh:" + RefreshTokenService.hash(refreshToken);
        assertThat(redis.hasKey(key)).isTrue();
        assertThat(redis.getExpire(key)).isPositive().isLessThanOrEqualTo(Duration.ofDays(7).toSeconds());
    }

    private String login() throws Exception {
        return mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "ana@example.com", "password": "secret123"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken": "%s"}
                        """.formatted(refreshToken)));
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return mockMvc.perform(post("/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken": "%s"}
                        """.formatted(refreshToken)));
    }

    private static String refreshTokenOf(String body) {
        return JsonPath.read(body, "$.refreshToken");
    }
}
