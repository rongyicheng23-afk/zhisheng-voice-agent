package com.wc.system;

import com.wc.config.MinioInfo;
import com.wc.funasr.config.FunasrProperties;
import com.wc.tts.config.TtsProperties;
import com.wc.voiceprint.config.VoiceprintProperties;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import javax.sql.DataSource;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;

/** Bounded probes, five-second cache; never exposes endpoints or exception details. */
@RestController
@RequestMapping("/api/system")
public class SystemStatusController {
    public record ServiceStatus(String name, String status, long latencyMs, String message) {}
    public record Snapshot(String status, List<ServiceStatus> services, Instant checkedAt) {}
    private final DataSource datasource;
    private final Map<String, String> endpoints = new LinkedHashMap<>();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(6,6,0,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(6), new ThreadPoolExecutor.AbortPolicy());
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    private Snapshot cached;
    private long checkedNanos;
    public SystemStatusController(DataSource datasource, MinioInfo minio, FunasrProperties asr,
                                  TtsProperties tts, VoiceprintProperties sv) {
        this.datasource = datasource;
        endpoints.put("minio", minio.getEndpoint() + "/minio/health/ready");
        endpoints.put("funasr-http", asr.getHttpBaseUrl() + asr.getHealthPath());
        endpoints.put("funasr-ws", asr.getWsUrl());
        endpoints.put("tts", tts.getHttpBaseUrl() + tts.getHealthPath());
        endpoints.put("voiceprint", sv.getHttpBaseUrl() + sv.getHealthPath());
    }
    @GetMapping("/status")
    public synchronized ResponseEntity<Snapshot> status() {
        if (cached == null || System.nanoTime() - checkedNanos > TimeUnit.SECONDS.toNanos(5)) {
            Map<String, Future<ServiceStatus>> pending = new LinkedHashMap<>();
            pending.put("mysql", submit("mysql", () -> {
                try (var connection = datasource.getConnection()) { return connection.isValid(1); }
            }));
            endpoints.forEach((name,url) -> pending.put(name, submit(name, () -> {
                if (name.equals("funasr-ws")) {
                    var socket = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(1))
                        .buildAsync(URI.create(url), new WebSocket.Listener() {}).get(2,TimeUnit.SECONDS);
                    socket.abort();
                    return true;
                }
                var response = http.send(java.net.http.HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.discarding());
                return response.statusCode() >= 200 && response.statusCode() < 300;
            })));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            List<ServiceStatus> results = new ArrayList<>();
            pending.forEach((name,future) -> {
                try { results.add(future.get(Math.max(1,deadline-System.nanoTime()), TimeUnit.NANOSECONDS)); }
                catch (Exception e) { future.cancel(true); results.add(new ServiceStatus(name,"UNKNOWN",3000,"探测超时或繁忙")); }
            });
            cached = new Snapshot(results.stream().allMatch(s -> s.status().equals("UP")) ? "UP" : "DEGRADED",
                List.copyOf(results), Instant.now());
            checkedNanos = System.nanoTime();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cached);
    }
    private Future<ServiceStatus> submit(String name, Callable<Boolean> check) {
        try {
            return executor.submit(() -> {
                long start = System.nanoTime();
                boolean up;
                try { up = check.call(); } catch (Exception e) { up = false; }
                return new ServiceStatus(name, up ? "UP" : "DOWN",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start), up ? "探测通过" : "服务不可用");
            });
        } catch (RejectedExecutionException e) {
            return CompletableFuture.completedFuture(new ServiceStatus(name,"UNKNOWN",0,"探测繁忙"));
        }
    }
    @PreDestroy public void close() { executor.shutdownNow(); }
}
