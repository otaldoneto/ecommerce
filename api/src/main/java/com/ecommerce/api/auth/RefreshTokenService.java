package com.ecommerce.api.auth;

import com.ecommerce.api.shared.error.UnauthorizedException;
import com.ecommerce.api.shared.security.RefreshTokenProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final RefreshTokenProperties properties;

    public RefreshTokenService(StringRedisTemplate redis, RefreshTokenProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public String create(UUID userId) {
        String familyId = UUID.randomUUID().toString();

        redis.opsForValue().set(familyKey(familyId), userId.toString(), properties.sessionMaxLifetime());
        redis.opsForSet().add(userFamiliesKey(userId.toString()), familyId);
        redis.expire(userFamiliesKey(userId.toString()), properties.sessionMaxLifetime());

        return issue(userId.toString(), familyId);
    }

    public Rotation rotate(String rawToken) {
        String hash = hash(rawToken);

        // GETDEL is atomic: of two concurrent requests with the same token, only one gets the value.
        String stored = redis.opsForValue().getAndDelete(tokenKey(hash));

        if (stored == null) {
            String reusedFamilyId = redis.opsForValue().get(usedKey(hash));
            if (reusedFamilyId != null) {
                revokeFamily(reusedFamilyId);
            }
            throw new UnauthorizedException("Invalid refresh token");
        }

        String[] parts = stored.split(":");
        String userId = parts[0];
        String familyId = parts[1];

        redis.opsForValue().set(usedKey(hash), familyId, properties.ttl());

        if (!Boolean.TRUE.equals(redis.hasKey(familyKey(familyId)))) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        return new Rotation(UUID.fromString(userId), issue(userId, familyId));
    }

    public void revoke(String rawToken) {
        String stored = redis.opsForValue().getAndDelete(tokenKey(hash(rawToken)));
        if (stored != null) {
            revokeFamily(stored.split(":")[1]);
        }
    }

    public void revokeAll(UUID userId) {
        Set<String> familyIds = redis.opsForSet().members(userFamiliesKey(userId.toString()));
        if (familyIds != null) {
            familyIds.forEach(familyId -> redis.delete(familyKey(familyId)));
        }
        redis.delete(userFamiliesKey(userId.toString()));
    }

    private void revokeFamily(String familyId) {
        String userId = redis.opsForValue().getAndDelete(familyKey(familyId));
        if (userId != null) {
            redis.opsForSet().remove(userFamiliesKey(userId), familyId);
        }
    }

    private String issue(String userId, String familyId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        redis.opsForValue().set(tokenKey(hash(rawToken)), userId + ":" + familyId, properties.ttl());
        return rawToken;
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String tokenKey(String hash) {
        return "refresh:" + hash;
    }

    private static String usedKey(String hash) {
        return "refresh-used:" + hash;
    }

    private static String familyKey(String familyId) {
        return "family:" + familyId;
    }

    private static String userFamiliesKey(String userId) {
        return "user-families:" + userId;
    }

    public record Rotation(UUID userId, String refreshToken) {
    }
}
