package com.app.service.rest.usersServer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan
@EnableCaching // <-- ОБЯЗАТЕЛЬНО добавь сюда
public class UsersServer {
    public static void main(String[] args) {
        System.setProperty("spring.config.name", "users-server");
        SpringApplication.run(UsersServer.class, args);
    }
}
