package com.app.service.rest.usersServer.configuration;

import com.app.service.rest.usersServer.userservice.UsersService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class HibernateWarmupConfig implements SmartInitializingSingleton {

    private final UsersService usersService;

    @Override
    public void afterSingletonsInstantiated() {
        log.info("🔥 [Warmup Config] КРИТИЧЕСКИЙ СИНХРОННЫЙ ПРОГРЕВ ОРМ НА ПОТОКЕ ОС: [{}]",
                Thread.currentThread().getName());
        try {
            // 1. Прогреваем планы списков (JPA и Protobuf)
            usersService.getAllUsers();
            usersService.getAllUsersProtobuf();

            // 2. Прогреваем планы профиля по имени
            usersService.findUserByUserName("admin");
            usersService.findUserByUserNameProtobuf("admin");

            // 3. Закрываем слепую зону поиска по ID
            usersService.findUserByIdProtobuf(1L);

            log.info("🚀 [Warmup Config] Синхронный прогрев завершен строго на потоке [main]. Сетевые порты gRPC/HTTP теперь могут безопасно открываться.");
        } catch (Exception e) {
            log.warn("⚠️ [Warmup Config] Предупреждение при прогреве ORM: {}", e.getMessage());
        }
    }

}
