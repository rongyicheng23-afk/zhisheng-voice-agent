package com.wc.funasr.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wc.entity.UserAudioHistory;
import com.wc.entity.UserInfo;
import com.wc.funasr.config.FunasrProperties;
import com.wc.service.UserAudioHistoryService;
import com.wc.service.UserInfoService;
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
    private final java.util.concurrent.ScheduledExecutorService reaper = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "asr-session-expiry");
        thread.setDaemon(true);
        return thread;
    });

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
        reaper.scheduleWithFixedDelay(() -> {
            for (ProxySessionContext context : contextMap.values()) {
                long now = System.nanoTime();
                if (now - context.lastActivity > java.util.concurrent.TimeUnit.SECONDS.toNanos(30)
                        || now - context.created > java.util.concurrent.TimeUnit.MINUTES.toNanos(30)) {
                    terminate(context, "实时会话已超时");
                }
            }
        }, 5, 5, java.util.concurrent.TimeUnit.SECONDS);
    }

    @jakarta.annotation.PreDestroy
    public void shutdown() {
        reaper.shutdownNow();
        for (ProxySessionContext context : contextMap.values()) terminate(context, "服务正在关闭");
    }

    private void terminate(ProxySessionContext context, String reason) {
        contextMap.remove(context.frontSession.getId(), context);
        try { finishContext(context, reason); } catch (Exception ignored) { }
        try { if (context.frontSession.isOpen()) context.frontSession.close(CloseStatus.SERVER_ERROR.withReason(reason)); }
        catch (Exception ignored) { }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Integer userId;
        try {
            userId = (Integer) session.getAttributes().get("realtimeUserId");
            if (userId == null) throw new IllegalArgumentException("ticket required");
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
        String mode = StringUtils.hasText(queryMode) ? queryMode : "2pass";
        boolean saveAudio = "true".equals(queryParam(session.getUri(), "save_audio"));
        ProxySessionContext context;
        synchronized (contextMap) {
            if (contextMap.size() >= 20 || contextMap.values().stream().filter(c -> userId.equals(c.userId)).count() >= 2) {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("实时连接数已达上限"));
                return;
            }
            UserAudioHistory history = saveAudio ? userAudioHistoryService.createPendingStreamHistory(userId, mode) : null;
            context = new ProxySessionContext(session, userId, history == null ? null : history.getId(), mode);
            contextMap.put(session.getId(), context);
        }
        try {
            WebSocket funasrSocket = funasrHttpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofMillis(funasrProperties.getConnectTimeoutMs()))
                    .subprotocols("binary")
                    .buildAsync(URI.create(funasrProperties.getWsUrl()), new FunasrServerListener(context))
                    .join();
            context.setFunasrSocket(funasrSocket);
            if (context.finished.get() || !session.isOpen()) {
                funasrSocket.abort();
                terminate(context, "连接已关闭");
                return;
            }
            // Browser upgrade can complete before the upstream model connects.
            // Only accept recording after this explicit readiness acknowledgement.
            session.sendMessage(new TextMessage("{\"event\":\"session.ready\"}"));
        } catch (Exception ex) {
            terminate(context, "连接语音识别服务失败");
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ProxySessionContext context = contextMap.get(session.getId());
        if (context == null || context.getFunasrSocket() == null) {
            return;
        }
        if (message.getPayloadLength() > 16384) { terminate(context, "配置消息过大"); return; }
        context.lastActivity = System.nanoTime();
        captureClientConfig(context, message.getPayload());
        context.getFunasrSocket().sendText(message.getPayload(), true).orTimeout(5, java.util.concurrent.TimeUnit.SECONDS).join();
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        ProxySessionContext context = contextMap.get(session.getId());
        if (context == null || context.getFunasrSocket() == null) {
            return;
        }
        ByteBuffer payload = message.getPayload().asReadOnlyBuffer();
        byte[] bytes = new byte[payload.remaining()];
        if (bytes.length == 0 || bytes.length > 65536 || bytes.length % 2 != 0) {
            terminate(context, "音频帧格式或大小不受支持"); return;
        }
        payload.get(bytes);
        if (!context.appendAudio(bytes)) { terminate(context, "单次实时音频已达20 MB上限，请分次录音"); return; }
        context.lastActivity = System.nanoTime();
        context.getFunasrSocket().sendBinary(ByteBuffer.wrap(bytes), true).orTimeout(5, java.util.concurrent.TimeUnit.SECONDS).join();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        finishContext(contextMap.remove(session.getId()), null);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        finishContext(contextMap.remove(session.getId()), "实时连接异常");
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
                funasrSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").orTimeout(1, java.util.concurrent.TimeUnit.SECONDS).join();
            } catch (Exception ex) {
                funasrSocket.abort();
            }
        }

        if (context.getHistoryId() == null) return;
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
            if (context.finished.get()) return CompletableFuture.completedFuture(null);
            if (buffer.length() + data.length() > 65536) {
                terminate(context, "识别响应过大"); return CompletableFuture.completedFuture(null);
            }
            buffer.append(data);
            if (last) {
                String payload = buffer.toString();
                buffer.setLength(0);
                if (!context.addServerMessage(payload)) {
                    terminate(context, "识别结果已达会话上限"); return CompletableFuture.completedFuture(null);
                }
                try {
                    if (context.getFrontSession().isOpen()) {
                        synchronized (context.getFrontSession()) {
                            context.getFrontSession().sendMessage(new TextMessage(payload));
                        }
                    }
                } catch (Exception ignored) {
                    terminate(context, "前端连接已中断");
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            terminate(context, "识别服务连接已关闭");
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            terminate(context, "识别服务连接异常");
        }
    }

    private static final class ProxySessionContext {

        private final WebSocketSession frontSession;
        private final Integer userId;
        private final Integer historyId;
        private final ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
        private final List<String> serverMessages = new CopyOnWriteArrayList<>();
        private final AtomicBoolean finished = new AtomicBoolean(false);
        private final long created = System.nanoTime();
        private volatile long lastActivity = created;
        private long audioBytesSeen;
        private long messageChars;

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

        private synchronized boolean appendAudio(byte[] bytes) {
            if (finished.get() || audioBytesSeen + bytes.length > 20L * 1024 * 1024) return false;
            audioBytesSeen += bytes.length;
            if (historyId != null) audioBuffer.write(bytes, 0, bytes.length);
            return true;
        }

        private synchronized byte[] getAudioBytes() {
            return audioBuffer.toByteArray();
        }

        private synchronized boolean addServerMessage(String payload) {
            if (finished.get() || messageChars + payload.length() > 2L * 1024 * 1024) return false;
            messageChars += payload.length();
            if (historyId != null) serverMessages.add(payload);
            return true;
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
