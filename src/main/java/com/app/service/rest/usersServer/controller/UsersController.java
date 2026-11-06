package com.app.service.rest.usersServer.controller;

import com.app.grpc.UserMsg;
import com.app.service.rest.usersServer.configuration.JwtUtils;
import com.app.service.rest.usersServer.dto.RegisterRequest;
import com.app.service.rest.usersServer.model.User;
import com.app.service.rest.usersServer.userservice.UsersService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;
//Сам класс помечен как @Controller а не @RestController —
// это позволяет методу @GetMapping("/login") возвращать HTML-страницу а не строку
//А методы регистрации и логина (@PostMapping) помечены как @ResponseBody —
// это заставляет их возвращать JSON-данные а не HTML-шаблон
@Controller// Поменял на @Controller, чтобы работали переходы на HTML страницы
@RequiredArgsConstructor // Генерирует конструктор для всех final-полей
public class UsersController {
    // 1. Все зависимости делаем private final
    // 2. Убираем @Autowired с полей — конструктор сделает всё сам
    private final UsersService usersService;
    private final JwtUtils jwtUtils;
    private final BCryptPasswordEncoder passwordEncoder;

    // --- СТРАНИЦЫ (UI) ---
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/registration")
    public String registrationPage() {
        return "registration";
    }

    //@Valid запускает валидацию полей (например, если в рекорде стоят аннотации @NotBlank или @Size на пароле/логине).
    //@RequestBody автоматически превращает пришедший от фронтенда JSON в Java-объект - рекорд RegisterRequest
    @PostMapping("/api/users/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
            return ResponseEntity.badRequest().body("Passwords do not match");
        }
        User newUser = User.of(request.username(), request.password());
        boolean saved = usersService.saveUser(newUser);
        return saved ? ResponseEntity.ok("Success") : ResponseEntity.badRequest().body("Fail");
    }

    @PostMapping("/api/users/login")
    @ResponseBody
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        UserMsg userMsg = usersService.findUserByUserNameProtobuf(username);
    //Бин passwordEncoder заглядывает внутрь хэша userMsg.getPassword(), сам вытаскивает из него 22 символа соли, подмешивает её к чистому password (который прислал юзер в JSON) и сравнивает результаты
        if (userMsg != null && passwordEncoder.matches(password, userMsg.getPassword())) {
    //Если пароль подошел, в дело вступает тот самый SecretKey key из @PostConstruct в JWTUtils в конфиге.
    // Метод generateToken берет ID и имя пользователя, подписывает их вашим секретным ключом (клеймом) и создает JWT-токен, который возвращается клиенту.
            String token = jwtUtils.generateToken(userMsg.getUsername(), userMsg.getId());
            return ResponseEntity.ok(Map.of("token", token, "userId", userMsg.getId()));
        }
        return ResponseEntity.status(401).body("Invalid username or password");
    }
}
