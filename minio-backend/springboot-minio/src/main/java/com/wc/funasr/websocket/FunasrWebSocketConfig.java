package com.wc.funasr.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class FunasrWebSocketConfig implements WebSocketConfigurer {

    private final FunasrRealtimeProxyHandler funasrRealtimeProxyHandler;

    public FunasrWebSocketConfig(FunasrRealtimeProxyHandler funasrRealtimeProxyHandler) {
        this.funasrRealtimeProxyHandler = funasrRealtimeProxyHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(funasrRealtimeProxyHandler, "/ws/funasr")
                .setAllowedOriginPatterns("*");
    }
}
