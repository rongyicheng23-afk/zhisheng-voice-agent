package com.wc.config;

import com.wc.interceptors.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;

    public WebConfig(LoginInterceptor loginInterceptor) {
        this.loginInterceptor = loginInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/user/**")
                .addPathPatterns("/api/funasr/**")
                .addPathPatterns("/api/funasr/history", "/api/funasr/history/**")
                .addPathPatterns("/api/funasr/minio/**")
                .addPathPatterns("/api/tts/**")
                .addPathPatterns("/api/voiceprint/**")
                .addPathPatterns("/api/speaker/**")
                .addPathPatterns("/api/meeting/**")
                .excludePathPatterns(
                        "/user/login",
                        "/user/register",
                        "/api/funasr/health",
                        "/api/tts/health",
                        "/api/voiceprint/health",
                        "/error"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("*")
                .allowedHeaders("*");
    }
}
