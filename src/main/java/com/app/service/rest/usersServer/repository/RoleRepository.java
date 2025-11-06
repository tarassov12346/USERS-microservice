package com.app.service.rest.usersServer.repository;

import com.app.service.rest.usersServer.model.Roles;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Roles, Long> {
}
