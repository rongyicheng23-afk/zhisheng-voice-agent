package com.wc.config;


import io.minio.MinioClient;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Config {

    @Resource
    MinioInfo minioInfo;
    //单例的 那么MinioClient会不会有线程安全问题呢？答案：没有线程安全问题！
    //链式编程，构建MinioClient对象
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(minioInfo.getEndpoint())//若是本地的只能自己电脑试
                .credentials(minioInfo.getAccessKey(), minioInfo.getSecretKey())
                .build();

    }}

