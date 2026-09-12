package com.wc.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Internal-only ticket handoff from Spring Boot to the Python realtime gateway. */
@RestController
@RequestMapping("/api/realtime/internal")
public class GatewayTicketController {
    private final RealtimeTicketService tickets;
    private final byte[] gatewayKey;

    public GatewayTicketController(
            RealtimeTicketService tickets,
            @Value("${realtime.gateway.internal-key:}") String gatewayKey
    ) {
        this.tickets = tickets;
        this.gatewayKey = gatewayKey.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/tickets/consume")
    public ResponseEntity<ConsumedTicket> consume(
            @RequestHeader(value = "X-Realtime-Gateway-Key", defaultValue = "") String suppliedKey,
            @RequestBody TicketRequest request
    ) {
        if (gatewayKey.length == 0 || !MessageDigest.isEqual(gatewayKey, suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid gateway key");
        }
        Integer userId = tickets.consume(request.ticket());
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid or expired ticket");
        }
        return ResponseEntity.ok().body(new ConsumedTicket(userId));
    }

    public record TicketRequest(String ticket) {}
    public record ConsumedTicket(Integer userId) {}
}
