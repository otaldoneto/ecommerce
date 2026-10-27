package com.ecommerce.api.auth;

import com.ecommerce.api.shared.error.ConflictException;
import com.ecommerce.api.shared.error.ResourceNotFoundException;
import com.ecommerce.api.shared.error.UnauthorizedException;
import com.ecommerce.api.user.Role;
import com.ecommerce.api.user.User;
import com.ecommerce.api.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-unknown-emails");
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already registered");
        }

        User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()), Role.CUSTOMER);

        try {
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException ex) {
            // Two simultaneous sign-ups can both pass the check above; the unique index decides.
            throw new ConflictException("Email already registered");
        }
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmail(request.email().toLowerCase(Locale.ROOT));

        // Hashing even when the email is unknown keeps both paths equally slow, so response time
        // does not reveal which emails are registered.
        String hash = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        if (user.isEmpty() || !passwordMatches) {
            throw new UnauthorizedException("Invalid email or password");
        }

        User authenticated = user.get();
        return tokenResponse(authenticated, refreshTokenService.create(authenticated.getId()));
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(RefreshRequest request) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(request.refreshToken());

        User user = userRepository.findById(rotation.userId())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        return tokenResponse(user, rotation.refreshToken());
    }

    public void logout(RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    public void logoutAll(UUID userId) {
        refreshTokenService.revokeAll(userId);
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private TokenResponse tokenResponse(User user, String refreshToken) {
        return new TokenResponse(jwtService.generateAccessToken(user), refreshToken, "Bearer",
                jwtService.expiresInSeconds());
    }
}
