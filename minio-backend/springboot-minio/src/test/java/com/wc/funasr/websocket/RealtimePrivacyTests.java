package com.wc.funasr.websocket;

import com.wc.entity.UserInfo;
import com.wc.entity.UserAudioHistory;
import com.wc.service.UserInfoService;
import com.wc.service.UserAudioHistoryService;
import com.wc.funasr.config.FunasrProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.web.socket.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class RealtimePrivacyTests {
    private FunasrRealtimeProxyHandler handler;
    private UserAudioHistoryService histories;
    private WebSocket upstream;
    private HttpClient http;
    @BeforeEach void setup() {
        http = mock(HttpClient.class); var builder = mock(WebSocket.Builder.class, RETURNS_SELF);
        upstream = mock(WebSocket.class);
        when(http.newWebSocketBuilder()).thenReturn(builder);
        when(builder.buildAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(upstream));
        when(upstream.sendClose(anyInt(), anyString())).thenReturn(CompletableFuture.completedFuture(upstream));
        when(upstream.sendBinary(any(), anyBoolean())).thenReturn(CompletableFuture.completedFuture(upstream));
        var users = mock(UserInfoService.class); when(users.getUserById(1)).thenReturn(new UserInfo());
        histories = mock(UserAudioHistoryService.class);
        handler = new FunasrRealtimeProxyHandler(http, new FunasrProperties(), users, histories, new ObjectMapper());
    }
    @AfterEach void cleanup() { handler.shutdown(); }
    private WebSocketSession front(String id, boolean save) {
        var session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(Map.of("realtimeUserId", 1));
        when(session.getUri()).thenReturn(URI.create("ws://localhost/ws/funasr" + (save ? "?save_audio=true" : "")));
        return session;
    }
    @Test void defaultSessionDoesNotCreateOrSaveHistory() throws Exception {
        var session = front("1", false);
        handler.afterConnectionEstablished(session);
        handler.handleBinaryMessage(session, new BinaryMessage(new byte[]{0, 0}));
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        verifyNoInteractions(histories);
        verify(upstream).sendBinary(any(), eq(true));
    }
    @Test void consentCreatesAndFinalizesHistoryOnce() throws Exception {
        var history = new UserAudioHistory(); history.setId(9);
        when(histories.createPendingStreamHistory(1, "2pass")).thenReturn(history);
        var session = front("1", true);
        handler.afterConnectionEstablished(session);
        handler.handleBinaryMessage(session, new BinaryMessage(new byte[]{0, 0}));
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        verify(histories).createPendingStreamHistory(1, "2pass");
        verify(histories).finishStreamHistory(eq(9), eq(1), any(), any(), aryEq(new byte[]{0, 0}), eq("2pass"), any(), any(), isNull());
    }
    @Test void twoConnectionsPerUserAndClosedSlotReused() throws Exception {
        var first = front("1", false); var second = front("2", false); var third = front("3", false);
        handler.afterConnectionEstablished(first); handler.afterConnectionEstablished(second); handler.afterConnectionEstablished(third);
        verify(third).close(any());
        verify(http, times(2)).newWebSocketBuilder();
        handler.afterConnectionClosed(first, CloseStatus.NORMAL);
        handler.afterConnectionEstablished(front("4", false));
        verify(http, times(3)).newWebSocketBuilder();
    }
    @Test void invalidFrameClosesWithoutForwarding() throws Exception {
        var session = front("1", false); handler.afterConnectionEstablished(session);
        handler.handleBinaryMessage(session, new BinaryMessage(new byte[]{1}));
        verify(session).close(any());
        verify(upstream, never()).sendBinary(any(), anyBoolean());
        verifyNoInteractions(histories);
    }
    private static byte[] aryEq(byte[] expected) { return org.mockito.AdditionalMatchers.aryEq(expected); }
}
