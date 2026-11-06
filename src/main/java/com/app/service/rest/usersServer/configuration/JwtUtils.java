package com.app.service.rest.usersServer.configuration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Component
public class JwtUtils {
    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expirationMs}")
    private int jwtExpirationMs;
    // Переменная private SecretKey key — это и есть тот самый криптографический ключ (секрет), с помощью которого ваше приложение будет проверять подлинность (валидировать) всех входящих JWT-токенов.
    // Когда пользователь успешно логинится, ваш сервис авторизации берет полезную нагрузку токена (например, id: 5, role: ADMIN) и с помощью этого секретного ключа key и алгоритма (например, HMAC-SHA256) создает криптографическую подпись — тот самый хэш в конце JWT.
    // Когда клиент присылает запрос с JWT-токеном, ваша библиотека для работы с JWT (например, jjwt) делает следующее:
    //1. Она отрезает подпись (хэш), которую прислал клиент.
    //2. Берет чистые данные из токена (payload) и заново генерирует хэш в памяти, используя ваш private SecretKey key.
    //3. Сравнивает два хэша: тот, что пришел от клиента, и тот, что только что посчитала сама.
    // Если хэши совпали: Токен валидный.
    private SecretKey key; // Поле для хранения готового ключа

    @PostConstruct
    public void init() {
        // Этот метод выполнится Спрингом сразу после заполнения полей
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username, Long userId) {
        return Jwts.builder()
                .setSubject(username)
                .addClaims(Map.of("userId", userId)) // Добавляем ID в полезную нагрузку
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
