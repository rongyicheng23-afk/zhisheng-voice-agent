package com.wc.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class JwtUtilTests {

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
