package com.wc.realtime;

import org.springframework.stereotype.Service;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Single-instance ticket store. Replace with atomic Redis GETDEL before scaling. */
@Service
public class RealtimeTickets {
    public static final long TTL_MS = 30_000;
    private final SecureRandom random = new SecureRandom();
    private final LongSupplier now;
    private final Map<String, Entry> tickets = new HashMap<>();
    private final Map<Integer, Window> windows = new HashMap<>();
    private record Entry(int userId, String origin, long expiresAt) {}
    private record Window(long expiresAt, int count) {}

    public RealtimeTickets() { this(System::currentTimeMillis); }
    RealtimeTickets(LongSupplier now) { this.now = now; }

    public synchronized String issue(int userId, String origin) {
        if (userId <= 0 || origin == null || origin.isBlank()) throw new IllegalArgumentException("invalid scope");
        long time = now.getAsLong();
        tickets.values().removeIf(e -> e.expiresAt() <= time);
        windows.values().removeIf(e -> e.expiresAt() <= time);
        Window window = windows.get(userId);
        if (tickets.size() >= 5000 || windows.size() >= 5000 || (window != null && window.count() >= 10)) {
            throw new IllegalStateException("ticket capacity exceeded");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(hash(ticket), new Entry(userId, origin, time + TTL_MS));
        windows.put(userId, new Window(window == null ? time + TTL_MS : window.expiresAt(),
                window == null ? 1 : window.count() + 1));
        return ticket;
    }

    public synchronized Integer consume(String ticket, String origin) {
        if (ticket == null || !ticket.matches("[A-Za-z0-9_-]{43}")) return null;
        String key = hash(ticket);
        Entry entry = tickets.get(key);
        if (entry == null) return null;
        if (entry.expiresAt() <= now.getAsLong()) { tickets.remove(key); return null; }
        if (!entry.origin().equals(origin)) return null;
        tickets.remove(key); // atomic single consumption, including concurrent handshakes
        return entry.userId();
    }

    private static String hash(String token) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }
}
