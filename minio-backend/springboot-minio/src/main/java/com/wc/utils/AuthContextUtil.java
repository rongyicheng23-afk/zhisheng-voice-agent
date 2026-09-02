package com.wc.utils;

import org.springframework.util.StringUtils;

import java.util.Map;

public class AuthContextUtil {

    private AuthContextUtil() {
    }

    public static Integer currentUserId() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        if (claims == null || claims.get("id") == null) {
            throw new IllegalArgumentException("未登录或登录已过期");
        }
        return parseUserId(claims.get("id"));
    }

    public static Integer parseUserIdFromToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("未登录或登录已过期");
        }
        Map<String, Object> claims = JwtUtil.parseToken(token);
        if (claims == null || claims.get("id") == null) {
            throw new IllegalArgumentException("未登录或登录已过期");
        }
        return parseUserId(claims.get("id"));
    }

    private static Integer parseUserId(Object idValue) {
        if (idValue instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(idValue));
    }
}
