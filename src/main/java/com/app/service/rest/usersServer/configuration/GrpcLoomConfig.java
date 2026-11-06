package com.app.service.rest.usersServer.configuration;

import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;

@Configuration
public class GrpcLoomConfig {
    /**
     * Настройка Loom для gRPC. Активируется только при spring.threads.virtual.enabled=true в проперти-файле.
     * Позволяет гибко отключать виртуальные потоки или безопасно запускать код на старых версиях Java.
     */
    @Bean
    @ConditionalOnProperty(name = "spring.threads.virtual.enabled", havingValue = "true")
    public GrpcServerConfigurer grpcVirtualThreadsConfigurer() {
        // Переводим обработку gRPC-запросов на виртуальные потоки Loom
        return serverBuilder -> serverBuilder.executor(Executors.newVirtualThreadPerTaskExecutor());
    }
}
