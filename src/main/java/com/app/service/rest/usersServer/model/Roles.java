package com.app.service.rest.usersServer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*; // ИМПОРТ ИЗМЕНЕН

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_role")
public class Roles {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String name;
}
