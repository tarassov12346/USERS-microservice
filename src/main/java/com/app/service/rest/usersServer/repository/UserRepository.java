package com.app.service.rest.usersServer.repository;

import com.app.service.rest.usersServer.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    // Старый метод оставляем для авторизации (там роли сразу не нужны)
    User findByUsername(String username);

    // Новый метод: загружает всех пользователей и их роли ЗА ОДИН SQL-ЗАПРОС
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles")
    List<User> findAllWithRoles();

    // Новый метод: оптимизированный поиск по имени для gRPC
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE u.username = :username")
    User findByUsernameWithRoles(@Param("username") String username);
}
