package com.app.service.rest.usersServer.repository;

import com.app.service.rest.usersServer.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByUsername(String username);
}
