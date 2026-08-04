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

        if (userMsg != null && passwordEncoder.matches(password, userMsg.getPassword())) {
            String token = jwtUtils.generateToken(userMsg.getUsername(), userMsg.getId());
            return ResponseEntity.ok(Map.of("token", token, "userId", userMsg.getId()));
        }
        return ResponseEntity.status(401).body("Invalid username or password");
    }
}
