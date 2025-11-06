package com.app.service.rest.usersServer.userserviceImpl;

import com.app.service.rest.usersServer.userservice.UsersService;
import com.app.service.rest.usersServer.model.Roles;
import com.app.service.rest.usersServer.model.User;
import com.app.service.rest.usersServer.repository.RoleRepository;
import com.app.service.rest.usersServer.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class UsersServiceImpl implements UsersService{

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PasswordEncoder bCryptPasswordEncoder;


    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public boolean deleteUser(Long userId) {
        if (userRepository.findById(userId).isPresent()) {
            userRepository.findById(userId).get().getRoles().clear();
            userRepository.deleteById(userId);
            return true;
        }
        return false;
    }

    @Override
    public User findUserById(Long userId) {
        return userRepository.findById(userId).get();
    }

    @Override
    public User findUserByUserName(String userName) {
        return userRepository.findByUsername(userName);
    }

    @Override
    public boolean saveUser(User user) {
        User userFromDB = userRepository.findByUsername(user.getUsername());
        if (userFromDB != null) {
            return false;
        }
        user.setRoles(Collections.singleton(roleRepository.findById(2L).get()));
        user.setPassword(bCryptPasswordEncoder.encode(user.getPassword()));
        userRepository.save(user);
        return true;
    }

    @Override
    public boolean isRolesDBEmpty() {
        return !roleRepository.findById(2L).isPresent();
    }

    @Override
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
    public void prepareUserDB() {
        User userAdmin = new User();
        userAdmin.setId(1L);
        userAdmin.setUsername("admin");
        userAdmin.setPassword("sam");
        userAdmin.setPasswordConfirm("sam");
        userAdmin.setRoles(Collections.singleton(roleRepository.findById(1L).get()));
        userAdmin.setPassword(bCryptPasswordEncoder.encode(userAdmin.getPassword()));
        userRepository.save(userAdmin);
    }

}
