package com.app.service.rest.usersServer;


import com.app.grpc.*;
import com.app.service.rest.usersServer.model.User;
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

    private final UsersService usersService;

    @Override
    public void findByUsername(SearchRequest request, StreamObserver<UserResponse> responseObserver) {
        log.info("📡 gRPC: Поиск пользователя {}", request.getValue());

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

        // Работаем со скомпилированным Protobuf из RAM, минуя мапы сессий Hibernate!
        UserMsg userMsg = usersService.findUserByIdProtobuf(request.getId());
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
    public void getAllUsers(UserEmpty request, StreamObserver<UserListResponse> responseObserver) {
        log.info("📡 gRPC: Запрос всех пользователей");

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
            user.setPassword(request.getPassword());

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
            boolean deleted = usersService.deleteUser(request.getId());
            responseObserver.onNext(ActionResponse.newBuilder().setSuccess(deleted).build());
        } catch (Exception e) {
            log.error("❌ Ошибка gRPC DeleteUser: {}", e.getMessage());
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

    // Внутри UserGrpcController (Микросервис)
    @Override
    public void prepareRolesDB(UserEmpty request, StreamObserver<ActionResponse> responseObserver) {
        log.info("📡 gRPC: Запуск комплексной подготовки БД (Роли + Дефолтный Админ)");
        try {
            // Выполняются последовательно в одном потоке сервера.
            // Вторая транзакция гарантированно увидит данные первой!
            usersService.prepareRolesDB();
            usersService.prepareUserDB();

            responseObserver.onNext(ActionResponse.newBuilder().setSuccess(true).build());
        } catch (Exception e) {
            responseObserver.onNext(ActionResponse.newBuilder().setSuccess(false).build());
        }
        responseObserver.onCompleted();
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