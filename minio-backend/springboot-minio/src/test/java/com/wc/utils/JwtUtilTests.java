package com.wc.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class JwtUtilTests {
    @Test void rejectsOldPublicSigningKey() {
        String forged = com.auth0.jwt.JWT.create().withClaim("claims", Map.of("id", 1))
                .sign(com.auth0.jwt.algorithms.Algorithm.HMAC256("wc"));
        Assertions.assertThrows(Exception.class, () -> JwtUtil.parseToken(forged));
    }
    @Test void requiresStrongConfiguredKeyAndRandomizesFallback() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> JwtUtil.resolveKey("short"));
        Assertions.assertNotEquals(JwtUtil.resolveKey(null), JwtUtil.resolveKey(null));
        Assertions.assertEquals("x".repeat(32), JwtUtil.resolveKey("x".repeat(32)));
    }

    @Test
    void parseUserIdFromTokenAcceptsRawToken() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", 21);
        claims.put("username", "tester");

        String token = JwtUtil.genToken(claims);

        Assertions.assertEquals(21, AuthContextUtil.parseUserIdFromToken(token));
    }

    @Test
    void parseTokenAcceptsBearerPrefix() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", 35);
        claims.put("username", "tester");

        String token = JwtUtil.genToken(claims);
        Map<String, Object> parsed = JwtUtil.parseToken("Bearer " + token);

        Assertions.assertEquals(35, ((Number) parsed.get("id")).intValue());
        Assertions.assertEquals("tester", parsed.get("username"));
    }
}
