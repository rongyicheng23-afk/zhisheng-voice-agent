package com.wc.config;

import com.wc.interceptors.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final LoginInterceptor loginInterceptor;
    private final com.wc.realtime.RealtimeOrigins origins;

    public WebConfig(LoginInterceptor loginInterceptor, com.wc.realtime.RealtimeOrigins origins) {
        this.loginInterceptor = loginInterceptor;
        this.origins = origins;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/user/**")
                .addPathPatterns("/api/user", "/api/user/**", "/api/users", "/api/download/**")
                .addPathPatterns("/api/realtime/**", "/api/system/**")
                .addPathPatterns("/api/funasr/**")
                .addPathPatterns("/api/funasr/history", "/api/funasr/history/**")
                .addPathPatterns("/api/funasr/minio/**")
                .addPathPatterns("/api/tts/**")
                .addPathPatterns("/api/voiceprint/**")
                .addPathPatterns("/api/speaker/**")
                .addPathPatterns("/api/meeting/**")
                .addPathPatterns("/api/knowledge/**")
                .excludePathPatterns(
                        "/user/login",
                        "/user/register",
                        "/api/funasr/health",
                        "/api/tts/health",
                        "/api/voiceprint/health",
                        "/api/system/status",
                        "/api/realtime/internal/tickets/consume",
                        "/api/realtime/internal/knowledge/search",
                        "/error"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(origins.values())
                .allowedMethods("*")
                .allowedHeaders("*");
    }
}
