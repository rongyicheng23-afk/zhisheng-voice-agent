package com.wc.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** $p2$ v1: PBKDF2-HMAC-SHA256, 600000 iterations, salt 16 bytes, hash 24 bytes.
 * 60 characters fits the existing VARCHAR(64). Never use this prefix for other parameters.
 */
public final class PasswordHash {
    private PasswordHash() {}
    public static String encode(String password) {
        validate(password);
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        byte[] payload = Arrays.copyOf(salt, 40);
        System.arraycopy(derive(password, salt), 0, payload, 16, 24);
        return "$p2$" + Base64.getEncoder().encodeToString(payload);
    }
    public static boolean legacy(String stored) { return stored != null && stored.matches("[a-fA-F0-9]{32}"); }
    public static boolean matches(String password, String stored) {
        if (password == null || password.length() > 256 || stored == null) return false;
        if (legacy(stored)) return MessageDigest.isEqual(Md5Util.getMD5String(password).getBytes(StandardCharsets.US_ASCII), stored.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
        if (!stored.startsWith("$p2$") || stored.length() != 60) return false;
        try {
            byte[] payload = Base64.getDecoder().decode(stored.substring(4));
            return payload.length == 40 && MessageDigest.isEqual(Arrays.copyOfRange(payload, 16, 40), derive(password, Arrays.copyOf(payload, 16)));
        } catch (IllegalArgumentException invalid) { return false; }
    }
    private static void validate(String password) {
        if (password == null || password.isEmpty() || password.length() > 256) throw new IllegalArgumentException("密码长度应为1–256字符");
    }
    private static byte[] derive(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 600000, 192);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (java.security.GeneralSecurityException ex) { throw new IllegalStateException("Password hashing unavailable", ex); }
        finally { spec.clearPassword(); }
    }
}
