package com.app.service.rest.usersServer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan
public class UsersServer {
    public static void main(String[] args) {
        System.setProperty("spring.config.name", "users-server");
        SpringApplication.run(UsersServer.class, args);
    }
}
