package com.wc.funasr.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class FunasrWebSocketConfig implements WebSocketConfigurer {
    @org.springframework.beans.factory.annotation.Autowired
    private com.wc.realtime.TicketHandshakeInterceptor ticketInterceptor;
    @org.springframework.beans.factory.annotation.Value("${app.allowed-origins:http://localhost:8081,http://127.0.0.1:8081}")
    private String allowedOrigins;

    private final FunasrRealtimeProxyHandler funasrRealtimeProxyHandler;

    public FunasrWebSocketConfig(FunasrRealtimeProxyHandler funasrRealtimeProxyHandler) {
        this.funasrRealtimeProxyHandler = funasrRealtimeProxyHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(funasrRealtimeProxyHandler, "/ws/funasr")
                .addInterceptors(ticketInterceptor)
                .setAllowedOrigins(allowedOrigins.split(","));
    }
}
