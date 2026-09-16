package com.wc.config;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AuthRateLimitTests {
    @Test void limitsEachAddressAndExpiresWindow() {
        var filter = new AuthRateLimitFilter();
        for (int i=0; i<30; i++) assertTrue(filter.admit("a", 0));
        assertFalse(filter.admit("a", 0));
        assertTrue(filter.admit("b", 0));
        assertTrue(filter.admit("a", 60000));
    }
    @Test void addressCacheIsBounded() {
        var filter = new AuthRateLimitFilter();
        for (int i=0; i<4096; i++) assertTrue(filter.admit("ip"+i, 0));
        assertFalse(filter.admit("extra", 0));
        assertTrue(filter.admit("extra", 60000));
    }
}
