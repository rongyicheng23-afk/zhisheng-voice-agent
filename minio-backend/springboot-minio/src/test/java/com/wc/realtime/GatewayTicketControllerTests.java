package com.wc.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GatewayTicketControllerTests {
    @Test
    void gatewayCanConsumeATicketExactlyOnceWithItsInternalKey() {
        RealtimeTicketService tickets = new RealtimeTicketService();
        GatewayTicketController controller = new GatewayTicketController(tickets, "a-local-test-gateway-key");
        String ticket = tickets.issue(42, "http://localhost:8081").ticket();

        assertEquals(42, controller.consume("a-local-test-gateway-key", new GatewayTicketController.TicketRequest(ticket, "http://localhost:8081"))
                .getBody().userId());
        assertThrows(ResponseStatusException.class, () ->
                controller.consume("a-local-test-gateway-key", new GatewayTicketController.TicketRequest(ticket, "http://localhost:8081")));
    }

    @Test
    void wrongInternalKeyDoesNotConsumeTheTicket() {
        RealtimeTicketService tickets = new RealtimeTicketService();
        GatewayTicketController controller = new GatewayTicketController(tickets, "a-local-test-gateway-key");
        String ticket = tickets.issue(42, "http://localhost:8081").ticket();

        assertThrows(ResponseStatusException.class, () ->
                controller.consume("wrong-key", new GatewayTicketController.TicketRequest(ticket, "http://localhost:8081")));
        assertEquals(42, controller.consume("a-local-test-gateway-key", new GatewayTicketController.TicketRequest(ticket, "http://localhost:8081"))
                .getBody().userId());
    }

    @Test
    void wrongOriginDoesNotConsumeTheTicket() {
        RealtimeTicketService tickets = new RealtimeTicketService();
        GatewayTicketController controller = new GatewayTicketController(tickets, "a-local-test-gateway-key");
        String ticket = tickets.issue(42, "http://localhost:8081").ticket();

        assertThrows(ResponseStatusException.class, () -> controller.consume("a-local-test-gateway-key",
                new GatewayTicketController.TicketRequest(ticket, "https://evil.example")));
        assertEquals(42, controller.consume("a-local-test-gateway-key",
                new GatewayTicketController.TicketRequest(ticket, "http://localhost:8081")).getBody().userId());
    }
}
