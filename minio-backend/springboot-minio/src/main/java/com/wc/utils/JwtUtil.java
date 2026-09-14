package com.wc.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import java.util.Date;
import java.util.Map;

public class JwtUtil {
    private static final String KEY = resolveKey(System.getenv("JWT_SECRET"));
    private static final long TTL_SECONDS = resolveTtlSeconds(System.getenv("JWT_TTL_SECONDS"));

    static String resolveKey(String configured) {
        if (configured != null && !configured.isBlank()) {
            if (configured.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
                throw new IllegalArgumentException("JWT_SECRET must contain at least 32 UTF-8 bytes");
            }
            return configured;
        }
        org.slf4j.LoggerFactory.getLogger(JwtUtil.class).warn(
                "JWT_SECRET is unset; using a temporary local key. Login tokens expire on restart.");
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    static long resolveTtlSeconds(String configured) {
        if (configured == null || configured.isBlank()) return 43_200L;
        long value = Long.parseLong(configured);
        if (value < 60 || value > 604_800) {
            throw new IllegalArgumentException("JWT_TTL_SECONDS must be between 60 and 604800");
        }
        return value;
    }

    private JwtUtil() {
    }

    public static String genToken(Map<String, Object> claims) {
        return JWT.create()
                .withClaim("claims", claims)
                .withExpiresAt(new Date(System.currentTimeMillis() + 1000L * TTL_SECONDS))
                .sign(Algorithm.HMAC256(KEY));
    }

    public static Map<String, Object> parseToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return JWT.require(Algorithm.HMAC256(KEY))
                .build()
                .verify(token)
                .getClaim("claims")
                .asMap();
    }
}
