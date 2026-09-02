package com.wc;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@MapperScan(basePackages = "com.wc.mapper")
@SpringBootApplication
public class SpringbootMinioApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringbootMinioApplication.class, args);
    }

}
