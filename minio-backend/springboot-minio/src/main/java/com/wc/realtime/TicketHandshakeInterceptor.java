package com.wc.realtime;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.server.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.*;
@Component
public class TicketHandshakeInterceptor implements HandshakeInterceptor {
    private final RealtimeTicketService tickets;
    private final Set<String> origins;
    public TicketHandshakeInterceptor(RealtimeTicketService tickets,
        @Value("${app.allowed-origins:http://localhost:8081,http://127.0.0.1:8081}") String origins) {
        this.tickets = tickets; this.origins = Set.of(origins.split(","));
    }
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Map<String,Object> attributes) {
        if (!origins.contains(request.getHeaders().getOrigin())) {
            response.setStatusCode(HttpStatus.FORBIDDEN); return false;
        }
        var query = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
        Integer userId = tickets.consume(query.getFirst("ticket"), request.getHeaders().getOrigin());
        if (userId == null) { response.setStatusCode(HttpStatus.UNAUTHORIZED); return false; }
        attributes.put("authenticatedUserId", userId);
        return true;
    }
    public void afterHandshake(ServerHttpRequest r, ServerHttpResponse s, WebSocketHandler h, Exception e) {}
}
