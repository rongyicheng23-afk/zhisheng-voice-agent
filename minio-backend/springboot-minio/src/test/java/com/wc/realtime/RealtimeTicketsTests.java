package com.wc.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class RealtimeTicketsTests {
    private static final String ORIGIN = "http://127.0.0.1:8081";

    @Test void ticketsAreOpaqueAndSingleUse() {
        var tickets = new RealtimeTickets();
        String ticket = tickets.issue(7, ORIGIN);
        assertTrue(ticket.matches("[A-Za-z0-9_-]{43}"));
        assertEquals(7, tickets.consume(ticket, ORIGIN));
        assertNull(tickets.consume(ticket, ORIGIN));
    }
    @Test void expiryAndWrongOriginFailClosed() {
        var now = new AtomicLong(1);
        var tickets = new RealtimeTickets(now::get);
        String ticket = tickets.issue(7, ORIGIN);
        assertNull(tickets.consume(ticket, "http://evil.example"));
        now.addAndGet(RealtimeTickets.TTL_MS);
        assertNull(tickets.consume(ticket, ORIGIN));
    }
    @Test void concurrentReplayOnlyOneSucceeds() throws Exception {
        var tickets = new RealtimeTickets();
        String ticket = tickets.issue(7, ORIGIN);
        var executor = Executors.newFixedThreadPool(4);
        try {
            var calls = java.util.stream.IntStream.range(0, 20)
                    .<java.util.concurrent.Callable<Integer>>mapToObj(i -> () -> tickets.consume(ticket, ORIGIN)).toList();
            int accepted = 0;
            for (var future : executor.invokeAll(calls)) if (future.get() != null) accepted++;
            assertEquals(1, accepted);
        } finally {
            executor.shutdownNow();
        }
    }
    @Test void perUserRateLimitSurvivesConsumptionAndExpires() {
        var now = new AtomicLong(1);
        var tickets = new RealtimeTickets(now::get);
        for (int i = 0; i < 10; i++) tickets.consume(tickets.issue(7, ORIGIN), ORIGIN);
        assertThrows(IllegalStateException.class, () -> tickets.issue(7, ORIGIN));
        assertNotNull(tickets.issue(8, ORIGIN));
        now.addAndGet(RealtimeTickets.TTL_MS);
        assertNotNull(tickets.issue(7, ORIGIN));
    }
    @Test void invalidOriginsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RealtimeOrigins("*"));
        assertThrows(IllegalArgumentException.class, () -> new RealtimeOrigins("https://good.example/path"));
        var origins = new RealtimeOrigins(ORIGIN);
        assertFalse(origins.allows(null));
        assertFalse(origins.allows(ORIGIN + ".evil.example"));
    }
    @Test void handshakeBindsUserAndRejectsReplayJwtAndUntrustedOrigin() {
        var tickets = new RealtimeTickets();
        var handshake = new RealtimeHandshake(tickets, new RealtimeOrigins(ORIGIN));
        String ticket = tickets.issue(7, ORIGIN);
        var attributes = new HashMap<String, Object>();
        assertTrue(connect(handshake, "ticket=" + ticket, ORIGIN, attributes));
        assertEquals(7, attributes.get("realtimeUserId"));
        assertFalse(connect(handshake, "ticket=" + ticket, ORIGIN, new HashMap<>()));
        assertFalse(connect(handshake, "token=old-jwt", ORIGIN, new HashMap<>()));
        String second = tickets.issue(7, ORIGIN);
        assertFalse(connect(handshake, "ticket=" + second, "http://evil.example", new HashMap<>()));
        assertTrue(connect(handshake, "ticket=" + second, ORIGIN, new HashMap<>()));
    }
    private boolean connect(RealtimeHandshake handshake, String query, String origin, HashMap<String, Object> attributes) {
        var request = new MockHttpServletRequest("GET", "/ws/funasr");
        request.setQueryString(query);
        request.addHeader("Origin", origin);
        return handshake.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()), null, attributes);
    }
}
