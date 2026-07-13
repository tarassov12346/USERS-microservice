package com.app.service.rest.usersServer;


import com.app.grpc.*;
import com.app.service.rest.usersServer.model.User;
import com.app.service.rest.usersServer.repository.RoleRepository;
import com.app.service.rest.usersServer.repository.UserRepository;
import com.app.service.rest.usersServer.userservice.UsersService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;

@GrpcService
@Slf4j
@RequiredArgsConstructor
public class UserGrpcController extends UserServiceGrpc.UserServiceImplBase {

    // Оставляем ТОЛЬКО сервис. Никаких репозиториев напрямую!
    private final UsersService usersService;

    @Override
    public void findByUsername(SearchRequest request, StreamObserver<UserResponse> responseObserver) {
        log.info("📡 gRPC: Поиск пользователя {}", request.getValue());

        // Переключаем на кэширующий метод сервиса, который мы сделаем
        UserMsg userMsg = usersService.findUserByUserNameProtobuf(request.getValue());
        UserResponse.Builder builder = UserResponse.newBuilder();

        if (userMsg != null) {
            builder.setExists(true).setUser(userMsg);
        } else {
            builder.setExists(false);
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void findById(IdRequest request, StreamObserver<UserResponse> responseObserver) {
        log.info("📡 gRPC: Поиск пользователя по ID: {}", request.getId());

        // Переключаем на метод сервиса
        User user = usersService.findUserById(request.getId());
        UserResponse.Builder builder = UserResponse.newBuilder();

        if (user != null) {
            builder.setExists(true).setUser(mapToMsg(user));
        } else {
            builder.setExists(false);
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getAllUsers(UserEmpty request, StreamObserver<UserListResponse> responseObserver) {
        log.info("📡 gRPC: Запрос всех пользователей");

        // Достаем ОПТИМИЗИРОВАННЫЙ и ЗА КЭШИРОВАННЫЙ список Protobuf-сообщений
        List<UserMsg> users = usersService.getAllUsersProtobuf();

        responseObserver.onNext(UserListResponse.newBuilder().addAllUsers(users).build());
        responseObserver.onCompleted();
    }

    @Override
    public void saveUser(UserMsg request, StreamObserver<ActionResponse> responseObserver) {
        log.info("📡 gRPC: Сохранение через сервис для {}", request.getUsername());
        try {
            User user = new User();
            if (request.getId() > 0) user.setId(request.getId());
            user.setUsername(request.getUsername());

            // ВАЖНО: передаем сырой пароль, сервис САМ захеширует его через BCrypt!
            user.setPassword(request.getPassword());

            // Вызываем правильный метод сервиса с поддержкой @Transactional и @CacheEvict
            boolean saved = usersService.saveUser(user);
            responseObserver.onNext(ActionResponse.newBuilder().setSuccess(saved).build());
        } catch (Exception e) {
            log.error("❌ Ошибка gRPC SaveUser: {}", e.getMessage());
            responseObserver.onNext(ActionResponse.newBuilder().setSuccess(false).build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void deleteUser(IdRequest request, StreamObserver<ActionResponse> responseObserver) {
        log.info("📡 gRPC: Удаление пользователя ID: {}", request.getId());
        try {
            // Вызываем метод сервиса с поддержкой @CacheEvict
            boolean deleted = usersService.deleteUser(request.getId());
            responseObserver.onNext(ActionResponse.newBuilder().setSuccess(deleted).build());
        } catch (Exception e) {
            responseObserver.onNext(ActionResponse.newBuilder().setSuccess(false).build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void isRolesEmpty(UserEmpty request, StreamObserver<ActionResponse> responseObserver) {
        boolean isEmpty = usersService.isRolesDBEmpty();
        responseObserver.onNext(ActionResponse.newBuilder().setSuccess(isEmpty).build());
        responseObserver.onCompleted();
    }

    @Override
    public void prepareRolesDB(UserEmpty request, StreamObserver<ActionResponse> responseObserver) {
        log.info("📡 gRPC: Подготовка БД Ролей");
        usersService.prepareRolesDB();
        responseObserver.onNext(ActionResponse.newBuilder().setSuccess(true).build());
        responseObserver.onCompleted();
    }

    @Override
    public void prepareUserDB(UserEmpty request, StreamObserver<ActionResponse> responseObserver) {
        log.info("📡 gRPC: Подготовка БД Пользователей");
        usersService.prepareUserDB();
        responseObserver.onNext(ActionResponse.newBuilder().setSuccess(true).build());
        responseObserver.onCompleted();
    }

    // Оставляем локальный маппер только для точечного findById
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