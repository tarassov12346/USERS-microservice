package com.app.service.rest.usersServer.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        try {
            // Явно задаем неблокирующий криптографический провайдер
            SecureRandom nonBlockingRandom = SecureRandom.getInstance("SHA1PRNG");
            return new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2Y, 10, nonBlockingRandom);
        } catch (NoSuchAlgorithmException e) {
            return new BCryptPasswordEncoder();
        }
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Отключаем для REST
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/users/login", "/api/users/register", "/registration", "/login", "/img/**").permitAll()
                        .anyRequest().permitAll() // В микросервисе Users мы пока разрешим всё, проверку сделает Gateway
                );
        return http.build();
    }
}
