package com.wc.funasr.websocket;
import com.wc.realtime.RealtimeHandshake;
import com.wc.realtime.RealtimeOrigins;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class FunasrWebSocketConfig implements WebSocketConfigurer {
    private final FunasrRealtimeProxyHandler funasrRealtimeProxyHandler;
    private final RealtimeHandshake handshake;
    private final RealtimeOrigins origins;

    public FunasrWebSocketConfig(FunasrRealtimeProxyHandler funasrRealtimeProxyHandler, RealtimeHandshake handshake, RealtimeOrigins origins) {
        this.funasrRealtimeProxyHandler = funasrRealtimeProxyHandler;
        this.handshake = handshake;
        this.origins = origins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(funasrRealtimeProxyHandler, "/ws/funasr")
                .addInterceptors(handshake)
                .setAllowedOrigins(origins.values());
    }
}
