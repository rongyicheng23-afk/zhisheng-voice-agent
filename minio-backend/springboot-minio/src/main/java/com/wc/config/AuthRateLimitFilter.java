package com.wc.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** JVM-local abuse guard. Does not trust spoofable forwarded-IP headers. */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private final Map<String, long[]> clients = new HashMap<>();
    synchronized boolean admit(String address, long now) {
        clients.values().removeIf(window -> window[0] <= now);
        long[] window = clients.get(address);
        if (window == null) {
            if (clients.size() >= 4096) return false;
            window = new long[]{now + 60000, 0}; clients.put(address, window);
        }
        return ++window[1] <= 30;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if ("POST".equals(request.getMethod()) && ("/user/login".equals(path) || "/user/register".equals(path))
                && !admit(request.getRemoteAddr(), System.currentTimeMillis())) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":429,\"msg\":\"登录或注册过于频繁，请一分钟后重试\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
