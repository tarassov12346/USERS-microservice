# Этап 1: Сборка
FROM maven:3.9.6-eclipse-temurin-8 AS builder
WORKDIR /app
COPY pom.xml .
# Сначала загружаем зависимости (кеширование)
RUN mvn dependency:go-offline
COPY src ./src
# Исправляем возможную опечатку в папке ресурсов, если она есть
RUN if [ -d "src/main/resourсes" ]; then mv src/main/resourсes src/main/resources; fi
RUN mvn clean package -DskipTests

# Этап 2: Запуск
FROM eclipse-temurin:8-jre
WORKDIR /app
# Копируем JAR из предыдущего этапа
COPY --from=builder /app/target/USERS-microservice-0.0.1-SNAPSHOT.jar app.jar

# Порт из вашего game-server.properties
EXPOSE 4444

# Запуск с указанием имени конфига
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.config.name=users-server"]