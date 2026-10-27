package com.ecommerce.api.auth;

public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
}
