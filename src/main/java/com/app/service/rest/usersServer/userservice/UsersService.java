package com.app.service.rest.usersServer.userservice;

import com.app.service.rest.usersServer.model.User;

import java.util.List;

public interface UsersService {
    List<User> getAllUsers();

    boolean deleteUser(Long userId);

    User findUserById(Long userId);

    User findUserByUserName(String userName);

    boolean saveUser(User user);

    boolean isRolesDBEmpty();

    void prepareRolesDB();

    void prepareUserDB();
}
