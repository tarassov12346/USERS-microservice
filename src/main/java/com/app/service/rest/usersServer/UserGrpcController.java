package com.app.service.rest.usersServer;


import com.app.grpc.*;
import com.app.service.rest.usersServer.model.User;
import com.app.service.rest.usersServer.repository.RoleRepository;
import com.app.service.rest.usersServer.repository.UserRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;

@GrpcService
@Slf4j
@RequiredArgsConstructor
public class UserGrpcController extends UserServiceGrpc.UserServiceImplBase{
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Override
    public void findByUsername(SearchRequest request, StreamObserver<UserResponse> responseObserver) {
        log.info("📡 gRPC: Поиск пользователя {}", request.getValue());
        User user = userRepository.findByUsername(request.getValue()); // Тут просто User

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
    public void findById(IdRequest request, StreamObserver<UserResponse> responseObserver) {
        var userOpt = userRepository.findById(request.getId());
        UserResponse.Builder builder = UserResponse.newBuilder();

        userOpt.ifPresentOrElse(
                u -> builder.setExists(true).setUser(mapToMsg(u)),
                () -> builder.setExists(false)
        );

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getAllUsers(UserEmpty request, StreamObserver<UserListResponse> responseObserver) {
        log.info("📡 gRPC: Запрос всех пользователей");
        List<UserMsg> users = userRepository.findAll().stream()
                .map(this::mapToMsg)
                .toList();

        responseObserver.onNext(UserListResponse.newBuilder().addAllUsers(users).build());
        responseObserver.onCompleted();
    }

    @Override
    public void saveUser(UserMsg request, StreamObserver<ActionResponse> responseObserver) {
        log.info("📡 gRPC: Сохранение {}", request.getUsername());
        try {
            User user = new User();
            if (request.getId() > 0) user.setId(request.getId());
            user.setUsername(request.getUsername());
            user.setPassword(request.getPassword());
            // Добавь маппинг ролей из request.getRolesList()

            userRepository.save(user);
            responseObserver.onNext(ActionResponse.newBuilder().setSuccess(true).build());
        } catch (Exception e) {
            log.error("❌ Ошибка gRPC SaveUser: {}", e.getMessage());
            responseObserver.onNext(ActionResponse.newBuilder().setSuccess(false).build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void deleteUser(IdRequest request, StreamObserver<ActionResponse> responseObserver) {
        try {
            userRepository.deleteById(request.getId());
            responseObserver.onNext(ActionResponse.newBuilder().setSuccess(true).build());
        } catch (Exception e) {
            responseObserver.onNext(ActionResponse.newBuilder().setSuccess(false).build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void isRolesEmpty(UserEmpty request, StreamObserver<ActionResponse> responseObserver) {
        boolean isEmpty = roleRepository.count() == 0;
        responseObserver.onNext(ActionResponse.newBuilder().setSuccess(isEmpty).build());
        responseObserver.onCompleted();
    }

    @Override
    public void prepareRolesDB(UserEmpty request, StreamObserver<ActionResponse> responseObserver) {
        log.info("📡 gRPC: Подготовка БД Ролей");
        // Вызови метод своего сервиса для наполнения ролей
        responseObserver.onNext(ActionResponse.newBuilder().setSuccess(true).build());
        responseObserver.onCompleted();
    }

    @Override
    public void prepareUserDB(UserEmpty request, StreamObserver<ActionResponse> responseObserver) {
        log.info("📡 gRPC: Подготовка БД Пользователей");
        // Вызови метод своего сервиса для создания админа
        responseObserver.onNext(ActionResponse.newBuilder().setSuccess(true).build());
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
