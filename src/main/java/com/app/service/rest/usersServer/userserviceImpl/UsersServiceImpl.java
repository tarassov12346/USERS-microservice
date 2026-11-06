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

    // ==================================================================================
    // ПОЧЕМУ ИСПОЛЬЗУЕТСЯ @PostConstruct ВМЕСТО ИНИЦИАЛИЗАЦИИ В КОНСТРУКТОРЕ:
    //
    // 1. Ограничение жизненного цикла Spring CacheManager. В момент выполнения конструктора
    //    инфраструктура менеджера кэша уже существует, но внутренние целевые контуры
    //    ("users_list" и "user_details") еще не проинициализированы настройками Caffeine.
    //    Попытка вызвать .getCache() в конструкторе вернет null или «сырой» объект.
    //
    // 2. Гарантия готовности кэш-контуров. Аннотация @PostConstruct гарантирует, что Spring
    //    успешно завершил создание и конфигурацию CacheManager. Извлечение нативного кэша
    //    через .getNativeCache() на этой фазе становится на 100% безопасным.
    //
    //
    //  3. Обход NoSuchBeanDefinitionException. Нативные объекты Caffeine Cache создаются внутри
    //     Spring-менеджера динамически на основании конфигурационных файлов. В самом IoC-контексте
    //     Spring они не зарегистрированы как самостоятельные бины, поэтому внедрить их напрямую
    //     через конструктор по типу Cache<String, Object> невозможно (потому что Caffeine спрятан
    //     глубоко внутри менеджера)— Spring выбросит ошибку NoSuchBeanDefinitionException.
    //     Прямое извлечение из CacheManager как у меня решает эту проблему.

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

    // ==================================================================================
    // ЗАЩИТА ОТ АЛЛОКАЦИОННОГО ПИННИНГА (LOCK-FREE ПОИСК ПО ДИНАМИЧЕСКИМ ID):
    //
    // Метод выполняет сверхбыстрое Lock-Free чтение профиля пользователя по его числовому ID.
    //
    // ПОЧЕМУ СТАНДАРТНЫЙ SPRING CACHE ЗДЕСЬ ПАДАЛ В THREAD PINNING:
    // На этапе старта невозможно предугадать автоинкрементные ID, которые PostgreSQL выдаст игрокам.
    // При первом входе нового пользователя стандартный механизм мапы (ConcurrentHashMap.compute)
    // сталкивался с числовым ключом типа Long, под который в памяти еще не существовало ячейки.
    // Чтобы выделить бакет под новый ID, мапа активировала внутренний блок synchronized(node).
    // Любой виртуальный поток (лум), попавший в этот замок во время проверки кэша, намертво
    // прилипал к физическому потоку ОС (Thread Pinning), парализуя рантайм Java 21.
    //
    // КАК ЭТО УСТРАНЕНО (ИНЖЕНЕРНОЕ РЕШЕНИЕ CAFFEINE):
    // 1. Строковый CAS-ключ: Ключ переведен в строку (userId + "_id_proto"). Это равномерно
    //    раскидывает данные по памяти, чтобы потоки не мешали друг другу при создании новых ячеек.
    // 2. Чтение без мутации структуры RAM: Метод .getIfPresent() спроектирован как строго
    //    неблокирующий. При промахе кэша (первый вызов ID) он за наносекунды возвращает null
    //    БЕЗ попыток выделить память под новую ячейку внутри мапы и БЕЗ активации synchronized.
    // 3. CAS-запись на свободе: Сетевой поход в PostgreSQL выполняется свободно вне замков кэша.
    //    Последующая запись через .put() выполняется библиотекой Caffeine на базе атомарных
    //    процессорных CAS-операций (Compare-And-Swap), что гарантирует 0% пиннинга в Java 21.


    @Override
    public UserMsg findUserByIdProtobuf(Long userId) {
        // 1. Быстрое Lock-Free чтение по уникальному текстовому ключу ID
        UserMsg cached = (UserMsg) userDetailsCache.getIfPresent(userId + "_id_proto");
        if (cached != null) {
            return cached;
        }

        log.info("💾 КЭШ МИСНУЛ (user_id_proto): Идем в PostgreSQL за ID: {}", userId);
        // 2. Ищем в БД по ID с загрузкой ролей (кастомный метод репозитория или findById)
        User user = userRepository.findByIdWithRoles(userId).orElse(null);
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
            roleAdmin.setName("ROLE_ADMIN"); // ID не трогаем, он равен 0 по умолчанию

            Roles roleUser = new Roles();
            roleUser.setName("ROLE_USER");   // ID не трогаем, он равен 0 по умолчанию

            // Сохраняем пачкой и принудительно пушим в базу данных здесь и сейчас
            roleRepository.saveAllAndFlush(java.util.List.of(roleAdmin, roleUser));

            log.info("✅ Базовые роли (ROLE_ADMIN, ROLE_USER) успешно сохранены в БД");
        }
    }


    @Override
    @Transactional
    public void prepareUserDB() {
        if (userRepository.findByUsername("admin") == null) {
            log.info("🛠 Создание стартовой учетной записи администратора...");

            User userAdmin = new User();
            // Позволяем JPA/PostgreSQL управлять ID, либо можно раскомментировать строку ниже, если захотим ID строго захардкорить:
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
    // ==================================================================================
    // АРХИТЕКТУРНЫЙ ДОНОР ДАННЫХ (СКВОЗНОЙ REACTION-FREE МАППИНГ):
    //
    // Метод преобразует JPA-сущность базы данных в неизменяемую (Immutable) Protobuf-модель.
    // Класс UserMsg используется как единый контейнер данных (DTO) одновременно для слоев
    // REST (HTTP/1.1) и gRPC (HTTP/2), что полностью ликвидирует дублирование логики маппинга.
    //
    // ПОЧЕМУ ЭТО ОПТИМИЗИРОВАНО ПОД LOOM И ВИРТУАЛЬНЫЕ ПОТОКИ:
    //
    // 1. Потокобезопасность в RAM: Сгенерированный Protobuf-объект UserMsg обладает 100%
    //    иммутабельной и Lock-Free структурой. Это позволяет кэшировать его в оперативной памяти
    //    Caffeine без риска конкурентных искажений данных между миллионами параллельных лумов.
    //
    // 2. Упреждающий прогрев кэша (REST -> gRPC): REST-авторизация пользователя (через вызов
    //    этого метода внутри одной JVM) упреждающе наполняет кэш user_details готовыми
    //    объектами UserMsg. Когда игровой движок через gRPC запрашивает этот профиль,
    //    транспортный слой Netty забирает уже скомпилированный UserMsg из памяти БЕЗ единого
    //    повторного обращения к Hibernate/PostgreSQL и БЕЗ тормозов тяжелой рефлексии.
    // ==================================================================================

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

