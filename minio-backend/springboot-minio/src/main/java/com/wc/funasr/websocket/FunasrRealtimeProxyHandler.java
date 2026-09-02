package com.wc.funasr.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wc.entity.UserAudioHistory;
import com.wc.entity.UserInfo;
import com.wc.funasr.config.FunasrProperties;
import com.wc.service.UserAudioHistoryService;
import com.wc.service.UserInfoService;
import com.wc.utils.AuthContextUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class FunasrRealtimeProxyHandler extends AbstractWebSocketHandler {

    private final HttpClient funasrHttpClient;
    private final FunasrProperties funasrProperties;
    private final UserInfoService userInfoService;
    private final UserAudioHistoryService userAudioHistoryService;
    private final ObjectMapper objectMapper;
    private final Map<String, ProxySessionContext> contextMap = new ConcurrentHashMap<>();

    public FunasrRealtimeProxyHandler(
            HttpClient funasrHttpClient,
            FunasrProperties funasrProperties,
            UserInfoService userInfoService,
            UserAudioHistoryService userAudioHistoryService,
            ObjectMapper objectMapper
    ) {
        this.funasrHttpClient = funasrHttpClient;
        this.funasrProperties = funasrProperties;
        this.userInfoService = userInfoService;
        this.userAudioHistoryService = userAudioHistoryService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Integer userId;
        try {
            userId = requiredAuthenticatedUserId(session.getUri());
        } catch (IllegalArgumentException ex) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("auth invalid"));
            return;
        }

        UserInfo userInfo = userInfoService.getUserById(userId);
        if (userInfo == null) {
            session.close(CloseStatus.BAD_DATA.withReason("userId not found"));
            return;
        }

        String queryMode = queryParam(session.getUri(), "mode");
        UserAudioHistory history = userAudioHistoryService.createPendingStreamHistory(
                userId,
                StringUtils.hasText(queryMode) ? queryMode : "2pass"
        );

        ProxySessionContext context = new ProxySessionContext(session, userId, history.getId(), history.getFunasrMode());
        try {
            WebSocket funasrSocket = funasrHttpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofMillis(funasrProperties.getConnectTimeoutMs()))
                    .subprotocols("binary")
                    .buildAsync(URI.create(funasrProperties.getWsUrl()), new FunasrServerListener(context))
                    .join();
            context.setFunasrSocket(funasrSocket);
            contextMap.put(session.getId(), context);
        } catch (Exception ex) {
            userAudioHistoryService.finishStreamHistory(
                    history.getId(),
                    userId,
                    context.resolveFilename(),
                    "application/octet-stream",
                    new byte[0],
                    context.getFunasrMode(),
                    "",
                    "[]",
                    "连接 FunASR WebSocket 失败: " + ex.getMessage()
            );
            session.close(CloseStatus.SERVER_ERROR.withReason("FunASR connect failed"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ProxySessionContext context = contextMap.get(session.getId());
        if (context == null || context.getFunasrSocket() == null) {
            return;
        }
        captureClientConfig(context, message.getPayload());
        context.getFunasrSocket().sendText(message.getPayload(), true).join();
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        ProxySessionContext context = contextMap.get(session.getId());
        if (context == null || context.getFunasrSocket() == null) {
            return;
        }
        ByteBuffer payload = message.getPayload().asReadOnlyBuffer();
        byte[] bytes = new byte[payload.remaining()];
        payload.get(bytes);
        context.appendAudio(bytes);
        context.getFunasrSocket().sendBinary(ByteBuffer.wrap(bytes), true).join();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        finishContext(contextMap.remove(session.getId()), null);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        finishContext(contextMap.remove(session.getId()), exception.getMessage());
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR.withReason("proxy error"));
        }
    }

    private void captureClientConfig(ProxySessionContext context, String payload) {
        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            String wavName = jsonNode.path("wav_name").asText("");
            if (StringUtils.hasText(wavName)) {
                context.setWavName(wavName);
            }
            String mode = jsonNode.path("mode").asText("");
            if (StringUtils.hasText(mode)) {
                context.setFunasrMode(mode);
            }
        } catch (Exception ignored) {
        }
    }

    private void finishContext(ProxySessionContext context, String errorMessage) throws Exception {
        if (context == null || !context.markFinished()) {
            return;
        }

        WebSocket funasrSocket = context.getFunasrSocket();
        if (funasrSocket != null) {
            try {
                funasrSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
            } catch (Exception ex) {
                funasrSocket.abort();
            }
        }

        String rawResult = objectMapper.writeValueAsString(context.getServerMessages());
        String transcription = extractTextFromServerMessages(context.getServerMessages());

        userAudioHistoryService.finishStreamHistory(
                context.getHistoryId(),
                context.getUserId(),
                context.resolveFilename(),
                "application/octet-stream",
                context.getAudioBytes(),
                context.getFunasrMode(),
                transcription,
                rawResult,
                errorMessage
        );
    }

    private String extractTextFromServerMessages(List<String> serverMessages) {
        StringBuilder offlineText = new StringBuilder();
        StringBuilder allText = new StringBuilder();

        for (String serverMessage : serverMessages) {
            try {
                JsonNode jsonNode = objectMapper.readTree(serverMessage);
                String text = jsonNode.path("text").asText("");
                if (!StringUtils.hasText(text)) {
                    continue;
                }
                String mode = jsonNode.path("mode").asText("");
                if (mode.contains("offline")) {
                    if (!offlineText.isEmpty()) {
                        offlineText.append('\n');
                    }
                    offlineText.append(text);
                }
                if (!allText.isEmpty()) {
                    allText.append('\n');
                }
                allText.append(text);
            } catch (Exception ignored) {
            }
        }

        return offlineText.isEmpty() ? allText.toString() : offlineText.toString();
    }

    private Integer requiredAuthenticatedUserId(URI uri) {
        String token = queryParam(uri, "token");
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("token is required");
        }
        return AuthContextUtil.parseUserIdFromToken(token);
    }

    private String queryParam(URI uri, String name) {
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst(name);
    }

    private String safeFilename(String value) {
        return StringUtils.hasText(value) ? value.replaceAll("[^a-zA-Z0-9._-]", "_") : "stream.pcm";
    }

    private final class FunasrServerListener implements WebSocket.Listener {

        private final ProxySessionContext context;
        private final StringBuilder buffer = new StringBuilder();

        private FunasrServerListener(ProxySessionContext context) {
            this.context = context;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String payload = buffer.toString();
                buffer.setLength(0);
                context.addServerMessage(payload);
                try {
                    if (context.getFrontSession().isOpen()) {
                        synchronized (context.getFrontSession()) {
                            context.getFrontSession().sendMessage(new TextMessage(payload));
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            context.addServerMessage("{\"mode\":\"error\",\"text\":\"\",\"error\":\"" + safeFilename(error.getMessage()) + "\"}");
        }
    }

    private static final class ProxySessionContext {

        private final WebSocketSession frontSession;
        private final Integer userId;
        private final Integer historyId;
        private final ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
        private final List<String> serverMessages = new CopyOnWriteArrayList<>();
        private final AtomicBoolean finished = new AtomicBoolean(false);

        private volatile WebSocket funasrSocket;
        private volatile String wavName;
        private volatile String funasrMode;

        private ProxySessionContext(WebSocketSession frontSession, Integer userId, Integer historyId, String funasrMode) {
            this.frontSession = frontSession;
            this.userId = userId;
            this.historyId = historyId;
            this.funasrMode = funasrMode;
            this.wavName = "stream_" + historyId + ".pcm";
        }

        private WebSocketSession getFrontSession() {
            return frontSession;
        }

        private Integer getUserId() {
            return userId;
        }

        private Integer getHistoryId() {
            return historyId;
        }

        private void appendAudio(byte[] bytes) {
            audioBuffer.write(bytes, 0, bytes.length);
        }

        private byte[] getAudioBytes() {
            return audioBuffer.toByteArray();
        }

        private void addServerMessage(String payload) {
            serverMessages.add(payload);
        }

        private List<String> getServerMessages() {
            return serverMessages;
        }

        private WebSocket getFunasrSocket() {
            return funasrSocket;
        }

        private void setFunasrSocket(WebSocket funasrSocket) {
            this.funasrSocket = funasrSocket;
        }

        private String getFunasrMode() {
            return funasrMode;
        }

        private void setFunasrMode(String funasrMode) {
            this.funasrMode = funasrMode;
        }

        private void setWavName(String wavName) {
            this.wavName = wavName;
        }

        private String resolveFilename() {
            if (!StringUtils.hasText(wavName)) {
                return "stream.pcm";
            }
            if (wavName.toLowerCase().endsWith(".pcm")) {
                return wavName;
            }
            int dotIndex = wavName.lastIndexOf('.');
            return dotIndex > 0 ? wavName.substring(0, dotIndex) + ".pcm" : wavName + ".pcm";
        }

        private boolean markFinished() {
            return finished.compareAndSet(false, true);
        }
    }
}
