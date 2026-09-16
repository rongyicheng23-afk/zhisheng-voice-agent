package com.wc.realtime;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.Map;

@Component
public class RealtimeHandshake implements HandshakeInterceptor {
    private final RealtimeTickets tickets;
    private final RealtimeOrigins origins;
    public RealtimeHandshake(RealtimeTickets tickets, RealtimeOrigins origins) {
        this.tickets = tickets; this.origins = origins;
    }
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Map<String, Object> attributes) {
        String origin = request.getHeaders().getOrigin();
        if (!origins.allows(origin)) { response.setStatusCode(HttpStatus.FORBIDDEN); return false; }
        var query = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
        if (query.containsKey("token") || query.get("ticket") == null || query.get("ticket").size() != 1) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED); return false;
        }
        Integer user = tickets.consume(query.getFirst("ticket"), origin);
        if (user == null) { response.setStatusCode(HttpStatus.UNAUTHORIZED); return false; }
        attributes.put("realtimeUserId", user);
        return true;
    }
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler handler, Exception exception) { }
}
