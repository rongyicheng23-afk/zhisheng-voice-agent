package com.wc.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RealtimeOrigins {
    private final Set<String> allowed;
    public RealtimeOrigins(@Value("${REALTIME_ALLOWED_ORIGINS:http://127.0.0.1:8081,http://localhost:8081}") String configured) {
        allowed = Arrays.stream(configured.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
        if (allowed.isEmpty()) throw new IllegalArgumentException("Realtime origins must be configured");
        for (String value : allowed) {
            URI uri = URI.create(value);
            if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !uri.getPath().isEmpty() || value.contains("*")) {
                throw new IllegalArgumentException("Invalid realtime origin");
            }
        }
    }
    public boolean allows(String origin) { return origin != null && allowed.contains(origin); }
    public String[] values() { return allowed.toArray(String[]::new); }
}
