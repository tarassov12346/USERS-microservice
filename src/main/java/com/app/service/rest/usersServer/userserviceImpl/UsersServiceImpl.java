package com.app.service.rest.usersServer.userserviceImpl;

import com.app.grpc.RoleMsg;
import com.app.grpc.UserMsg;
import com.app.service.rest.usersServer.model.Roles;
import com.app.service.rest.usersServer.model.User;
import com.app.service.rest.usersServer.repository.RoleRepository;
import com.app.service.rest.usersServer.repository.UserRepository;
import com.app.service.rest.usersServer.userservice.UsersService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor // Автоматически генерирует конструктор для всех private final полей
public class UsersServiceImpl implements UsersService {

    // Внедряем зависимости через конструктор (private final) — канон для продакшена
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder bCryptPasswordEncoder;

    @Override
    @Cacheable(value = "users_list", key = "'allUsers'", sync = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // --- НАШИ НОВЫЕ СВЕРХБЫСТРЫЕ МЕТОДЫ КЭШИРОВАНИЯ PROTOPUF DTO ДЛЯ gRPC ---

    @Override
    @Cacheable(value = "users_list", key = "'all_proto'", sync = true)
    public List<UserMsg> getAllUsersProtobuf() {
        log.info("💾 КЭШ МИСНУЛ (all_proto): Идем в PostgreSQL через JOIN FETCH");
        return userRepository.findAllWithRoles().stream()
                .map(this::mapToMsg)
                .toList();
    }

    @Override
    @Cacheable(value = "user_details", key = "#userName + '_proto'", sync = true)
    public UserMsg findUserByUserNameProtobuf(String userName) {
        log.info("💾 КЭШ МИСНУЛ (user_proto): Идем в PostgreSQL за пользователем: {}", userName);
        User user = userRepository.findByUsernameWithRoles(userName);
        return user != null ? mapToMsg(user) : null;
    }

    // ----------------------------------------------------------------------

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "users_list", allEntries = true, beforeInvocation = true),
            @CacheEvict(value = "user_details", allEntries = true, beforeInvocation = true) // Сбрасываем всё, чтобы не ловить рассинхрон ID/Имя
    })
    public boolean deleteUser(Long userId) {
        var userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.getRoles().clear(); // Безопасно чистим связи ManyToMany
            userRepository.delete(user);
            return true;
        }
        return false;
    }

    @Override
    @Cacheable(value = "user_details", key = "#userId", sync = true)
    public User findUserById(Long userId) {
        // Безопасное извлечение без риска выбросить NoSuchElementException
        return userRepository.findById(userId).orElse(null);
    }

    @Override
    @Cacheable(value = "user_details", key = "#userName", sync = true)
    public User findUserByUserName(String userName) {
        log.info("💾 КЭШ МИСНУЛ (user_login): Идем в PostgreSQL через JOIN FETCH за {}", userName);
        // ЗАМЕНЯЕМ findByUsername на findByUsernameWithRoles!
        return userRepository.findByUsernameWithRoles(userName);
    }


    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "users_list", allEntries = true, beforeInvocation = true),
            @CacheEvict(value = "user_details", key = "#user.username", beforeInvocation = true),
            @CacheEvict(value = "user_details", key = "#user.username + '_proto'", beforeInvocation = true)
    })
    public boolean saveUser(User user) {
        User userFromDB = userRepository.findByUsername(user.getUsername());
        if (userFromDB != null) {
            return false;
        }

        // Безопасно вытаскиваем роль по умолчанию
        roleRepository.findById(2L).ifPresent(role ->
                user.setRoles(Collections.singleton(role))
        );

        // Хешируем пароль (работает одинаково и для gRPC, и для Thymeleaf)
        user.setPassword(bCryptPasswordEncoder.encode(user.getPassword()));
        userRepository.save(user);
        return true;
    }

    @Override
    @Transactional
    public boolean isRolesDBEmpty() {
        return roleRepository.count() == 0;
    }

    @Override
    @Transactional
    public void prepareRolesDB() {
        Roles rolesAdmin = new Roles();
        rolesAdmin.setId(1L);
        rolesAdmin.setName("ROLE_ADMIN");

        Roles rolesUser = new Roles();
        rolesUser.setId(2L);
        rolesUser.setName("ROLE_USER");

        roleRepository.save(rolesAdmin);
        roleRepository.save(rolesUser);
    }

    @Override
    @Transactional
    public void prepareUserDB() {
        User userAdmin = new User();
        userAdmin.setId(1L);
        userAdmin.setUsername("admin");
        userAdmin.setPassword("sam");
        userAdmin.setPasswordConfirm("sam");

        // 1. Сначала привязываем роль по умолчанию из базы данных
        roleRepository.findById(1L).ifPresent(role ->
                userAdmin.setRoles(Collections.singleton(role))
        );

        // 2. Хешируем пароль администратора
        userAdmin.setPassword(bCryptPasswordEncoder.encode(userAdmin.getPassword()));

        // 3. Сохраняем пользователя через USER_REPOSITORY (а не roleRepository!)
        userRepository.save(userAdmin);
    }


    // Единый переиспользуемый маппер
    private UserMsg mapToMsg(User u) {
        return UserMsg.newBuilder()
                .setId(u.getId())
                .setUsername(u.getUsername())
                .setPassword(u.getPassword())
                .addAllRoles(u.getRoles().stream()
                        .map(r -> RoleMsg.newBuilder().setId(r.getId()).setName(r.getName()).build())
                        .toList())
                .build();
    }
}