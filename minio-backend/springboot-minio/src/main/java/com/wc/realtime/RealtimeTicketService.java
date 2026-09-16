package com.wc.realtime;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
@Service
public class RealtimeTicketService {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RealtimeTicketService.class);
    private final Clock clock;
    private final Map<String, Entry> tickets = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    public RealtimeTicketService() { this(Clock.systemUTC()); }
    RealtimeTicketService(Clock clock) { this.clock = clock; }
    public record Issued(String ticket, Instant expiresAt, int expiresInSeconds) {}
    private record Entry(int userId, String origin, Instant expiresAt) {}
    public synchronized Issued issue(int userId, String origin) {
        Instant now = clock.instant();
        tickets.values().removeIf(e -> !e.expiresAt().isAfter(now));
        if (tickets.size() >= 10000 || tickets.values().stream().filter(e -> e.userId() == userId).count() >= 5)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiry = now.plusSeconds(60);
        tickets.put(ticket, new Entry(userId, origin, expiry));
        LOG.info("event=ticket.issued userId={}", userId);
        return new Issued(ticket, expiry, 60);
    }
    public synchronized Integer consume(String ticket, String origin) {
        Entry entry = tickets.get(ticket);
        boolean accepted = entry != null && entry.expiresAt().isAfter(clock.instant())
                && Objects.equals(entry.origin(), origin);
        if (accepted || entry != null && !entry.expiresAt().isAfter(clock.instant())) tickets.remove(ticket);
        LOG.info("event=ticket.consume accepted={}", accepted);
        return accepted ? entry.userId() : null;
    }
}
