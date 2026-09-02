package com.wc.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Md5Util {
    private static final char[] HEX_DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8',
            '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    private Md5Util() {
    }

    public static String getMD5String(String value) {
        return getMD5String(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String getMD5String(byte[] bytes) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.update(bytes);
            return bufferToHex(messageDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("初始化MD5失败", e);
        }
    }

    private static String bufferToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(HEX_DIGITS[(value & 0xf0) >> 4]);
            builder.append(HEX_DIGITS[value & 0x0f]);
        }
        return builder.toString();
    }
}
