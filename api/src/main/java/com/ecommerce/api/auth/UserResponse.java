package com.ecommerce.api.auth;

import com.ecommerce.api.user.Role;
import com.ecommerce.api.user.User;

import java.util.UUID;

public record UserResponse(UUID id, String name, String email, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
