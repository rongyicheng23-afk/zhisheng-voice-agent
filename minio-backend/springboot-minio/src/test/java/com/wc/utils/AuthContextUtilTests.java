package com.wc.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class AuthContextUtilTests {

    @AfterEach
    void tearDown() {
        ThreadLocalUtil.remove();
    }

    @Test
    void currentUserIdReadsNumericClaimFromThreadLocal() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", 7);
        ThreadLocalUtil.set(claims);

        Assertions.assertEquals(7, AuthContextUtil.currentUserId());
    }

    @Test
    void currentUserIdReadsStringClaimFromThreadLocal() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", "12");
        ThreadLocalUtil.set(claims);

        Assertions.assertEquals(12, AuthContextUtil.currentUserId());
    }

    @Test
    void currentUserIdRejectsMissingContext() {
        Assertions.assertThrows(IllegalArgumentException.class, AuthContextUtil::currentUserId);
    }
}
