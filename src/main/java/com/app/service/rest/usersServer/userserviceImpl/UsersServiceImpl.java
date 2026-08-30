package com.app.service.rest.usersServer.userserviceImpl;

import com.app.grpc.RoleMsg;
import com.app.grpc.UserMsg;
import com.app.service.rest.usersServer.model.Roles;
import com.app.service.rest.usersServer.model.User;
import com.app.service.rest.usersServer.repository.RoleRepository;
import com.app.service.rest.usersServer.repository.UserRepository;
import com.app.service.rest.usersServer.userservice.UsersService;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor // Автоматически генерирует конструктор для всех private final полей
public class UsersServiceImpl implements UsersService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder bCryptPasswordEncoder;

    // Внедряем менеджер кэшей Caffeine напрямую
    private final org.springframework.cache.CacheManager cacheManager;

    // Нативные, потокобезопасные и дружелюбные к Loom кэши Caffeine
    private com.github.benmanes.caffeine.cache.Cache<String, Object> usersListCache;
    private com.github.benmanes.caffeine.cache.Cache<String, Object> userDetailsCache;

    @PostConstruct
    @SuppressWarnings("unchecked")
    public void initCaffeineCaches() {
        // Извлекаем чистые нативные кэши Caffeine, минуя блокирующие прокси Spring
        this.usersListCache = (com.github.benmanes.caffeine.cache.Cache<String, Object>)
                cacheManager.getCache("users_list").getNativeCache();
        this.userDetailsCache = (com.github.benmanes.caffeine.cache.Cache<String, Object>)
                cacheManager.getCache("user_details").getNativeCache();
        log.info("🚀 Lock-Free Caffeine кэши успешно интегрированы в обход ConcurrentHashMap.compute()");
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<UserMsg> getAllUsersProtobuf() {
        // 1. Быстрое чтение из RAM (Lock-Free)
        List<UserMsg> cached = (List<UserMsg>) usersListCache.getIfPresent("all_proto");
        if (cached != null) {
            return cached;
        }

        // 2. Сетевой I/O выполняется СВОБОДНО, вне замков мапы кэша
        log.info("💾 КЭШ МИСНУЛ (all_proto): Идем в PostgreSQL через JOIN FETCH");
        List<UserMsg> dbResult = userRepository.findAllWithRoles().stream()
                .map(this::mapToMsg)
                .toList();

        // 3. Атомарная запись через CAS-операции (Lock-Free)
        if (!dbResult.isEmpty()) {
            usersListCache.put("all_proto", dbResult);
        }
        return dbResult;
    }

    @Override
    public UserMsg findUserByUserNameProtobuf(String userName) {
        // 1. Быстрое Lock-Free чтение из RAM
        UserMsg cached = (UserMsg) userDetailsCache.getIfPresent(userName + "_proto");
        if (cached != null) {
            return cached;
        }

        // 2. Сетевой I/O выполняется СВОБОДНО, вне замков мапы кэша Caffeine
        log.info("💾 КЭШ МИСНУЛ (user_proto): Идем в PostgreSQL за пользователем: {}", userName);
        User user = userRepository.findByUsernameWithRoles(userName);
        if (user == null) {
            return null;
        }

        // 3. Мапим в Protobuf и делаем атомарную Lock-Free запись через CAS-операции
        UserMsg msg = mapToMsg(user);
        userDetailsCache.put(userName + "_proto", msg);
        return msg;
    }


    @Override
    @Transactional
    public boolean deleteUser(Long userId) {
        // 1. Атомарно чистим Lock-Free кэш Caffeine
        usersListCache.invalidateAll();
        userDetailsCache.invalidateAll();

        // 2. Сразу бьем нативным SQL. Метод executeUpdate вернет количество удаленных строк
        userRepository.deleteRolesByUserId(userId);
        int deletedRows = userRepository.deleteUserByIdNative(userId);

        return deletedRows > 0;
    }

    @Override
    public UserMsg findUserByIdProtobuf(Long userId) {
        // 1. Быстрое Lock-Free чтение по уникальному текстовому ключу ID
        UserMsg cached = (UserMsg) userDetailsCache.getIfPresent(userId + "_id_proto");
        if (cached != null) {
            return cached;
        }

        log.info("💾 КЭШ МИСНУЛ (user_id_proto): Идем в PostgreSQL за ID: {}", userId);
        // 2. Ищем в БД по ID с загрузкой ролей (кастомный метод репозитория или findById)
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }

        // 3. Мапим в Protobuf и атомарно сохраняем в кэш
        UserMsg msg = mapToMsg(user);
        userDetailsCache.put(userId + "_id_proto", msg);
        return msg;
    }


    @Override
    @Transactional
    public boolean saveUser(User user) {
        User userFromDB = userRepository.findByUsername(user.getUsername());
        if (userFromDB != null) {
            return false;
        }

        // Точечная атомарная инвалидация кэша Caffeine без ConcurrentHashMap.clear()
        usersListCache.invalidateAll();
        userDetailsCache.invalidate(user.getUsername());
        userDetailsCache.invalidate(user.getUsername() + "_proto");

        roleRepository.findById(2L).ifPresent(role ->
                user.setRoles(Collections.singleton(role))
        );

        // Сценарий 1 побежден: шифрование BCrypt использует неблокирующий SecureRandom
        user.setPassword(bCryptPasswordEncoder.encode(user.getPassword()));
        userRepository.save(user);
        return true;
    }

    // =================================================================
    // 🔥 ИСПРАВЛЕННАЯ РЕАЛИЗАЦИЯ МЕТОДОВ ПОДГОТОВКИ БД
    // =================================================================

    @Override
    @Transactional
    public boolean isRolesDBEmpty() {
        long count = roleRepository.count();
        log.info("🔍 gRPC Проверка: Запрос количества ролей в PostgreSQL. Найдено: {}", count);
        return count == 0;
    }

    @Override
    @Transactional
    public void prepareRolesDB() {
        if (roleRepository.count() == 0) {
            log.info("🛠 Инициализация таблицы ролей базовыми значениями...");
            Roles roleAdmin = new Roles();
            roleAdmin.setId(1L);
            roleAdmin.setName("ROLE_ADMIN");
            Roles roleUser = new Roles();
            roleUser.setId(2L);
            roleUser.setName("ROLE_USER");
            roleRepository.save(roleAdmin);
            roleRepository.save(roleUser);
            log.info("✅ Базовые роли (ROLE_ADMIN, ROLE_USER) успешно сохранены в БД");
        }
    }

    @Override
    @Transactional
    public void prepareUserDB() {
        if (userRepository.findByUsername("admin") == null) {
            log.info("🛠 Создание стартовой учетной записи администратора...");

            User userAdmin = new User();
            // Позволяем JPA/PostgreSQL управлять ID, либо раскомментируйте строку ниже, если ID строго захардкожен:
            // userAdmin.setId(1L);
            userAdmin.setUsername("admin");
            userAdmin.setPassword("sam");
            userAdmin.setPasswordConfirm("sam"); // Для прохождения внутренней валидации, если она есть

            // Привязываем ROLE_ADMIN (ID: 1L)
            roleRepository.findById(1L).ifPresent(role ->
                    userAdmin.setRoles(Collections.singleton(role))
            );

            // Безопасное неблокирующее шифрование пароля
            userAdmin.setPassword(bCryptPasswordEncoder.encode(userAdmin.getPassword()));
            userRepository.save(userAdmin);

            // 🔥 Точечная Lock-Free инвалидация кэша, чтобы клиент сразу увидел изменения в RAM
            usersListCache.invalidateAll();
            userDetailsCache.invalidate("admin");
            userDetailsCache.invalidate("admin_proto");

            log.info("✅ Администратор 'admin' успешно создан. Кэш Caffeine сброшен.");
        }
    }

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

