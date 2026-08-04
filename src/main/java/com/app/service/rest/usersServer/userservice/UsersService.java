package com.app.service.rest.usersServer.userservice;

import com.app.grpc.UserMsg;
import com.app.service.rest.usersServer.model.User;
import java.util.List;

public interface UsersService {

    boolean deleteUser(Long userId);

    boolean saveUser(User user);

    boolean isRolesDBEmpty();

    void prepareRolesDB();

    void prepareUserDB();

    // === ДОБАВЬТЕ ЭТИ ДВЕ СТРОЧКИ В КОНЕЦ ИНТЕРФЕЙСА ===
    List<UserMsg> getAllUsersProtobuf();

    UserMsg findUserByUserNameProtobuf(String userName);

    UserMsg findUserByIdProtobuf(Long userId);
}
