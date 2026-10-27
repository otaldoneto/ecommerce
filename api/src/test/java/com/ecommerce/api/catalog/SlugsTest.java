package com.ecommerce.api.catalog;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SlugsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Camiseta Básica Preta    | camiseta-basica-preta",
            "Tênis Ação & Aventura!   | tenis-acao-aventura",
            "  --Café   Coração--     | cafe-coracao",
            "iPhone 15 Pro (256GB)    | iphone-15-pro-256gb"
    })
    void createsUrlFriendlySlugs(String name, String expected) {
        assertThat(Slugs.from(name)).isEqualTo(expected);
    }
}
