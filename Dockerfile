# ==============================================================================
# Этап 1: Сборка gRPC-классов и JAR-артефакта (Среда Ubuntu для работы с protoc)
# ==============================================================================
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app

# Копируем дескриптор сборки для кэширования зависимостей Maven
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Копируем исходный код приложения
COPY src ./src

# СОХРАНЯЕМ КОСТЫЛЬ: Исправляем возможную опечатку с русской буквой "с" в названии папки ресурсов
RUN if [ -d "src/main/resourсes" ]; then mv src/main/resourсes src/main/resources; fi

# Генерируем gRPC-классы, подменяем javax на jakarta через Antrun и собираем JAR
RUN mvn clean package -DskipTests

# ==============================================================================
# Этап 2: Финальный высокопроизводительный рантайм (Ultra-Low Latency)
# ==============================================================================
# Используем легковесный JRE 21 на базе Alpine Linux
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Создаем безопасного не-root пользователя для контура авторизации
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Копируем собранный JAR-файл из этапа сборки
COPY --from=builder /app/target/USERS-microservice-0.0.1-SNAPSHOT.jar app.jar

# ОТКРЫВАЕМ ТОЧНЫЕ ПОРТЫ ИЗ PROPERTIES:
# 4444 - HTTP REST / Web-интерфейс Thymeleaf
# 6567 - Выделенный внутренний gRPC-сервер авторизации
EXPOSE 4444 6567

# Точка входа с поддержкой лимитов Docker, ZGC для Loom и флагом трассировки пиннинга
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:+UseZGC", \
            "-Djdk.tracePinnedThreads=short", \
            "-jar", "app.jar", \
            "--spring.config.name=users-server"]
