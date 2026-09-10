package com.wc.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import java.util.Date;
import java.util.Map;

public class JwtUtil {
    private static final String KEY = signingKey();
    private static final long TTL_SECONDS = Long.parseLong(System.getenv().getOrDefault("JWT_TTL_SECONDS", "43200"));

    private static String signingKey() {
        String configured = System.getenv("JWT_SECRET");
        if (configured != null && !configured.isBlank()) {
            if (configured.length() < 32) throw new IllegalStateException("JWT_SECRET must contain at least 32 characters");
            return configured;
        }
        // Local development only: restarting invalidates existing login tokens.
        byte[] bytes = new byte[32];
        org.slf4j.LoggerFactory.getLogger(JwtUtil.class).warn("JWT_SECRET is unset; using a temporary local key. Login tokens expire on restart.");
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getEncoder().encodeToString(bytes);
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
