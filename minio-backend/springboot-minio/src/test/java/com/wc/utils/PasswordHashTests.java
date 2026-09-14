package com.wc.utils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PasswordHashTests {
    @Test void saltedHashesFitExistingColumnAndVerify() {
        String first = PasswordHash.encode("示例-password");
        assertEquals(60, first.length());
        assertNotEquals(first, PasswordHash.encode("示例-password"));
        assertTrue(PasswordHash.matches("示例-password", first));
        assertFalse(PasswordHash.matches("wrong", first));
    }
    @Test void legacyVerificationRemainsAvailableForMigration() {
        String old = Md5Util.getMD5String("123456");
        assertTrue(PasswordHash.legacy(old));
        assertTrue(PasswordHash.matches("123456", old));
        assertFalse(PasswordHash.matches("wrong", old));
    }
    @Test void corruptAndOversizedInputsFailClosed() {
        assertFalse(PasswordHash.matches("pw", "$p2$bad"));
        assertFalse(PasswordHash.matches("x".repeat(257), "anything"));
        assertThrows(IllegalArgumentException.class, () -> PasswordHash.encode("x".repeat(257)));
    }
}
