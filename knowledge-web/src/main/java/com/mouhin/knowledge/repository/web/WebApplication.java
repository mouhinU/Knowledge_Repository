package com.mouhin.knowledge.repository.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Knowledge Repository 应用启动类
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.mouhin.knowledge.repository")
@MapperScan("com.mouhin.knowledge.repository.infrastructure.persistence.mapper")
public class WebApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }
}
