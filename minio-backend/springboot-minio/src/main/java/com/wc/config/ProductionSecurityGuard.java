package com.wc.config;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class ProductionSecurityGuard {
    public ProductionSecurityGuard(Environment env) {
        if (env.acceptsProfiles(Profiles.of("prod"))) {
            String secret = System.getenv("JWT_SECRET");
            if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
                throw new IllegalStateException("Production requires JWT_SECRET environment variable (32+ bytes)");
            }
        } else if (System.getenv("JWT_SECRET") == null) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("JWT uses a process-local random key; login again after restart. Configure JWT_SECRET for persistent sessions.");
        }
    }
}
