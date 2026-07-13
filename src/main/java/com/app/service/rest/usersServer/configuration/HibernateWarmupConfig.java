package com.app.service.rest.usersServer.configuration;

import com.app.service.rest.usersServer.model.User;
import com.app.service.rest.usersServer.repository.UserRepository;
import com.app.service.rest.usersServer.userservice.UsersService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class HibernateWarmupConfig {
    // Объявляем прогрев как Bean-конфигурацию
    @Bean
    public ApplicationListener<ApplicationReadyEvent> hibernatePlanWarmup(UsersService usersService) {
        return event -> {
            log.info("🔥 [Warmup Config] Тотальный прогрев СЕРВИСНОГО контура и кэша Caffeine...");
            try {
                // 1. Прогреваем кэш Caffeine для списков (JPA-сущностей и Protobuf DTO)
                usersService.getAllUsers();
                usersService.getAllUsersProtobuf();
                log.info("✅ [Warmup Config] Сервисные планы списков успешно прогреты в Caffeine.");

                // 2. Прогреваем кэш Caffeine для админа (как для REST, так и для gRPC контуров)
                usersService.findUserByUserName("admin");
                usersService.findUserByUserNameProtobuf("admin");
                log.info("✅ [Warmup Config] Сервисные планы профиля 'admin' успешно прогреты в Caffeine.");

                log.info("🚀 [Warmup Config] Абсолютный прогресс завершен! Рантайм полностью очищен от блокировок.");
            } catch (Exception e) {
                log.warn("⚠️ [Warmup Config] Предупреждение при прогреве сервиса: {}", e.getMessage());
            }
        };
    }
}
