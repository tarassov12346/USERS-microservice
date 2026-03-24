package com.app.service.rest.usersServer.controller;

import com.app.service.rest.usersServer.configuration.JwtUtils;
import com.app.service.rest.usersServer.model.User;
import com.app.service.rest.usersServer.userservice.UsersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller// Поменял на @Controller, чтобы работали переходы на HTML страницы
public class UsersController {
    @Autowired
    UsersService usersService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    // --- СТРАНИЦЫ (UI) ---

    @GetMapping("/login")
    public String loginPage() {
        return "login"; // ищет login.html в templates
    }

    @GetMapping("/registration")
    public String registrationPage() {
        return "registration"; // ищет registration.html в templates
    }

    // --- API ЭНДПОИНТЫ ---

    @PostMapping("/api/users/register")
    @ResponseBody
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        String confirm = request.get("passwordConfirm");

        if (!password.equals(confirm)) {
            return ResponseEntity.badRequest().body("Passwords do not match");
        }

        User newUser = new User();
        newUser.setUsername(username); // Проверь, как в модели: setUserName или setUsername
        newUser.setPassword(password); // Передаем СЫРОЙ пароль, сервис сам его зашифрует

        // Вызываем твой существующий метод saveUser
        boolean saved = usersService.saveUser(newUser);

        return saved ? ResponseEntity.ok("User registered") :
                ResponseEntity.badRequest().body("User already exists");
    }

    @PostMapping("/api/users/login")
    @ResponseBody
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        User user = usersService.findUserByUserName(username);

        // bCryptPasswordEncoder.matches сравнит сырой пароль из запроса и хэш из БД
        if (user != null && passwordEncoder.matches(password, user.getPassword())) {
            String token = jwtUtils.generateToken(user.getUsername(), user.getId());

            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "userId", user.getId()
            ));
        }

        return ResponseEntity.status(401).body("Invalid username or password");
    }


    @RequestMapping("/save")
    @ResponseBody
    public boolean save(@RequestBody User newUser) {
        return usersService.saveUser(newUser);
    }

    @RequestMapping("/delete")
    @ResponseBody
    public void doDelete(@RequestParam Long userId) {
        usersService.deleteUser(userId);
    }

    @RequestMapping("/findId")
    @ResponseBody
    public User findId(@RequestParam Long userId){
        return usersService.findUserById(userId);
    }

    @RequestMapping("/findName")
    @ResponseBody
    public User findId(@RequestParam String userName){
        return usersService.findUserByUserName(userName);
    }

    @RequestMapping("/users")
    @ResponseBody
    public List<User> getAllUsers() {
        return  usersService.getAllUsers();
    }

    @RequestMapping("/isEmpty")
    @ResponseBody
    public boolean isEmpty() {
        return usersService.isRolesDBEmpty();
    }

    @RequestMapping("/prepareRolesDB")
    @ResponseBody
    public void doPrepareRoles() {
        usersService.prepareRolesDB();
    }

    @RequestMapping("/prepareUserDB")
    @ResponseBody
    public void doPrepareUsers() {
        usersService.prepareUserDB();
    }

}
