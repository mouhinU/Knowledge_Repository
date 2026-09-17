package com.mouhin.knowledge.repository.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 应用层安全组件配置
 *
 * <p>为考生认证执行器提供 BCrypt 密码编码器单例，替代原先在各服务中手工 new 的做法。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Configuration
public class SecurityBeanConfig {

    @Bean
    public BCryptPasswordEncoder studentPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
